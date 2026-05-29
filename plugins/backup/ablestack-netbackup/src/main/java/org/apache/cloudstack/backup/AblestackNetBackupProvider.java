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
package org.apache.cloudstack.backup;

import com.cloud.agent.AgentManager;
import com.cloud.exception.AgentUnavailableException;
import com.cloud.exception.OperationTimedoutException;
import com.cloud.host.Host;
import com.cloud.host.HostVO;
import com.cloud.host.Status;
import com.cloud.host.dao.HostDao;
import com.cloud.hypervisor.Hypervisor;
import com.cloud.offering.DiskOffering;
import com.cloud.resource.ResourceManager;
import com.cloud.storage.DataStoreRole;
import com.cloud.storage.Snapshot;
import com.cloud.storage.SnapshotVO;
import com.cloud.storage.ScopeType;
import com.cloud.storage.Storage;
import com.cloud.storage.Volume;
import com.cloud.storage.VolumeApiServiceImpl;
import com.cloud.storage.VolumeVO;
import com.cloud.storage.dao.DiskOfferingDao;
import com.cloud.storage.dao.SnapshotDao;
import com.cloud.storage.dao.VolumeDao;
import com.cloud.utils.NumbersUtil;
import com.cloud.utils.Pair;
import com.cloud.utils.Ternary;
import com.cloud.utils.component.AdapterBase;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.utils.ssh.SshHelper;
import com.cloud.vm.VirtualMachine;
import com.cloud.vm.snapshot.VMSnapshot;
import com.cloud.vm.snapshot.VMSnapshotDetailsVO;
import com.cloud.vm.snapshot.VMSnapshotVO;
import com.cloud.vm.snapshot.dao.VMSnapshotDao;
import com.cloud.vm.snapshot.dao.VMSnapshotDetailsDao;
import org.apache.cloudstack.backup.dao.BackupDao;
import org.apache.cloudstack.backup.dao.BackupDetailsDao;
import org.apache.cloudstack.backup.dao.BackupOfferingDao;
import org.apache.cloudstack.engine.subsystem.api.storage.DataStore;
import org.apache.cloudstack.engine.subsystem.api.storage.DataStoreManager;
import org.apache.cloudstack.framework.config.ConfigKey;
import org.apache.cloudstack.framework.config.Configurable;
import org.apache.cloudstack.framework.config.dao.ConfigurationDao;
import org.apache.cloudstack.storage.datastore.db.PrimaryDataStoreDao;
import org.apache.cloudstack.storage.datastore.db.StoragePoolVO;
import org.apache.cloudstack.storage.to.PrimaryDataStoreTO;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.inject.Inject;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static org.apache.cloudstack.backup.BackupManager.BackupChainSize;
import static org.apache.cloudstack.backup.BackupManager.BackupFrameworkEnabled;
import static org.apache.cloudstack.backup.BackupManager.KvmIncrementalBackup;

public class AblestackNetBackupProvider extends AdapterBase implements BackupProvider, Configurable {

    private static final Logger LOG = LogManager.getLogger(AblestackNetBackupProvider.class);

    private static final String BACKUP_ROOT = "/tmp/mold/netbackup";
    private static final String BACKUP_TYPE_FULL = "FULL";
    private static final String BACKUP_TYPE_INCREMENTAL = "INCREMENTAL";
    private static final String BACKUP_ENGINE_QCOW2 = "QCOW2";
    private static final String BACKUP_ENGINE_RBD_DIFF = "RBD_DIFF";
    private static final String DETAIL_CHECKPOINT_NAME = "netbackup.checkpoint.name";
    private static final String DETAIL_CHECKPOINT_PATH = "netbackup.checkpoint.path";
    private static final String DETAIL_CHECKPOINT_XML = "netbackup.checkpoint.xml";
    private static final String DETAIL_PARENT_BACKUP_UUID = "netbackup.parent.backup.uuid";
    private static final String DETAIL_PARENT_BACKUP_PATH = "netbackup.parent.backup.path";
    private static final String DETAIL_PARENT_CHECKPOINT_NAME = "netbackup.parent.checkpoint.name";
    private static final String DETAIL_PARENT_CHECKPOINT_PATH = "netbackup.parent.checkpoint.path";
    private static final String DETAIL_BACKUP_ENGINE = "netbackup.backup.engine";
    private static final String DETAIL_RBD_DISK_PATHS = "netbackup.rbd.disk.paths";
    private static final String DETAIL_POLICY_NAME = "netbackup.policy.name";
    private static final String DETAIL_JOB_ID = "netbackup.job.id";
    private static final String MISSING_PARENT_RBD_SNAPSHOT_ERROR = "Parent RBD snapshot";
    private static final long STALE_BACKUP_THRESHOLD_MS = 24L * 60L * 60L * 1000L;
    private static final String NETBACKUP_OFFERING_NAME = "netbackup";
    private static final String NETBACKUP_OFFERING_EXTERNAL_ID = "netbackup";

