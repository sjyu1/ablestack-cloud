//
// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.
//

package com.cloud.hypervisor.kvm.resource.wrapper;

import com.cloud.agent.api.Answer;
import com.cloud.hypervisor.kvm.resource.LibvirtComputingResource;
import com.cloud.hypervisor.kvm.storage.KVMPhysicalDisk;
import com.cloud.hypervisor.kvm.storage.KVMStoragePool;
import com.cloud.hypervisor.kvm.storage.KVMStoragePoolManager;
import com.cloud.resource.CommandWrapper;
import com.cloud.resource.ResourceWrapper;
import com.cloud.storage.Storage;
import com.cloud.utils.Pair;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.utils.script.Script;
import com.cloud.vm.VirtualMachine;
import org.apache.cloudstack.backup.BackupAnswer;
import org.apache.cloudstack.backup.CommvaultRestoreBackupCommand;
import org.apache.cloudstack.storage.to.PrimaryDataStoreTO;
import org.apache.cloudstack.utils.qemu.QemuImg;
import org.apache.cloudstack.utils.qemu.QemuImgException;
import org.apache.cloudstack.utils.qemu.QemuImgFile;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.libvirt.LibvirtException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@ResourceWrapper(handles = CommvaultRestoreBackupCommand.class)
public class LibvirtCommvaultRestoreBackupCommandWrapper extends CommandWrapper<CommvaultRestoreBackupCommand, Answer, LibvirtComputingResource> {
    private static final String FILE_PATH_PLACEHOLDER = "%s/%s";
    private static final String ATTACH_QCOW2_DISK_COMMAND = " virsh attach-disk %s %s %s --driver qemu --subdriver qcow2 --cache none";
    private static final String ATTACH_RBD_DISK_XML_COMMAND = " virsh attach-device %s /dev/stdin <<EOF%sEOF";
    private static final String CURRRENT_DEVICE = "virsh domblklist --domain %s | tail -n 3 | head -n 1 | awk '{print $1}'";
    private static final String RSYNC_COMMAND = "rsync -az %s %s";
    private static final String MKDIR_P = "mkdir -p %s";
    private static final String RSYNC_DIR_FROM_REMOTE = "rsync -az -e \"ssh -o StrictHostKeyChecking=no\" %s:%s/ %s/";

    @Override
    public Answer execute(CommvaultRestoreBackupCommand command, LibvirtComputingResource serverResource) {
        String vmName = command.getVmName();
        String backupPath = command.getBackupPath();
        Boolean vmExists = command.isVmExists();
        String diskType = command.getDiskType();
        List<String> backedVolumeUUIDs = command.getBackupVolumesUUIDs();
        List<String> backupFiles = command.getBackupFiles();
        List<String> backupFileChains = command.getBackupFileChains();
        List<PrimaryDataStoreTO> restoreVolumePools = command.getRestoreVolumePools();
        List<String> restoreVolumePaths = command.getRestoreVolumePaths();
        String restoreVolumeUuid = command.getRestoreVolumeUUID();
        int timeout = command.getTimeout();
        String cacheMode = command.getCacheMode();
        String hostName = command.getHostName();
        KVMStoragePoolManager storagePoolMgr = serverResource.getStoragePoolMgr();

        String newVolumeId = null;
        try {
            if (hostName != null) {
                fetchBackupFile(hostName, backupPath);
            }
            if (Objects.isNull(vmExists)) {
                PrimaryDataStoreTO volumePool = restoreVolumePools.get(0);
                String volumePath = restoreVolumePaths.get(0);
                int lastIndex = volumePath.lastIndexOf("/");
                newVolumeId = volumePath.substring(lastIndex + 1);
                restoreVolume(storagePoolMgr, backupPath, volumePool, volumePath, diskType, restoreVolumeUuid, backupFiles, backupFileChains,
                        new Pair<>(vmName, command.getVmState()), timeout, cacheMode);
            } else if (Boolean.TRUE.equals(vmExists)) {
                restoreVolumesOfExistingVM(storagePoolMgr, restoreVolumePools, restoreVolumePaths, backedVolumeUUIDs, backupPath, backupFiles, backupFileChains, timeout);
            } else {
                restoreVolumesOfDestroyedVMs(storagePoolMgr, restoreVolumePools, restoreVolumePaths, vmName, backupPath, backupFiles, backupFileChains, timeout);
            }
        } catch (CloudRuntimeException e) {
            String errorMessage = e.getMessage() != null ? e.getMessage() : "";
            return new BackupAnswer(command, false, errorMessage);
        }

        return new BackupAnswer(command, true, newVolumeId);
    }

