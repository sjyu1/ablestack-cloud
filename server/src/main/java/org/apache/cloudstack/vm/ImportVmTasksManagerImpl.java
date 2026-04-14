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
package org.apache.cloudstack.vm;

import com.cloud.agent.AgentManager;
import com.cloud.agent.api.AblestackV2KUndefineDomainCommand;
import com.cloud.agent.api.AblestackV2KStatusAnswer;
import com.cloud.agent.api.AblestackV2KStatusCommand;
import com.cloud.agent.api.Answer;
import com.cloud.dc.DataCenter;
import com.cloud.dc.DataCenterVO;
import com.cloud.dc.dao.ClusterDao;
import com.cloud.dc.dao.DataCenterDao;
import com.cloud.exception.AgentUnavailableException;
import com.cloud.exception.OperationTimedoutException;
import com.cloud.host.Host;
import com.cloud.host.Status;
import com.cloud.host.HostVO;
import com.cloud.host.dao.HostDao;
import com.cloud.serializer.GsonHelper;
import com.cloud.user.Account;
import com.cloud.user.AccountService;
import com.cloud.utils.DateUtil;
import com.cloud.utils.Pair;
import com.cloud.vm.ImportVMTaskVO;
import com.cloud.vm.UserVmVO;
import com.cloud.vm.dao.ImportVMTaskDao;
import com.cloud.vm.dao.UserVmDao;
import com.cloud.service.dao.ServiceOfferingDao;
import org.apache.cloudstack.api.ApiErrorCode;
import org.apache.cloudstack.api.ServerApiException;
import org.apache.cloudstack.api.command.admin.vm.ListImportVMTasksCmd;
import org.apache.cloudstack.api.response.ImportVMTaskResponse;
import org.apache.cloudstack.api.response.ListResponse;
import com.cloud.org.Cluster;
import com.cloud.service.ServiceOfferingVO;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.inject.Inject;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.apache.cloudstack.vm.ImportVmTask.Step.CloningInstance;
import static org.apache.cloudstack.vm.ImportVmTask.Step.Completed;
import static org.apache.cloudstack.vm.ImportVmTask.Step.ConvertingInstance;
import static org.apache.cloudstack.vm.ImportVmTask.Step.Importing;
import static org.apache.cloudstack.vm.ImportVmTask.Step.Prepare;

public class ImportVmTasksManagerImpl implements ImportVmTasksManager {

    protected Logger logger = LogManager.getLogger(ImportVmTasksManagerImpl.class);
    private static final String V2K_STATUS_PREFIX = "[V2K] ";
    private static final String V2K_STEP_NONE = "None";
    private static final Pattern V2K_PHASE_PATTERN = Pattern.compile("(?i)phase:\\s*([^|\\n]+)");
    private static final Pattern V2K_STATE_PATTERN = Pattern.compile("(?i)state:\\s*([^|\\n]+)");

    @Inject
    private ImportVMTaskDao importVMTaskDao;
    @Inject
    private DataCenterDao dataCenterDao;
    @Inject
    private ClusterDao clusterDao;
    @Inject
    private AccountService accountService;
    @Inject
    private HostDao hostDao;
    @Inject
    private UserVmDao userVmDao;
    @Inject
    private ServiceOfferingDao serviceOfferingDao;
    @Inject
    private AgentManager agentManager;
    public ImportVmTasksManagerImpl() {
    }

    @Override
    public ListResponse<ImportVMTaskResponse> listImportVMTasks(ListImportVMTasksCmd cmd) {
        Long zoneId = cmd.getZoneId();
        Long accountId = cmd.getAccountId();
        String vcenter = cmd.getVcenter();
        Long convertHostId = cmd.getConvertHostId();
        Long startIndex = cmd.getStartIndex();
        Long pageSizeVal = cmd.getPageSizeVal();

        ImportVmTask.TaskState state = getStateFromFilter(cmd.getTasksFilter());
        Pair<List<ImportVMTaskVO>, Integer> result = importVMTaskDao.listImportVMTasks(zoneId, accountId, vcenter, convertHostId, state, startIndex, pageSizeVal);
        List<ImportVMTaskVO> tasks = result.first();

        List<ImportVMTaskResponse> responses = new ArrayList<>();
        for (ImportVMTaskVO task : tasks) {
            responses.add(createImportVMTaskResponse(task));
        }
        ListResponse<ImportVMTaskResponse> listResponses = new ListResponse<>();
        listResponses.setResponses(responses, result.second());
        return listResponses;
    }