    private final ConfigKey<Integer> NetBackupRestoreTimeout = new ConfigKey<>("Advanced", Integer.class,
            "netbackup.restore.timeout",
            "1800",
            "Timeout in seconds after which NetBackup restore operations fail.",
            true,
            BackupFrameworkEnabled.key());

    public ConfigKey<String> NetBackupUrl = new ConfigKey<>("Advanced", String.class,
            "backup.plugin.netbackup.url", "https://localhost:1556/netbackup",
            "NetBackup API URL.", true, ConfigKey.Scope.Zone);

    private ConfigKey<String> NetBackupApiKey = new ConfigKey<>("Advanced", String.class,
            "backup.plugin.netbackup.apikey", "apikey",
            "NetBackup API Key.", true, ConfigKey.Scope.Zone);

    private ConfigKey<Integer> NetBackupApiRequestTimeout = new ConfigKey<>("Advanced", Integer.class,
            "backup.plugin.netbackup.request.timeout", "300",
            "NetBackup API request timeout in seconds.", true, ConfigKey.Scope.Zone);

    @Inject
    private BackupDao backupDao;
    @Inject
    private BackupDetailsDao backupDetailsDao;
    @Inject
    private BackupOfferingDao backupOfferingDao;
    @Inject
    private HostDao hostDao;
    @Inject
    private VolumeDao volumeDao;
    @Inject
    private SnapshotDao snapshotDao;
    @Inject
    private VMSnapshotDao vmSnapshotDao;
    @Inject
    private VMSnapshotDetailsDao vmSnapshotDetailsDao;
    @Inject
    private PrimaryDataStoreDao primaryDataStoreDao;
    @Inject
    private DataStoreManager dataStoreMgr;
    @Inject
    private AgentManager agentManager;
    @Inject
    private BackupManager backupManager;
    @Inject
    private ResourceManager resourceManager;
    @Inject
    private DiskOfferingDao diskOfferingDao;
    @Inject
    private ConfigurationDao configDao;

    @Override
    public Pair<Boolean, Backup> takeBackup(final VirtualMachine vm, final Boolean quiesceVM) {
        return takeBackup(vm, quiesceVM, null);
    }

    @Override
    public Pair<Boolean, Backup> takeBackup(final VirtualMachine vm, final Boolean quiesceVM, final Long backupScheduleId) {
        final Host host = getVMHypervisorHostForBackup(vm);
        validateNoKvmFileBasedVmSnapshots(vm);

        final List<VolumeVO> vmVolumes = volumeDao.findByInstance(vm.getId());
        vmVolumes.sort(Comparator.comparing(Volume::getDeviceId));
        final Pair<List<PrimaryDataStoreTO>, List<String>> volumePoolsAndPaths = getVolumePoolsAndPaths(vmVolumes);
        validateVolumePoolTypes(volumePoolsAndPaths.first());

        final BackupVO latestBackup = getLatestBackedUpBackup(vm);
        final boolean incrementalBackup = shouldUseIncrementalBackup(vm, latestBackup, backupScheduleId);
        BackupExecutionResult result = executeBackup(vm, quiesceVM, host, vmVolumes, volumePoolsAndPaths, latestBackup, incrementalBackup);
        if (!result.success && incrementalBackup && shouldRetryAsFullAfterIncrementalFailure(result, vmVolumes)) {
            cleanupFailedBackupForFullRetry(result.backup);
            LOG.warn("Incremental NetBackup backup failed for VM [{}] due to [{}]. Retrying as full backup.", vm.getInstanceName(), result.details);
            result = executeBackup(vm, quiesceVM, host, vmVolumes, volumePoolsAndPaths, null, false);
        }
        return new Pair<>(result.success, result.backup);
    }

