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
import com.cloud.vm.VMInstanceVO;
import com.cloud.vm.VirtualMachine;
import com.cloud.vm.dao.VMInstanceDao;
import com.cloud.vm.snapshot.VMSnapshot;
import com.cloud.vm.snapshot.VMSnapshotDetailsVO;
import com.cloud.vm.snapshot.VMSnapshotVO;
import com.cloud.vm.snapshot.dao.VMSnapshotDao;
import com.cloud.vm.snapshot.dao.VMSnapshotDetailsDao;
import org.apache.cloudstack.backup.dao.BackupDao;
import org.apache.cloudstack.backup.dao.BackupDetailsDao;
import org.apache.cloudstack.backup.dao.BackupOfferingDao;
import org.apache.cloudstack.backup.netbackup.AblestackNetBackupClient;
import org.apache.cloudstack.engine.subsystem.api.storage.DataStore;
import org.apache.cloudstack.engine.subsystem.api.storage.DataStoreManager;
import org.apache.cloudstack.framework.config.ConfigKey;
import org.apache.cloudstack.framework.config.Configurable;
import org.apache.cloudstack.framework.config.dao.ConfigurationDao;
import org.apache.cloudstack.storage.datastore.db.PrimaryDataStoreDao;
import org.apache.cloudstack.storage.datastore.db.StoragePoolVO;
import org.apache.cloudstack.storage.to.PrimaryDataStoreTO;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.inject.Inject;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
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
    private static final String DETAIL_BACKUP_ID = "netbackup.backup.id";
    private static final String DETAIL_MEMBER_COUNT = "netbackup.backup.member.count";
    private static final String DETAIL_POLICY_NAME = "netbackup.policy.name";
    private static final String DETAIL_MAX_CHAIN = "netbackup.max.chain";
    private static final String DETAIL_RESTORE_ROOT_JOB_ID = "netbackup.restore.root.job.id";
    private static final String DETAIL_RESTORE_CHAIN_JOB_ID = "netbackup.restore.chain.job.id";
    private static final String MISSING_PARENT_RBD_SNAPSHOT_ERROR = "Parent RBD snapshot";
    private static final long STALE_BACKUP_THRESHOLD_MS = 24L * 60L * 60L * 1000L;
    private static final long NETBACKUP_SYNC_DELETE_GRACE_MS = 10L * 60L * 1000L;
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
    private VMInstanceDao vmInstanceDao;
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
        BackupExecutionResult result = executeBackup(vm, quiesceVM, host, vmVolumes, volumePoolsAndPaths, latestBackup,
                incrementalBackup, null, null);
        Backup failedIncrementalBackup = null;
        if (!result.success && incrementalBackup && shouldRetryAsFullAfterIncrementalFailure(result, vmVolumes)) {
            failedIncrementalBackup = result.backup;
            cleanupFailedBackupForFullRetry(host, failedIncrementalBackup);
            LOG.warn("Incremental NetBackup backup failed for VM [{}] due to [{}]. Retrying as full backup.", vm.getInstanceName(), result.details);
            result = executeBackup(vm, quiesceVM, host, vmVolumes, volumePoolsAndPaths, null, false,
                    null, null);
            if (result.success && failedIncrementalBackup != null) {
                removeFailedBackupAfterSuccessfulFullRetry(failedIncrementalBackup);
            }
        }
        return new Pair<>(result.success, result.backup);
    }

    @Override
    public Pair<Boolean, Backup> takeNetBackup(final VirtualMachine vm, final String policyName, final String maxChain) {
        final Host host = getVMHypervisorHostForBackup(vm);
        validateNoKvmFileBasedVmSnapshots(vm);

        final List<VolumeVO> vmVolumes = volumeDao.findByInstance(vm.getId());
        vmVolumes.sort(Comparator.comparing(Volume::getDeviceId));
        final Pair<List<PrimaryDataStoreTO>, List<String>> volumePoolsAndPaths = getVolumePoolsAndPaths(vmVolumes);
        validateVolumePoolTypes(volumePoolsAndPaths.first());

        final BackupVO latestBackup = getLatestBackedUpBackup(vm);
        final boolean incrementalBackup = shouldUseIncrementalBackupForNetBackup(vm, latestBackup, maxChain);
        BackupExecutionResult result = executeBackup(vm, null, host, vmVolumes, volumePoolsAndPaths, latestBackup,
                incrementalBackup, policyName, maxChain);
        Backup failedIncrementalBackup = null;
        if (!result.success && incrementalBackup && shouldRetryAsFullAfterIncrementalFailure(result, vmVolumes)) {
            failedIncrementalBackup = result.backup;
            cleanupFailedBackupForFullRetry(host, failedIncrementalBackup);
            LOG.warn("Incremental NetBackup backup failed for VM [{}] due to [{}]. Retrying as full backup.", vm.getInstanceName(), result.details);
            result = executeBackup(vm, null, host, vmVolumes, volumePoolsAndPaths, null, false,
                    policyName, maxChain);
            if (result.success && failedIncrementalBackup != null) {
                removeFailedBackupAfterSuccessfulFullRetry(failedIncrementalBackup);
            }
        }
        return new Pair<>(result.success, result.backup);
    }

    private BackupExecutionResult executeBackup(final VirtualMachine vm, final Boolean quiesceVM, final Host vmHost,
            final List<VolumeVO> vmVolumes, final Pair<List<PrimaryDataStoreTO>, List<String>> volumePoolsAndPaths,
            final Backup latestBackup, final boolean incrementalBackup, final String policyName, final String maxChain) {
        final String backupPath = buildBackupPath(vm);
        final String checkpointName = backupPath.substring(backupPath.lastIndexOf("/") + 1);
        final String backupEngine = areAllVolumesOnRbdPool(volumePoolsAndPaths.first()) ? BACKUP_ENGINE_RBD_DIFF : BACKUP_ENGINE_QCOW2;
        final String requestedBackupType = incrementalBackup ? BACKUP_TYPE_INCREMENTAL : BACKUP_TYPE_FULL;
        final List<String> backupFiles = buildBackupFileNames(vmVolumes, backupEngine, incrementalBackup);
        final Map<String, String> backupDetails = getBackupDetails(vm, backupPath, checkpointName, backupEngine, latestBackup,
                incrementalBackup, policyName, maxChain);

        final BackupVO backupVO = createBackupObject(vm, backupPath, requestedBackupType, backupDetails);
        AblestackNetBackupTakeBackupCommand command = new AblestackNetBackupTakeBackupCommand(vm.getInstanceName(), backupPath);
        command.setQuiesce(quiesceVM);
        command.setVolumePools(volumePoolsAndPaths.first());
        command.setVolumePaths(volumePoolsAndPaths.second());
        command.setBackupType(requestedBackupType);
        command.setCheckpointName(checkpointName);
        command.setBackupFiles(backupFiles);
        command.setPolicyId(backupDetails.get(DETAIL_POLICY_NAME));
        command.setMaxBackups(backupDetails.get(DETAIL_MAX_CHAIN));
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
                backupVO.setDetails(backupDetails);
                backupVO.setBackedUpVolumes(createVolumeInfoFromVolumes(vmVolumes, backupFiles));
                if (backupDao.update(backupVO.getId(), backupVO)) {
                    return BackupExecutionResult.success(backupVO);
                }
                throw new CloudRuntimeException("Failed to update NetBackup backup");
            }

            final String details = answer != null ? answer.getDetails() : "No answer received";
            LOG.error("Failed to take NetBackup backup for VM {}: {}", vm.getInstanceName(), details);
            backupVO.setStatus(Backup.Status.Failed);
            backupDao.update(backupVO.getId(), backupVO);
            return BackupExecutionResult.failure(details, backupVO);
        } catch (final AgentUnavailableException e) {
            backupVO.setStatus(Backup.Status.Failed);
            backupDao.update(backupVO.getId(), backupVO);
            throw new CloudRuntimeException("Unable to contact backend control plane to initiate NetBackup backup", e);
        } catch (final OperationTimedoutException e) {
            backupVO.setStatus(Backup.Status.Failed);
            backupDao.update(backupVO.getId(), backupVO);
            throw new CloudRuntimeException("Operation to initiate NetBackup backup timed out, please try again", e);
        } catch (final RuntimeException e) {
            try {
                final Backup existingBackup = backupDao.findById(backupVO.getId());
                if (existingBackup != null) {
                    backupVO.setStatus(Backup.Status.Failed);
                    backupDao.update(backupVO.getId(), backupVO);
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

    private boolean shouldUseIncrementalBackupForNetBackup(final VirtualMachine vm, final Backup latestBackup, final String maxChain) {
        if (latestBackup == null) {
            return false;
        }
        loadBackupDetailsIfNeeded(latestBackup);

        final Long clusterId = getClusterIdFromRootVolume(vm);
        if (clusterId == null || !KvmIncrementalBackup.valueIn(clusterId)) {
            return false;
        }

        if (!hasHealthyIncrementalSource(latestBackup)) {
            return false;
        }

        return getBackupChainSize(vm, latestBackup) < resolveMaxChainLimit(maxChain);
    }

    private int resolveMaxChainLimit(final String maxChain) {
        if (StringUtils.isBlank(maxChain)) {
            return BackupChainSize.value();
        }
        try {
            return Integer.parseInt(maxChain);
        } catch (NumberFormatException e) {
            LOG.warn("Invalid NetBackup maxChain [{}]. Falling back to global backup chain size [{}].", maxChain, BackupChainSize.value());
            return BackupChainSize.value();
        }
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

    private void cleanupFailedBackupForFullRetry(final Host host, final Backup backup) {
        if (backup == null) {
            return;
        }

        final String failedBackupPath = backup.getExternalId();
        if (StringUtils.isNotBlank(failedBackupPath)) {
            cleanupBackupPathsOnHost(backup.getZoneId(), host.getName(), List.of(failedBackupPath));
        }

        LOG.info("Removed failed NetBackup backup path [{}] before full retry for backup [{}].",
                failedBackupPath, backup.getUuid());
    }

    private void removeFailedBackupAfterSuccessfulFullRetry(final Backup backup) {
        if (backup == null) {
            return;
        }

        try {
            removeBackupWithDetails(backup.getId());
            LOG.info("Removed failed NetBackup backup row [{}] after successful full retry.", backup.getUuid());
        } catch (Exception e) {
            LOG.warn("Failed to remove failed NetBackup backup row [{}] after successful full retry: {}",
                    backup.getUuid(), e.getMessage(), e);
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
            final Backup latestBackup, final boolean incrementalBackup, final String policyName, final String maxChain) {
        final Map<String, String> details = new HashMap<>();
        final Map<String, String> backupDetailsFromVm = backupManager.getBackupDetailsFromVM(vm);
        if (backupDetailsFromVm != null) {
            details.putAll(backupDetailsFromVm);
        }
        if (StringUtils.isNotBlank(policyName)) {
            details.put(DETAIL_POLICY_NAME, policyName);
        }
        if (StringUtils.isNotBlank(maxChain)) {
            details.put(DETAIL_MAX_CHAIN, maxChain);
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

    private Host resolveRestoreHost(final VirtualMachine vm, final String hostIp) {
        if (StringUtils.isNotBlank(hostIp)) {
            Host host = hostDao.findByIp(hostIp);
            if (host == null) {
                host = hostDao.findByName(hostIp);
            }
            if (host == null) {
                throw new CloudRuntimeException(String.format("Unable to find restore host [%s] for NetBackup restore", hostIp));
            }
            if (!Status.Up.equals(host.getStatus()) || !Hypervisor.HypervisorType.KVM.equals(host.getHypervisorType())) {
                throw new CloudRuntimeException(String.format("Restore host [%s] is not an available KVM host for NetBackup restore", host.getName()));
            }
            return host;
        }
        return getVMHypervisorHostForBackup(vm);
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
        throw new CloudRuntimeException("NetBackup backups are managed by backup ID groups and cannot be deleted individually from Mold.");
    }

    @Override
    public Pair<Boolean, String> restoreBackupToVM(final VirtualMachine vm, final Backup backup, final String hostIp, final String dataStoreUuid) {
        return restoreVirtualMachine(vm, backup, hostIp);
    }

    @Override
    public Pair<Boolean, String> restoreBackupToVM(final Long backupId, final String vmName) {
        final Backup backup = backupDao.findByIdIncludingRemoved(backupId);
        if (backup == null) {
            return new Pair<>(false, String.format("Backup [%s] was not found for NetBackup restore", backupId));
        }

        final VMInstanceVO vm = vmInstanceDao.findVMByInstanceName(vmName);
        if (vm == null) {
            return new Pair<>(false, String.format("VM [%s] was not found for NetBackup restore", vmName));
        }

        return restoreVirtualMachine(vm, backup, null);
    }

    @Override
    public boolean restoreVMFromBackup(final VirtualMachine vm, final Backup backup) {
        return restoreVirtualMachine(vm, backup, null, false).first();
    }

    public boolean restoreVMFromPreparedBackup(final VirtualMachine vm, final Backup backup, final String restoreHostIp) {
        return restoreVirtualMachine(vm, backup, restoreHostIp, true).first();
    }

    private Pair<Boolean, String> restoreVirtualMachine(final VirtualMachine vm, final Backup backup, final String restoreHostIp) {
        return restoreVirtualMachine(vm, backup, restoreHostIp, false);
    }

    private Pair<Boolean, String> restoreVirtualMachine(final VirtualMachine vm, final Backup backup, final String restoreHostIp,
            final boolean restoreSourcesAlreadyPrepared) {
        validateNoKvmFileBasedVmSnapshots(vm);
        loadBackupDetailsIfNeeded(backup);
        validateRestoreChainIntegrity(backup);
        final Host host = resolveRestoreHost(vm, restoreHostIp);
        final List<Backup> restoreChain = getRestoreChainForBackup(backup);
        final List<Backup> stagedRestoreChain = getStagedRestoreChainForBackup(backup);
        final boolean incrementalRestore = StringUtils.equalsIgnoreCase(BACKUP_TYPE_INCREMENTAL, backup.getType());
        LOG.info("NetBackup restore flow starting. vm=[{}], backup=[{}], restoreHost=[{}], preparedSourcesAlreadyPrepared=[{}], incrementalRestore=[{}], restoreChain={}",
                vm.getInstanceName(), backup.getUuid(), host.getName(), restoreSourcesAlreadyPrepared, incrementalRestore,
                restoreChain.stream().map(Backup::getExternalId).collect(Collectors.toList()));
        try {
            if (incrementalRestore) {
                LOG.info("Waiting for NetBackup root restore job to finish before preparing incremental chain restore. vm=[{}], backup=[{}], restoreHost=[{}], rootPath=[{}]",
                        vm.getInstanceName(), backup.getUuid(), host.getName(), backup.getExternalId());
                final String restoreJobId = prepareRestoreJobGateOnDestinationHost(vm.getDataCenterId(), host.getName(), backup.getExternalId());
                if (StringUtils.isNotBlank(restoreJobId)) {
                    backupDetailsDao.removeDetail(backup.getId(), DETAIL_RESTORE_ROOT_JOB_ID);
                    backupDetailsDao.addDetail(backup.getId(), DETAIL_RESTORE_ROOT_JOB_ID, restoreJobId, false);
                }
                LOG.info("Incremental restore will skip the already-restored target path from staged sources. vm=[{}], backup=[{}], excludedPath=[{}], stagedRestoreChain={}",
                        vm.getInstanceName(), backup.getUuid(), backup.getExternalId(),
                        stagedRestoreChain.stream().map(Backup::getExternalId).collect(Collectors.toList()));
            }
            if (!restoreSourcesAlreadyPrepared || incrementalRestore) {
                prepareRestoreSourcesOnStageHosts(vm.getDataCenterId(), host.getName(), stagedRestoreChain);
            }

            final List<Backup.VolumeInfo> backupVolumes = backup.getBackedUpVolumes();
            if (backupVolumes == null || backupVolumes.isEmpty()) {
                throw new CloudRuntimeException(String.format("Backup [%s] does not contain backed up volume information.", backup.getUuid()));
            }

            final List<String> backedVolumesUUIDs = backupVolumes.stream()
                    .sorted(Comparator.comparingLong(Backup.VolumeInfo::getDeviceId))
                    .map(Backup.VolumeInfo::getUuid)
                    .collect(Collectors.toList());

            final List<VolumeVO> restoreVolumes = volumeDao.findByInstance(vm.getId()).stream()
                    .sorted(Comparator.comparingLong(VolumeVO::getDeviceId))
                    .collect(Collectors.toList());
            if (restoreVolumes.size() != backupVolumes.size()) {
                throw new CloudRuntimeException(String.format(
                        "Unable to restore VM [%s] from NetBackup [%s] because the backup has [%s] disks but the VM has [%s] disks.",
                        vm.getInstanceName(), backup.getUuid(), backupVolumes.size(), restoreVolumes.size()));
            }

            final AblestackNetBackupRestoreBackupCommand restoreCommand = new AblestackNetBackupRestoreBackupCommand();
            restoreCommand.setBackupPath(backup.getExternalId());
            restoreCommand.setVmName(vm.getName());
            restoreCommand.setBackupVolumesUUIDs(backedVolumesUUIDs);
            restoreCommand.setBackupFiles(getBackupFiles(backupVolumes, backup));
            restoreCommand.setBackupFileChains(getBackupFileChains(backupVolumes, backup));
            restoreCommand.setVolumeChainStates(getVolumeChainStates(backupVolumes, backup));
            final Pair<List<PrimaryDataStoreTO>, List<String>> volumePoolsAndPaths = getVolumePoolsAndPaths(restoreVolumes);
            restoreCommand.setRestoreVolumePools(volumePoolsAndPaths.first());
            restoreCommand.setRestoreVolumePaths(volumePoolsAndPaths.second());
            restoreCommand.setVmExists(vm.getRemoved() == null);
            restoreCommand.setVmState(vm.getState());
            restoreCommand.setRestorePlan(createRestorePlan(false));
            restoreCommand.setTimeout(NetBackupRestoreTimeout.value());

            final BackupAnswer answer;
            try {
                answer = (BackupAnswer) agentManager.send(host.getId(), restoreCommand);
            } catch (final AgentUnavailableException e) {
                throw new CloudRuntimeException("Unable to contact backend control plane to initiate NetBackup restore", e);
            } catch (final OperationTimedoutException e) {
                throw new CloudRuntimeException("Operation to restore NetBackup backup timed out, please try again", e);
            }
            return new Pair<>(answer != null && answer.getResult(), answer != null ? answer.getDetails() : null);
        } finally {
            cleanupRestoreSourcesOnStageHosts(vm.getDataCenterId(), host.getName(), stagedRestoreChain);
        }
    }

    @Override
    public Pair<Boolean, String> restoreBackedUpVolume(final Backup backup, final Backup.VolumeInfo backupVolumeInfo, final String hostIp,
            final String dataStoreUuid, final Pair<String, VirtualMachine.State> vmNameAndState) {
        loadBackupDetailsIfNeeded(backup);
        validateRestoreChainIntegrity(backup);

        final StoragePoolVO pool = primaryDataStoreDao.findByUuid(dataStoreUuid);
        if (pool == null) {
            throw new CloudRuntimeException(String.format("Unable to find datastore [%s] for NetBackup volume restore", dataStoreUuid));
        }

        HostVO restoreHost = hostDao.findByIp(hostIp);
        if (restoreHost == null) {
            restoreHost = hostDao.findByName(hostIp);
        }
        if (restoreHost == null) {
            throw new CloudRuntimeException(String.format("Unable to find host [%s] for NetBackup volume restore", hostIp));
        }

        final Backup.VolumeInfo matchingVolume = getBackedUpVolumeInfo(backup.getBackedUpVolumes(), backupVolumeInfo.getUuid());
        if (matchingVolume == null) {
            throw new CloudRuntimeException(String.format(
                    "Unable to find volume [%s] in backed up volumes for backup [%s]", backupVolumeInfo.getUuid(), backup.getUuid()));
        }

        final DiskOffering diskOffering = diskOfferingDao.findByUuid(backupVolumeInfo.getDiskOfferingId());
        if (diskOffering == null) {
            throw new CloudRuntimeException(String.format("Unable to find disk offering [%s] for restored volume",
                    backupVolumeInfo.getDiskOfferingId()));
        }

        final List<Backup> restoreChain = getRestoreChainForBackup(backup);
        final List<Backup> stagedRestoreChain = getStagedRestoreChainForBackup(backup);
        try {
            prepareRestoreSourcesOnStageHosts(backup.getZoneId(), restoreHost.getName(), stagedRestoreChain);

            final VolumeVO restoredVolume = new VolumeVO(Volume.Type.DATADISK, null, backup.getZoneId(),
                    backup.getDomainId(), backup.getAccountId(), 0, null, backup.getSize(), null, null, null);
            final String volumeUuid = UUID.randomUUID().toString();
            restoredVolume.setName("RestoredVol-" + backupVolumeInfo.getUuid());
            restoredVolume.setProvisioningType(diskOffering.getProvisioningType());
            restoredVolume.setUpdated(new Date());
            restoredVolume.setUuid(volumeUuid);
            restoredVolume.setRemoved(null);
            restoredVolume.setDisplayVolume(true);
            restoredVolume.setPoolId(pool.getId());
            restoredVolume.setPoolType(pool.getPoolType());
            restoredVolume.setPath(restoredVolume.getUuid());
            restoredVolume.setState(Volume.State.Copying);
            restoredVolume.setSize(backupVolumeInfo.getSize());
            restoredVolume.setDiskOfferingId(diskOffering.getId());
            restoredVolume.setFormat(pool.getPoolType() != Storage.StoragePoolType.RBD ? Storage.ImageFormat.QCOW2 : Storage.ImageFormat.RAW);

            final AblestackNetBackupRestoreBackupCommand restoreCommand = new AblestackNetBackupRestoreBackupCommand();
            restoreCommand.setBackupPath(backup.getExternalId());
            restoreCommand.setVmName(vmNameAndState.first());
            restoreCommand.setBackupFiles(Collections.singletonList(isLegacyBackup(backup) ? getLegacyBackupFileName(matchingVolume) : matchingVolume.getPath()));
            if (!isLegacyBackup(backup)) {
                restoreCommand.setBackupFileChains(Collections.singletonList(getBackupFileChain(matchingVolume, backup)));
            }
            restoreCommand.setVolumeChainStates(getVolumeChainStates(Collections.singletonList(matchingVolume), backup));
            final String restoreVolumePath = String.format("%s/%s", getVolumePathPrefix(pool), volumeUuid);
            restoreCommand.setRestoreVolumePaths(Collections.singletonList(restoreVolumePath));
            final DataStore dataStore = dataStoreMgr.getDataStore(pool.getId(), DataStoreRole.Primary);
            if (dataStore == null) {
                throw new CloudRuntimeException(String.format(
                        "Unable to get primary datastore TO for pool [%s] while restoring volume [%s]", pool.getUuid(), backupVolumeInfo.getUuid()));
            }
            restoreCommand.setRestoreVolumePools(Collections.singletonList((PrimaryDataStoreTO) dataStore.getTO()));
            restoreCommand.setDiskType(matchingVolume.getType().name().toLowerCase(Locale.ROOT));
            restoreCommand.setVmExists(null);
            restoreCommand.setVmState(vmNameAndState.second());
            restoreCommand.setRestoreVolumeUUID(backupVolumeInfo.getUuid());
            restoreCommand.setRestorePlan(createRestorePlan(false));
            restoreCommand.setTimeout(NetBackupRestoreTimeout.value());

            final BackupAnswer answer;
            try {
                answer = (BackupAnswer) agentManager.send(restoreHost.getId(), restoreCommand);
            } catch (AgentUnavailableException e) {
                throw new CloudRuntimeException("Unable to contact backend control plane to initiate NetBackup restore");
            } catch (OperationTimedoutException e) {
                throw new CloudRuntimeException("Operation to restore backed up volume timed out, please try again");
            }

            if (answer != null && answer.getResult()) {
                try {
                    volumeDao.persist(restoredVolume);
                } catch (Exception e) {
                    throw new CloudRuntimeException("Unable to create restored volume due to: " + e);
                }
                return new Pair<>(true, restoredVolume.getUuid());
            }

            return new Pair<>(false, answer != null ? answer.getDetails() : "NetBackup restore agent returned no response");
        } finally {
            cleanupRestoreSourcesOnStageHosts(backup.getZoneId(), restoreHost.getName(), stagedRestoreChain);
        }
    }

    private String prepareRestoreJobGateOnDestinationHost(final Long zoneId, final String destinationHostName, final String restorePath) {
        if (zoneId == null || StringUtils.isBlank(destinationHostName) || StringUtils.isBlank(restorePath)) {
            return null;
        }
        final AblestackNetBackupClient client = getClient(zoneId);
        return client.waitForRestoreJobCompletionForPaths(destinationHostName, Collections.singletonList(restorePath));
    }

    @Override
    public String getRestoreJobState(final Long zoneId, final String recoveryJobId) {
        if (StringUtils.isBlank(recoveryJobId)) {
            return null;
        }
        if (zoneId == null) {
            return null;
        }
        return getClient(zoneId).getRecoveryJobState(recoveryJobId);
    }

    @Override
    public void syncBackupMetrics(final Long zoneId) {
    }

    @Override
    public List<Backup.RestorePoint> listRestorePoints(final VirtualMachine vm) {
        final List<Backup.RestorePoint> restorePoints = new ArrayList<>();
        for (final Backup backup : backupDao.listByVmId(vm.getDataCenterId(), vm.getId())) {
            if (!Backup.Status.BackedUp.equals(backup.getStatus())
                    || backup.getDate() == null
                    || StringUtils.isBlank(backup.getExternalId())) {
                continue;
            }
            final BackupOfferingVO backupOffering = backupOfferingDao.findById(backup.getBackupOfferingId());
            if (backupOffering == null || !StringUtils.equalsIgnoreCase(getName(), backupOffering.getProvider())) {
                continue;
            }
            restorePoints.add(new Backup.RestorePoint(
                    backup.getExternalId(),
                    backup.getDate(),
                    backup.getType(),
                    backup.getSize(),
                    backup.getProtectedSize()));
        }
        restorePoints.sort(Comparator.comparing(Backup.RestorePoint::getCreated).reversed());
        return restorePoints;
    }

    @Override
    public Backup createNewBackupEntryForRestorePoint(final Backup.RestorePoint restorePoint, final VirtualMachine vm) {
        throw new CloudRuntimeException("NetBackup provider does not import out-of-band restore points.");
    }

    @Override
    public boolean supportsInstanceFromBackup() {
        return true;
    }

    @Override
    public boolean supportsRestorePlan() {
        return true;
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

    private List<String> getBackupFiles(final List<Backup.VolumeInfo> backedVolumes, final Backup backup) {
        final List<String> backupFiles = new ArrayList<>();
        final List<Backup.VolumeInfo> sortedVolumes = new ArrayList<>(backedVolumes);
        sortedVolumes.sort(Comparator.comparingLong(Backup.VolumeInfo::getDeviceId));
        for (final Backup.VolumeInfo backedVolume : sortedVolumes) {
            if (isLegacyBackup(backup)) {
                backupFiles.add(getLegacyBackupFileName(backedVolume));
            } else {
                backupFiles.add(backedVolume.getPath());
            }
        }
        return backupFiles;
    }

    private BackupRestorePlan createRestorePlan(final boolean attachRequired) {
        return AblestackBackupFrameworkUtils.createRestorePlan(attachRequired, true);
    }

    private List<String> getBackupFileChains(final List<Backup.VolumeInfo> backupVolumes, final Backup backup) {
        return backupVolumes.stream()
                .sorted(Comparator.comparingLong(Backup.VolumeInfo::getDeviceId))
                .map(volume -> getBackupFileChain(volume, backup))
                .collect(Collectors.toList());
    }

    private String getBackupFileChain(final Backup.VolumeInfo backupVolume, final Backup backup) {
        loadBackupDetailsIfNeeded(backup);
        final List<String> chain = getBackupChain(backupVolume, backup);
        return String.join(";", chain);
    }

    private List<BackupVolumeChainState> getVolumeChainStates(final List<Backup.VolumeInfo> backupVolumes, final Backup backup) {
        final String backupEngine = getBackupDetail(backup, DETAIL_BACKUP_ENGINE);
        final List<BackupVolumeChainState> volumeChainStates = backupVolumes.stream()
                .sorted(Comparator.comparingLong(Backup.VolumeInfo::getDeviceId))
                .map(volume -> new BackupVolumeChainState(volume.getUuid(), backupEngine,
                        AblestackBackupFrameworkUtils.sanitizeChainFiles(getBackupChain(volume, backup))))
                .collect(Collectors.toList());
        AblestackBackupFrameworkUtils.validateVolumeChainStates(volumeChainStates);
        return volumeChainStates;
    }

    private List<String> getBackupChain(final Backup.VolumeInfo backupVolume, final Backup backup) {
        loadBackupDetailsIfNeeded(backup);
        final List<Backup> chain = getBackupChain(backup);
        final List<String> files = new ArrayList<>();
        for (final Backup chainBackup : chain) {
            final Backup.VolumeInfo volumeInfo = getBackedUpVolumeInfo(chainBackup.getBackedUpVolumes(), backupVolume.getUuid());
            if (volumeInfo != null) {
                final String filePath = BACKUP_ENGINE_RBD_DIFF.equals(getBackupDetail(chainBackup, DETAIL_BACKUP_ENGINE))
                        ? String.format("%s/%s", chainBackup.getExternalId(), volumeInfo.getPath())
                        : String.format("%s/%s", chainBackup.getExternalId(), volumeInfo.getPath());
                files.add(filePath);
            }
        }
        return files;
    }

    private List<Backup> getBackupChain(final Backup backup) {
        loadBackupDetailsIfNeeded(backup);
        final List<Backup> backups = backupDao.listByVmIdAndOffering(backup.getZoneId(), backup.getVmId(), backup.getBackupOfferingId());
        final Map<String, Backup> backupsByUuid = new HashMap<>();
        for (final Backup candidate : backups) {
            if (candidate instanceof BackupVO) {
                backupDao.loadDetails((BackupVO) candidate);
            }
            backupsByUuid.put(candidate.getUuid(), candidate);
        }

        final List<Backup> chain = new ArrayList<>();
        Backup current = backup;
        while (current != null) {
            chain.add(current);
            final String parentBackupUuid = getBackupDetail(current, DETAIL_PARENT_BACKUP_UUID);
            current = parentBackupUuid != null ? backupsByUuid.get(parentBackupUuid) : null;
        }
        Collections.reverse(chain);
        return chain;
    }

    private List<Backup> getRestoreChainForBackup(final Backup backup) {
        if (backup != null && StringUtils.equalsIgnoreCase(BACKUP_TYPE_INCREMENTAL, backup.getType())) {
            return getBackupChain(backup);
        }
        return Collections.singletonList(backup);
    }

    private List<Backup> getStagedRestoreChainForBackup(final Backup backup) {
        final List<Backup> restoreChain = getRestoreChainForBackup(backup);
        if (CollectionUtils.isEmpty(restoreChain)) {
            return restoreChain;
        }
        if (!StringUtils.equalsIgnoreCase(BACKUP_TYPE_INCREMENTAL, backup != null ? backup.getType() : null)) {
            return restoreChain;
        }
        if (restoreChain.size() <= 1) {
            return Collections.emptyList();
        }
        return new ArrayList<>(restoreChain.subList(0, restoreChain.size() - 1));
    }

    private void prepareRestoreSourcesOnStageHosts(final Long zoneId, final String destinationHostName, final List<Backup> restoreChain) {
        if (CollectionUtils.isEmpty(restoreChain)) {
            return;
        }
        final AblestackNetBackupClient client = getClient(zoneId);
        for (final Map.Entry<String, List<Backup>> entry : groupRestoreChainByStageHost(destinationHostName, restoreChain).entrySet()) {
            final String sourceHost = entry.getKey();
            final List<Backup> sourceHostChain = entry.getValue();
            if (CollectionUtils.isEmpty(sourceHostChain)) {
                continue;
            }
            LOG.info("Preparing NetBackup restore sources from stage/source host [{}] to destination host [{}] for backup paths {}",
                    sourceHost, destinationHostName,
                    sourceHostChain.stream().map(Backup::getExternalId).collect(Collectors.toList()));
            final String chainJobId = client.restoreBackupChain(sourceHost, destinationHostName, sourceHostChain);
            if (StringUtils.isNotBlank(chainJobId) && CollectionUtils.isNotEmpty(sourceHostChain)) {
                final Backup chainTarget = sourceHostChain.get(sourceHostChain.size() - 1);
                backupDetailsDao.removeDetail(chainTarget.getId(), DETAIL_RESTORE_CHAIN_JOB_ID);
                backupDetailsDao.addDetail(chainTarget.getId(), DETAIL_RESTORE_CHAIN_JOB_ID, chainJobId, false);
            }
        }
    }

    private void cleanupRestoreSourcesOnStageHosts(final Long zoneId, final String destinationHostName, final List<Backup> restoreChain) {
        if (CollectionUtils.isEmpty(restoreChain)) {
            return;
        }
        for (final Map.Entry<String, List<Backup>> entry : groupRestoreChainByStageHost(destinationHostName, restoreChain).entrySet()) {
            final String sourceHost = entry.getKey();
            final List<Backup> sourceHostChain = entry.getValue();
            if (CollectionUtils.isEmpty(sourceHostChain)) {
                continue;
            }
            LOG.info("Cleaning up NetBackup restore sources on stage/source host [{}] for backup paths {}",
                    sourceHost, sourceHostChain.stream().map(Backup::getExternalId).collect(Collectors.toList()));
            cleanupBackupPathsOnHost(zoneId, sourceHost, sourceHostChain.stream()
                    .map(Backup::getExternalId)
                    .filter(StringUtils::isNotBlank)
                    .distinct()
                    .collect(Collectors.toList()));
        }
    }

    private void cleanupBackupPathsOnHost(final Long zoneId, final String hostName, final List<String> backupPaths) {
        if (CollectionUtils.isEmpty(backupPaths) || StringUtils.isBlank(hostName)) {
            return;
        }
        final HostVO host = findRestoreHost(hostName);
        if (host == null) {
            LOG.warn("Unable to find restore host [{}] while cleaning up NetBackup restore paths {}.", hostName, backupPaths);
            return;
        }
        final int sshPort = NumbersUtil.parseInt(configDao.getValue("kvm.ssh.port"), 22);
        final Ternary<String, String, String> credentials = getKVMHyperisorCredentials(host);
        for (final String backupPath : backupPaths) {
            try {
                executeDeleteBackupPathCommand(host, credentials.first(), credentials.second(), sshPort,
                        String.format("rm -rf %s", shellQuote(backupPath)));
            } catch (final Exception e) {
                LOG.warn("Failed to cleanup NetBackup restore source path [{}] on host [{}]", backupPath, hostName, e);
            }
        }
    }

    private HostVO findRestoreHost(final String restoreHostName) {
        HostVO host = hostDao.findByName(restoreHostName);
        if (host != null) {
            return host;
        }
        return hostDao.findByIp(restoreHostName);
    }

    private void executeDeleteBackupPathCommand(final HostVO host, final String username, final String password, final int sshPort,
            final String command) {
        if (host == null || StringUtils.isBlank(command)) {
            return;
        }
        try {
            final Pair<Boolean, String> response = SshHelper.sshExecute(host.getPrivateIpAddress(), sshPort,
                    username, null, password, command, 120000, 120000, 3600000);
            if (!response.first()) {
                LOG.warn("NetBackup restore cleanup command failed on host [{}]: {}", host.getName(), response.second());
            }
        } catch (final Exception e) {
            LOG.warn("Failed to execute NetBackup restore cleanup command on host [{}]", host.getName(), e);
        }
    }

    private LinkedHashMap<String, List<Backup>> groupRestoreChainByStageHost(final String destinationHostName, final List<Backup> restoreChain) {
        final LinkedHashMap<String, List<Backup>> grouped = new LinkedHashMap<>();
        for (final Backup chainBackup : restoreChain) {
            loadBackupDetailsIfNeeded(chainBackup);
            final String sourceHost = getRestoreSourceHost(chainBackup, destinationHostName);
            grouped.computeIfAbsent(sourceHost, key -> new ArrayList<>()).add(chainBackup);
        }
        return grouped;
    }

    private String getRestoreSourceHost(final Backup backup, final String defaultHostName) {
        final String sourceHost = getBackupDetail(backup, DETAIL_POLICY_NAME);
        if (StringUtils.isBlank(sourceHost)) {
            LOG.warn("NetBackup source/stage host detail [{}] is missing for backup [{}]. Falling back to destination host [{}].",
                    DETAIL_POLICY_NAME, backup != null ? backup.getUuid() : null, defaultHostName);
            return defaultHostName;
        }
        return sourceHost;
    }

    private void validateRestoreChainIntegrity(final Backup backup) {
        if (backup == null) {
            return;
        }
        loadBackupDetailsIfNeeded(backup);
        if (isLegacyBackup(backup)) {
            return;
        }

        final Set<String> visitedBackupUuids = new HashSet<>();
        Backup current = backup;
        while (current != null) {
            final String currentBackupUuid = current.getUuid();
            if (StringUtils.isNotBlank(currentBackupUuid) && !visitedBackupUuids.add(currentBackupUuid)) {
                throw new CloudRuntimeException(String.format(
                        "Unable to restore backup [%s] because the incremental backup chain contains a cycle at [%s].",
                        backup.getUuid(), currentBackupUuid));
            }

            final String parentBackupUuid = getBackupDetail(current, DETAIL_PARENT_BACKUP_UUID);
            if (StringUtils.isBlank(parentBackupUuid)) {
                return;
            }

            final Backup parentBackup = backupDao.findByUuid(parentBackupUuid);
            if (parentBackup == null) {
                throw new CloudRuntimeException(String.format(
                        "Unable to restore backup [%s] because parent backup [%s] is missing from the incremental chain.",
                        backup.getUuid(), parentBackupUuid));
            }
            loadBackupDetailsIfNeeded(parentBackup);
            current = parentBackup;
        }
    }

    private boolean isLegacyBackup(final Backup backup) {
        return getBackupDetail(backup, DETAIL_BACKUP_ENGINE) == null;
    }

    private String getLegacyBackupFileName(final Backup.VolumeInfo volumeInfo) {
        final String diskPrefix = Volume.Type.ROOT.equals(volumeInfo.getType()) ? "root" : "datadisk";
        return String.format("%s.%s.qcow2", diskPrefix, volumeInfo.getUuid());
    }

    private Backup.VolumeInfo getBackedUpVolumeInfo(final List<Backup.VolumeInfo> backedUpVolumes, final String volumeUuid) {
        return backedUpVolumes.stream()
                .filter(v -> v.getUuid().equals(volumeUuid))
                .findFirst()
                .orElse(null);
    }

    private AblestackNetBackupClient getClient(final Long zoneId) {
        try {
            return new AblestackNetBackupClient(NetBackupUrl.valueIn(zoneId), NetBackupApiKey.valueIn(zoneId),
                    NetBackupApiRequestTimeout.valueIn(zoneId));
        } catch (URISyntaxException e) {
            throw new CloudRuntimeException("Failed to parse NetBackup API URL: " + e.getMessage());
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            LOG.error("Failed to build NetBackup API client due to: ", e);
        }
        throw new CloudRuntimeException("Failed to build NetBackup API client");
    }

    @Override
    public String getCatalogBackupTime(final Long zoneId, final String backupId) {
        if (StringUtils.isBlank(backupId)) {
            return null;
        }
        return getClient(zoneId).getCatalogBackupTime(backupId);
    }

    @Override
    public void syncBackups(final VirtualMachine vm) {
        AblestackNetBackupClient client = null;
        final Set<Long> removedBackupIds = new HashSet<>();
        for (final Backup backup : backupDao.listByVmId(vm.getDataCenterId(), vm.getId())) {
            if (removedBackupIds.contains(backup.getId())) {
                continue;
            }
            if (Backup.Status.BackingUp.equals(backup.getStatus())) {
                if (backup.getDate() == null || backup.getDate().getTime() > System.currentTimeMillis() - STALE_BACKUP_THRESHOLD_MS) {
                    continue;
                }
                LOG.warn("Removing stale NetBackup backup [{}] for VM [{}] stuck in BackingUp for over one day.",
                        backup.getUuid(), vm.getInstanceName());
                removeBackupWithDetails(backup.getId());
                continue;
            }

            if (!Backup.Status.BackedUp.equals(backup.getStatus()) || backup.getDate() == null) {
                continue;
            }
            if (backup.getDate().getTime() > System.currentTimeMillis() - NETBACKUP_SYNC_DELETE_GRACE_MS) {
                continue;
            }
            final BackupOfferingVO backupOffering = backupOfferingDao.findById(backup.getBackupOfferingId());
            if (backupOffering == null || !StringUtils.equalsIgnoreCase(getName(), backupOffering.getProvider())) {
                continue;
            }

            loadBackupDetailsIfNeeded(backup);
            final String backupId = getBackupDetail(backup, DETAIL_BACKUP_ID);
            if (StringUtils.isBlank(backupId)) {
                continue;
            }

            if (client == null) {
                client = getClient(backup.getZoneId());
            }
            if (client.backupImageExists(backupId)) {
                continue;
            }

            final List<Long> removedIds = removeBackupGroup(backupId);
            removedBackupIds.addAll(removedIds);
            LOG.warn("Removed NetBackup backup group identified by backupId [{}] for VM [{}] because the catalog image no longer exists in NetBackup. Removed backup row ids={}",
                    backupId, vm.getInstanceName(), removedIds);
        }
    }

    private List<Long> removeBackupGroup(final String backupId) {
        final Set<Long> backupIdsToRemove = new LinkedHashSet<>();
        if (StringUtils.isNotBlank(backupId)) {
            backupDetailsDao.findDetails(DETAIL_BACKUP_ID, backupId, false).stream()
                    .map(BackupDetailVO::getResourceId)
                    .forEach(backupIdsToRemove::add);
        }
        if (backupIdsToRemove.isEmpty()) {
            return Collections.emptyList();
        }

        final List<Long> removedIds = new ArrayList<>();
        for (final Long backupIdToRemove : backupIdsToRemove) {
            final Backup backup = backupDao.findByIdIncludingRemoved(backupIdToRemove);
            if (backup == null) {
                continue;
            }
            final BackupOfferingVO backupOffering = backupOfferingDao.findById(backup.getBackupOfferingId());
            if (backupOffering == null || !StringUtils.equalsIgnoreCase(getName(), backupOffering.getProvider())) {
                continue;
            }
            removeBackupWithDetails(backupIdToRemove);
            removedIds.add(backupIdToRemove);
        }
        return removedIds;
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

    @Override
    public boolean supportsOutOfBandBackupSync() {
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