    private ImportVmTask.TaskState getStateFromFilter(String tasksFilter) {
        if (StringUtils.isBlank(tasksFilter) || tasksFilter.equalsIgnoreCase("all")) {
            return null;
        }
        try {
            return ImportVmTask.TaskState.getValue(tasksFilter);
        } catch (IllegalArgumentException e) {
            throw new ServerApiException(ApiErrorCode.PARAM_ERROR, String.format("Invalid value for task state: %s", tasksFilter));
        }
    }

    @Override
    public ImportVmTask createImportVMTaskRecord(DataCenter zone, Account owner, long userId, String displayName, String vcenter, String datacenterName, String sourceVMName, Host convertHost, Host importHost) {
        logger.debug("Creating import VM task entry for VM: {} for account {} on zone {} " +
                        "from the vCenter: {} / datacenter: {} / source VM: {}",
                sourceVMName, owner.getAccountName(), zone.getName(), displayName, vcenter, datacenterName);
        ImportVMTaskVO importVMTaskVO = new ImportVMTaskVO(zone.getId(), owner.getAccountId(), userId, displayName,
                vcenter, datacenterName, sourceVMName, convertHost.getId(), importHost.getId());
        importVMTaskVO.setState(ImportVmTask.TaskState.Running);
        return importVMTaskDao.persist(importVMTaskVO);
    }

    private String getStepDescription(ImportVMTaskVO importVMTaskVO, Host convertHost, Host importHost,
                                      ImportVMTaskVO.Step step, Date updatedDate) {
        String sourceVMName = importVMTaskVO.getSourceVMName();
        String vcenter = importVMTaskVO.getVcenter();
        String datacenter = importVMTaskVO.getDatacenter();

        StringBuilder stringBuilder = new StringBuilder();
        if (Completed == step) {
            stringBuilder.append("Completed at ").append(DateUtil.getDateDisplayString(TimeZone.getTimeZone("GMT"), updatedDate));
        } else {
            if (CloningInstance == step) {
                stringBuilder.append(String.format("Cloning source instance: %s on vCenter: %s / datacenter: %s", sourceVMName, vcenter, datacenter));
            } else if (ConvertingInstance == step) {
                stringBuilder.append(String.format("Converting the cloned VMware instance to a KVM instance on the host: %s", convertHost.getName()));
            } else if (Importing == step) {
                stringBuilder.append(String.format("Importing the converted KVM instance on the host: %s", importHost.getName()));
            } else if (Prepare == step) {
                stringBuilder.append("Preparing to convert Vmware instance");
            }
        }
        return stringBuilder.toString();
    }

    @Override
    public void updateImportVMTaskStep(ImportVmTask importVMTask, DataCenter zone, Account owner, Host convertHost,
                                       Host importHost, Long vmId, ImportVmTask.Step step) {
        ImportVMTaskVO importVMTaskVO = (ImportVMTaskVO) importVMTask;
        logger.debug("Updating import VM task entry for VM: {} for account {} on zone {} " +
                        "from the vCenter: {} / datacenter: {} / source VM: {} to step: {}",
                importVMTaskVO.getSourceVMName(), owner.getAccountName(), zone.getName(), importVMTaskVO.getDisplayName(),
                importVMTaskVO.getVcenter(), importVMTaskVO.getDatacenter(), step);
        Date updatedDate = DateUtil.now();
        String description = getStepDescription(importVMTaskVO, convertHost, importHost, step, updatedDate);
        importVMTaskVO.setStep(step);
        importVMTaskVO.setDescription(description);
        importVMTaskVO.setUpdated(updatedDate);
        if (Completed == step) {
            Duration duration = Duration.between(importVMTaskVO.getCreated().toInstant(), updatedDate.toInstant());
            importVMTaskVO.setDuration(duration.toMillis());
            importVMTaskVO.setVmId(vmId);
            importVMTaskVO.setState(ImportVmTask.TaskState.Completed);
        }
        importVMTaskDao.update(importVMTaskVO.getId(), importVMTaskVO);
    }