    private BackupExecutionResult executeBackup(final VirtualMachine vm, final Boolean quiesceVM, final Host vmHost,
            final List<VolumeVO> vmVolumes, final Pair<List<PrimaryDataStoreTO>, List<String>> volumePoolsAndPaths,
            final Backup latestBackup, final boolean incrementalBackup) {
        final String backupPath = buildBackupPath(vm);
        final String checkpointName = backupPath.substring(backupPath.lastIndexOf("/") + 1);
        final String backupEngine = areAllVolumesOnRbdPool(volumePoolsAndPaths.first()) ? BACKUP_ENGINE_RBD_DIFF : BACKUP_ENGINE_QCOW2;
        final String requestedBackupType = incrementalBackup ? BACKUP_TYPE_INCREMENTAL : BACKUP_TYPE_FULL;
        final List<String> backupFiles = buildBackupFileNames(vmVolumes, backupEngine, incrementalBackup);
        final Map<String, String> backupDetails = getBackupDetails(vm, backupPath, checkpointName, backupEngine, latestBackup,
                incrementalBackup);

        final BackupVO backupVO = createBackupObject(vm, backupPath, requestedBackupType, backupDetails);
        final AblestackNetBackupTakeBackupCommand command = new AblestackNetBackupTakeBackupCommand(vm.getInstanceName(), backupPath);
        command.setQuiesce(quiesceVM);
        command.setVolumePools(volumePoolsAndPaths.first());
        command.setVolumePaths(volumePoolsAndPaths.second());
        command.setBackupType(requestedBackupType);
        command.setCheckpointName(checkpointName);
        command.setBackupFiles(backupFiles);
        command.setPolicyName(backupDetails.get(DETAIL_POLICY_NAME));
        command.setJobId(backupDetails.get(DETAIL_JOB_ID));
        if (incrementalBackup && latestBackup != null) {
            command.setParentBackupPath(getBackupDetail(latestBackup, DETAIL_PARENT_BACKUP_PATH,
                    latestBackup.getExternalId()));
            command.setParentCheckpointName(getBackupDetail(latestBackup, DETAIL_CHECKPOINT_NAME));
            command.setParentCheckpointPath(getBackupDetail(latestBackup, DETAIL_CHECKPOINT_PATH));
            command.setParentCheckpointXml(getBackupDetail(latestBackup, DETAIL_CHECKPOINT_XML));
            command.setParentCheckpointXmlChain(getParentCheckpointXmlChain(latestBackup));
        }

        try {
            final BackupAnswer answer = (BackupAnswer) agentManager.send(vmHost.getId(), command);
            if (answer != null && answer.getResult()) {
                if (BACKUP_ENGINE_QCOW2.equals(backupEngine)) {
                    final HostVO vmHostVO = hostDao.findById(vmHost.getId());
                    if (vmHostVO != null) {
                        final int sshPort = NumbersUtil.parseInt(configDao.getValue("kvm.ssh.port"), 22);
                        final Ternary<String, String, String> credentials = getKVMHyperisorCredentials(vmHostVO);
                        final String checkpointXml = readFileContentsOnHost(vmHostVO, credentials.first(), credentials.second(), sshPort,
                                getCheckpointPath(backupPath, checkpointName, backupEngine));
                        if (StringUtils.isNotBlank(checkpointXml)) {
                            backupDetails.put(DETAIL_CHECKPOINT_XML, checkpointXml);
                            backupDetailsDao.removeDetail(backupVO.getId(), DETAIL_CHECKPOINT_XML);
                            backupDetailsDao.addDetail(backupVO.getId(), DETAIL_CHECKPOINT_XML, checkpointXml, false);
                        }
                    }
                }

                backupVO.setDate(new Date());
                backupVO.setSize(answer.getSize() != null ? answer.getSize() : backupVO.getProtectedSize());
                backupVO.setStatus(Backup.Status.BackedUp);
                backupVO.setDetails(backupDetails);
                backupVO.setBackedUpVolumes(createVolumeInfoFromVolumes(vmVolumes, backupFiles));
                if (backupDao.update(backupVO.getId(), backupVO)) {
                    return BackupExecutionResult.success(backupVO);
                }
                throw new CloudRuntimeException("Failed to update NetBackup backup");
            }

            final String details = answer != null ? answer.getDetails() : "No answer received";
            LOG.error("Failed to take NetBackup backup for VM {}: {}", vm.getInstanceName(), details);
            if (answer != null && answer.getNeedsCleanup()) {
                backupVO.setStatus(Backup.Status.Error);
                backupDao.update(backupVO.getId(), backupVO);
            } else {
                backupVO.setStatus(Backup.Status.Failed);
                removeBackupWithDetails(backupVO.getId());
            }
            return BackupExecutionResult.failure(details, backupVO);
        } catch (final AgentUnavailableException e) {
            backupVO.setStatus(Backup.Status.Failed);
            removeBackupWithDetails(backupVO.getId());
            throw new CloudRuntimeException("Unable to contact backend control plane to initiate NetBackup backup", e);
        } catch (final OperationTimedoutException e) {
            backupVO.setStatus(Backup.Status.Failed);
            removeBackupWithDetails(backupVO.getId());
            throw new CloudRuntimeException("Operation to initiate NetBackup backup timed out, please try again", e);
        } catch (final RuntimeException e) {
            try {
                final Backup existingBackup = backupDao.findById(backupVO.getId());
                if (existingBackup != null) {
                    backupVO.setStatus(Backup.Status.Failed);
                    removeBackupWithDetails(backupVO.getId());
                }
            } catch (final Exception cleanupException) {
                LOG.warn("Failed to cleanup incomplete NetBackup backup entry [{}]", backupVO.getUuid(), cleanupException);
            }
            throw e;
        }
    }