    private void restoreVolumesOfExistingVM(KVMStoragePoolManager storagePoolMgr, List<PrimaryDataStoreTO> restoreVolumePools, List<String> restoreVolumePaths, List<String> backedVolumesUUIDs,
                                            String backupPath, List<String> backupFiles, List<String> backupFileChains, int timeout) {
        String diskType = "root";
        try {
            for (int idx = 0; idx < restoreVolumePaths.size(); idx++) {
                PrimaryDataStoreTO restoreVolumePool = restoreVolumePools.get(idx);
                String restoreVolumePath = restoreVolumePaths.get(idx);
                String backupVolumeUuid = backedVolumesUUIDs.get(idx);
                List<String> localBackupPaths = getLocalBackupPaths(backupPath, backupFiles, backupFileChains, idx, getLegacyBackupFileName(diskType, backupVolumeUuid));
                diskType = "datadisk";
                if (!replaceVolumeWithBackup(storagePoolMgr, restoreVolumePool, restoreVolumePath, localBackupPaths, timeout, backupPath, idx)) {
                    throw new CloudRuntimeException(String.format("Unable to restore contents from the backup volume [%s].", backupVolumeUuid));
                }
            }
        } finally {
            deleteBackupDirectory(backupPath);
        }
    }

    private void restoreVolumesOfDestroyedVMs(KVMStoragePoolManager storagePoolMgr, List<PrimaryDataStoreTO> volumePools, List<String> volumePaths, String vmName, String backupPath,
                                              List<String> backupFiles, List<String> backupFileChains, int timeout) {
        String diskType = "root";
        try {
            for (int i = 0; i < volumePaths.size(); i++) {
                PrimaryDataStoreTO volumePool = volumePools.get(i);
                String volumePath = volumePaths.get(i);
                String volumeUuid = volumePath.substring(volumePath.lastIndexOf(File.separator) + 1);
                List<String> localBackupPaths = getLocalBackupPaths(backupPath, backupFiles, backupFileChains, i, getLegacyBackupFileName(diskType, volumeUuid));
                diskType = "datadisk";
                if (!replaceVolumeWithBackup(storagePoolMgr, volumePool, volumePath, localBackupPaths, timeout, backupPath, i)) {
                    throw new CloudRuntimeException(String.format("Unable to restore contents from the backup volume [%s].", volumeUuid));
                }
            }
        } finally {
            deleteBackupDirectory(backupPath);
        }
    }

    private void restoreVolume(KVMStoragePoolManager storagePoolMgr, String backupPath, PrimaryDataStoreTO volumePool, String volumePath, String diskType, String volumeUUID,
                               List<String> backupFiles, List<String> backupFileChains,
                               Pair<String, VirtualMachine.State> vmNameAndState, int timeout, String cacheMode) {
        try {
            List<String> localBackupPaths = getLocalBackupPaths(backupPath, backupFiles, backupFileChains, 0, getLegacyBackupFileName(diskType, volumeUUID));
            if (!replaceVolumeWithBackup(storagePoolMgr, volumePool, volumePath, localBackupPaths, timeout, backupPath, 0, true)) {
                throw new CloudRuntimeException(String.format("Unable to restore contents from the backup volume [%s].", volumeUUID));
            }
            if (VirtualMachine.State.Running.equals(vmNameAndState.second())) {
                if (!attachVolumeToVm(storagePoolMgr, vmNameAndState.first(), volumePool, volumePath, cacheMode)) {
                    throw new CloudRuntimeException(String.format("Failed to attach volume to VM: %s", vmNameAndState.first()));
                }
            }
        } finally {
            deleteBackupDirectory(backupPath);
        }
    }

    private void deleteBackupDirectory(String backupDirectory) {
        try {
            FileUtils.deleteDirectory(new File(backupDirectory));
        } catch (IOException e) {
            logger.error(String.format("Failed to delete backup directory: %s", backupDirectory), e);
            throw new CloudRuntimeException("Failed to delete the backup directory");
        }
    }