    @Override
    public void updateImportVMTaskV2KStep(ImportVmTask importVMTask, ImportVmTask.V2KStep step) {
        ImportVMTaskVO importVMTaskVO = (ImportVMTaskVO) importVMTask;
        Date updatedDate = DateUtil.now();
        importVMTaskVO.setV2kStep(step != null ? step.name() : V2K_STEP_NONE);
        importVMTaskVO.setUpdated(updatedDate);
        importVMTaskDao.update(importVMTaskVO.getId(), importVMTaskVO);
    }

    @Override
    public void updateImportVMTaskV2KContext(ImportVmTask importVMTask, Long clusterId, Long serviceOfferingId,
                                             Long targetStoragePoolId, String sourceClusterName, String sourceHostName,
                                             Long vcenterId, String vcenterUsername, String vcenterPassword,
                                             Map<String, String> serviceOfferingDetails,
                                             Map<String, Map<String, String>> nicSelectionMap) {
        ImportVMTaskVO importVMTaskVO = (ImportVMTaskVO) importVMTask;
        importVMTaskVO.setClusterId(clusterId);
        importVMTaskVO.setServiceOfferingId(serviceOfferingId);
        importVMTaskVO.setV2kTargetStoragePoolId(targetStoragePoolId);
        importVMTaskVO.setSourceClusterName(sourceClusterName);
        importVMTaskVO.setSourceHostName(sourceHostName);
        importVMTaskVO.setVcenterId(vcenterId);
        importVMTaskVO.setVcenterUsername(vcenterUsername);
        importVMTaskVO.setVcenterPassword(vcenterPassword);
        importVMTaskVO.setServiceOfferingDetails(serializeMap(serviceOfferingDetails));
        importVMTaskVO.setNicNetworkMap(serializeMap(nicSelectionMap));
        importVMTaskDao.update(importVMTaskVO.getId(), importVMTaskVO);
    }

    private String serializeMap(Map<?, ?> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        return GsonHelper.getGson().toJson(map);
    }

    @Override
    public void updateImportVMTaskErrorState(ImportVmTask importVMTask, ImportVmTask.TaskState state, String errorMsg) {
        ImportVMTaskVO importVMTaskVO = (ImportVMTaskVO) importVMTask;
        Date updatedDate = DateUtil.now();
        importVMTaskVO.setUpdated(updatedDate);
        importVMTaskVO.setState(state);
        importVMTaskVO.setDescription(errorMsg);
        importVMTaskDao.update(importVMTaskVO.getId(), importVMTaskVO);
    }