    private boolean shouldUseIncrementalBackup(final VirtualMachine vm, final Backup latestBackup, final Long backupScheduleId) {
        if (latestBackup == null) {
            return false;
        }
        loadBackupDetailsIfNeeded(latestBackup);

        if (backupScheduleId != null && !hasBackedUpBackupForSchedule(backupScheduleId)) {
            return false;
        }

        final Long clusterId = getClusterIdFromRootVolume(vm);
        if (clusterId == null || !KvmIncrementalBackup.valueIn(clusterId)) {
            return false;
        }

        if (!hasHealthyIncrementalSource(latestBackup)) {
            return false;
        }

        return getBackupChainSize(vm, latestBackup) < BackupChainSize.value();
    }

    private boolean hasHealthyIncrementalSource(final Backup latestBackup) {
        final String backupEngine = getBackupDetail(latestBackup, DETAIL_BACKUP_ENGINE);
        if (StringUtils.isBlank(backupEngine)) {
            return false;
        }
        if (StringUtils.isBlank(getBackupDetail(latestBackup, DETAIL_CHECKPOINT_NAME))
                || StringUtils.isBlank(getBackupDetail(latestBackup, DETAIL_CHECKPOINT_PATH))) {
            return false;
        }
        if (BACKUP_ENGINE_QCOW2.equals(backupEngine)) {
            return StringUtils.isNotBlank(getBackupDetail(latestBackup, DETAIL_CHECKPOINT_XML));
        }
        return true;
    }

    private int getBackupChainSize(final VirtualMachine vm, final Backup latestBackup) {
        final List<BackupVO> backups = backupDao.listByVmIdAndOffering(vm.getDataCenterId(), vm.getId(), vm.getBackupOfferingId()).stream()
                .filter(BackupVO.class::isInstance)
                .map(BackupVO.class::cast)
                .filter(backup -> Backup.Status.BackedUp.equals(backup.getStatus()))
                .peek(this::loadBackupDetailsIfNeeded)
                .collect(Collectors.toList());
        final Map<String, BackupVO> backupsByUuid = backups.stream().collect(Collectors.toMap(BackupVO::getUuid, backup -> backup, (left, right) -> left));
        return AblestackBackupFrameworkUtils.getBackupChainSize(latestBackup, backupsByUuid,
                current -> getBackupDetail(current, DETAIL_PARENT_BACKUP_UUID));
    }

    private boolean hasBackedUpBackupForSchedule(final Long backupScheduleId) {
        return backupDao.listBySchedule(backupScheduleId).stream()
                .anyMatch(backup -> Backup.Status.BackedUp.equals(backup.getStatus()));
    }

    private boolean shouldRetryAsFullAfterIncrementalFailure(final BackupExecutionResult result, final List<VolumeVO> vmVolumes) {
        if (result == null || result.success) {
            return false;
        }
        if (StringUtils.contains(result.details, MISSING_PARENT_RBD_SNAPSHOT_ERROR)) {
            return true;
        }
        return vmVolumes.size() > 1;
    }

    private void cleanupFailedBackupForFullRetry(final Backup backup) {
        if (backup != null) {
            removeBackupWithDetails(backup.getId());
        }
    }

    private BackupVO createBackupObject(final VirtualMachine vm, final String backupPath, final String backupType, final Map<String, String> details) {
        final BackupVO backup = new BackupVO();
        backup.setVmId(vm.getId());
        backup.setExternalId(backupPath);
        backup.setType(backupType);
        backup.setDate(new Date());
        long virtualSize = 0L;
        for (final Volume volume : volumeDao.findByInstance(vm.getId())) {
            if (Volume.State.Ready.equals(volume.getState())) {
                virtualSize += volume.getSize();
            }
        }
        backup.setProtectedSize(virtualSize);
        backup.setStatus(Backup.Status.BackingUp);
        backup.setBackupOfferingId(vm.getBackupOfferingId());
        backup.setAccountId(vm.getAccountId());
        backup.setDomainId(vm.getDomainId());
        backup.setZoneId(vm.getDataCenterId());
        backup.setName(backupManager.getBackupNameFromVM(vm));
        backup.setDetails(details);
        return backupDao.persist(backup);
    }