    private List<String> getLocalBackupPaths(String backupPath, List<String> backupFiles, List<String> backupFileChains, int index, String legacyBackupFileName) {
        List<String> localPaths = new ArrayList<>();
        if (backupFileChains != null && backupFileChains.size() > index && StringUtils.isNotBlank(backupFileChains.get(index))) {
            for (String chainPath : backupFileChains.get(index).split(";")) {
                if (StringUtils.isBlank(chainPath)) {
                    continue;
                }
                localPaths.add(resolveBackupPath(backupPath, chainPath));
            }
        }
        if (localPaths.isEmpty() && backupFiles != null && backupFiles.size() > index && StringUtils.isNotBlank(backupFiles.get(index))) {
            localPaths.add(resolveBackupPath(backupPath, backupFiles.get(index)));
        }
        if (localPaths.isEmpty()) {
            localPaths.add(String.format(FILE_PATH_PLACEHOLDER, backupPath, legacyBackupFileName));
        }
        return localPaths;
    }

    private String resolveBackupPath(String backupPath, String chainPath) {
        if (chainPath.startsWith("/")) {
            return chainPath;
        }
        if (chainPath.contains("/")) {
            return String.format(FILE_PATH_PLACEHOLDER, backupPath, chainPath);
        }
        return String.format(FILE_PATH_PLACEHOLDER, backupPath, chainPath);
    }

    private String getLegacyBackupFileName(String diskType, String volumeUuid) {
        return String.format("%s.%s.qcow2", diskType.toLowerCase(Locale.ROOT), volumeUuid);
    }

    private boolean checkBackupFileImage(String backupPath) {
        int exitValue = Script.runSimpleBashScriptForExitValue(String.format("qemu-img check %s", backupPath));
        return exitValue == 0;
    }

    private boolean checkBackupPathExists(String backupPath) {
        int exitValue = Script.runSimpleBashScriptForExitValue(String.format("ls %s", backupPath));
        return exitValue == 0;
    }

    private boolean replaceVolumeWithBackup(KVMStoragePoolManager storagePoolMgr, PrimaryDataStoreTO volumePool, String volumePath, List<String> backupPaths, int timeout) {
        return replaceVolumeWithBackup(storagePoolMgr, volumePool, volumePath, backupPaths, timeout, null, 0, false);
    }

    private boolean replaceVolumeWithBackup(KVMStoragePoolManager storagePoolMgr, PrimaryDataStoreTO volumePool, String volumePath, List<String> backupPaths, int timeout,
                                            String backupRootPath, int backupIndex) {
        return replaceVolumeWithBackup(storagePoolMgr, volumePool, volumePath, backupPaths, timeout, backupRootPath, backupIndex, false);
    }

    private boolean replaceVolumeWithBackup(KVMStoragePoolManager storagePoolMgr, PrimaryDataStoreTO volumePool, String volumePath, List<String> backupPaths, int timeout,
                                            String backupRootPath, int backupIndex, boolean createTargetVolume) {
        if (backupPaths == null || backupPaths.isEmpty()) {
            return false;
        }
        if (volumePool.getPoolType() != Storage.StoragePoolType.RBD) {
            if (backupPaths.stream().anyMatch(path -> path.endsWith(".rbdiff"))) {
                return restoreIncrementalRbdBackupChainToFileVolume(volumePath, backupPaths, timeout, backupRootPath, backupIndex);
            }
            return replaceFileVolumeWithBackup(volumePath, getLastExistingBackupPath(backupPaths), timeout);
        }

        return replaceRbdVolumeWithBackup(storagePoolMgr, volumePool, volumePath, backupPaths, timeout, createTargetVolume);
    }