    private ImportVMTaskResponse createImportVMTaskResponse(ImportVMTaskVO task) {
        ImportVMTaskResponse response = new ImportVMTaskResponse();
        response.setId(task.getUuid());
        DataCenterVO zone = dataCenterDao.findById(task.getZoneId());
        if (zone != null) {
            response.setZoneId(zone.getUuid());
            response.setZoneName(zone.getName());
        }
        Account account = accountService.getAccount(task.getAccountId());
        if (account != null) {
            response.setAccountId(account.getUuid());
            response.setAccountName(account.getAccountName());
        }
        Cluster cluster = task.getClusterId() != null ? clusterDao.findById(task.getClusterId()) : null;
        if (cluster != null) {
            response.setClusterId(cluster.getUuid());
        }
        ServiceOfferingVO serviceOffering = task.getServiceOfferingId() != null ? serviceOfferingDao.findById(task.getServiceOfferingId()) : null;
        if (serviceOffering != null) {
            response.setServiceOfferingId(serviceOffering.getUuid());
        }
        response.setVcenter(task.getVcenter());
        response.setDatacenterName(task.getDatacenter());
        response.setSourceVMName(task.getSourceVMName());
        response.setDisplayName(task.getDisplayName());
        String resolvedV2kStep = resolveV2KStep(task);
        response.setV2kStep(StringUtils.defaultIfBlank(resolvedV2kStep, V2K_STEP_NONE));
        response.setStep(StringUtils.isNotBlank(resolvedV2kStep) ? getStoredV2KStepDisplayField(resolvedV2kStep) : getStepDisplayField(task.getStep()));
        response.setDescription(task.getDescription());
        response.setState(task.getState().name());
        setAblestackV2KStatus(response, task);

        Date updated = task.getUpdated();
        Date currentDate = new Date();

        if (updated != null) {
            if (ImportVmTask.TaskState.Running == task.getState()) {
                Duration stepDuration = Duration.between(updated.toInstant(), currentDate.toInstant());
                response.setStepDuration(getDurationDisplay(stepDuration.toMillis()));
            } else {
                Duration totalDuration = Duration.between(task.getCreated().toInstant(), updated.toInstant());
                response.setDuration(getDurationDisplay(totalDuration.toMillis()));
            }
        }

        HostVO host = hostDao.findById(task.getConvertHostId());
        if (host != null) {
            response.setConvertInstanceHostId(host.getUuid());
            response.setConvertInstanceHostName(host.getName());
        }
        if (task.getVmId() != null) {
            UserVmVO userVm = userVmDao.findById(task.getVmId());
            if (userVm != null) {
                // Migrated VM could have been removed from CloudStack after the migration
                response.setVirtualMachineId(userVm.getUuid());
            }
        }
        response.setCreated(task.getCreated());
        response.setLastUpdated(task.getUpdated());
        response.setObjectName("importvmtask");
        return response;
    }

    private void setAblestackV2KStatus(ImportVMTaskResponse response, ImportVMTaskVO task) {
        if (task == null || task.getState() != ImportVmTask.TaskState.Running || !isV2KTask(task)) {
            return;
        }
        HostVO host = hostDao.findById(task.getConvertHostId());
        if (host == null || host.getStatus() != Status.Up) {
            return;
        }

        AblestackV2KStatusCommand statusCommand = new AblestackV2KStatusCommand(task.getSourceVMName());
        statusCommand.setWait(30);
        try {
            Answer answer = agentManager.send(host.getId(), statusCommand);
            if (!(answer instanceof AblestackV2KStatusAnswer) || !answer.getResult()) {
                logger.debug("Unable to retrieve ablestack-v2k status for source VM {} on host {}: {}",
                        task.getSourceVMName(), host.getName(), answer != null ? answer.getDetails() : "no answer");
                return;
            }
            AblestackV2KStatusAnswer status = (AblestackV2KStatusAnswer) answer;
            response.setPhase(status.getPhase());
            response.setMigrationState(status.getMigrationState());
            response.setMigrationStep(status.getMigrationStep());
            response.setSyncPhysical(status.getSyncPhysical());
            response.setWorkdir(status.getWorkdir());
            updateTaskDescriptionWithV2KStatus(response, task, status);
        } catch (AgentUnavailableException | OperationTimedoutException e) {
            logger.debug("Error while retrieving ablestack-v2k status for source VM {} on host {}: {}",
                    task.getSourceVMName(), host.getName(), e.getMessage());
        }
    }