    private Map<String, String> getBackupDetails(final VirtualMachine vm, final String backupPath, final String checkpointName, final String backupEngine,
            final Backup latestBackup, final boolean incrementalBackup) {
        final Map<String, String> details = new HashMap<>();
        final Map<String, String> backupDetailsFromVm = backupManager.getBackupDetailsFromVM(vm);
        if (backupDetailsFromVm != null) {
            details.putAll(backupDetailsFromVm);
        }
        details.put(DETAIL_BACKUP_ENGINE, backupEngine);
        details.put(DETAIL_CHECKPOINT_NAME, checkpointName);
        details.put(DETAIL_CHECKPOINT_PATH, getCheckpointPath(backupPath, checkpointName, backupEngine));
        if (BACKUP_ENGINE_RBD_DIFF.equals(backupEngine)) {
            details.put(DETAIL_RBD_DISK_PATHS, String.join(",", getVolumePoolsAndPaths(volumeDao.findByInstance(vm.getId())).second()));
        }
        if (incrementalBackup && latestBackup != null) {
            details.put(DETAIL_PARENT_BACKUP_UUID, latestBackup.getUuid());
            details.put(DETAIL_PARENT_BACKUP_PATH, latestBackup.getExternalId());
            details.put(DETAIL_PARENT_CHECKPOINT_NAME, getBackupDetail(latestBackup, DETAIL_CHECKPOINT_NAME));
            details.put(DETAIL_PARENT_CHECKPOINT_PATH, getBackupDetail(latestBackup, DETAIL_CHECKPOINT_PATH));
        }
        return details;
    }

    private String getCheckpointPath(final String backupPath, final String checkpointName, final String backupEngine) {
        if (BACKUP_ENGINE_RBD_DIFF.equals(backupEngine)) {
            return String.format("%s/checkpoints/%s.meta", backupPath, checkpointName);
        }
        return String.format("%s/checkpoints/%s.xml", backupPath, checkpointName);
    }

    private Pair<List<PrimaryDataStoreTO>, List<String>> getVolumePoolsAndPaths(final List<VolumeVO> volumes) {
        final List<PrimaryDataStoreTO> volumePools = new ArrayList<>();
        final List<String> volumePaths = new ArrayList<>();
        for (final VolumeVO volume : volumes) {
            final StoragePoolVO storagePool = primaryDataStoreDao.findById(volume.getPoolId());
            if (storagePool == null) {
                throw new CloudRuntimeException("Unable to find storage pool associated to the volume");
            }

            final DataStore dataStore = dataStoreMgr.getDataStore(storagePool.getId(), DataStoreRole.Primary);
            volumePools.add(dataStore != null ? (PrimaryDataStoreTO) dataStore.getTO() : null);

            final String volumePathPrefix = getVolumePathPrefix(storagePool);
            volumePaths.add(String.format("%s/%s", volumePathPrefix, volume.getPath()));
        }
        return new Pair<>(volumePools, volumePaths);
    }

    private String getVolumePathPrefix(final StoragePoolVO storagePool) {
        if (ScopeType.HOST.equals(storagePool.getScope())
                || Storage.StoragePoolType.SharedMountPoint.equals(storagePool.getPoolType())
                || Storage.StoragePoolType.RBD.equals(storagePool.getPoolType())) {
            return storagePool.getPath();
        }
        return String.format("/mnt/%s", storagePool.getUuid());
    }

    private void validateVolumePoolTypes(final List<PrimaryDataStoreTO> volumePools) {
        final boolean hasRbd = volumePools.stream().anyMatch(pool -> pool != null && pool.getPoolType() == Storage.StoragePoolType.RBD);
        final boolean hasNonRbd = volumePools.stream().anyMatch(pool -> pool != null && pool.getPoolType() != Storage.StoragePoolType.RBD);
        if (hasRbd && hasNonRbd) {
            throw new CloudRuntimeException("NetBackup incremental backup does not support VMs with mixed RBD and non-RBD volumes");
        }
    }

    private boolean areAllVolumesOnRbdPool(final List<PrimaryDataStoreTO> volumePools) {
        return !volumePools.isEmpty() && volumePools.stream().allMatch(pool -> pool != null && pool.getPoolType() == Storage.StoragePoolType.RBD);
    }