    private boolean restoreIncrementalRbdBackupChainToFileVolume(String volumePath, List<String> backupPaths, int timeout, String backupRootPath, int backupIndex) {
        if (StringUtils.isBlank(backupRootPath)) {
            throw new CloudRuntimeException("Unable to locate backup root path for incremental RBD restore");
        }
        RbdImageSpec sourceImage = getRbdImageSpecFromMetadata(backupRootPath, backupIndex);
        String tempImage = sourceImage.buildTempImageSpec();
        try {
            if (!importBackupChainToTemporaryRbd(backupPaths, timeout, sourceImage, tempImage)) {
                return false;
            }
            return convertTemporaryRbdToFileVolume(volumePath, timeout, sourceImage, tempImage);
        } finally {
            removeTemporaryRbdImage(sourceImage, tempImage, timeout);
        }
    }

    private String getFirstExistingBackupPath(List<String> backupPaths) {
        for (String backupPath : backupPaths) {
            if (StringUtils.isNotBlank(backupPath) && Files.exists(Paths.get(backupPath))) {
                return backupPath;
            }
        }
        return backupPaths.get(0);
    }

    private String getLastExistingBackupPath(List<String> backupPaths) {
        for (int i = backupPaths.size() - 1; i >= 0; i--) {
            String backupPath = backupPaths.get(i);
            if (StringUtils.isNotBlank(backupPath) && Files.exists(Paths.get(backupPath))) {
                return backupPath;
            }
        }
        return backupPaths.get(backupPaths.size() - 1);
    }

    private boolean replaceFileVolumeWithBackup(String volumePath, String backupPath, int timeout) {
        QemuImgFile srcBackupFile = null;
        QemuImgFile destVolumeFile = null;
        try {
            QemuImg qemu = new QemuImg(timeout * 1000, true, false);
            srcBackupFile = new QemuImgFile(backupPath, getBackupFileFormat(backupPath));
            destVolumeFile = new QemuImgFile(volumePath, getFileVolumeFormat(volumePath));
            qemu.convert(srcBackupFile, destVolumeFile);
            return true;
        } catch (QemuImgException | LibvirtException e) {
            String srcFilename = srcBackupFile != null ? srcBackupFile.getFileName() : null;
            String destFilename = destVolumeFile != null ? destVolumeFile.getFileName() : null;
            logger.error("Failed to convert backup {} to volume {}, the error was: {}", srcFilename, destFilename, e.getMessage());
            return false;
        }
    }

    private boolean convertTemporaryRbdToFileVolume(String volumePath, int timeout, RbdImageSpec sourceImage, String tempImage) {
        QemuImgFile srcBackupFile = null;
        QemuImgFile destVolumeFile = null;
        try {
            QemuImg qemu = new QemuImg(timeout * 1000, true, false);
            srcBackupFile = new QemuImgFile(sourceImage.buildQemuUri(tempImage), QemuImg.PhysicalDiskFormat.RAW);
            destVolumeFile = new QemuImgFile(volumePath, getFileVolumeFormat(volumePath));
            qemu.convert(srcBackupFile, destVolumeFile);
            return true;
        } catch (QemuImgException | LibvirtException e) {
            String srcFilename = srcBackupFile != null ? srcBackupFile.getFileName() : tempImage;
            String destFilename = destVolumeFile != null ? destVolumeFile.getFileName() : volumePath;
            logger.error("Failed to convert temporary RBD {} to volume {}, the error was: {}", srcFilename, destFilename, e.getMessage());
            return false;
        }
    }

    private QemuImg.PhysicalDiskFormat getBackupFileFormat(String backupPath) {
        if (backupPath.endsWith(".raw")) {
            return QemuImg.PhysicalDiskFormat.RAW;
        }
        return QemuImg.PhysicalDiskFormat.QCOW2;
    }

    private QemuImg.PhysicalDiskFormat getFileVolumeFormat(String volumePath) {
        if (!Files.exists(Paths.get(volumePath))) {
            return QemuImg.PhysicalDiskFormat.QCOW2;
        }
        try {
            QemuImg qemu = new QemuImg(0);
            java.util.Map<String, String> info = qemu.info(new QemuImgFile(volumePath));
            String format = info.get("file_format");
            if (StringUtils.isNotBlank(format)) {
                return QemuImg.PhysicalDiskFormat.valueOf(format.toUpperCase(Locale.ROOT));
            }
        } catch (QemuImgException | LibvirtException | IllegalArgumentException e) {
            logger.warn("Failed to detect file volume format for path {}. Falling back to qcow2.", volumePath, e);
        }
        return QemuImg.PhysicalDiskFormat.QCOW2;
    }