    private void updateTaskDescriptionWithV2KStatus(ImportVMTaskResponse response, ImportVMTaskVO task, AblestackV2KStatusAnswer status) {
        Date now = DateUtil.now();
        boolean changed = false;
        ImportVmTask.TaskState previousTaskState = task.getState();
        ImportVmTask.V2KStep v2kStep = getProgressiveV2KStep(task != null ? task.getV2kStep() : null, getV2KStepFromStatus(status));
        String v2kStepName = v2kStep.name();
        String v2kStatusMessage = buildV2KStatusMessage(status);
        String descriptionWithoutV2K = stripV2KStatusLine(task.getDescription());
        String mergedDescription = mergeV2KStatusAndDescription(v2kStatusMessage, descriptionWithoutV2K);

        response.setDescription(mergedDescription);
        if (!StringUtils.equals(task.getDescription(), mergedDescription)) {
            task.setDescription(mergedDescription);
            changed = true;
        }

        response.setV2kStep(v2kStepName);
        if (!StringUtils.equals(task.getV2kStep(), v2kStepName)) {
            task.setV2kStep(v2kStepName);
            changed = true;
        }

        ImportVmTask.TaskState taskState = getTaskStateFromV2KStatus(task, status, v2kStep);
        if (taskState != task.getState()) {
            task.setState(taskState);
            changed = true;
        }

        if (task.getCreated() != null) {
            Long durationMs = Duration.between(task.getCreated().toInstant(), now.toInstant()).toMillis();
            if (!durationMs.equals(task.getDuration())) {
                task.setDuration(durationMs);
                changed = true;
            }
        }

        response.setState(task.getState().name());
        response.setStep(getV2KStepDisplayField(v2kStep));

        if (!changed) {
            return;
        }

        if (previousTaskState != ImportVmTask.TaskState.Failed && taskState == ImportVmTask.TaskState.Failed) {
            cleanupAblestackV2KDomain(task);
        }

        // Keep updated as step-start timestamp while running; update it only on terminal states.
        if (taskState == ImportVmTask.TaskState.Completed || taskState == ImportVmTask.TaskState.Failed) {
            task.setUpdated(now);
        }
        importVMTaskDao.update(task.getId(), task);
    }

    private void cleanupAblestackV2KDomain(ImportVMTaskVO task) {
        if (task == null || task.getConvertHostId() == 0L || StringUtils.isBlank(task.getSourceVMName())) {
            return;
        }
        HostVO convertHost = hostDao.findById(task.getConvertHostId());
        if (convertHost == null) {
            logger.warn("Unable to cleanup ablestack-v2k domain {} because conversion host id {} does not exist",
                    task.getSourceVMName(), task.getConvertHostId());
            return;
        }
        AblestackV2KUndefineDomainCommand cleanupCommand = new AblestackV2KUndefineDomainCommand(task.getSourceVMName(), true);
        cleanupCommand.setWait(60);
        try {
            Answer answer = agentManager.send(convertHost.getId(), cleanupCommand);
            if (answer == null || !answer.getResult()) {
                logger.warn("Unable to cleanup ablestack-v2k domain {} on host {} after failed status detection: {}",
                        task.getSourceVMName(), convertHost.getName(), answer != null ? answer.getDetails() : "no answer");
            }
        } catch (AgentUnavailableException | OperationTimedoutException e) {
            logger.warn("Could not cleanup ablestack-v2k domain {} on host {} after failed status detection due to: {}",
                    task.getSourceVMName(), convertHost.getName(), e.getMessage());
        }
    }

    private ImportVmTask.V2KStep getProgressiveV2KStep(String persistedV2kStep, ImportVmTask.V2KStep statusV2kStep) {
        String normalizedPersistedStep = normalizeV2KStep(persistedV2kStep);
        if (StringUtils.isBlank(normalizedPersistedStep)) {
            return statusV2kStep;
        }
        try {
            ImportVmTask.V2KStep persistedStep = ImportVmTask.V2KStep.valueOf(normalizedPersistedStep);
            return persistedStep.ordinal() > statusV2kStep.ordinal() ? persistedStep : statusV2kStep;
        } catch (IllegalArgumentException e) {
            logger.debug("Invalid persisted v2k step [{}] found for import VM task while resolving current step.", persistedV2kStep);
            return statusV2kStep;
        }
    }