    private List<String> buildBackupFileNames(final List<VolumeVO> volumes, final String backupEngine, final boolean incrementalBackup) {
        final List<String> backupFiles = new ArrayList<>();
        for (final VolumeVO volume : volumes) {
            final String diskPrefix = Volume.Type.ROOT.equals(volume.getVolumeType()) ? "root" : "datadisk";
            if (BACKUP_ENGINE_RBD_DIFF.equals(backupEngine)) {
                final String suffix = incrementalBackup ? ".rbdiff" : ".raw";
                backupFiles.add(String.format("%s.%s%s", diskPrefix, volume.getUuid(), suffix));
            } else {
                backupFiles.add(String.format("%s.%s.qcow2", diskPrefix, volume.getUuid()));
            }
        }
        return backupFiles;
    }

    private String createVolumeInfoFromVolumes(final List<VolumeVO> volumes, final List<String> backupFiles) {
        final List<Backup.VolumeInfo> infoList = new ArrayList<>();
        for (int i = 0; i < volumes.size(); i++) {
            final VolumeVO volume = volumes.get(i);
            final DiskOffering diskOffering = diskOfferingDao.findById(volume.getDiskOfferingId());
            final String diskOfferingUuid = diskOffering != null ? diskOffering.getUuid() : null;
            infoList.add(new Backup.VolumeInfo(volume.getUuid(), backupFiles.get(i), volume.getVolumeType(), volume.getSize(),
                    volume.getDeviceId(), diskOfferingUuid, volume.getMinIops(), volume.getMaxIops()));
        }
        return new com.google.gson.Gson().toJson(infoList.toArray(), Backup.VolumeInfo[].class);
    }