    private boolean replaceRbdVolumeWithBackup(KVMStoragePoolManager storagePoolMgr, PrimaryDataStoreTO volumePool, String volumePath, List<String> backupPaths, int timeout, boolean createTargetVolume) {
        if (backupPaths.stream().anyMatch(path -> path.endsWith(".rbdiff"))) {
            return restoreIncrementalRbdBackupChain(storagePoolMgr, volumePool, volumePath, backupPaths, timeout, createTargetVolume);
        }

        String backupPath = getFirstExistingBackupPath(backupPaths);
        KVMStoragePool volumeStoragePool = storagePoolMgr.getStoragePool(volumePool.getPoolType(), volumePool.getUuid());
        if (getBackupFileFormat(backupPath) == QemuImg.PhysicalDiskFormat.RAW) {
            return importRawBackupToRbd(volumeStoragePool, volumePath, backupPath, timeout, createTargetVolume);
        }

        QemuImg qemu;
        try {
            qemu = new QemuImg(timeout * 1000, true, false);
            if (!createTargetVolume) {
                KVMPhysicalDisk rdbDisk = volumeStoragePool.getPhysicalDisk(volumePath);
                logger.debug("Restoring RBD volume: {}", rdbDisk.toString());
                qemu.setSkipTargetVolumeCreation(true);
            }
        } catch (LibvirtException ex) {
            throw new CloudRuntimeException("Failed to create qemu-img command to restore RBD volume with backup", ex);
        }

        QemuImgFile srcBackupFile = null;
        QemuImgFile destVolumeFile = null;
        try {
            srcBackupFile = new QemuImgFile(backupPath, getBackupFileFormat(backupPath));
            String rbdDestVolumeFile = KVMPhysicalDisk.RBDStringBuilder(volumeStoragePool, volumePath);
            destVolumeFile = new QemuImgFile(rbdDestVolumeFile, QemuImg.PhysicalDiskFormat.RAW);

            logger.debug("Starting convert backup  {} to RBD volume  {}", backupPath, volumePath);
            qemu.convert(srcBackupFile, destVolumeFile);
            logger.debug("Successfully converted backup {} to RBD volume  {}", backupPath, volumePath);
        } catch (QemuImgException | LibvirtException e) {
            String srcFilename = srcBackupFile != null ? srcBackupFile.getFileName() : null;
            String destFilename = destVolumeFile != null ? destVolumeFile.getFileName() : null;
            logger.error("Failed to convert backup {} to volume {}, the error was: {}", srcFilename, destFilename, e.getMessage());
            return false;
        }

        return true;
    }

    private boolean importRawBackupToRbd(KVMStoragePool volumeStoragePool, String volumePath, String backupPath, int timeout, boolean createTargetVolume) {
        if (!createTargetVolume && !volumeStoragePool.deletePhysicalDisk(volumePath, Storage.ImageFormat.RAW)) {
            logger.error("Failed to delete existing RBD volume {} before raw import", volumePath);
            return false;
        }

        String importCommand = buildRbdImportCommand(volumeStoragePool, backupPath, volumePath);
        if (Script.runSimpleBashScriptForExitValue(importCommand, timeout * 1000, false) != 0) {
            logger.error("Failed to import raw backup {} into volume {}", backupPath, volumePath);
            return false;
        }
        return true;
    }

    private boolean restoreIncrementalRbdBackupChain(KVMStoragePoolManager storagePoolMgr, PrimaryDataStoreTO volumePool, String volumePath, List<String> backupPaths,
                                                     int timeout, boolean createTargetVolume) {
        if (backupPaths.isEmpty() || !backupPaths.get(0).endsWith(".raw")) {
            throw new CloudRuntimeException("Incremental RBD backup chain is missing the base full backup");
        }

        if (!replaceRbdVolumeWithBackup(storagePoolMgr, volumePool, volumePath, List.of(backupPaths.get(0)), timeout, createTargetVolume)) {
            return false;
        }

        KVMStoragePool volumeStoragePool = storagePoolMgr.getStoragePool(volumePool.getPoolType(), volumePool.getUuid());
        for (int index = 1; index < backupPaths.size(); index++) {
            String backupPath = backupPaths.get(index);
            if (!backupPath.endsWith(".rbdiff")) {
                continue;
            }
            String importDiffCommand = buildRbdImportDiffCommand(volumeStoragePool, backupPath, volumePath);
            if (Script.runSimpleBashScriptForExitValue(importDiffCommand, timeout * 1000, false) != 0) {
                logger.error("Failed to import RBD diff {} into volume {}", backupPath, volumePath);
                return false;
            }
        }
        return true;
    }