    private ImportVmTask.TaskState getTaskStateFromV2KStatus(ImportVMTaskVO task, AblestackV2KStatusAnswer status, ImportVmTask.V2KStep v2kStep) {
        if (isFailedV2KMigrationState(status.getMigrationState())) {
            return ImportVmTask.TaskState.Failed;
        }
        // For V2K workflow, task is completed only after unmanaged->managed import has finished.
        if (v2kStep == ImportVmTask.V2KStep.Completed && task != null && task.getVmId() != null) {
            return ImportVmTask.TaskState.Completed;
        }
        return ImportVmTask.TaskState.Running;
    }

    private boolean isFailedV2KMigrationState(String migrationState) {
        String state = StringUtils.lowerCase(StringUtils.trimToEmpty(migrationState));
        return state.contains("fail") || state.contains("error") || state.contains("abort") || state.contains("cancel");
    }

    private boolean isCompletedV2KMigrationState(String migrationState) {
        String state = StringUtils.lowerCase(StringUtils.trimToEmpty(migrationState));
        return state.contains("complete") || state.contains("success") || "done".equals(state) || "finished".equals(state);
    }

    private ImportVmTask.V2KStep getV2KStepFromStatus(AblestackV2KStatusAnswer status) {
        String phase = StringUtils.lowerCase(StringUtils.trimToEmpty(status.getPhase()));
        boolean completed = isCompletedV2KMigrationState(status.getMigrationState());

        if (phase.contains("phase2")) {
            return completed ? ImportVmTask.V2KStep.Phase2_Completed : ImportVmTask.V2KStep.Phase2_In_Progress;
        }
        if (phase.contains("phase1")) {
            return completed ? ImportVmTask.V2KStep.Phase1_Completed : ImportVmTask.V2KStep.Phase1_In_Progress;
        }
        return completed ? ImportVmTask.V2KStep.Completed : ImportVmTask.V2KStep.Phase1_In_Progress;
    }

    private String resolveV2KStep(ImportVMTaskVO task) {
        String normalizedPersistedStep = normalizeV2KStep(task != null ? task.getV2kStep() : null);
        if (StringUtils.isNotBlank(normalizedPersistedStep)) {
            return normalizedPersistedStep;
        }
        ImportVmTask.V2KStep inferredStep = inferV2KStepFromDescription(task != null ? task.getDescription() : null);
        return inferredStep != null ? inferredStep.name() : null;
    }

    private String normalizeV2KStep(String v2kStep) {
        String normalized = StringUtils.trimToNull(v2kStep);
        if (normalized == null || V2K_STEP_NONE.equalsIgnoreCase(normalized) || "null".equalsIgnoreCase(normalized)) {
            return null;
        }
        return normalized;
    }

    private ImportVmTask.V2KStep inferV2KStepFromDescription(String description) {
        String normalizedDescription = StringUtils.defaultString(description);
        if (!StringUtils.containsIgnoreCase(normalizedDescription, V2K_STATUS_PREFIX)
                && !StringUtils.containsIgnoreCase(normalizedDescription, "phase:")) {
            return null;
        }

        String phase = extractV2KField(normalizedDescription, V2K_PHASE_PATTERN);
        String migrationState = extractV2KField(normalizedDescription, V2K_STATE_PATTERN);
        boolean completed = isCompletedV2KMigrationState(migrationState);
        String phaseValue = StringUtils.lowerCase(StringUtils.trimToEmpty(phase));

        if (phaseValue.contains("phase2")) {
            return completed ? ImportVmTask.V2KStep.Phase2_Completed : ImportVmTask.V2KStep.Phase2_In_Progress;
        }
        if (phaseValue.contains("phase1")) {
            return completed ? ImportVmTask.V2KStep.Phase1_Completed : ImportVmTask.V2KStep.Phase1_In_Progress;
        }
        if (StringUtils.isNotBlank(migrationState)) {
            return completed ? ImportVmTask.V2KStep.Completed : ImportVmTask.V2KStep.Phase1_In_Progress;
        }
        return null;
    }