    private String buildBackupPath(final VirtualMachine vm) {
        return String.format("%s/%s/%s", BACKUP_ROOT, vm.getInstanceName(),
                new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss.SSS").format(new Date()));
    }

    private BackupVO getLatestBackedUpBackup(final VirtualMachine vm) {
        return backupDao.listByVmIdAndOffering(vm.getDataCenterId(), vm.getId(), vm.getBackupOfferingId()).stream()
                .filter(BackupVO.class::isInstance)
                .map(BackupVO.class::cast)
                .filter(backup -> Backup.Status.BackedUp.equals(backup.getStatus()))
                .peek(this::loadBackupDetailsIfNeeded)
                .filter(backup -> getBackupDetail(backup, DETAIL_CHECKPOINT_NAME) != null)
                .max(Comparator.comparing(BackupVO::getDate))
                .orElse(null);
    }

    private Map<String, String> getParentCheckpointXmlChain(final Backup latestBackup) {
        final Map<String, String> checkpointXmlChain = new LinkedHashMap<>();
        Backup current = latestBackup;
        final Set<String> visitedBackupUuids = new HashSet<>();
        while (current != null && StringUtils.isNotBlank(current.getUuid()) && visitedBackupUuids.add(current.getUuid())) {
            loadBackupDetailsIfNeeded(current);
            final String checkpointPath = getBackupDetail(current, DETAIL_CHECKPOINT_PATH);
            final String checkpointXml = getBackupDetail(current, DETAIL_CHECKPOINT_XML);
            if (StringUtils.isNotBlank(checkpointPath) && StringUtils.isNotBlank(checkpointXml)) {
                checkpointXmlChain.putIfAbsent(checkpointPath, checkpointXml);
            }
            final String parentBackupUuid = getBackupDetail(current, DETAIL_PARENT_BACKUP_UUID);
            if (StringUtils.isBlank(parentBackupUuid)) {
                break;
            }
            current = backupDao.findByUuid(parentBackupUuid);
        }
        return checkpointXmlChain;
    }

    private void loadBackupDetailsIfNeeded(final Backup backup) {
        if (backup instanceof BackupVO && (backup.getDetails() == null || backup.getDetails().isEmpty())) {
            backupDao.loadDetails((BackupVO) backup);
        }
    }

    private String getBackupDetail(final Backup backup, final String key) {
        return backup == null ? null : backup.getDetail(key);
    }

    private String getBackupDetail(final Backup backup, final String key, final String defaultValue) {
        final String value = getBackupDetail(backup, key);
        return value == null ? defaultValue : value;
    }

    private void removeBackupWithDetails(final long backupId) {
        backupDetailsDao.removeDetails(backupId);
        backupDao.remove(backupId);
    }

    private Host getVMHypervisorHostForBackup(final VirtualMachine vm) {
        Long hostId = vm.getHostId();
        if (hostId == null && VirtualMachine.State.Running.equals(vm.getState())) {
            throw new CloudRuntimeException(String.format("Unable to find the hypervisor host for %s. Make sure the virtual machine is running", vm.getName()));
        }
        if (VirtualMachine.State.Stopped.equals(vm.getState())) {
            hostId = vm.getLastHostId();
        }
        if (hostId == null) {
            throw new CloudRuntimeException(String.format("Unable to find the hypervisor host for stopped VM: %s", vm));
        }
        final Host host = hostDao.findById(hostId);
        if (host == null || !Status.Up.equals(host.getStatus()) || !Hypervisor.HypervisorType.KVM.equals(host.getHypervisorType())) {
            throw new CloudRuntimeException("Unable to contact backend control plane to initiate NetBackup backup");
        }
        return host;
    }

    private Long getClusterIdFromRootVolume(final VirtualMachine vm) {
        final VolumeVO rootVolume = volumeDao.getInstanceRootVolume(vm.getId());
        if (rootVolume != null) {
            final StoragePoolVO rootDiskPool = primaryDataStoreDao.findById(rootVolume.getPoolId());
            if (rootDiskPool != null && rootDiskPool.getClusterId() != null) {
                return rootDiskPool.getClusterId();
            }
        }

        if (vm.getHostId() != null) {
            final HostVO host = hostDao.findById(vm.getHostId());
            if (host != null && host.getClusterId() != null) {
                return host.getClusterId();
            }
        }

        if (vm.getLastHostId() != null) {
            final HostVO host = hostDao.findById(vm.getLastHostId());
            if (host != null) {
                return host.getClusterId();
            }
        }
        return null;
    }

    private void validateNoKvmFileBasedVmSnapshots(final VirtualMachine vm) {
        if (hasKvmFileBasedVmSnapshots(vm)) {
            throw new CloudRuntimeException(String.format("Cannot take backup of VM [%s] as it has KVM file-based VM snapshots.", vm.getUuid()));
        }
        if (hasVolumeSnapshots(vm)) {
            throw new CloudRuntimeException(String.format("Cannot take backup of VM [%s] as it has volume snapshots.", vm.getUuid()));
        }
    }

    private boolean hasKvmFileBasedVmSnapshots(final VirtualMachine vm) {
        for (final VMSnapshotVO vmSnapshotVO : vmSnapshotDao.findByVmAndByType(vm.getId(), VMSnapshot.Type.Disk)) {
            final List<VMSnapshotDetailsVO> vmSnapshotDetails = vmSnapshotDetailsDao.listDetails(vmSnapshotVO.getId());
            if (vmSnapshotDetails.stream().anyMatch(detail -> VolumeApiServiceImpl.KVM_FILE_BASED_STORAGE_SNAPSHOT.equals(detail.getName()))) {
                return true;
            }
        }
        return false;
    }

    private boolean hasVolumeSnapshots(final VirtualMachine vm) {
        for (final VolumeVO volume : volumeDao.findByInstance(vm.getId())) {
            final List<SnapshotVO> snapshots = snapshotDao.listByVolumeId(volume.getId());
            if (snapshots.stream().anyMatch(snapshot -> !Snapshot.State.Destroyed.equals(snapshot.getState()))) {
                return true;
            }
        }
        return false;
    }

    protected Ternary<String, String, String> getKVMHyperisorCredentials(final HostVO host) {
        String username = null;
        String password = null;
        if (host != null && host.getHypervisorType() == Hypervisor.HypervisorType.KVM) {
            hostDao.loadDetails(host);
            password = host.getDetail("password");
            username = host.getDetail("username");
        }
        if (password == null || username == null) {
            throw new CloudRuntimeException("Cannot find login credentials for HYPERVISOR " + Objects.requireNonNull(host).getUuid());
        }
        return new Ternary<>(username, password, null);
    }

    private String readFileContentsOnHost(final HostVO host, final String username, final String password, final int port, final String path) {
        if (host == null || StringUtils.isBlank(path)) {
            return null;
        }
        final String command = String.format("test -f %s && cat %s", shellQuote(path), shellQuote(path));
        try {
            final Pair<Boolean, String> response = SshHelper.sshExecute(host.getPrivateIpAddress(), port,
                    username, null, password, command, 120000, 120000, 3600000);
            if (!response.first()) {
                return null;
            }
            return response.second();
        } catch (final Exception e) {
            LOG.warn("Failed to read file [{}] on host [{}]", path, host.getName(), e);
            return null;
        }
    }

    private String shellQuote(final String value) {
        return "'" + StringUtils.defaultString(value).replace("'", "'\"'\"'") + "'";
    }

    @Override
    public boolean assignVMToBackupOffering(final VirtualMachine vm, final BackupOffering backupOffering) {
        if (hasKvmFileBasedVmSnapshots(vm) || hasVolumeSnapshots(vm)) {
            return false;
        }
        return Hypervisor.HypervisorType.KVM.equals(vm.getHypervisorType());
    }

    @Override
    public boolean removeVMFromBackupOffering(final VirtualMachine vm) {
        return true;
    }

    @Override
    public boolean willDeleteBackupsOnOfferingRemoval() {
        return false;
    }

    @Override
    public boolean deleteBackup(final Backup backup, final boolean forced) {
        throw new UnsupportedOperationException("NetBackup delete is not implemented yet");
    }

    @Override
    public Pair<Boolean, String> restoreBackupToVM(final VirtualMachine vm, final Backup backup, final String hostIp, final String dataStoreUuid) {
        return new Pair<>(false, "NetBackup restore is not implemented yet");
    }

    @Override
    public Pair<Boolean, String> restoreBackupToVM(final Long backupId, final String vmName) {
        return new Pair<>(false, "NetBackup restore is not implemented yet");
    }

    @Override
    public boolean restoreVMFromBackup(final VirtualMachine vm, final Backup backup) {
        return false;
    }

    @Override
    public Pair<Boolean, String> restoreBackedUpVolume(final Backup backup, final Backup.VolumeInfo backupVolumeInfo, final String hostIp,
            final String dataStoreUuid, final Pair<String, VirtualMachine.State> vmNameAndState) {
        return new Pair<>(false, "NetBackup volume restore is not implemented yet");
    }

    @Override
    public void syncBackupMetrics(final Long zoneId) {
    }

    @Override
    public List<Backup.RestorePoint> listRestorePoints(final VirtualMachine vm) {
        return Collections.emptyList();
    }

    @Override
    public Backup createNewBackupEntryForRestorePoint(final Backup.RestorePoint restorePoint, final VirtualMachine vm) {
        return null;
    }

    @Override
    public boolean supportsInstanceFromBackup() {
        return false;
    }

    @Override
    public boolean supportsMemoryVmSnapshot() {
        return false;
    }

    @Override
    public Pair<Long, Long> getBackupStorageStats(final Long zoneId) {
        return new Pair<>(0L, 0L);
    }

    @Override
    public void syncBackupStorageStats(final Long zoneId) {
    }

    @Override
    public List<BackupOffering> listBackupOfferings(final Long zoneId) {
        return Collections.singletonList(new AblestackNetBackupOffering(
                NETBACKUP_OFFERING_NAME,
                NETBACKUP_OFFERING_EXTERNAL_ID
        ));
    }

    @Override
    public boolean isValidProviderOffering(final Long zoneId, final String uuid) {
        return StringUtils.equalsIgnoreCase(uuid, NETBACKUP_OFFERING_EXTERNAL_ID);
    }

    @Override
    public Boolean crossZoneInstanceCreationEnabled(final BackupOffering backupOffering) {
        return false;
    }

    @Override
    public ConfigKey<?>[] getConfigKeys() {
        return new ConfigKey[]{
            NetBackupRestoreTimeout,
            NetBackupUrl,
            NetBackupApiKey,
            NetBackupApiRequestTimeout
        };
    }

    @Override
    public String getName() {
        return "ablestack-netbackup";
    }

    @Override
    public String getDescription() {
        return "NetBackup Backup Plugin";
    }

    @Override
    public String getConfigComponentName() {
        return BackupService.class.getSimpleName();
    }

    @Override
    public void syncBackups(final VirtualMachine vm) {
        for (final Backup backup : backupDao.listByVmId(vm.getDataCenterId(), vm.getId())) {
            if (!Backup.Status.BackingUp.equals(backup.getStatus()) || backup.getDate() == null) {
                continue;
            }
            if (backup.getDate().getTime() > System.currentTimeMillis() - STALE_BACKUP_THRESHOLD_MS) {
                continue;
            }
            LOG.warn("Removing stale NetBackup backup [{}] for VM [{}] stuck in BackingUp for over one day.",
                    backup.getUuid(), vm.getInstanceName());
            removeBackupWithDetails(backup.getId());
        }
    }

    @Override
    public boolean checkBackupAgent(final Long zoneId) {
        return true;
    }

    @Override
    public boolean installBackupAgent(final Long zoneId) {
        return true;
    }

    @Override
    public boolean importBackupPlan(final Long zoneId, final String retentionPeriod, final String externalId) {
        return true;
    }

    @Override
    public boolean updateBackupPlan(final Long zoneId, final String retentionPeriod, final String externalId) {
        return true;
    }

    private static final class BackupExecutionResult {
        private final boolean success;
        private final Backup backup;
        private final String details;

        private BackupExecutionResult(final boolean success, final Backup backup, final String details) {
            this.success = success;
            this.backup = backup;
            this.details = details;
        }

        private static BackupExecutionResult success(final Backup backup) {
            return new BackupExecutionResult(true, backup, null);
        }

        private static BackupExecutionResult failure(final String details, final Backup backup) {
            return new BackupExecutionResult(false, backup, details);
        }
    }
}