    private String buildRbdImportDiffCommand(KVMStoragePool storagePool, String backupPath, String volumePath) {
        StringBuilder command = new StringBuilder("rbd");
        if (StringUtils.isNotBlank(storagePool.getSourceHost())) {
            command.append(" -m ").append(formatRbdMonHosts(storagePool.getSourceHost(), storagePool.getSourcePort()));
        }
        if (StringUtils.isNotBlank(storagePool.getAuthUserName())) {
            command.append(" --id ").append(storagePool.getAuthUserName());
        }
        if (StringUtils.isNotBlank(storagePool.getAuthSecret())) {
            command.append(" --key ").append(storagePool.getAuthSecret());
        }
        command.append(" import-diff ").append(backupPath).append(" ").append(volumePath);
        return command.toString();
    }

    private String buildRbdImportCommand(KVMStoragePool storagePool, String backupPath, String volumePath) {
        StringBuilder command = new StringBuilder("rbd");
        if (StringUtils.isNotBlank(storagePool.getSourceHost())) {
            command.append(" -m ").append(formatRbdMonHosts(storagePool.getSourceHost(), storagePool.getSourcePort()));
        }
        if (StringUtils.isNotBlank(storagePool.getAuthUserName())) {
            command.append(" --id ").append(storagePool.getAuthUserName());
        }
        if (StringUtils.isNotBlank(storagePool.getAuthSecret())) {
            command.append(" --key ").append(storagePool.getAuthSecret());
        }
        command.append(" import ").append(backupPath).append(" ").append(volumePath);
        return command.toString();
    }

    private String formatRbdMonHosts(String hosts, int port) {
        String[] hostValues = hosts.split(",");
        List<String> formattedHosts = new ArrayList<>();
        for (String host : hostValues) {
            String normalizedHost = host.replace("[", "").replace("]", "").trim();
            if (StringUtils.isBlank(normalizedHost)) {
                continue;
            }
            formattedHosts.add(port > 0 ? normalizedHost + ":" + port : normalizedHost);
        }
        return String.join(",", formattedHosts);
    }

    private boolean importBackupChainToTemporaryRbd(List<String> backupPaths, int timeout, RbdImageSpec sourceImage, String tempImage) {
        if (backupPaths.isEmpty() || !backupPaths.get(0).endsWith(".raw")) {
            throw new CloudRuntimeException("Incremental RBD backup chain is missing the base full backup");
        }
        String importCommand = sourceImage.buildRbdCommand("import", quote(backupPaths.get(0)), quote(tempImage));
        if (Script.runSimpleBashScriptForExitValue(importCommand, timeout * 1000, false) != 0) {
            logger.error("Failed to import base RBD backup {} into temporary image {}", backupPaths.get(0), tempImage);
            return false;
        }
        for (int index = 1; index < backupPaths.size(); index++) {
            String backupPath = backupPaths.get(index);
            if (!backupPath.endsWith(".rbdiff")) {
                continue;
            }
            String importDiffCommand = sourceImage.buildRbdCommand("import-diff", quote(backupPath), quote(tempImage));
            if (Script.runSimpleBashScriptForExitValue(importDiffCommand, timeout * 1000, false) != 0) {
                logger.error("Failed to import RBD diff {} into temporary image {}", backupPath, tempImage);
                return false;
            }
        }
        return true;
    }

    private void removeTemporaryRbdImage(RbdImageSpec sourceImage, String tempImage, int timeout) {
        String removeCommand = sourceImage.buildRbdCommand("rm", quote(tempImage));
        Script.runSimpleBashScriptForExitValue(removeCommand, timeout * 1000, false);
    }