    private String extractV2KField(String description, Pattern pattern) {
        Matcher matcher = pattern.matcher(StringUtils.defaultString(description));
        if (!matcher.find()) {
            return null;
        }
        return StringUtils.trimToNull(matcher.group(1));
    }

    private String mergeV2KStatusAndDescription(String v2kStatusMessage, String descriptionWithoutV2K) {
        String statusLine = StringUtils.trimToNull(v2kStatusMessage);
        String message = StringUtils.trimToNull(descriptionWithoutV2K);
        if (statusLine == null) {
            return StringUtils.defaultString(message);
        }
        if (message == null) {
            return statusLine;
        }
        return String.format("%s%n%s", statusLine, message);
    }

    private String stripV2KStatusLine(String description) {
        String normalized = StringUtils.defaultString(description);
        if (StringUtils.isBlank(normalized)) {
            return "";
        }
        String[] lines = normalized.split("\\R");
        List<String> messageLines = new ArrayList<>();
        for (String line : lines) {
            if (StringUtils.startsWith(StringUtils.trimToEmpty(line), V2K_STATUS_PREFIX)) {
                continue;
            }
            messageLines.add(line);
        }
        return StringUtils.trimToEmpty(String.join("\n", messageLines));
    }

    private String buildV2KStatusMessage(AblestackV2KStatusAnswer status) {
        return String.format("%sPHASE: %s | STATE: %s | STEP: %s | SYNC(Physical): %s | WORKDIR: %s",
                V2K_STATUS_PREFIX,
                StringUtils.defaultString(status.getPhase(), "-"),
                StringUtils.defaultString(status.getMigrationState(), "-"),
                StringUtils.defaultString(status.getMigrationStep(), "-"),
                StringUtils.defaultString(status.getSyncPhysical(), "-"),
                StringUtils.defaultString(status.getWorkdir(), "-"));
    }

    protected String getStepDisplayField(ImportVMTaskVO.Step step) {
        if (step == null) {
            return "-";
        }
        int totalSteps = ImportVMTaskVO.Step.values().length;
        return String.format("[%s/%s] %s", step.ordinal() + 1, totalSteps, step.name());
    }

    protected String getStoredV2KStepDisplayField(String storedV2kStep) {
        if (StringUtils.isBlank(storedV2kStep) || V2K_STEP_NONE.equalsIgnoreCase(storedV2kStep)) {
            return "-";
        }
        try {
            return getV2KStepDisplayField(ImportVmTask.V2KStep.valueOf(storedV2kStep));
        } catch (IllegalArgumentException e) {
            logger.debug("Invalid persisted v2k step [{}] found for import VM task.", storedV2kStep);
            return storedV2kStep;
        }
    }

    protected String getV2KStepDisplayField(ImportVmTask.V2KStep step) {
        if (step == null) {
            return "-";
        }
        int totalSteps = ImportVmTask.V2KStep.values().length;
        return String.format("[%s/%s] %s", step.ordinal() + 1, totalSteps, step.name());
    }

    protected boolean isV2KTask(ImportVMTaskVO task) {
        return StringUtils.isNotBlank(resolveV2KStep(task));
    }

    protected static String getDurationDisplay(Long durationMs) {
        if (durationMs == null) {
            return null;
        }
        long hours = durationMs / (1000 * 60 * 60);
        long minutes = (durationMs / (1000 * 60)) % 60;
        long seconds = (durationMs / 1000) % 60;

        StringBuilder result = new StringBuilder();
        if (hours > 0) {
            result.append(String.format("%s hs ", hours));
        }
        if (minutes > 0) {
            result.append(String.format("%s min ", minutes));
        }
        if (seconds > 0) {
            result.append(String.format("%s secs", seconds));
        }
        return result.toString();
    }
}