    private RbdImageSpec getRbdImageSpecFromMetadata(String backupRootPath, int backupIndex) {
        java.nio.file.Path metadataPath = Paths.get(backupRootPath, "rbd-backup.meta");
        if (!Files.exists(metadataPath)) {
            throw new CloudRuntimeException(String.format("RBD backup metadata file not found: %s", metadataPath));
        }
        try {
            java.util.Map<String, String> metadata = Files.readAllLines(metadataPath).stream()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty() && line.contains("="))
                    .map(line -> line.split("=", 2))
                    .collect(java.util.stream.Collectors.toMap(parts -> parts[0], parts -> parts[1], (left, right) -> right));
            String diskPaths = metadata.get("disk_paths");
            if (StringUtils.isBlank(diskPaths)) {
                throw new CloudRuntimeException("RBD backup metadata does not contain disk_paths");
            }
            List<String> values = Arrays.asList(diskPaths.split(","));
            if (backupIndex >= values.size()) {
                throw new CloudRuntimeException(String.format("RBD backup metadata does not contain disk path for index %d", backupIndex));
            }
            return RbdImageSpec.fromUri(values.get(backupIndex));
        } catch (IOException e) {
            throw new CloudRuntimeException(String.format("Failed to read RBD backup metadata: %s", metadataPath), e);
        }
    }

    private String quote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    private boolean attachVolumeToVm(KVMStoragePoolManager storagePoolMgr, String vmName, PrimaryDataStoreTO volumePool, String volumePath, String cacheMode) {
        String deviceToAttachDiskTo = getDeviceToAttachDisk(vmName);
        int exitValue;
        if (volumePool.getPoolType() != Storage.StoragePoolType.RBD) {
            exitValue = Script.runSimpleBashScriptForExitValue(String.format(ATTACH_QCOW2_DISK_COMMAND, vmName, volumePath, deviceToAttachDiskTo));
        } else {
            String xmlForRbdDisk = getXmlForRbdDisk(storagePoolMgr, volumePool, volumePath, deviceToAttachDiskTo, cacheMode);
            logger.debug("RBD disk xml to attach: {}", xmlForRbdDisk);
            exitValue = Script.runSimpleBashScriptForExitValue(String.format(ATTACH_RBD_DISK_XML_COMMAND, vmName, xmlForRbdDisk));
        }
        return exitValue == 0;
    }

    private String getDeviceToAttachDisk(String vmName) {
        String currentDevice = Script.runSimpleBashScript(String.format(CURRRENT_DEVICE, vmName));
        char lastChar = currentDevice.charAt(currentDevice.length() - 1);
        char incrementedChar = (char) (lastChar + 1);
        return currentDevice.substring(0, currentDevice.length() - 1) + incrementedChar;
    }

    private String getXmlForRbdDisk(KVMStoragePoolManager storagePoolMgr, PrimaryDataStoreTO volumePool, String volumePath, String deviceToAttachDiskTo, String cacheMode) {
        StringBuilder diskBuilder = new StringBuilder();
        diskBuilder.append("\n<disk ");
        diskBuilder.append(" device='disk'");
        diskBuilder.append(" type='network'");
        diskBuilder.append(">\n");

        diskBuilder.append("<driver name='qemu' type='raw'");
        if (StringUtils.isBlank(cacheMode)) {
            cacheMode = "none";
        }
        diskBuilder.append(" cache='").append(cacheMode).append("'/> \n");

        diskBuilder.append("<source ");
        diskBuilder.append(" protocol='rbd'");
        diskBuilder.append(" name='" + volumePath + "'");
        diskBuilder.append(">\n");
        for (String sourceHost : volumePool.getHost().split(",")) {
            diskBuilder.append("<host name='");
            diskBuilder.append(sourceHost.replace("[", "").replace("]", ""));
            if (volumePool.getPort() != 0) {
                diskBuilder.append("' port='");
                diskBuilder.append(volumePool.getPort());
            }
            diskBuilder.append("'/>\n");
        }
        diskBuilder.append("</source>\n");
        String authUserName = null;
        final KVMStoragePool primaryPool = storagePoolMgr.getStoragePool(volumePool.getPoolType(), volumePool.getUuid());
        if (primaryPool != null) {
            authUserName = primaryPool.getAuthUserName();
        }
        if (StringUtils.isNotBlank(authUserName)) {
            diskBuilder.append("<auth username='" + authUserName + "'>\n");
            diskBuilder.append("<secret type='ceph' uuid='" + volumePool.getUuid() + "'/>\n");
            diskBuilder.append("</auth>\n");
        }
        diskBuilder.append("<target dev='" + deviceToAttachDiskTo + "'");
        diskBuilder.append(" bus='virtio'");
        diskBuilder.append("/>\n");
        diskBuilder.append("</disk>\n");
        return diskBuilder.toString();
    }

    private void fetchBackupFile(String hostName, String backupPath) {
        int mkdirExit = Script.runSimpleBashScriptForExitValue(String.format(MKDIR_P, backupPath));
        if (mkdirExit != 0) {
            throw new CloudRuntimeException(String.format("Failed to create local backup directory: %s", backupPath));
        }

        String cmd = String.format(RSYNC_DIR_FROM_REMOTE, hostName, backupPath, backupPath);
        logger.debug("Fetching commvault backup directory from remote host. cmd={}", cmd);

        int exit = Script.runSimpleBashScriptForExitValue(cmd);
        if (exit != 0) {
            throw new CloudRuntimeException(String.format(
                    "Failed to fetch backup directory from remote host [%s]. remotePath=[%s], localPath=[%s]",
                    hostName, backupPath, backupPath));
        }
    }

    private static final class RbdImageSpec {
        private final String image;
        private final String monHost;
        private final String user;
        private final String key;

        private RbdImageSpec(String image, String monHost, String user, String key) {
            this.image = image;
            this.monHost = monHost;
            this.user = user;
            this.key = key;
        }

        private static RbdImageSpec fromUri(String uri) {
            String image = null;
            String monHost = null;
            String user = null;
            String key = null;
            if (uri.startsWith("rbd:")) {
                String payload = uri.substring("rbd:".length());
                image = payload.contains(":") ? payload.substring(0, payload.indexOf(':')) : payload;
                monHost = extract(uri, ":mon_host=([^:]*)");
                if (monHost != null) {
                    monHost = monHost.replace("\\;", ",").replace("\\:", ":");
                }
                user = extract(uri, ":id=([^:]*)");
                key = extract(uri, ":key=([^:]*)");
            } else if (uri.startsWith("rbd/")) {
                image = uri;
            }
            if (StringUtils.isBlank(image)) {
                throw new CloudRuntimeException(String.format("Unable to parse RBD disk path: %s", uri));
            }
            return new RbdImageSpec(image, monHost, user, key);
        }

        private static String extract(String value, String regex) {
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(regex).matcher(value);
            return matcher.find() ? matcher.group(1) : null;
        }

        private String buildTempImageSpec() {
            return String.format("%s-csrestore-%s", image, org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric(8).toLowerCase(Locale.ROOT));
        }

        private String buildRbdCommand(String action, String source, String target) {
            StringBuilder command = new StringBuilder("rbd");
            if (StringUtils.isNotBlank(monHost)) {
                command.append(" -m ").append(quoteArg(monHost));
            }
            if (StringUtils.isNotBlank(user)) {
                command.append(" --id ").append(quoteArg(user));
            }
            if (StringUtils.isNotBlank(key)) {
                command.append(" --key ").append(quoteArg(key));
            }
            command.append(" ").append(action);
            if (StringUtils.isNotBlank(source)) {
                command.append(" ").append(source);
            }
            if (StringUtils.isNotBlank(target)) {
                command.append(" ").append(target);
            }
            return command.toString();
        }

        private String buildRbdCommand(String action, String target) {
            return buildRbdCommand(action, null, target);
        }

        private String buildQemuUri(String imageSpec) {
            StringBuilder uri = new StringBuilder("rbd:").append(imageSpec);
            if (StringUtils.isNotBlank(monHost)) {
                uri.append(":mon_host=").append(monHost.replace(",", "\\;"));
            }
            if (StringUtils.isNotBlank(user)) {
                uri.append(":id=").append(user);
            }
            if (StringUtils.isNotBlank(key)) {
                uri.append(":key=").append(key);
            }
            return uri.toString();
        }

        private String quoteArg(String value) {
            return "'" + value.replace("'", "'\"'\"'") + "'";
        }
    }
}
