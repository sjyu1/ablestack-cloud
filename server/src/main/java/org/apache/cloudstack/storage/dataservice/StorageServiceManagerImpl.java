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

package org.apache.cloudstack.storage.dataservice;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;

import org.apache.cloudstack.api.ApiCommandResourceType;
import org.apache.cloudstack.api.command.user.storage.dataservice.AttachStorageVolumeToFileShareCmd;
import org.apache.cloudstack.api.command.user.storage.dataservice.CreateStorageIscsiAclCmd;
import org.apache.cloudstack.api.command.user.storage.dataservice.CreateStorageIscsiTargetCmd;
import org.apache.cloudstack.api.command.user.storage.dataservice.CreateStorageNfsAclCmd;
import org.apache.cloudstack.api.command.user.storage.dataservice.CreateStorageNfsExportCmd;
import org.apache.cloudstack.api.command.user.storage.dataservice.CreateStorageNvmeOfHostAclCmd;
import org.apache.cloudstack.api.command.user.storage.dataservice.CreateStorageNvmeOfNamespaceCmd;
import org.apache.cloudstack.api.command.user.storage.dataservice.CreateStorageNvmeOfSubsystemCmd;
import org.apache.cloudstack.api.command.user.storage.dataservice.CreateStorageSmbAclCmd;
import org.apache.cloudstack.api.command.user.storage.dataservice.CreateStorageSmbShareCmd;
import org.apache.cloudstack.api.command.user.storage.dataservice.CreateStorageServiceInstanceCmd;
import org.apache.cloudstack.api.command.user.storage.dataservice.DeleteStorageIscsiAclCmd;
import org.apache.cloudstack.api.command.user.storage.dataservice.DeleteStorageIscsiTargetCmd;
import org.apache.cloudstack.api.command.user.storage.dataservice.DeleteStorageNfsAclCmd;
import org.apache.cloudstack.api.command.user.storage.dataservice.DeleteStorageNfsExportCmd;
import org.apache.cloudstack.api.command.user.storage.dataservice.DeleteStorageNvmeOfHostAclCmd;
import org.apache.cloudstack.api.command.user.storage.dataservice.DeleteStorageNvmeOfNamespaceCmd;
import org.apache.cloudstack.api.command.user.storage.dataservice.DeleteStorageNvmeOfSubsystemCmd;
import org.apache.cloudstack.api.command.user.storage.dataservice.DeleteStorageSmbAclCmd;
import org.apache.cloudstack.api.command.user.storage.dataservice.DeleteStorageSmbShareCmd;
import org.apache.cloudstack.api.command.user.storage.dataservice.DeleteStorageServiceProtocolCmd;
import org.apache.cloudstack.api.command.user.storage.dataservice.DetachStorageServiceBackingVolumeCmd;
import org.apache.cloudstack.api.command.user.storage.dataservice.DisconnectStorageServiceSessionCmd;
import org.apache.cloudstack.api.command.user.storage.dataservice.EnableStorageServiceProtocolCmd;
import org.apache.cloudstack.api.command.user.storage.dataservice.JoinStorageServiceToAdDomainCmd;
import org.apache.cloudstack.api.command.user.storage.dataservice.LeaveStorageServiceFromAdDomainCmd;
import org.apache.cloudstack.api.command.user.storage.dataservice.ListStorageIscsiAclsCmd;
import org.apache.cloudstack.api.command.user.storage.dataservice.ListStorageIscsiTargetsCmd;
import org.apache.cloudstack.api.command.user.storage.dataservice.ListStorageNfsAclsCmd;
import org.apache.cloudstack.api.command.user.storage.dataservice.ListStorageNfsExportsCmd;
import org.apache.cloudstack.api.command.user.storage.dataservice.ListStorageNvmeOfHostAclsCmd;
import org.apache.cloudstack.api.command.user.storage.dataservice.ListStorageNvmeOfSubsystemsCmd;
import org.apache.cloudstack.api.command.user.storage.dataservice.ListStorageServiceDomainStatusCmd;
import org.apache.cloudstack.api.command.user.storage.dataservice.ListStorageServiceHealthCmd;
import org.apache.cloudstack.api.command.user.storage.dataservice.ListStorageServiceInventoryCmd;
import org.apache.cloudstack.api.command.user.storage.dataservice.ListStorageServiceInstancesCmd;
import org.apache.cloudstack.api.command.user.storage.dataservice.ListStorageServiceSessionsCmd;
import org.apache.cloudstack.api.command.user.storage.dataservice.ListStorageSmbAclsCmd;
import org.apache.cloudstack.api.command.user.storage.dataservice.ListStorageSmbSharesCmd;
import org.apache.cloudstack.api.command.user.storage.dataservice.PrepareStorageServiceNvmeOfVmCmd;
import org.apache.cloudstack.api.command.user.storage.dataservice.ResizeStorageFileShareCmd;
import org.apache.cloudstack.api.command.user.storage.dataservice.UpdateStorageIscsiAclCmd;
import org.apache.cloudstack.api.command.user.storage.dataservice.UpdateStorageIscsiTargetCmd;
import org.apache.cloudstack.api.command.user.storage.dataservice.UpdateStorageNfsAclCmd;
import org.apache.cloudstack.api.command.user.storage.dataservice.UpdateStorageNfsExportCmd;
import org.apache.cloudstack.api.command.user.storage.dataservice.UpdateStorageNvmeOfHostAclCmd;
import org.apache.cloudstack.api.command.user.storage.dataservice.UpdateStorageNvmeOfSubsystemCmd;
import org.apache.cloudstack.api.command.user.storage.dataservice.UpdateStorageSmbAclCmd;
import org.apache.cloudstack.api.command.user.storage.dataservice.UpdateStorageSmbShareCmd;
import org.apache.cloudstack.api.command.user.volume.ResizeVolumeCmd;
import org.apache.cloudstack.api.response.ListResponse;
import org.apache.cloudstack.api.response.StorageAccessRuleResponse;
import org.apache.cloudstack.api.response.StorageBlockTargetResponse;
import org.apache.cloudstack.api.response.StorageFileShareResponse;
import org.apache.cloudstack.api.response.StorageIdentityDomainResponse;
import org.apache.cloudstack.api.response.StorageNfsExportResponse;
import org.apache.cloudstack.api.response.StorageServiceInstanceResponse;
import org.apache.cloudstack.api.response.StorageServiceProtocolResponse;
import org.apache.cloudstack.api.response.StorageServiceRuntimeResponse;
import org.apache.cloudstack.api.response.StorageSmbShareResponse;
import org.apache.cloudstack.context.CallContext;
import org.apache.cloudstack.framework.config.ConfigKey;
import org.apache.cloudstack.framework.config.Configurable;
import org.apache.cloudstack.storage.sharedfs.SharedFS;
import org.apache.cloudstack.storage.dataservice.dao.StorageAccessRuleDao;
import org.apache.cloudstack.storage.dataservice.dao.StorageBlockTargetDao;
import org.apache.cloudstack.storage.dataservice.dao.StorageFileShareDao;
import org.apache.cloudstack.storage.dataservice.dao.StorageIdentityDomainDao;
import org.apache.cloudstack.storage.dataservice.dao.StorageServiceInstanceDao;
import org.apache.cloudstack.storage.dataservice.dao.StorageServiceProtocolDao;
import org.apache.commons.lang3.StringUtils;

import com.cloud.dc.DataCenterVO;
import com.cloud.dc.dao.DataCenterDao;
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.exception.ResourceAllocationException;
import com.cloud.network.dao.NetworkDao;
import com.cloud.network.dao.NetworkVO;
import com.cloud.service.ServiceOfferingVO;
import com.cloud.service.dao.ServiceOfferingDao;
import com.cloud.storage.Volume;
import com.cloud.storage.VolumeApiService;
import com.cloud.storage.VolumeVO;
import com.cloud.storage.dao.VolumeDao;
import com.cloud.user.Account;
import com.cloud.user.dao.AccountDao;
import com.cloud.utils.component.ManagerBase;
import com.cloud.utils.component.PluggableService;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.vm.VMInstanceVO;
import com.cloud.vm.NicVO;
import com.cloud.vm.dao.NicDao;
import com.cloud.vm.dao.NicSecondaryIpDao;
import com.cloud.vm.dao.NicSecondaryIpVO;
import com.cloud.vm.dao.VMInstanceDao;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class StorageServiceManagerImpl extends ManagerBase implements StorageService, PluggableService, Configurable {
    private static final Gson GSON = new Gson();
    private static final Gson RUNTIME_RESULT_GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final int NFS_ANONYMOUS_UID = 65534;
    private static final int NFS_ANONYMOUS_GID = 65534;
    private static final String NFS_WRITABLE_ROOT_SQUASH_MODE = "0775";
    private static final int FILE_SHARE_VOLUME_READY_ATTEMPTS = 30;
    private static final long FILE_SHARE_VOLUME_READY_INTERVAL_MS = 2000L;
    private static final List<String> SUPPORTED_FILE_SHARE_FILESYSTEMS = Arrays.asList("xfs", "ext4");
    private static final Pattern NFS_ENDPOINT_MODE_PATTERN = Pattern.compile("\"endpointMode\"\\s*:\\s*\"(ALL|SELECTED)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern IPV4_ADDRESS_PATTERN = Pattern.compile("\\b(?:(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)\\.){3}(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)\\b");

    @Inject
    private StorageServiceInstanceDao storageServiceInstanceDao;
    @Inject
    private StorageServiceProtocolDao storageServiceProtocolDao;
    @Inject
    private StorageFileShareDao storageFileShareDao;
    @Inject
    private StorageIdentityDomainDao storageIdentityDomainDao;
    @Inject
    private StorageBlockTargetDao storageBlockTargetDao;
    @Inject
    private StorageAccessRuleDao storageAccessRuleDao;
    @Inject
    private StorageServiceGuestCommandDispatcher guestCommandDispatcher;
    @Inject
    private AccountDao accountDao;
    @Inject
    private DataCenterDao dataCenterDao;
    @Inject
    private NetworkDao networkDao;
    @Inject
    private ServiceOfferingDao serviceOfferingDao;
    @Inject
    private VMInstanceDao vmInstanceDao;
    @Inject
    private NicDao nicDao;
    @Inject
    private NicSecondaryIpDao nicSecondaryIpDao;
    @Inject
    private VolumeDao volumeDao;
    @Inject
    private VolumeApiService volumeApiService;

    @Override
    public List<Class<?>> getCommands() {
        final List<Class<?>> commands = new ArrayList<>();
        commands.add(CreateStorageServiceInstanceCmd.class);
        commands.add(ListStorageServiceInstancesCmd.class);
        commands.add(EnableStorageServiceProtocolCmd.class);
        commands.add(DeleteStorageServiceProtocolCmd.class);
        commands.add(CreateStorageNfsExportCmd.class);
        commands.add(UpdateStorageNfsExportCmd.class);
        commands.add(DeleteStorageNfsExportCmd.class);
        commands.add(ListStorageNfsExportsCmd.class);
        commands.add(CreateStorageNfsAclCmd.class);
        commands.add(UpdateStorageNfsAclCmd.class);
        commands.add(DeleteStorageNfsAclCmd.class);
        commands.add(ListStorageNfsAclsCmd.class);
        commands.add(CreateStorageSmbShareCmd.class);
        commands.add(UpdateStorageSmbShareCmd.class);
        commands.add(DeleteStorageSmbShareCmd.class);
        commands.add(ListStorageSmbSharesCmd.class);
        commands.add(CreateStorageSmbAclCmd.class);
        commands.add(UpdateStorageSmbAclCmd.class);
        commands.add(DeleteStorageSmbAclCmd.class);
        commands.add(ListStorageSmbAclsCmd.class);
        commands.add(JoinStorageServiceToAdDomainCmd.class);
        commands.add(LeaveStorageServiceFromAdDomainCmd.class);
        commands.add(ListStorageServiceDomainStatusCmd.class);
        commands.add(ListStorageServiceHealthCmd.class);
        commands.add(ListStorageServiceInventoryCmd.class);
        commands.add(ListStorageServiceSessionsCmd.class);
        commands.add(DisconnectStorageServiceSessionCmd.class);
        commands.add(AttachStorageVolumeToFileShareCmd.class);
        commands.add(DetachStorageServiceBackingVolumeCmd.class);
        commands.add(ResizeStorageFileShareCmd.class);
        commands.add(PrepareStorageServiceNvmeOfVmCmd.class);
        commands.add(CreateStorageIscsiTargetCmd.class);
        commands.add(UpdateStorageIscsiTargetCmd.class);
        commands.add(DeleteStorageIscsiTargetCmd.class);
        commands.add(ListStorageIscsiTargetsCmd.class);
        commands.add(CreateStorageIscsiAclCmd.class);
        commands.add(UpdateStorageIscsiAclCmd.class);
        commands.add(DeleteStorageIscsiAclCmd.class);
        commands.add(ListStorageIscsiAclsCmd.class);
        commands.add(CreateStorageNvmeOfSubsystemCmd.class);
        commands.add(UpdateStorageNvmeOfSubsystemCmd.class);
        commands.add(DeleteStorageNvmeOfSubsystemCmd.class);
        commands.add(ListStorageNvmeOfSubsystemsCmd.class);
        commands.add(CreateStorageNvmeOfNamespaceCmd.class);
        commands.add(DeleteStorageNvmeOfNamespaceCmd.class);
        commands.add(CreateStorageNvmeOfHostAclCmd.class);
        commands.add(UpdateStorageNvmeOfHostAclCmd.class);
        commands.add(DeleteStorageNvmeOfHostAclCmd.class);
        commands.add(ListStorageNvmeOfHostAclsCmd.class);
        return commands;
    }

    @Override
    public StorageServiceInstanceResponse createStorageServiceInstance(final CreateStorageServiceInstanceCmd cmd) {
        final long accountId = cmd.getEntityOwnerId();
        final Account account = accountDao.findById(accountId);
        if (account == null) {
            throw new InvalidParameterValueException("Unable to find account with id " + accountId);
        }
        final DataCenterVO zone = dataCenterDao.findById(cmd.getZoneId());
        if (zone == null) {
            throw new InvalidParameterValueException("Unable to find zone with id " + cmd.getZoneId());
        }
        if (cmd.getServiceOfferingId() != null && serviceOfferingDao.findById(cmd.getServiceOfferingId()) == null) {
            throw new InvalidParameterValueException("Unable to find service offering with id " + cmd.getServiceOfferingId());
        }
        if (cmd.getVirtualMachineId() != null && vmInstanceDao.findById(cmd.getVirtualMachineId()) == null) {
            throw new InvalidParameterValueException("Unable to find System VM with id " + cmd.getVirtualMachineId());
        }

        StorageServiceInstanceVO instance = new StorageServiceInstanceVO(cmd.getName(), cmd.getDescription(), account.getDomainId(),
                accountId, cmd.getZoneId(), cmd.getServiceOfferingId(), cmd.getProvider());
        instance.setVmId(cmd.getVirtualMachineId());
        instance.setState(cmd.getVirtualMachineId() == null ? StorageServiceInstance.State.Allocated : StorageServiceInstance.State.Running);
        instance = storageServiceInstanceDao.persist(instance);
        CallContext.current().setEventResourceId(instance.getId());
        CallContext.current().setEventResourceType(ApiCommandResourceType.None);
        return createInstanceResponse(instance);
    }

    @Override
    public ListResponse<StorageServiceInstanceResponse> listStorageServiceInstances(final ListStorageServiceInstancesCmd cmd) {
        final List<StorageServiceInstanceVO> instances = new ArrayList<>();
        if (cmd.getId() != null) {
            final StorageServiceInstanceVO instance = storageServiceInstanceDao.findById(cmd.getId());
            if (instance != null) {
                instances.add(instance);
            }
        } else if (cmd.getZoneId() != null) {
            instances.addAll(storageServiceInstanceDao.listByZoneId(cmd.getZoneId()));
        } else {
            instances.addAll(storageServiceInstanceDao.listAll());
        }

        final List<StorageServiceInstanceResponse> responses = new ArrayList<>();
        for (final StorageServiceInstanceVO instance : instances) {
            if (cmd.getName() != null && !cmd.getName().equals(instance.getName())) {
                continue;
            }
            responses.add(createInstanceResponse(instance));
        }
        final ListResponse<StorageServiceInstanceResponse> response = new ListResponse<>();
        response.setResponses(responses, responses.size());
        return response;
    }

    @Override
    public StorageServiceProtocolResponse enableStorageServiceProtocol(final EnableStorageServiceProtocolCmd cmd) {
        final StorageServiceInstanceVO instance = requireInstance(cmd.getInstanceId());
        final StorageServiceInstance.Protocol protocol = parseProtocol(cmd.getProtocol());
        final Integer port = normalizeStorageServiceProtocolPort(protocol, cmd.getPort());
        final NicVO listenNic = resolveProtocolListenAddress(instance, cmd.getListenIp());
        StorageServiceProtocolVO protocolVO = storageServiceProtocolDao.findByInstanceIdAndProtocol(instance.getId(), protocol);
        final boolean created = protocolVO == null;
        final Boolean previousEnabled = created ? null : protocolVO.isEnabled();
        final String previousListenIp = created ? null : protocolVO.getListenIp();
        final Integer previousPort = created ? null : protocolVO.getPort();
        final StorageServiceInstance.ResourceState previousState = created ? null : protocolVO.getState();
        if (protocolVO == null) {
            protocolVO = new StorageServiceProtocolVO(instance.getId(), protocol, true, cmd.getListenIp(), port);
            protocolVO.setState(StorageServiceInstance.ResourceState.Ready);
            protocolVO = storageServiceProtocolDao.persist(protocolVO);
        } else {
            protocolVO.setEnabled(true);
            protocolVO.setListenIp(cmd.getListenIp());
            protocolVO.setPort(port);
            protocolVO.setState(StorageServiceInstance.ResourceState.Ready);
            storageServiceProtocolDao.update(protocolVO.getId(), protocolVO);
        }

        try {
            applyStorageServiceProtocolDesiredState(instance, protocol);
            registerProtocolListenAddress(instance, cmd.getListenIp(), listenNic);
        } catch (final RuntimeException e) {
            rollbackProtocolEnable(protocolVO, created, previousEnabled, previousListenIp, previousPort, previousState);
            throw e;
        }
        return createProtocolResponse(protocolVO);
    }

    @Override
    public boolean deleteStorageServiceProtocol(final DeleteStorageServiceProtocolCmd cmd) {
        final StorageServiceInstanceVO instance = requireInstance(cmd.getInstanceId());
        final StorageServiceInstance.Protocol protocol = parseProtocol(cmd.getProtocol());
        if (StringUtils.isNotBlank(cmd.getListenIp())) {
            return deleteStorageServiceEndpoint(instance, protocol, cmd.getListenIp());
        }
        final StorageServiceProtocolVO protocolVO = storageServiceProtocolDao.findByInstanceIdAndProtocol(instance.getId(), protocol);
        if (protocolVO == null) {
            return true;
        }
        validateProtocolCanBeDeleted(instance, protocol);
        final Boolean previousEnabled = protocolVO.isEnabled();
        final StorageServiceInstance.ResourceState previousState = protocolVO.getState();
        protocolVO.setEnabled(false);
        protocolVO.setState(StorageServiceInstance.ResourceState.Updating);
        storageServiceProtocolDao.update(protocolVO.getId(), protocolVO);
        try {
            applyStorageServiceProtocolDesiredState(instance, protocol);
            storageServiceProtocolDao.remove(protocolVO.getId());
        } catch (final RuntimeException e) {
            protocolVO.setEnabled(Boolean.TRUE.equals(previousEnabled));
            protocolVO.setState(previousState);
            storageServiceProtocolDao.update(protocolVO.getId(), protocolVO);
            throw e;
        }
        return true;
    }

    @Override
    public StorageNfsExportResponse createStorageNfsExport(final CreateStorageNfsExportCmd cmd) {
        final StorageServiceInstanceVO instance = requireInstance(cmd.getInstanceId());
        validateStorageServiceBackingVolume(instance, cmd.getVolumeId(), "NFS export");
        final VolumeVO requestedVolume = cmd.getVolumeId() == null ? null : requireVolume(cmd.getVolumeId());
        validateFileShareFilesystem(cmd.getFilesystem(), cmd.getImportMode());
        final String path = resolveNfsExportPath(cmd.getPath(), cmd.getName());
        validateNfsExportName(cmd.getName());
        validateNfsExportPath(path, cmd.getName());
        validateFileSharePathAvailable(instance, path, null, cmd.getVolumeId(), "NFS export");
        String configJson = buildNfsConfigJson(null, cmd.getReadOnly(), cmd.getRootSquash(), cmd.getAllSquash(), cmd.getAnonUid(), cmd.getAnonGid(),
                cmd.getOwnerUid(), cmd.getOwnerGid(), cmd.getMode(), cmd.getRecursivePermission(), cmd.getSync(), cmd.getSecure(),
                cmd.getEndpointMode(), cmd.getListenIps(), true);
        configJson = buildFileShareDirectoryConfigJson(configJson, requestedVolume, cmd.getImportMode(), cmd.getCreateDirectory());
        validateJsonObjectConfigOrThrow(configJson, "NFS export " + cmd.getName());
        StorageFileShareVO share = new StorageFileShareVO(instance.getId(), StorageServiceInstance.Protocol.NFS, cmd.getName(), path,
                cmd.getVolumeId(), cmd.getFilesystem(), cmd.getQuotaBytes(), StorageServiceInstance.ResourceState.Creating,
                configJson);
        share = storageFileShareDao.persist(share);
        share.setState(StorageServiceInstance.ResourceState.Updating);
        storageFileShareDao.update(share.getId(), share);
        try {
            prepareFileShareBackingVolume(instance, share, cmd.getImportMode());
            applyNfsDesiredState(instance);
            share.setState(instance.getVmId() == null ? StorageServiceInstance.ResourceState.Allocated : StorageServiceInstance.ResourceState.Ready);
            storageFileShareDao.update(share.getId(), share);
        } catch (final RuntimeException e) {
            markFileShareCreateFailed(instance, share, e, Boolean.TRUE.equals(cmd.getCleanupVolumeOnFailure()));
            throw e;
        }
        return createExportResponse(share);
    }

    @Override
    public StorageNfsExportResponse updateStorageNfsExport(final UpdateStorageNfsExportCmd cmd) {
        final StorageFileShareVO share = requireNfsExport(cmd.getId());
        final StorageServiceInstanceVO instance = requireInstance(share.getInstanceId());
        if (cmd.getName() != null) {
            validateNfsExportName(cmd.getName());
            share.setName(cmd.getName());
        }
        if (cmd.getName() != null || cmd.getPath() != null || cmd.getRelativePath() != null || cmd.getVolumeId() != null) {
            final Long effectiveVolumeId = cmd.getVolumeId() == null ? share.getVolumeId() : cmd.getVolumeId();
            final String path = resolveNfsExportPath(cmd.getPath(), share.getName());
            validateNfsExportPath(path, share.getName());
            validateFileSharePathAvailable(instance, path, share.getId(), effectiveVolumeId, "NFS export");
            share.setPath(path);
        }
        if (cmd.getVolumeId() != null) {
            validateStorageServiceBackingVolume(instance, cmd.getVolumeId(), "NFS export");
            share.setVolumeId(cmd.getVolumeId());
        }
        if (cmd.getFilesystem() != null) {
            validateFileShareFilesystem(cmd.getFilesystem(), cmd.getImportMode());
            share.setFilesystem(cmd.getFilesystem());
        }
        if (cmd.getQuotaBytes() != null) {
            share.setQuotaBytes(cmd.getQuotaBytes());
        }
        share.setConfigJson(buildNfsConfigJson(share.getConfigJson(), cmd.getReadOnly(), cmd.getRootSquash(), cmd.getAllSquash(), cmd.getAnonUid(),
                cmd.getAnonGid(), cmd.getOwnerUid(), cmd.getOwnerGid(), cmd.getMode(), cmd.getRecursivePermission(), cmd.getSync(), cmd.getSecure(),
                cmd.getEndpointMode(), cmd.getListenIps(), true));
        share.setConfigJson(buildFileShareDirectoryConfigJson(share.getConfigJson(), cmd.getVolumeId() == null ? null : requireVolume(cmd.getVolumeId()),
                cmd.getImportMode(), cmd.getCreateDirectory()));
        validateJsonObjectConfigOrThrow(share.getConfigJson(), "NFS export " + share.getUuid());
        share.setState(StorageServiceInstance.ResourceState.Updating);
        storageFileShareDao.update(share.getId(), share);
        try {
            prepareFileShareBackingVolume(instance, share, cmd.getImportMode());
            applyNfsDesiredState(instance);
            share.setState(instance.getVmId() == null ? StorageServiceInstance.ResourceState.Allocated : StorageServiceInstance.ResourceState.Ready);
            storageFileShareDao.update(share.getId(), share);
        } catch (final RuntimeException e) {
            share.setState(StorageServiceInstance.ResourceState.Error);
            storageFileShareDao.update(share.getId(), share);
            throw e;
        }
        return createExportResponse(share);
    }

    @Override
    public boolean deleteStorageNfsExport(final DeleteStorageNfsExportCmd cmd) {
        final StorageFileShareVO share = requireNfsExport(cmd.getId());
        final StorageServiceInstanceVO instance = requireInstance(share.getInstanceId());
        for (final StorageAccessRuleVO rule : storageAccessRuleDao.listByResource(StorageServiceInstance.AccessResourceType.FILE_SHARE, share.getId())) {
            storageAccessRuleDao.remove(rule.getId());
        }
        storageFileShareDao.remove(share.getId());
        applyNfsDesiredState(instance);
        return true;
    }

    @Override
    public ListResponse<StorageNfsExportResponse> listStorageNfsExports(final ListStorageNfsExportsCmd cmd) {
        final List<StorageFileShareVO> shares = new ArrayList<>();
        if (cmd.getId() != null) {
            final StorageFileShareVO share = storageFileShareDao.findById(cmd.getId());
            if (share != null && share.getProtocol() == StorageServiceInstance.Protocol.NFS) {
                shares.add(share);
            }
        } else if (cmd.getInstanceId() != null) {
            shares.addAll(storageFileShareDao.listByInstanceIdAndProtocol(cmd.getInstanceId(), StorageServiceInstance.Protocol.NFS));
        } else {
            shares.addAll(storageFileShareDao.listAll());
        }

        final List<StorageNfsExportResponse> responses = new ArrayList<>();
        for (final StorageFileShareVO share : shares) {
            if (share.getProtocol() != StorageServiceInstance.Protocol.NFS) {
                continue;
            }
            if (cmd.getName() != null && !cmd.getName().equals(share.getName())) {
                continue;
            }
            responses.add(createExportResponse(share));
        }
        final ListResponse<StorageNfsExportResponse> response = new ListResponse<>();
        response.setResponses(responses, responses.size());
        return response;
    }

    @Override
    public StorageAccessRuleResponse createStorageNfsAcl(final CreateStorageNfsAclCmd cmd) {
        final StorageFileShareVO share = requireNfsExport(cmd.getExportId());
        final StorageServiceInstanceVO instance = requireInstance(share.getInstanceId());
        final StorageServiceInstance.PrincipalType principalType = parseNfsPrincipalType(cmd.getPrincipalType());
        final StorageServiceInstance.Permission permission = parseNfsPermission(cmd.getPermission());
        final List<String> principals = parseNfsPrincipals(cmd.getPrincipal(), cmd.getPrincipals());
        final List<StorageAccessRuleVO> persistedRules = new ArrayList<>();
        final List<StorageAccessRuleVO> existingRules = storageAccessRuleDao.listByResource(StorageServiceInstance.AccessResourceType.FILE_SHARE, share.getId());
        for (final String principal : principals) {
            StorageAccessRuleVO rule = null;
            for (final StorageAccessRuleVO existingRule : existingRules) {
                if (existingRule.getPrincipalType() == principalType && principal.equals(existingRule.getPrincipal())) {
                    if (rule == null) {
                        rule = existingRule;
                    } else {
                        storageAccessRuleDao.remove(existingRule.getId());
                    }
                }
            }
            if (rule == null) {
                rule = new StorageAccessRuleVO(StorageServiceInstance.AccessResourceType.FILE_SHARE, share.getId(),
                        principalType, principal, permission, StorageServiceInstance.ResourceState.Creating, null);
                rule = storageAccessRuleDao.persist(rule);
            }
            rule.setPermission(permission);
            rule.setConfigJson(buildNfsConfigJson(rule.getConfigJson(), null, cmd.getRootSquash(), cmd.getAllSquash(), cmd.getAnonUid(), cmd.getAnonGid(),
                    null, null, null, null, cmd.getSync(), cmd.getSecure(), null, null, false));
            rule.setState(instance.getVmId() == null ? StorageServiceInstance.ResourceState.Allocated : StorageServiceInstance.ResourceState.Ready);
            storageAccessRuleDao.update(rule.getId(), rule);
            persistedRules.add(rule);
        }
        applyNfsDesiredState(instance);
        return createAclResponse(persistedRules.get(0));
    }

    @Override
    public StorageAccessRuleResponse updateStorageNfsAcl(final UpdateStorageNfsAclCmd cmd) {
        final StorageAccessRuleVO rule = requireAcl(cmd.getId());
        final StorageFileShareVO share = requireNfsExport(rule.getResourceId());
        final StorageServiceInstanceVO instance = requireInstance(share.getInstanceId());
        if (cmd.getPrincipal() != null) {
            rule.setPrincipal(cmd.getPrincipal());
        }
        if (cmd.getPermission() != null) {
            rule.setPermission(parseNfsPermission(cmd.getPermission()));
        }
        rule.setConfigJson(buildNfsConfigJson(rule.getConfigJson(), null, cmd.getRootSquash(), cmd.getAllSquash(), cmd.getAnonUid(), cmd.getAnonGid(),
                null, null, null, null, cmd.getSync(), cmd.getSecure(), null, null, false));
        rule.setState(StorageServiceInstance.ResourceState.Updating);
        storageAccessRuleDao.update(rule.getId(), rule);
        applyNfsDesiredState(instance);
        rule.setState(instance.getVmId() == null ? StorageServiceInstance.ResourceState.Allocated : StorageServiceInstance.ResourceState.Ready);
        storageAccessRuleDao.update(rule.getId(), rule);
        return createAclResponse(rule);
    }

    @Override
    public boolean deleteStorageNfsAcl(final DeleteStorageNfsAclCmd cmd) {
        final StorageAccessRuleVO rule = requireAcl(cmd.getId());
        final StorageFileShareVO share = requireNfsExport(rule.getResourceId());
        final StorageServiceInstanceVO instance = requireInstance(share.getInstanceId());
        storageAccessRuleDao.remove(rule.getId());
        applyNfsDesiredState(instance);
        return true;
    }

    @Override
    public ListResponse<StorageAccessRuleResponse> listStorageNfsAcls(final ListStorageNfsAclsCmd cmd) {
        final List<StorageAccessRuleVO> rules = new ArrayList<>();
        final Long instanceId = resolveStorageServiceInstanceId(cmd.getInstanceId(), cmd.getFullUrlParams());
        if (cmd.getId() != null) {
            final StorageAccessRuleVO rule = storageAccessRuleDao.findById(cmd.getId());
            if (rule != null) {
                rules.add(rule);
            }
        } else if (cmd.getExportId() != null) {
            rules.addAll(storageAccessRuleDao.listByResource(StorageServiceInstance.AccessResourceType.FILE_SHARE, cmd.getExportId()));
        } else if (instanceId != null) {
            requireInstance(instanceId);
            for (final StorageFileShareVO share : storageFileShareDao.listByInstanceIdAndProtocol(instanceId, StorageServiceInstance.Protocol.NFS)) {
                rules.addAll(storageAccessRuleDao.listByResource(StorageServiceInstance.AccessResourceType.FILE_SHARE, share.getId()));
            }
        } else {
            rules.addAll(storageAccessRuleDao.listAll());
        }

        final List<StorageAccessRuleResponse> responses = new ArrayList<>();
        for (final StorageAccessRuleVO rule : rules) {
            if (rule.getResourceType() != StorageServiceInstance.AccessResourceType.FILE_SHARE) {
                continue;
            }
            if (rule.getPrincipalType() != StorageServiceInstance.PrincipalType.CIDR && rule.getPrincipalType() != StorageServiceInstance.PrincipalType.IP_ADDRESS) {
                continue;
            }
            final StorageFileShareVO share = storageFileShareDao.findById(rule.getResourceId());
            if (share == null || share.getProtocol() != StorageServiceInstance.Protocol.NFS) {
                continue;
            }
            responses.add(createAclResponse(rule));
        }
        final ListResponse<StorageAccessRuleResponse> response = new ListResponse<>();
        response.setResponses(responses, responses.size());
        return response;
    }

    protected Long resolveStorageServiceInstanceId(final Long instanceId, final Map<String, String> params) {
        if (instanceId != null) {
            return instanceId;
        }
        final String instanceUuid = params == null ? null : params.get("instanceid");
        if (StringUtils.isBlank(instanceUuid)) {
            return null;
        }
        final StorageServiceInstanceVO instance = storageServiceInstanceDao.findByUuid(instanceUuid);
        if (instance == null) {
            throw new InvalidParameterValueException("Unable to find Storage Service instance with id " + instanceUuid);
        }
        return instance.getId();
    }

    @Override
    public StorageSmbShareResponse createStorageSmbShare(final CreateStorageSmbShareCmd cmd) {
        final StorageServiceInstanceVO instance = requireInstance(cmd.getInstanceId());
        validateStorageServiceBackingVolume(instance, cmd.getVolumeId(), "SMB share");
        validateFileSharePath(cmd.getPath(), "SMB share");
        StorageFileShareVO share = new StorageFileShareVO(instance.getId(), StorageServiceInstance.Protocol.SMB, cmd.getName(), cmd.getPath(),
                cmd.getVolumeId(), cmd.getFilesystem(), cmd.getQuotaBytes(), StorageServiceInstance.ResourceState.Creating,
                buildSmbConfigJson(null, cmd.getReadOnly(), cmd.getBrowseable(), cmd.getGuestOk()));
        share = storageFileShareDao.persist(share);
        if (StringUtils.isBlank(share.getPath())) {
            share.setPath("/srv/ablestack-storage/smb/" + share.getUuid());
        }
        share.setState(StorageServiceInstance.ResourceState.Updating);
        storageFileShareDao.update(share.getId(), share);
        try {
            prepareFileShareBackingVolume(instance, share, "MOUNT_EXISTING");
            applySmbDesiredState(instance);
            share.setState(instance.getVmId() == null ? StorageServiceInstance.ResourceState.Allocated : StorageServiceInstance.ResourceState.Ready);
            storageFileShareDao.update(share.getId(), share);
        } catch (final RuntimeException e) {
            share.setState(StorageServiceInstance.ResourceState.Error);
            storageFileShareDao.update(share.getId(), share);
            throw e;
        }
        return createSmbShareResponse(share);
    }

    @Override
    public StorageSmbShareResponse updateStorageSmbShare(final UpdateStorageSmbShareCmd cmd) {
        final StorageFileShareVO share = requireSmbShare(cmd.getId());
        final StorageServiceInstanceVO instance = requireInstance(share.getInstanceId());
        if (cmd.getName() != null) {
            share.setName(cmd.getName());
        }
        if (cmd.getPath() != null) {
            validateFileSharePath(cmd.getPath(), "SMB share");
            share.setPath(cmd.getPath());
        }
        if (cmd.getVolumeId() != null) {
            validateStorageServiceBackingVolume(instance, cmd.getVolumeId(), "SMB share");
            share.setVolumeId(cmd.getVolumeId());
        }
        if (cmd.getFilesystem() != null) {
            share.setFilesystem(cmd.getFilesystem());
        }
        if (cmd.getQuotaBytes() != null) {
            share.setQuotaBytes(cmd.getQuotaBytes());
        }
        share.setConfigJson(buildSmbConfigJson(share.getConfigJson(), cmd.getReadOnly(), cmd.getBrowseable(), cmd.getGuestOk()));
        share.setState(StorageServiceInstance.ResourceState.Updating);
        storageFileShareDao.update(share.getId(), share);
        try {
            prepareFileShareBackingVolume(instance, share, "MOUNT_EXISTING");
            applySmbDesiredState(instance);
            share.setState(instance.getVmId() == null ? StorageServiceInstance.ResourceState.Allocated : StorageServiceInstance.ResourceState.Ready);
            storageFileShareDao.update(share.getId(), share);
        } catch (final RuntimeException e) {
            share.setState(StorageServiceInstance.ResourceState.Error);
            storageFileShareDao.update(share.getId(), share);
            throw e;
        }
        return createSmbShareResponse(share);
    }

    @Override
    public boolean deleteStorageSmbShare(final DeleteStorageSmbShareCmd cmd) {
        final StorageFileShareVO share = requireSmbShare(cmd.getId());
        final StorageServiceInstanceVO instance = requireInstance(share.getInstanceId());
        for (final StorageAccessRuleVO rule : storageAccessRuleDao.listByResource(StorageServiceInstance.AccessResourceType.FILE_SHARE, share.getId())) {
            storageAccessRuleDao.remove(rule.getId());
        }
        storageFileShareDao.remove(share.getId());
        applySmbDesiredState(instance);
        return true;
    }

    @Override
    public ListResponse<StorageSmbShareResponse> listStorageSmbShares(final ListStorageSmbSharesCmd cmd) {
        final List<StorageFileShareVO> shares = new ArrayList<>();
        if (cmd.getId() != null) {
            final StorageFileShareVO share = storageFileShareDao.findById(cmd.getId());
            if (share != null && share.getProtocol() == StorageServiceInstance.Protocol.SMB) {
                shares.add(share);
            }
        } else if (cmd.getInstanceId() != null) {
            shares.addAll(storageFileShareDao.listByInstanceIdAndProtocol(cmd.getInstanceId(), StorageServiceInstance.Protocol.SMB));
        } else {
            shares.addAll(storageFileShareDao.listAll());
        }

        final List<StorageSmbShareResponse> responses = new ArrayList<>();
        for (final StorageFileShareVO share : shares) {
            if (share.getProtocol() != StorageServiceInstance.Protocol.SMB) {
                continue;
            }
            if (cmd.getName() != null && !cmd.getName().equals(share.getName())) {
                continue;
            }
            responses.add(createSmbShareResponse(share));
        }
        final ListResponse<StorageSmbShareResponse> response = new ListResponse<>();
        response.setResponses(responses, responses.size());
        return response;
    }

    @Override
    public StorageAccessRuleResponse createStorageSmbAcl(final CreateStorageSmbAclCmd cmd) {
        final StorageFileShareVO share = requireSmbShare(cmd.getShareId());
        final StorageServiceInstanceVO instance = requireInstance(share.getInstanceId());
        final StorageServiceInstance.PrincipalType principalType = parseSmbPrincipalType(cmd.getPrincipalType());
        final StorageServiceInstance.Permission permission = parseSmbPermission(cmd.getPermission());
        StorageAccessRuleVO rule = new StorageAccessRuleVO(StorageServiceInstance.AccessResourceType.FILE_SHARE, share.getId(),
                principalType, cmd.getPrincipal(), permission, StorageServiceInstance.ResourceState.Creating, buildSmbAclConfigJson(principalType, cmd.getPassword()));
        rule = storageAccessRuleDao.persist(rule);
        rule.setState(instance.getVmId() == null ? StorageServiceInstance.ResourceState.Allocated : StorageServiceInstance.ResourceState.Ready);
        storageAccessRuleDao.update(rule.getId(), rule);
        try {
            applySmbDesiredState(instance, buildSecretMap(rule.getId(), cmd.getPassword()));
        } catch (final RuntimeException e) {
            rule.setState(StorageServiceInstance.ResourceState.Error);
            storageAccessRuleDao.update(rule.getId(), rule);
            throw e;
        }
        return createAclResponse(rule);
    }

    @Override
    public StorageAccessRuleResponse updateStorageSmbAcl(final UpdateStorageSmbAclCmd cmd) {
        final StorageAccessRuleVO rule = requireSmbAcl(cmd.getId());
        final StorageFileShareVO share = requireSmbShare(rule.getResourceId());
        final StorageServiceInstanceVO instance = requireInstance(share.getInstanceId());
        if (cmd.getPrincipal() != null) {
            rule.setPrincipal(cmd.getPrincipal());
        }
        if (cmd.getPermission() != null) {
            rule.setPermission(parseSmbPermission(cmd.getPermission()));
        }
        rule.setConfigJson(buildSmbAclConfigJson(rule.getPrincipalType(), cmd.getPassword()));
        rule.setState(StorageServiceInstance.ResourceState.Updating);
        storageAccessRuleDao.update(rule.getId(), rule);
        try {
            applySmbDesiredState(instance, buildSecretMap(rule.getId(), cmd.getPassword()));
            rule.setState(instance.getVmId() == null ? StorageServiceInstance.ResourceState.Allocated : StorageServiceInstance.ResourceState.Ready);
            storageAccessRuleDao.update(rule.getId(), rule);
        } catch (final RuntimeException e) {
            rule.setState(StorageServiceInstance.ResourceState.Error);
            storageAccessRuleDao.update(rule.getId(), rule);
            throw e;
        }
        return createAclResponse(rule);
    }

    @Override
    public boolean deleteStorageSmbAcl(final DeleteStorageSmbAclCmd cmd) {
        final StorageAccessRuleVO rule = requireSmbAcl(cmd.getId());
        final StorageFileShareVO share = requireSmbShare(rule.getResourceId());
        final StorageServiceInstanceVO instance = requireInstance(share.getInstanceId());
        storageAccessRuleDao.remove(rule.getId());
        applySmbDesiredState(instance);
        return true;
    }

    @Override
    public ListResponse<StorageAccessRuleResponse> listStorageSmbAcls(final ListStorageSmbAclsCmd cmd) {
        final List<StorageAccessRuleVO> rules = new ArrayList<>();
        if (cmd.getId() != null) {
            final StorageAccessRuleVO rule = storageAccessRuleDao.findById(cmd.getId());
            if (rule != null) {
                rules.add(rule);
            }
        } else if (cmd.getShareId() != null) {
            rules.addAll(storageAccessRuleDao.listByResource(StorageServiceInstance.AccessResourceType.FILE_SHARE, cmd.getShareId()));
        } else {
            rules.addAll(storageAccessRuleDao.listAll());
        }

        final List<StorageAccessRuleResponse> responses = new ArrayList<>();
        for (final StorageAccessRuleVO rule : rules) {
            if (rule.getResourceType() != StorageServiceInstance.AccessResourceType.FILE_SHARE || !isSmbPrincipalType(rule.getPrincipalType())) {
                continue;
            }
            responses.add(createAclResponse(rule));
        }
        final ListResponse<StorageAccessRuleResponse> response = new ListResponse<>();
        response.setResponses(responses, responses.size());
        return response;
    }

    @Override
    public StorageIdentityDomainResponse joinStorageServiceToAdDomain(final JoinStorageServiceToAdDomainCmd cmd) {
        final StorageServiceInstanceVO instance = requireInstance(cmd.getInstanceId());
        ensureSmbProtocol(instance);
        StorageIdentityDomainVO domain = storageIdentityDomainDao.findByInstanceId(instance.getId());
        if (domain == null) {
            domain = new StorageIdentityDomainVO(instance.getId(), cmd.getDomainName(), cmd.getOrganizationalUnit(), cmd.getDnsServers(),
                    StorageServiceInstance.DomainJoinState.JOINING, "UNKNOWN", buildIdentityDomainConfigJson(cmd.getWorkgroup()));
            domain = storageIdentityDomainDao.persist(domain);
        } else {
            domain.setDomainName(cmd.getDomainName());
            domain.setOrganizationalUnit(cmd.getOrganizationalUnit());
            domain.setDnsServers(cmd.getDnsServers());
            domain.setJoinState(StorageServiceInstance.DomainJoinState.JOINING);
            domain.setHealthState("UNKNOWN");
            domain.setConfigJson(buildIdentityDomainConfigJson(cmd.getWorkgroup()));
            storageIdentityDomainDao.update(domain.getId(), domain);
        }

        try {
            applyAdJoin(instance, domain, cmd.getUsername(), cmd.getPassword());
        } catch (final RuntimeException e) {
            domain.setJoinState(StorageServiceInstance.DomainJoinState.ERROR);
            domain.setHealthState("ERROR");
            storageIdentityDomainDao.update(domain.getId(), domain);
            throw e;
        }
        domain.setJoinState(StorageServiceInstance.DomainJoinState.JOINED);
        domain.setHealthState("OK");
        storageIdentityDomainDao.update(domain.getId(), domain);
        applySmbDesiredState(instance);
        return createIdentityDomainResponse(domain);
    }

    @Override
    public StorageIdentityDomainResponse leaveStorageServiceFromAdDomain(final LeaveStorageServiceFromAdDomainCmd cmd) {
        final StorageServiceInstanceVO instance = requireInstance(cmd.getInstanceId());
        StorageIdentityDomainVO domain = storageIdentityDomainDao.findByInstanceId(instance.getId());
        if (domain == null) {
            domain = new StorageIdentityDomainVO(instance.getId(), "", null, null,
                    StorageServiceInstance.DomainJoinState.NOT_JOINED, "UNKNOWN", null);
            domain = storageIdentityDomainDao.persist(domain);
        } else {
            domain.setJoinState(StorageServiceInstance.DomainJoinState.LEAVING);
            storageIdentityDomainDao.update(domain.getId(), domain);
        }

        try {
            applyAdLeave(instance, cmd.getUsername(), cmd.getPassword());
        } catch (final RuntimeException e) {
            domain.setJoinState(StorageServiceInstance.DomainJoinState.ERROR);
            domain.setHealthState("ERROR");
            storageIdentityDomainDao.update(domain.getId(), domain);
            throw e;
        }
        domain.setJoinState(StorageServiceInstance.DomainJoinState.NOT_JOINED);
        domain.setHealthState("NOT_JOINED");
        storageIdentityDomainDao.update(domain.getId(), domain);
        applySmbDesiredState(instance);
        return createIdentityDomainResponse(domain);
    }

    @Override
    public ListResponse<StorageIdentityDomainResponse> listStorageServiceDomainStatus(final ListStorageServiceDomainStatusCmd cmd) {
        final List<StorageIdentityDomainVO> domains = new ArrayList<>();
        if (cmd.getInstanceId() != null) {
            final StorageServiceInstanceVO instance = requireInstance(cmd.getInstanceId());
            StorageIdentityDomainVO domain = storageIdentityDomainDao.findByInstanceId(instance.getId());
            if (domain != null) {
                domains.add(domain);
            }
        } else {
            domains.addAll(storageIdentityDomainDao.listAll());
        }
        final List<StorageIdentityDomainResponse> responses = new ArrayList<>();
        for (StorageIdentityDomainVO domain : domains) {
            responses.add(createIdentityDomainResponse(domain));
        }
        final ListResponse<StorageIdentityDomainResponse> response = new ListResponse<>();
        response.setResponses(responses, responses.size());
        return response;
    }

    @Override
    public ListResponse<StorageServiceRuntimeResponse> listStorageServiceHealth(final ListStorageServiceHealthCmd cmd) {
        return listRuntimeOperation(cmd.getInstanceId(), "health");
    }

    @Override
    public ListResponse<StorageServiceRuntimeResponse> listStorageServiceInventory(final ListStorageServiceInventoryCmd cmd) {
        return listRuntimeOperation(cmd.getInstanceId(), "inventory");
    }

    @Override
    public ListResponse<StorageServiceRuntimeResponse> listStorageServiceSessions(final ListStorageServiceSessionsCmd cmd) {
        final JsonObject payload = new JsonObject();
        addStringProperty(payload, "protocol", cmd.getProtocol());
        addStringProperty(payload, "resourceId", cmd.getResourceId());
        addStringProperty(payload, "client", cmd.getClient());
        addStringProperty(payload, "state", cmd.getState());
        return listRuntimeOperation(cmd.getInstanceId(), "sessions", GSON.toJson(payload));
    }

    @Override
    public StorageServiceRuntimeResponse disconnectStorageServiceSession(final DisconnectStorageServiceSessionCmd cmd) {
        final StorageServiceInstanceVO instance = requireInstance(cmd.getInstanceId());
        final JsonObject payload = new JsonObject();
        addStringProperty(payload, "protocol", cmd.getProtocol());
        addStringProperty(payload, "sessionId", cmd.getSessionId());
        addStringProperty(payload, "peer", cmd.getPeer());
        addStringProperty(payload, "local", cmd.getLocal());
        addStringProperty(payload, "resourceId", cmd.getResourceId());
        if (cmd.getForce() != null) {
            payload.addProperty("force", cmd.getForce());
        }
        return createRuntimeResponse(instance, "session disconnect", GSON.toJson(payload));
    }

    @Override
    public StorageFileShareResponse attachStorageVolumeToFileShare(final AttachStorageVolumeToFileShareCmd cmd) {
        final StorageFileShareVO share = requireFileShare(cmd.getId());
        final StorageServiceInstanceVO instance = requireInstance(share.getInstanceId());
        final VolumeVO volume = requireVolume(cmd.getVolumeId());
        if (volume.getInstanceId() != null && !volume.getInstanceId().equals(instance.getVmId())) {
            throw new InvalidParameterValueException("Volume " + volume.getUuid() + " is already attached to another VM");
        }

        share.setState(StorageServiceInstance.ResourceState.Updating);
        share.setVolumeId(volume.getId());
        if (cmd.getPath() != null) {
            share.setPath(cmd.getPath());
        }
        if (cmd.getFilesystem() != null) {
            share.setFilesystem(cmd.getFilesystem());
        }
        share.setConfigJson(buildFileShareAttachConfigJson(share.getConfigJson(), cmd.getImportMode(), volume, null));
        storageFileShareDao.update(share.getId(), share);

        if (instance.getVmId() != null && volume.getInstanceId() == null) {
            final VolumeVO attachableVolume = waitForFileShareVolumeAttachable(volume.getId());
            volumeApiService.attachVolumeToVM(instance.getVmId(), attachableVolume.getId(), null, true);
        }
        if (instance.getVmId() != null) {
            inspectAttachedFileShareVolume(instance, share, volume, cmd.getImportMode());
        }
        applyFileShareDesiredState(instance, share.getProtocol());

        share.setState(instance.getVmId() == null ? StorageServiceInstance.ResourceState.Allocated : StorageServiceInstance.ResourceState.Ready);
        storageFileShareDao.update(share.getId(), share);
        return createFileShareResponse(share);
    }

    @Override
    public StorageServiceRuntimeResponse detachStorageServiceBackingVolume(final DetachStorageServiceBackingVolumeCmd cmd) {
        final StorageServiceInstanceVO instance = requireInstance(cmd.getInstanceId());
        final VolumeVO volume = requireVolume(cmd.getVolumeId());
        if (instance.getVmId() == null) {
            throw new InvalidParameterValueException("Storage Service instance has no System VM");
        }
        if (!instance.getVmId().equals(volume.getInstanceId())) {
            throw new InvalidParameterValueException("Backing volume " + volume.getUuid() + " is not attached to this Storage Service System VM");
        }
        validateBackingVolumeUnused(instance, volume.getId());

        final JsonObject payload = new JsonObject();
        payload.addProperty("instanceUuid", instance.getUuid());
        payload.addProperty("volumeId", volume.getId());
        payload.addProperty("volumeUuid", volume.getUuid());
        payload.addProperty("volumeName", volume.getName());
        final String knownMountRoot = findKnownFileShareVolumeMountRoot(instance, volume.getId());
        if (StringUtils.isNotBlank(knownMountRoot)) {
            payload.addProperty("mountPath", knownMountRoot);
        }
        final StorageServiceGuestCommandResult result = guestCommandDispatcher.dispatch(new StorageServiceGuestCommand(instance.getVmId(),
                "volume detach prepare", GSON.toJson(payload), StorageServiceInstance.StorageServiceCommandTimeout.value(), Collections.emptySet()));
        if (!result.isSuccess()) {
            throw new CloudRuntimeException("Failed to prepare Storage Service backing volume detach: " + result.getDetails());
        }
        volumeApiService.detachVolumeViaDestroyVM(instance.getVmId(), volume.getId());
        return createRuntimeResponse(instance, "volume detach prepare", true, extractRuntimeStatus(result), result.getDetails(), result.getResultJson());
    }

    protected void prepareFileShareBackingVolume(final StorageServiceInstanceVO instance, final StorageFileShareVO share, final String importMode) {
        if (share.getVolumeId() == null || StringUtils.isBlank(importMode)) {
            return;
        }
        VolumeVO volume = requireVolume(share.getVolumeId());
        final Long attachedVmId = volume.getInstanceId();
        if (attachedVmId != null && !attachedVmId.equals(instance.getVmId())) {
            throw new InvalidParameterValueException("Backing volume " + volume.getUuid() + " is already attached to another VM");
        }
        if (instance.getVmId() != null && attachedVmId == null) {
            volume = waitForFileShareVolumeAttachable(volume.getId());
            volumeApiService.attachVolumeToVM(instance.getVmId(), volume.getId(), null, true);
            volume = requireVolume(share.getVolumeId());
        }
        if (instance.getVmId() != null) {
            inspectAttachedFileShareVolume(instance, share, volume, importMode);
        }
    }

    protected void cleanupFailedFileShareCreate(final StorageServiceInstanceVO instance, final StorageFileShareVO share, final boolean cleanupVolumeOnFailure) {
        if (share == null) {
            return;
        }
        final Long volumeId = share.getVolumeId();
        try {
            for (final StorageAccessRuleVO rule : storageAccessRuleDao.listByResource(StorageServiceInstance.AccessResourceType.FILE_SHARE, share.getId())) {
                storageAccessRuleDao.remove(rule.getId());
            }
            storageFileShareDao.remove(share.getId());
            applyFileShareDesiredState(instance, share.getProtocol());
        } catch (final RuntimeException cleanupError) {
            logger.warn("Failed to reconcile Storage Service file share [{}] after create failure", share.getUuid(), cleanupError);
        }
        if (cleanupVolumeOnFailure && volumeId != null) {
            cleanupCreatedBackingVolume(instance, volumeId);
        }
    }

    protected void markFileShareCreateFailed(final StorageServiceInstanceVO instance, final StorageFileShareVO share, final RuntimeException failure,
            final boolean cleanupVolumeOnFailure) {
        if (share == null) {
            return;
        }
        final Long volumeId = share.getVolumeId();
        try {
            share.setState(StorageServiceInstance.ResourceState.Error);
            share.setConfigJson(buildFileShareErrorConfigJson(share.getConfigJson(), failure));
            storageFileShareDao.update(share.getId(), share);
            applyFileShareDesiredState(instance, share.getProtocol());
        } catch (final RuntimeException reconcileError) {
            logger.warn("Failed to preserve failed Storage Service file share [{}] after create failure", share.getUuid(), reconcileError);
        }
        if (cleanupVolumeOnFailure && volumeId != null) {
            cleanupCreatedBackingVolume(instance, volumeId);
        }
    }

    protected void cleanupCreatedBackingVolume(final StorageServiceInstanceVO instance, final Long volumeId) {
        try {
            VolumeVO volume = volumeDao.findById(volumeId);
            if (volume == null) {
                return;
            }
            if (instance.getVmId() != null && instance.getVmId().equals(volume.getInstanceId())) {
                volumeApiService.detachVolumeViaDestroyVM(instance.getVmId(), volume.getId());
                volume = volumeDao.findById(volumeId);
            }
            if (volume != null && volume.getInstanceId() == null) {
                volumeApiService.destroyVolume(volume.getId(), CallContext.current().getCallingAccount(), true, true);
            } else if (volume != null) {
                logger.warn("Skipping cleanup of newly created backing volume [{}] because it is still attached to VM ID [{}]", volume.getUuid(), volume.getInstanceId());
            }
        } catch (final RuntimeException cleanupError) {
            logger.warn("Failed to cleanup newly created backing volume [{}] after Storage Service file share create failure", volumeId, cleanupError);
        }
    }

    @Override
    public StorageFileShareResponse resizeStorageFileShare(final ResizeStorageFileShareCmd cmd) {
        if (cmd.getSize() == null && cmd.getQuotaBytes() == null) {
            throw new InvalidParameterValueException("Either size or quotabytes must be provided");
        }
        final StorageFileShareVO share = requireFileShare(cmd.getId());
        final StorageServiceInstanceVO instance = requireInstance(share.getInstanceId());
        if (share.getVolumeId() == null) {
            throw new InvalidParameterValueException("File share " + share.getUuid() + " has no backing volume to resize");
        }

        share.setState(StorageServiceInstance.ResourceState.Updating);
        storageFileShareDao.update(share.getId(), share);

        if (Boolean.TRUE.equals(cmd.getResizeVolume()) && cmd.getSize() != null) {
            resizeBackingVolume(share.getVolumeId(), cmd.getSize());
        }
        if (cmd.getQuotaBytes() != null) {
            share.setQuotaBytes(cmd.getQuotaBytes());
        }
        if (instance.getVmId() != null) {
            growFileShareFilesystem(instance, share, cmd.getSize(), cmd.getQuotaBytes());
        }
        applyFileShareDesiredState(instance, share.getProtocol());

        share.setState(instance.getVmId() == null ? StorageServiceInstance.ResourceState.Allocated : StorageServiceInstance.ResourceState.Ready);
        storageFileShareDao.update(share.getId(), share);
        return createFileShareResponse(share);
    }

    @Override
    public StorageServiceRuntimeResponse prepareStorageServiceNvmeOfVm(final PrepareStorageServiceNvmeOfVmCmd cmd) {
        final StorageServiceInstanceVO instance = requireInstance(cmd.getInstanceId());
        final String engine = StringUtils.defaultIfBlank(cmd.getEngine(), "KERNEL_NVMET").toUpperCase();
        if ("SPDK".equals(engine)) {
            return createRuntimeResponse(instance, "nvmeof prepare", false, "PREPARATION_REQUIRED",
                    "SPDK NVMe-oF requires VM Runtime Capability support for HugePage, NUMA, CPU pinning, memlock, SR-IOV, or PCI passthrough. " +
                            "Storage Service keeps SPDK as a planned engine until that VM-level feature is available.",
                    buildNvmeOfPreparationResult(engine, cmd.getTransport(), cmd.getRuntimeCapabilityProfileId(), cmd.getValidateOnly()));
        }
        if (!"KERNEL_NVMET".equals(engine)) {
            throw new InvalidParameterValueException("Unsupported NVMe-oF engine: " + cmd.getEngine());
        }
        if (instance.getVmId() == null) {
            return createRuntimeResponse(instance, "nvmeof prepare", false, "NOT_ATTACHED", "Storage Service instance has no System VM",
                    buildNvmeOfPreparationResult(engine, cmd.getTransport(), cmd.getRuntimeCapabilityProfileId(), cmd.getValidateOnly()));
        }

        final JsonObject payload = new JsonObject();
        payload.addProperty("instanceUuid", instance.getUuid());
        payload.addProperty("engine", engine);
        payload.addProperty("transport", StringUtils.defaultIfBlank(cmd.getTransport(), "tcp"));
        payload.addProperty("validateOnly", Boolean.TRUE.equals(cmd.getValidateOnly()));
        final StorageServiceGuestCommandResult result = guestCommandDispatcher.dispatch(new StorageServiceGuestCommand(instance.getVmId(),
                "nvmeof prepare", GSON.toJson(payload), StorageServiceInstance.StorageServiceCommandTimeout.value(), Collections.emptySet()));
        final String status = extractRuntimeStatus(result);
        return createRuntimeResponse(instance, "nvmeof prepare", result.isSuccess(), status, result.getDetails(), result.getResultJson());
    }

    @Override
    public StorageBlockTargetResponse createStorageIscsiTarget(final CreateStorageIscsiTargetCmd cmd) {
        final StorageServiceInstanceVO instance = requireInstance(cmd.getInstanceId());
        validateStorageServiceBackingVolume(instance, cmd.getVolumeId(), "iSCSI target");
        ensureProtocol(instance, StorageServiceInstance.Protocol.ISCSI);
        StorageBlockTargetVO target = new StorageBlockTargetVO(instance.getId(), StorageServiceInstance.Protocol.ISCSI, cmd.getTargetName(),
                StringUtils.defaultIfBlank(cmd.getLun(), "0"), cmd.getVolumeId(), StorageServiceInstance.ResourceState.Creating,
                buildIscsiTargetConfigJson(null, cmd.getBackingPath(), cmd.getLunSizeBytes()));
        target = storageBlockTargetDao.persist(target);
        target.setState(instance.getVmId() == null ? StorageServiceInstance.ResourceState.Allocated : StorageServiceInstance.ResourceState.Ready);
        storageBlockTargetDao.update(target.getId(), target);
        applyIscsiDesiredState(instance);
        return createBlockTargetResponse(target, "storageiscsitarget");
    }

    @Override
    public StorageBlockTargetResponse updateStorageIscsiTarget(final UpdateStorageIscsiTargetCmd cmd) {
        final StorageBlockTargetVO target = requireBlockTarget(cmd.getId(), StorageServiceInstance.Protocol.ISCSI);
        final StorageServiceInstanceVO instance = requireInstance(target.getInstanceId());
        if (cmd.getTargetName() != null) {
            target.setTargetName(cmd.getTargetName());
        }
        if (cmd.getLun() != null) {
            target.setLunOrNamespace(cmd.getLun());
        }
        if (cmd.getVolumeId() != null) {
            validateStorageServiceBackingVolume(instance, cmd.getVolumeId(), "iSCSI target");
            target.setVolumeId(cmd.getVolumeId());
        }
        target.setConfigJson(buildIscsiTargetConfigJson(target.getConfigJson(), cmd.getBackingPath(), cmd.getLunSizeBytes()));
        target.setState(StorageServiceInstance.ResourceState.Updating);
        storageBlockTargetDao.update(target.getId(), target);
        applyIscsiDesiredState(instance);
        target.setState(instance.getVmId() == null ? StorageServiceInstance.ResourceState.Allocated : StorageServiceInstance.ResourceState.Ready);
        storageBlockTargetDao.update(target.getId(), target);
        return createBlockTargetResponse(target, "storageiscsitarget");
    }

    @Override
    public boolean deleteStorageIscsiTarget(final DeleteStorageIscsiTargetCmd cmd) {
        final StorageBlockTargetVO target = requireBlockTarget(cmd.getId(), StorageServiceInstance.Protocol.ISCSI);
        final StorageServiceInstanceVO instance = requireInstance(target.getInstanceId());
        for (final StorageAccessRuleVO rule : storageAccessRuleDao.listByResource(StorageServiceInstance.AccessResourceType.BLOCK_TARGET, target.getId())) {
            storageAccessRuleDao.remove(rule.getId());
        }
        storageBlockTargetDao.remove(target.getId());
        applyIscsiDesiredState(instance);
        return true;
    }

    @Override
    public ListResponse<StorageBlockTargetResponse> listStorageIscsiTargets(final ListStorageIscsiTargetsCmd cmd) {
        final List<StorageBlockTargetVO> targets = listBlockTargets(cmd.getId(), cmd.getInstanceId(), StorageServiceInstance.Protocol.ISCSI);
        final List<StorageBlockTargetResponse> responses = new ArrayList<>();
        for (final StorageBlockTargetVO target : targets) {
            if (cmd.getTargetName() != null && !cmd.getTargetName().equals(target.getTargetName())) {
                continue;
            }
            responses.add(createBlockTargetResponse(target, "storageiscsitarget"));
        }
        final ListResponse<StorageBlockTargetResponse> response = new ListResponse<>();
        response.setResponses(responses, responses.size());
        return response;
    }

    @Override
    public StorageAccessRuleResponse createStorageIscsiAcl(final CreateStorageIscsiAclCmd cmd) {
        final StorageBlockTargetVO target = requireBlockTarget(cmd.getTargetId(), StorageServiceInstance.Protocol.ISCSI);
        final StorageServiceInstanceVO instance = requireInstance(target.getInstanceId());
        final StorageServiceInstance.Permission permission = parseBlockPermission(cmd.getPermission());
        StorageAccessRuleVO rule = new StorageAccessRuleVO(StorageServiceInstance.AccessResourceType.BLOCK_TARGET, target.getId(),
                StorageServiceInstance.PrincipalType.ISCSI_INITIATOR_IQN, cmd.getInitiatorIqn(), permission, StorageServiceInstance.ResourceState.Creating,
                buildIscsiAclConfigJson(null, cmd.getChapEnabled(), cmd.getChapUsername(), cmd.getMutualChapEnabled(), cmd.getMutualChapUsername(),
                        cmd.getChapSecret(), cmd.getMutualChapSecret()));
        rule = storageAccessRuleDao.persist(rule);
        rule.setState(instance.getVmId() == null ? StorageServiceInstance.ResourceState.Allocated : StorageServiceInstance.ResourceState.Ready);
        storageAccessRuleDao.update(rule.getId(), rule);
        applyIscsiDesiredState(instance, buildChapSecretMap(rule.getId(), cmd.getChapSecret(), cmd.getMutualChapSecret()));
        return createAclResponse(rule);
    }

    @Override
    public StorageAccessRuleResponse updateStorageIscsiAcl(final UpdateStorageIscsiAclCmd cmd) {
        final StorageAccessRuleVO rule = requireBlockAcl(cmd.getId(), StorageServiceInstance.Protocol.ISCSI);
        final StorageBlockTargetVO target = requireBlockTarget(rule.getResourceId(), StorageServiceInstance.Protocol.ISCSI);
        final StorageServiceInstanceVO instance = requireInstance(target.getInstanceId());
        if (cmd.getInitiatorIqn() != null) {
            rule.setPrincipal(cmd.getInitiatorIqn());
        }
        if (cmd.getPermission() != null) {
            rule.setPermission(parseBlockPermission(cmd.getPermission()));
        }
        rule.setConfigJson(buildIscsiAclConfigJson(rule.getConfigJson(), cmd.getChapEnabled(), cmd.getChapUsername(), cmd.getMutualChapEnabled(), cmd.getMutualChapUsername(),
                cmd.getChapSecret(), cmd.getMutualChapSecret()));
        rule.setState(StorageServiceInstance.ResourceState.Updating);
        storageAccessRuleDao.update(rule.getId(), rule);
        applyIscsiDesiredState(instance, buildChapSecretMap(rule.getId(), cmd.getChapSecret(), cmd.getMutualChapSecret()));
        rule.setState(instance.getVmId() == null ? StorageServiceInstance.ResourceState.Allocated : StorageServiceInstance.ResourceState.Ready);
        storageAccessRuleDao.update(rule.getId(), rule);
        return createAclResponse(rule);
    }

    @Override
    public boolean deleteStorageIscsiAcl(final DeleteStorageIscsiAclCmd cmd) {
        final StorageAccessRuleVO rule = requireBlockAcl(cmd.getId(), StorageServiceInstance.Protocol.ISCSI);
        final StorageBlockTargetVO target = requireBlockTarget(rule.getResourceId(), StorageServiceInstance.Protocol.ISCSI);
        final StorageServiceInstanceVO instance = requireInstance(target.getInstanceId());
        storageAccessRuleDao.remove(rule.getId());
        applyIscsiDesiredState(instance);
        return true;
    }

    @Override
    public ListResponse<StorageAccessRuleResponse> listStorageIscsiAcls(final ListStorageIscsiAclsCmd cmd) {
        return listBlockAcls(cmd.getId(), cmd.getTargetId(), StorageServiceInstance.Protocol.ISCSI);
    }

    @Override
    public StorageBlockTargetResponse createStorageNvmeOfSubsystem(final CreateStorageNvmeOfSubsystemCmd cmd) {
        final StorageServiceInstanceVO instance = requireInstance(cmd.getInstanceId());
        ensureProtocol(instance, StorageServiceInstance.Protocol.NVME_OF);
        StorageBlockTargetVO subsystem = new StorageBlockTargetVO(instance.getId(), StorageServiceInstance.Protocol.NVME_OF, cmd.getSubsystemNqn(),
                null, null, StorageServiceInstance.ResourceState.Creating,
                buildNvmeOfConfigJson(null, "subsystem", cmd.getAllowAnyHost(), null, cmd.getEngine(), cmd.getTransport(), null));
        subsystem = storageBlockTargetDao.persist(subsystem);
        subsystem.setState(instance.getVmId() == null ? StorageServiceInstance.ResourceState.Allocated : StorageServiceInstance.ResourceState.Ready);
        storageBlockTargetDao.update(subsystem.getId(), subsystem);
        applyNvmeOfDesiredState(instance);
        return createBlockTargetResponse(subsystem, "storagenvmeofsubsystem");
    }

    @Override
    public StorageBlockTargetResponse updateStorageNvmeOfSubsystem(final UpdateStorageNvmeOfSubsystemCmd cmd) {
        final StorageBlockTargetVO subsystem = requireNvmeOfSubsystem(cmd.getId());
        final StorageServiceInstanceVO instance = requireInstance(subsystem.getInstanceId());
        if (cmd.getSubsystemNqn() != null) {
            updateNvmeOfSubsystemName(subsystem, cmd.getSubsystemNqn());
        }
        subsystem.setConfigJson(buildNvmeOfConfigJson(subsystem.getConfigJson(), "subsystem", cmd.getAllowAnyHost(), null, cmd.getEngine(), cmd.getTransport(), null));
        subsystem.setState(StorageServiceInstance.ResourceState.Updating);
        storageBlockTargetDao.update(subsystem.getId(), subsystem);
        applyNvmeOfDesiredState(instance);
        subsystem.setState(instance.getVmId() == null ? StorageServiceInstance.ResourceState.Allocated : StorageServiceInstance.ResourceState.Ready);
        storageBlockTargetDao.update(subsystem.getId(), subsystem);
        return createBlockTargetResponse(subsystem, "storagenvmeofsubsystem");
    }

    @Override
    public boolean deleteStorageNvmeOfSubsystem(final DeleteStorageNvmeOfSubsystemCmd cmd) {
        final StorageBlockTargetVO subsystem = requireNvmeOfSubsystem(cmd.getId());
        final StorageServiceInstanceVO instance = requireInstance(subsystem.getInstanceId());
        for (final StorageBlockTargetVO target : storageBlockTargetDao.listByInstanceIdAndProtocol(instance.getId(), StorageServiceInstance.Protocol.NVME_OF)) {
            if (subsystem.getTargetName().equals(target.getTargetName())) {
                for (final StorageAccessRuleVO rule : storageAccessRuleDao.listByResource(StorageServiceInstance.AccessResourceType.BLOCK_TARGET, target.getId())) {
                    storageAccessRuleDao.remove(rule.getId());
                }
                storageBlockTargetDao.remove(target.getId());
            }
        }
        applyNvmeOfDesiredState(instance);
        return true;
    }

    @Override
    public ListResponse<StorageBlockTargetResponse> listStorageNvmeOfSubsystems(final ListStorageNvmeOfSubsystemsCmd cmd) {
        final List<StorageBlockTargetVO> targets = listBlockTargets(cmd.getId(), cmd.getInstanceId(), StorageServiceInstance.Protocol.NVME_OF);
        final List<StorageBlockTargetResponse> responses = new ArrayList<>();
        for (final StorageBlockTargetVO target : targets) {
            if (cmd.getSubsystemNqn() != null && !cmd.getSubsystemNqn().equals(target.getTargetName())) {
                continue;
            }
            responses.add(createBlockTargetResponse(target, isNvmeOfSubsystem(target) ? "storagenvmeofsubsystem" : "storagenvmeofnamespace"));
        }
        final ListResponse<StorageBlockTargetResponse> response = new ListResponse<>();
        response.setResponses(responses, responses.size());
        return response;
    }

    @Override
    public StorageBlockTargetResponse createStorageNvmeOfNamespace(final CreateStorageNvmeOfNamespaceCmd cmd) {
        final StorageBlockTargetVO subsystem = requireNvmeOfSubsystem(cmd.getSubsystemId());
        final StorageServiceInstanceVO instance = requireInstance(subsystem.getInstanceId());
        validateStorageServiceBackingVolume(instance, cmd.getVolumeId(), "NVMe-oF namespace");
        StorageBlockTargetVO namespace = new StorageBlockTargetVO(instance.getId(), StorageServiceInstance.Protocol.NVME_OF, subsystem.getTargetName(),
                StringUtils.defaultIfBlank(cmd.getNamespaceId(), "1"), cmd.getVolumeId(), StorageServiceInstance.ResourceState.Creating,
                buildNvmeOfConfigJson(null, "namespace", null, cmd.getBackingPath(), null, null, cmd.getNamespaceSizeBytes()));
        namespace = storageBlockTargetDao.persist(namespace);
        namespace.setState(instance.getVmId() == null ? StorageServiceInstance.ResourceState.Allocated : StorageServiceInstance.ResourceState.Ready);
        storageBlockTargetDao.update(namespace.getId(), namespace);
        applyNvmeOfDesiredState(instance);
        return createBlockTargetResponse(namespace, "storagenvmeofnamespace");
    }

    @Override
    public boolean deleteStorageNvmeOfNamespace(final DeleteStorageNvmeOfNamespaceCmd cmd) {
        final StorageBlockTargetVO namespace = requireNvmeOfNamespace(cmd.getId());
        final StorageServiceInstanceVO instance = requireInstance(namespace.getInstanceId());
        storageBlockTargetDao.remove(namespace.getId());
        applyNvmeOfDesiredState(instance);
        return true;
    }

    @Override
    public StorageAccessRuleResponse createStorageNvmeOfHostAcl(final CreateStorageNvmeOfHostAclCmd cmd) {
        final StorageBlockTargetVO subsystem = requireNvmeOfSubsystem(cmd.getSubsystemId());
        final StorageServiceInstanceVO instance = requireInstance(subsystem.getInstanceId());
        StorageAccessRuleVO rule = new StorageAccessRuleVO(StorageServiceInstance.AccessResourceType.BLOCK_TARGET, subsystem.getId(),
                StorageServiceInstance.PrincipalType.NVME_HOST_NQN, cmd.getHostNqn(), StorageServiceInstance.Permission.READ_WRITE,
                StorageServiceInstance.ResourceState.Creating,
                buildNvmeOfHostAclConfigJson(null, cmd.getDhChapEnabled(), cmd.getDhChapCtrlEnabled(), cmd.getDhChapKey(), cmd.getDhChapCtrlKey()));
        rule = storageAccessRuleDao.persist(rule);
        final StorageServiceInstance.ResourceState finalState = getAppliedResourceState(instance);
        try {
            applyNvmeOfDesiredState(instance, buildNvmeOfSecretMap(rule.getId(), cmd.getDhChapKey(), cmd.getDhChapCtrlKey()),
                    Collections.singletonMap(rule.getId(), finalState));
            rule.setState(finalState);
            storageAccessRuleDao.update(rule.getId(), rule);
        } catch (final RuntimeException e) {
            rule.setState(StorageServiceInstance.ResourceState.Error);
            storageAccessRuleDao.update(rule.getId(), rule);
            throw e;
        }
        return createAclResponse(rule);
    }

    @Override
    public StorageAccessRuleResponse updateStorageNvmeOfHostAcl(final UpdateStorageNvmeOfHostAclCmd cmd) {
        final StorageAccessRuleVO rule = requireBlockAcl(cmd.getId(), StorageServiceInstance.Protocol.NVME_OF);
        final StorageBlockTargetVO target = requireBlockTarget(rule.getResourceId(), StorageServiceInstance.Protocol.NVME_OF);
        final StorageServiceInstanceVO instance = requireInstance(target.getInstanceId());
        if (cmd.getHostNqn() != null) {
            rule.setPrincipal(cmd.getHostNqn());
        }
        rule.setConfigJson(buildNvmeOfHostAclConfigJson(rule.getConfigJson(), cmd.getDhChapEnabled(), cmd.getDhChapCtrlEnabled(),
                cmd.getDhChapKey(), cmd.getDhChapCtrlKey()));
        rule.setState(StorageServiceInstance.ResourceState.Updating);
        storageAccessRuleDao.update(rule.getId(), rule);
        final StorageServiceInstance.ResourceState finalState = getAppliedResourceState(instance);
        try {
            applyNvmeOfDesiredState(instance, buildNvmeOfSecretMap(rule.getId(), cmd.getDhChapKey(), cmd.getDhChapCtrlKey()),
                    Collections.singletonMap(rule.getId(), finalState));
            rule.setState(finalState);
            storageAccessRuleDao.update(rule.getId(), rule);
        } catch (final RuntimeException e) {
            rule.setState(StorageServiceInstance.ResourceState.Error);
            storageAccessRuleDao.update(rule.getId(), rule);
            throw e;
        }
        return createAclResponse(rule);
    }

    @Override
    public boolean deleteStorageNvmeOfHostAcl(final DeleteStorageNvmeOfHostAclCmd cmd) {
        final StorageAccessRuleVO rule = requireBlockAcl(cmd.getId(), StorageServiceInstance.Protocol.NVME_OF);
        final StorageBlockTargetVO target = requireBlockTarget(rule.getResourceId(), StorageServiceInstance.Protocol.NVME_OF);
        final StorageServiceInstanceVO instance = requireInstance(target.getInstanceId());
        storageAccessRuleDao.remove(rule.getId());
        applyNvmeOfDesiredState(instance);
        return true;
    }

    @Override
    public ListResponse<StorageAccessRuleResponse> listStorageNvmeOfHostAcls(final ListStorageNvmeOfHostAclsCmd cmd) {
        return listBlockAcls(cmd.getId(), cmd.getSubsystemId(), StorageServiceInstance.Protocol.NVME_OF);
    }

    protected void applyNfsDesiredState(final StorageServiceInstanceVO instance) {
        applyNfsDesiredState(instance, null);
    }

    protected void applyNfsDesiredState(final StorageServiceInstanceVO instance, final String removeListenIp) {
        if (instance.getVmId() == null) {
            logger.debug("Storage Service instance [{}] has no System VM yet; NFS state is stored but not applied", instance.getUuid());
            return;
        }

        final JsonObject payload = new JsonObject();
        payload.addProperty("instanceUuid", instance.getUuid());
        payload.addProperty("instanceId", instance.getId());
        final StorageServiceProtocolVO protocol = storageServiceProtocolDao.findByInstanceIdAndProtocol(instance.getId(), StorageServiceInstance.Protocol.NFS);
        payload.addProperty("enabled", protocol == null || protocol.isEnabled());
        if (protocol != null) {
            payload.addProperty("listenIp", protocol.getListenIp());
            if (protocol.getPort() != null) {
                payload.addProperty("port", protocol.getPort());
            }
        }
        if (StringUtils.isNotBlank(removeListenIp)) {
            payload.addProperty("removeListenIp", removeListenIp);
        }

        final JsonArray exports = new JsonArray();
        for (final StorageFileShareVO share : storageFileShareDao.listByInstanceIdAndProtocol(instance.getId(), StorageServiceInstance.Protocol.NFS)) {
            if (!isApplicableFileShareState(share.getState())) {
                logger.debug("Skipping NFS export [{}] in state [{}] while building desired state", share.getUuid(), share.getState());
                continue;
            }
            final JsonObject shareConfig = parseJsonObjectStrict(share.getConfigJson(), "NFS export " + share.getUuid());
            validateNfsExportBackingConfig(share, shareConfig);
            final JsonObject export = new JsonObject();
            export.addProperty("id", share.getId());
            export.addProperty("uuid", share.getUuid());
            export.addProperty("name", share.getName());
            export.addProperty("path", share.getPath());
            if (share.getVolumeId() != null) {
                export.addProperty("volumeId", share.getVolumeId());
            }
            export.addProperty("filesystem", share.getFilesystem());
            if (share.getQuotaBytes() != null) {
                export.addProperty("quotaBytes", share.getQuotaBytes());
            }
            export.addProperty("state", share.getState().name());
            export.add("config", shareConfig);

            final JsonArray acls = new JsonArray();
            final HashSet<String> aclKeys = new HashSet<>();
            for (final StorageAccessRuleVO rule : storageAccessRuleDao.listByResource(StorageServiceInstance.AccessResourceType.FILE_SHARE, share.getId())) {
                if (!isApplicableResourceState(rule.getState())) {
                    logger.debug("Skipping NFS ACL [{}] in state [{}] while building desired state", rule.getUuid(), rule.getState());
                    continue;
                }
                final String aclKey = StringUtils.defaultString(rule.getPrincipalType() == null ? null : rule.getPrincipalType().name()) + ":" +
                        StringUtils.defaultString(rule.getPrincipal());
                if (aclKeys.contains(aclKey)) {
                    logger.warn("Skipping duplicate NFS ACL [{}] for export [{}] while building desired state", aclKey, share.getUuid());
                    continue;
                }
                aclKeys.add(aclKey);
                final JsonObject acl = new JsonObject();
                acl.addProperty("id", rule.getId());
                acl.addProperty("uuid", rule.getUuid());
                acl.addProperty("principalType", rule.getPrincipalType().name());
                acl.addProperty("principal", rule.getPrincipal());
                acl.addProperty("permission", rule.getPermission().name());
                acl.addProperty("state", rule.getState().name());
                acl.add("config", parseJsonObjectStrict(rule.getConfigJson(), "NFS ACL " + rule.getUuid()));
                acls.add(acl);
            }
            export.add("acls", acls);
            exports.add(export);
        }
        payload.add("exports", exports);

        final StorageServiceGuestCommandResult result = guestCommandDispatcher.dispatch(new StorageServiceGuestCommand(instance.getVmId(),
                "nfs export apply", GSON.toJson(payload), StorageServiceInstance.StorageServiceCommandTimeout.value(), Collections.emptySet()));
        if (!result.isSuccess()) {
            throw new CloudRuntimeException("Failed to apply NFS desired state on Storage Service System VM: " + result.getDetails());
        }
    }

    protected void applySmbDesiredState(final StorageServiceInstanceVO instance) {
        applySmbDesiredState(instance, Collections.emptyMap());
    }

    protected void applySmbDesiredState(final StorageServiceInstanceVO instance, final Map<Long, String> rulePasswords) {
        if (instance.getVmId() == null) {
            logger.debug("Storage Service instance [{}] has no System VM yet; SMB state is stored but not applied", instance.getUuid());
            return;
        }

        final JsonObject payload = new JsonObject();
        payload.addProperty("instanceUuid", instance.getUuid());
        payload.addProperty("instanceId", instance.getId());
        final StorageServiceProtocolVO protocol = storageServiceProtocolDao.findByInstanceIdAndProtocol(instance.getId(), StorageServiceInstance.Protocol.SMB);
        payload.addProperty("enabled", protocol == null || protocol.isEnabled());
        if (protocol != null) {
            payload.addProperty("listenIp", protocol.getListenIp());
            if (protocol.getPort() != null) {
                payload.addProperty("port", protocol.getPort());
            }
        }

        final StorageIdentityDomainVO domain = storageIdentityDomainDao.findByInstanceId(instance.getId());
        if (domain != null) {
            final JsonObject identity = new JsonObject();
            identity.addProperty("domainName", domain.getDomainName());
            identity.addProperty("organizationalUnit", domain.getOrganizationalUnit());
            identity.addProperty("dnsServers", domain.getDnsServers());
            identity.addProperty("joinState", domain.getJoinState().name());
            identity.addProperty("healthState", domain.getHealthState());
            identity.add("config", parseJsonObject(domain.getConfigJson()));
            payload.add("identityDomain", identity);
        }

        final JsonArray shares = new JsonArray();
        for (final StorageFileShareVO share : storageFileShareDao.listByInstanceIdAndProtocol(instance.getId(), StorageServiceInstance.Protocol.SMB)) {
            final JsonObject smbShare = new JsonObject();
            smbShare.addProperty("id", share.getId());
            smbShare.addProperty("uuid", share.getUuid());
            smbShare.addProperty("name", share.getName());
            smbShare.addProperty("path", share.getPath());
            if (share.getVolumeId() != null) {
                smbShare.addProperty("volumeId", share.getVolumeId());
            }
            smbShare.addProperty("filesystem", share.getFilesystem());
            if (share.getQuotaBytes() != null) {
                smbShare.addProperty("quotaBytes", share.getQuotaBytes());
            }
            smbShare.addProperty("state", share.getState().name());
            smbShare.add("config", parseJsonObject(share.getConfigJson()));

            final JsonArray acls = new JsonArray();
            for (final StorageAccessRuleVO rule : storageAccessRuleDao.listByResource(StorageServiceInstance.AccessResourceType.FILE_SHARE, share.getId())) {
                if (!isSmbPrincipalType(rule.getPrincipalType())) {
                    continue;
                }
                final JsonObject acl = new JsonObject();
                acl.addProperty("id", rule.getId());
                acl.addProperty("uuid", rule.getUuid());
                acl.addProperty("principalType", rule.getPrincipalType().name());
                acl.addProperty("principal", rule.getPrincipal());
                acl.addProperty("permission", rule.getPermission().name());
                acl.addProperty("state", rule.getState().name());
                acl.add("config", parseJsonObject(rule.getConfigJson()));
                if (rulePasswords != null && rulePasswords.containsKey(rule.getId())) {
                    acl.addProperty("password", rulePasswords.get(rule.getId()));
                }
                acls.add(acl);
            }
            smbShare.add("acls", acls);
            shares.add(smbShare);
        }
        payload.add("shares", shares);

        final StorageServiceGuestCommandResult result = guestCommandDispatcher.dispatch(new StorageServiceGuestCommand(instance.getVmId(),
                "smb share apply", GSON.toJson(payload), StorageServiceInstance.StorageServiceCommandTimeout.value(), Collections.singleton("password")));
        if (!result.isSuccess()) {
            throw new CloudRuntimeException("Failed to apply SMB desired state on Storage Service System VM: " + result.getDetails());
        }
    }

    protected void applyAdJoin(final StorageServiceInstanceVO instance, final StorageIdentityDomainVO domain,
            final String username, final String password) {
        if (instance.getVmId() == null) {
            logger.debug("Storage Service instance [{}] has no System VM yet; AD join state is stored but not applied", instance.getUuid());
            return;
        }
        final JsonObject payload = new JsonObject();
        payload.addProperty("instanceUuid", instance.getUuid());
        payload.addProperty("domainName", domain.getDomainName());
        payload.addProperty("username", username);
        payload.addProperty("password", password);
        payload.addProperty("organizationalUnit", domain.getOrganizationalUnit());
        payload.addProperty("dnsServers", domain.getDnsServers());
        payload.add("config", parseJsonObject(domain.getConfigJson()));
        final StorageServiceGuestCommandResult result = guestCommandDispatcher.dispatch(new StorageServiceGuestCommand(instance.getVmId(),
                "smb domain join", GSON.toJson(payload), StorageServiceInstance.StorageServiceCommandTimeout.value(), Collections.singleton("password")));
        if (!result.isSuccess()) {
            throw new CloudRuntimeException("Failed to join SMB AD domain on Storage Service System VM: " + result.getDetails());
        }
    }

    protected void applyAdLeave(final StorageServiceInstanceVO instance, final String username, final String password) {
        if (instance.getVmId() == null) {
            logger.debug("Storage Service instance [{}] has no System VM yet; AD leave state is stored but not applied", instance.getUuid());
            return;
        }
        final StorageIdentityDomainVO domain = storageIdentityDomainDao.findByInstanceId(instance.getId());
        final JsonObject payload = new JsonObject();
        payload.addProperty("instanceUuid", instance.getUuid());
        payload.addProperty("domainName", domain == null ? null : domain.getDomainName());
        payload.addProperty("username", username);
        payload.addProperty("password", password);
        final StorageServiceGuestCommandResult result = guestCommandDispatcher.dispatch(new StorageServiceGuestCommand(instance.getVmId(),
                "smb domain leave", GSON.toJson(payload), StorageServiceInstance.StorageServiceCommandTimeout.value(), Collections.singleton("password")));
        if (!result.isSuccess()) {
            throw new CloudRuntimeException("Failed to leave SMB AD domain on Storage Service System VM: " + result.getDetails());
        }
    }

    protected void applyIscsiDesiredState(final StorageServiceInstanceVO instance) {
        applyIscsiDesiredState(instance, Collections.emptyMap());
    }

    protected void applyIscsiDesiredState(final StorageServiceInstanceVO instance, final Map<Long, JsonObject> chapSecrets) {
        if (instance.getVmId() == null) {
            logger.debug("Storage Service instance [{}] has no System VM yet; iSCSI state is stored but not applied", instance.getUuid());
            return;
        }

        final JsonObject payload = buildBlockProtocolPayload(instance, StorageServiceInstance.Protocol.ISCSI);
        final JsonArray targets = new JsonArray();
        for (final StorageBlockTargetVO target : storageBlockTargetDao.listByInstanceIdAndProtocol(instance.getId(), StorageServiceInstance.Protocol.ISCSI)) {
            final JsonObject targetJson = createBlockTargetJson(target);
            final JsonArray acls = new JsonArray();
            for (final StorageAccessRuleVO rule : storageAccessRuleDao.listByResource(StorageServiceInstance.AccessResourceType.BLOCK_TARGET, target.getId())) {
                if (rule.getPrincipalType() != StorageServiceInstance.PrincipalType.ISCSI_INITIATOR_IQN) {
                    continue;
                }
                final JsonObject acl = createBlockAclJson(rule);
                if (chapSecrets != null && chapSecrets.containsKey(rule.getId())) {
                    acl.add("secrets", chapSecrets.get(rule.getId()));
                }
                acls.add(acl);
            }
            targetJson.add("acls", acls);
            targets.add(targetJson);
        }
        payload.add("targets", targets);

        final StorageServiceGuestCommandResult result = guestCommandDispatcher.dispatch(new StorageServiceGuestCommand(instance.getVmId(),
                "iscsi target apply", GSON.toJson(payload), StorageServiceInstance.StorageServiceCommandTimeout.value(),
                new HashSet<>(Arrays.asList("chapSecret", "mutualChapSecret"))));
        if (!result.isSuccess()) {
            throw new CloudRuntimeException("Failed to apply iSCSI desired state on Storage Service System VM: " + result.getDetails());
        }
    }

    protected void applyNvmeOfDesiredState(final StorageServiceInstanceVO instance) {
        applyNvmeOfDesiredState(instance, Collections.emptyMap());
    }

    protected void applyNvmeOfDesiredState(final StorageServiceInstanceVO instance, final Map<Long, JsonObject> nvmeSecrets) {
        applyNvmeOfDesiredState(instance, nvmeSecrets, Collections.emptyMap());
    }

    protected void applyNvmeOfDesiredState(final StorageServiceInstanceVO instance, final Map<Long, JsonObject> nvmeSecrets,
            final Map<Long, StorageServiceInstance.ResourceState> hostStateOverrides) {
        if (instance.getVmId() == null) {
            logger.debug("Storage Service instance [{}] has no System VM yet; NVMe-oF state is stored but not applied", instance.getUuid());
            return;
        }

        final JsonObject payload = buildBlockProtocolPayload(instance, StorageServiceInstance.Protocol.NVME_OF);
        final JsonArray subsystems = new JsonArray();
        for (final StorageBlockTargetVO target : storageBlockTargetDao.listByInstanceIdAndProtocol(instance.getId(), StorageServiceInstance.Protocol.NVME_OF)) {
            if (!isNvmeOfSubsystem(target)) {
                continue;
            }
            final JsonObject subsystem = createBlockTargetJson(target);
            final JsonArray namespaces = new JsonArray();
            for (final StorageBlockTargetVO namespace : storageBlockTargetDao.listByInstanceIdAndProtocol(instance.getId(), StorageServiceInstance.Protocol.NVME_OF)) {
                if (!isNvmeOfNamespace(namespace) || !target.getTargetName().equals(namespace.getTargetName())) {
                    continue;
                }
                namespaces.add(createBlockTargetJson(namespace));
            }
            final JsonArray hosts = new JsonArray();
            for (final StorageAccessRuleVO rule : storageAccessRuleDao.listByResource(StorageServiceInstance.AccessResourceType.BLOCK_TARGET, target.getId())) {
                if (rule.getPrincipalType() == StorageServiceInstance.PrincipalType.NVME_HOST_NQN) {
                    final JsonObject host = createBlockAclJson(rule);
                    if (hostStateOverrides != null && hostStateOverrides.containsKey(rule.getId())) {
                        host.addProperty("state", hostStateOverrides.get(rule.getId()).name());
                    }
                    if (nvmeSecrets != null && nvmeSecrets.containsKey(rule.getId())) {
                        host.add("secrets", nvmeSecrets.get(rule.getId()));
                    }
                    hosts.add(host);
                }
            }
            subsystem.add("namespaces", namespaces);
            subsystem.add("hosts", hosts);
            subsystems.add(subsystem);
        }
        payload.add("subsystems", subsystems);

        final StorageServiceGuestCommandResult result = guestCommandDispatcher.dispatch(new StorageServiceGuestCommand(instance.getVmId(),
                "nvmeof subsystem apply", GSON.toJson(payload), StorageServiceInstance.StorageServiceCommandTimeout.value(),
                new HashSet<>(Arrays.asList("dhChapKey", "dhChapCtrlKey"))));
        if (!result.isSuccess()) {
            throw new CloudRuntimeException("Failed to apply NVMe-oF desired state on Storage Service System VM: " + result.getDetails());
        }
    }

    protected boolean deleteStorageServiceEndpoint(final StorageServiceInstanceVO instance, final StorageServiceInstance.Protocol protocol, final String listenIp) {
        if (protocol != StorageServiceInstance.Protocol.NFS) {
            throw new InvalidParameterValueException("Endpoint removal is currently supported for NFS protocol endpoints only");
        }
        final String endpoint = StringUtils.trim(listenIp);
        if (!isValidIpv4Address(endpoint)) {
            throw new InvalidParameterValueException("Invalid Storage Service endpoint IP: " + listenIp);
        }
        for (final NicVO nic : nicDao.listByVmId(instance.getVmId())) {
            if (endpoint.equals(nic.getIPv4Address())) {
                throw new InvalidParameterValueException("Primary Storage Service NIC IP cannot be removed as an endpoint: " + endpoint);
            }
        }

        boolean changed = false;
        for (final StorageFileShareVO share : storageFileShareDao.listByInstanceIdAndProtocol(instance.getId(), StorageServiceInstance.Protocol.NFS)) {
            final JsonObject config = parseJsonObject(share.getConfigJson());
            if (removeListenIpFromConfig(config, endpoint)) {
                share.setConfigJson(GSON.toJson(config));
                storageFileShareDao.update(share.getId(), share);
                changed = true;
            }
        }

        final StorageServiceProtocolVO protocolVO = storageServiceProtocolDao.findByInstanceIdAndProtocol(instance.getId(), protocol);
        if (protocolVO != null && endpoint.equals(protocolVO.getListenIp())) {
            protocolVO.setListenIp(null);
            storageServiceProtocolDao.update(protocolVO.getId(), protocolVO);
            changed = true;
        }
        removeSecondaryListenAddress(instance, endpoint);
        applyNfsDesiredState(instance, endpoint);
        return changed;
    }

    protected StorageServiceInstance.ResourceState getAppliedResourceState(final StorageServiceInstanceVO instance) {
        return instance.getVmId() == null ? StorageServiceInstance.ResourceState.Allocated : StorageServiceInstance.ResourceState.Ready;
    }

    protected void applyStorageServiceProtocolDesiredState(final StorageServiceInstanceVO instance, final StorageServiceInstance.Protocol protocol) {
        if (protocol == StorageServiceInstance.Protocol.NFS) {
            applyNfsDesiredState(instance);
        } else if (protocol == StorageServiceInstance.Protocol.SMB) {
            applySmbDesiredState(instance);
        } else if (protocol == StorageServiceInstance.Protocol.ISCSI) {
            applyIscsiDesiredState(instance);
        } else if (protocol == StorageServiceInstance.Protocol.NVME_OF) {
            applyNvmeOfDesiredState(instance);
        }
    }

    protected void applyFileShareDesiredState(final StorageServiceInstanceVO instance, final StorageServiceInstance.Protocol protocol) {
        if (protocol == StorageServiceInstance.Protocol.NFS) {
            applyNfsDesiredState(instance);
        } else if (protocol == StorageServiceInstance.Protocol.SMB) {
            applySmbDesiredState(instance);
        } else {
            throw new InvalidParameterValueException("Protocol " + protocol + " is not a file service protocol");
        }
    }

    protected void inspectAttachedFileShareVolume(final StorageServiceInstanceVO instance, final StorageFileShareVO share,
            final VolumeVO volume, final String importMode) {
        final JsonObject payload = createFileShareVolumePayload(instance, share, volume);
        payload.addProperty("importMode", StringUtils.defaultIfBlank(importMode, "MOUNT_EXISTING").toUpperCase());
        final StorageServiceGuestCommandResult result = guestCommandDispatcher.dispatch(new StorageServiceGuestCommand(instance.getVmId(),
                "volume attach inspect", GSON.toJson(payload), StorageServiceInstance.StorageServiceCommandTimeout.value(), Collections.emptySet()));
        if (!result.isSuccess()) {
            throw new CloudRuntimeException("Failed to inspect attached Storage Service volume: " + result.getDetails());
        }
        final JsonObject resultJson = parseJsonObject(result.getResultJson());
        if (resultJson.has("filesystem") && !resultJson.get("filesystem").isJsonNull()) {
            share.setFilesystem(resultJson.get("filesystem").getAsString());
        }
        share.setConfigJson(buildFileShareAttachConfigJson(share.getConfigJson(), importMode, volume, resultJson));
        storageFileShareDao.update(share.getId(), share);
    }

    protected void growFileShareFilesystem(final StorageServiceInstanceVO instance, final StorageFileShareVO share,
            final Long volumeSizeGb, final Long quotaBytes) {
        final VolumeVO volume = requireVolume(share.getVolumeId());
        final JsonObject payload = createFileShareVolumePayload(instance, share, volume);
        if (volumeSizeGb != null) {
            payload.addProperty("volumeSizeGb", volumeSizeGb);
        }
        if (quotaBytes != null) {
            payload.addProperty("quotaBytes", quotaBytes);
        }
        final StorageServiceGuestCommandResult result = guestCommandDispatcher.dispatch(new StorageServiceGuestCommand(instance.getVmId(),
                "filesystem resize", GSON.toJson(payload), StorageServiceInstance.StorageServiceCommandTimeout.value(), Collections.emptySet()));
        if (!result.isSuccess()) {
            share.setState(StorageServiceInstance.ResourceState.Error);
            storageFileShareDao.update(share.getId(), share);
            throw new CloudRuntimeException("Failed to resize Storage Service file share filesystem: " + result.getDetails());
        }
        final JsonObject resultJson = parseJsonObject(result.getResultJson());
        share.setConfigJson(buildFileShareResizeConfigJson(share.getConfigJson(), resultJson, quotaBytes));
        storageFileShareDao.update(share.getId(), share);
    }

    protected JsonObject createFileShareVolumePayload(final StorageServiceInstanceVO instance, final StorageFileShareVO share, final VolumeVO volume) {
        final JsonObject payload = new JsonObject();
        payload.addProperty("instanceUuid", instance.getUuid());
        payload.addProperty("shareId", share.getId());
        payload.addProperty("shareUuid", share.getUuid());
        payload.addProperty("protocol", share.getProtocol().name());
        payload.addProperty("name", share.getName());
        payload.addProperty("path", share.getPath());
        payload.addProperty("filesystem", share.getFilesystem());
        payload.addProperty("quotaBytes", share.getQuotaBytes());
        payload.addProperty("volumeId", volume.getId());
        payload.addProperty("volumeUuid", volume.getUuid());
        payload.addProperty("volumeName", volume.getName());
        payload.addProperty("volumeSizeBytes", volume.getSize());
        final JsonObject config = parseJsonObject(share.getConfigJson());
        if (!config.has("volumeMountPath") || config.get("volumeMountPath").isJsonNull()) {
            config.addProperty("volumeMountPath", resolveFileShareVolumeMountRoot(instance, volume, share.getPath()));
        }
        payload.add("config", config);
        return payload;
    }

    protected void resizeBackingVolume(final Long volumeId, final Long sizeGb) {
        final ResizeVolumeCmd resizeVolumeCmd = new ResizeVolumeCmd();
        resizeVolumeCmd.setId(volumeId);
        resizeVolumeCmd.setSize(sizeGb);
        try {
            final Volume resizedVolume = volumeApiService.resizeVolume(resizeVolumeCmd);
            if (resizedVolume == null) {
                throw new CloudRuntimeException("CloudStack volume resize returned no volume for id " + volumeId);
            }
        } catch (final ResourceAllocationException e) {
            throw new CloudRuntimeException("Failed to resize backing volume " + volumeId + ": " + e.getMessage(), e);
        }
    }

    protected JsonObject buildBlockProtocolPayload(final StorageServiceInstanceVO instance, final StorageServiceInstance.Protocol protocolType) {
        final JsonObject payload = new JsonObject();
        payload.addProperty("instanceUuid", instance.getUuid());
        payload.addProperty("instanceId", instance.getId());
        final StorageServiceProtocolVO protocol = storageServiceProtocolDao.findByInstanceIdAndProtocol(instance.getId(), protocolType);
        payload.addProperty("enabled", protocol == null || protocol.isEnabled());
        if (protocol != null) {
            payload.addProperty("listenIp", protocol.getListenIp());
            if (protocol.getPort() != null) {
                payload.addProperty("port", protocol.getPort());
            }
        }
        return payload;
    }

    protected JsonObject createBlockTargetJson(final StorageBlockTargetVO target) {
        final JsonObject targetJson = new JsonObject();
        final JsonObject config = parseJsonObject(target.getConfigJson());
        Long configuredSize = getJsonLong(config, "lunSizeBytes");
        if (configuredSize == null) {
            configuredSize = getJsonLong(config, "namespaceSizeBytes");
        }
        Long volumeSize = null;
        targetJson.addProperty("id", target.getId());
        targetJson.addProperty("uuid", target.getUuid());
        targetJson.addProperty("protocol", target.getProtocol().name());
        targetJson.addProperty("targetName", target.getTargetName());
        targetJson.addProperty("lunOrNamespace", target.getLunOrNamespace());
        if (target.getVolumeId() != null) {
            targetJson.addProperty("volumeId", target.getVolumeId());
            final VolumeVO volume = volumeDao.findById(target.getVolumeId());
            if (volume != null) {
                targetJson.addProperty("volumeUuid", volume.getUuid());
                targetJson.addProperty("volumeName", volume.getName());
                volumeSize = volume.getSize();
                targetJson.addProperty("volumeSizeBytes", volumeSize);
            }
        }
        if (configuredSize != null) {
            targetJson.addProperty("lunSizeBytes", configuredSize);
        }
        if (configuredSize != null || volumeSize != null) {
            targetJson.addProperty("effectiveSizeBytes", configuredSize == null ? volumeSize : configuredSize);
        }
        final String backingPath = getJsonString(config, "backingPath");
        if (StringUtils.isNotBlank(backingPath)) {
            targetJson.addProperty("backingPath", backingPath);
        }
        StorageServiceInstance.ResourceState desiredState = target.getState();
        if (desiredState == StorageServiceInstance.ResourceState.Creating || desiredState == StorageServiceInstance.ResourceState.Updating) {
            desiredState = StorageServiceInstance.ResourceState.Ready;
        }
        targetJson.addProperty("state", desiredState.name());
        targetJson.add("config", config);
        return targetJson;
    }

    protected JsonObject createBlockAclJson(final StorageAccessRuleVO rule) {
        final JsonObject acl = new JsonObject();
        acl.addProperty("id", rule.getId());
        acl.addProperty("uuid", rule.getUuid());
        acl.addProperty("principalType", rule.getPrincipalType().name());
        acl.addProperty("principal", rule.getPrincipal());
        acl.addProperty("permission", rule.getPermission().name());
        acl.addProperty("state", rule.getState().name());
        acl.add("config", parseJsonObject(rule.getConfigJson()));
        return acl;
    }

    protected ListResponse<StorageServiceRuntimeResponse> listRuntimeOperation(final Long instanceId, final String operation) {
        return listRuntimeOperation(instanceId, operation, "");
    }

    protected ListResponse<StorageServiceRuntimeResponse> listRuntimeOperation(final Long instanceId, final String operation, final String payload) {
        final List<StorageServiceInstanceVO> instances = new ArrayList<>();
        if (instanceId != null) {
            instances.add(requireInstance(instanceId));
        } else {
            instances.addAll(storageServiceInstanceDao.listAll());
        }

        final List<StorageServiceRuntimeResponse> responses = new ArrayList<>();
        for (final StorageServiceInstanceVO instance : instances) {
            responses.add(createRuntimeResponse(instance, operation, payload));
        }
        final ListResponse<StorageServiceRuntimeResponse> response = new ListResponse<>();
        response.setResponses(responses, responses.size());
        return response;
    }

    protected StorageServiceRuntimeResponse createRuntimeResponse(final StorageServiceInstanceVO instance, final String operation) {
        return createRuntimeResponse(instance, operation, "");
    }

    protected StorageServiceRuntimeResponse createRuntimeResponse(final StorageServiceInstanceVO instance, final String operation, final String payload) {
        if (instance.getVmId() == null) {
            return createRuntimeResponse(instance, operation, false, "NOT_ATTACHED", "Storage Service instance has no System VM", "{}");
        }
        try {
            final StorageServiceGuestCommandResult result = guestCommandDispatcher.dispatch(new StorageServiceGuestCommand(instance.getVmId(),
                    operation, payload == null ? "" : payload, StorageServiceInstance.StorageServiceCommandTimeout.value(), Collections.emptySet()));
            final String status = extractRuntimeStatus(result);
            return createRuntimeResponse(instance, operation, result.isSuccess(), status, result.getDetails(), result.getResultJson());
        } catch (final RuntimeException e) {
            logger.warn("Failed to query Storage Service runtime operation [{}] for instance [{}]", operation, instance.getUuid(), e);
            return createRuntimeResponse(instance, operation, false, "ERROR", e.getMessage(), "{}");
        }
    }

    protected StorageServiceRuntimeResponse createRuntimeResponse(final StorageServiceInstanceVO instance, final String operation,
            final boolean success, final String status, final String details, final String resultJson) {
        final StorageServiceRuntimeResponse response = new StorageServiceRuntimeResponse();
        response.setId(instance.getUuid());
        response.setOperation(operation);
        response.setSuccess(success);
        response.setStatus(status);
        response.setDetails(details);
        response.setResultJson(sanitizeRuntimeResultJson(resultJson));
        response.setObjectName("storageserviceruntime");
        return response;
    }

    protected String sanitizeRuntimeResultJson(final String resultJson) {
        if (StringUtils.isBlank(resultJson)) {
            return "{}";
        }
        try {
            final JsonElement parsed = new JsonParser().parse(normalizeRuntimeResultJson(resultJson));
            redactSensitiveRuntimeFields(parsed);
            return RUNTIME_RESULT_GSON.toJson(parsed);
        } catch (final RuntimeException e) {
            logger.warn("Ignoring invalid Storage Service runtime result JSON", e);
            return "{}";
        }
    }

    protected String normalizeRuntimeResultJson(final String resultJson) {
        String normalized = resultJson == null ? "{}" : resultJson;
        while (normalized.contains("\\=")) {
            normalized = normalized.replace("\\=", "=");
        }
        return normalized;
    }

    protected void redactSensitiveRuntimeFields(final JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return;
        }
        if (element.isJsonArray()) {
            for (final JsonElement child : element.getAsJsonArray()) {
                redactSensitiveRuntimeFields(child);
            }
            return;
        }
        if (!element.isJsonObject()) {
            return;
        }
        final JsonObject object = element.getAsJsonObject();
        final List<String> keysToRemove = new ArrayList<>();
        for (final Map.Entry<String, JsonElement> entry : object.entrySet()) {
            final String key = entry.getKey();
            final String lowerKey = key.toLowerCase();
            if ("secrets".equals(lowerKey) || lowerKey.contains("secret") || lowerKey.contains("password") || lowerKey.endsWith("key")) {
                keysToRemove.add(key);
            } else {
                redactSensitiveRuntimeFields(entry.getValue());
            }
        }
        for (final String key : keysToRemove) {
            object.remove(key);
        }
    }

    protected void addStringProperty(final JsonObject object, final String key, final String value) {
        if (StringUtils.isNotBlank(value)) {
            object.addProperty(key, value);
        }
    }

    protected String extractRuntimeStatus(final StorageServiceGuestCommandResult result) {
        final JsonObject json = parseJsonObject(result.getResultJson());
        if (json.has("status")) {
            return json.get("status").getAsString();
        }
        return result.isSuccess() ? "OK" : "ERROR";
    }

    protected StorageServiceInstanceVO requireInstance(final Long id) {
        if (id == null) {
            throw new InvalidParameterValueException("Storage Service instance id is required");
        }
        final StorageServiceInstanceVO instance = storageServiceInstanceDao.findById(id);
        if (instance == null) {
            throw new InvalidParameterValueException("Unable to find Storage Service instance with id " + id);
        }
        return instance;
    }

    protected StorageFileShareVO requireFileShare(final Long id) {
        if (id == null) {
            throw new InvalidParameterValueException("Storage Service file share id is required");
        }
        final StorageFileShareVO share = storageFileShareDao.findById(id);
        if (share == null || (share.getProtocol() != StorageServiceInstance.Protocol.NFS && share.getProtocol() != StorageServiceInstance.Protocol.SMB)) {
            throw new InvalidParameterValueException("Unable to find Storage Service file share with id " + id);
        }
        return share;
    }

    protected StorageFileShareVO requireNfsExport(final Long id) {
        if (id == null) {
            throw new InvalidParameterValueException("NFS export id is required");
        }
        final StorageFileShareVO share = storageFileShareDao.findById(id);
        if (share == null || share.getProtocol() != StorageServiceInstance.Protocol.NFS) {
            throw new InvalidParameterValueException("Unable to find NFS export with id " + id);
        }
        return share;
    }

    protected StorageFileShareVO requireSmbShare(final Long id) {
        if (id == null) {
            throw new InvalidParameterValueException("SMB share id is required");
        }
        final StorageFileShareVO share = storageFileShareDao.findById(id);
        if (share == null || share.getProtocol() != StorageServiceInstance.Protocol.SMB) {
            throw new InvalidParameterValueException("Unable to find SMB share with id " + id);
        }
        return share;
    }

    protected StorageAccessRuleVO requireAcl(final Long id) {
        if (id == null) {
            throw new InvalidParameterValueException("ACL id is required");
        }
        final StorageAccessRuleVO rule = storageAccessRuleDao.findById(id);
        if (rule == null || rule.getResourceType() != StorageServiceInstance.AccessResourceType.FILE_SHARE) {
            throw new InvalidParameterValueException("Unable to find NFS ACL with id " + id);
        }
        return rule;
    }

    protected StorageAccessRuleVO requireSmbAcl(final Long id) {
        if (id == null) {
            throw new InvalidParameterValueException("SMB ACL id is required");
        }
        final StorageAccessRuleVO rule = storageAccessRuleDao.findById(id);
        if (rule == null || rule.getResourceType() != StorageServiceInstance.AccessResourceType.FILE_SHARE || !isSmbPrincipalType(rule.getPrincipalType())) {
            throw new InvalidParameterValueException("Unable to find SMB ACL with id " + id);
        }
        return rule;
    }

    protected StorageBlockTargetVO requireBlockTarget(final Long id, final StorageServiceInstance.Protocol protocol) {
        if (id == null) {
            throw new InvalidParameterValueException("Block target id is required");
        }
        final StorageBlockTargetVO target = storageBlockTargetDao.findById(id);
        if (target == null || target.getProtocol() != protocol) {
            throw new InvalidParameterValueException("Unable to find " + protocol + " block target with id " + id);
        }
        return target;
    }

    protected StorageBlockTargetVO requireNvmeOfSubsystem(final Long id) {
        final StorageBlockTargetVO target = requireBlockTarget(id, StorageServiceInstance.Protocol.NVME_OF);
        if (!isNvmeOfSubsystem(target)) {
            throw new InvalidParameterValueException("Unable to find NVMe-oF subsystem with id " + id);
        }
        return target;
    }

    protected StorageBlockTargetVO requireNvmeOfNamespace(final Long id) {
        final StorageBlockTargetVO target = requireBlockTarget(id, StorageServiceInstance.Protocol.NVME_OF);
        if (!isNvmeOfNamespace(target)) {
            throw new InvalidParameterValueException("Unable to find NVMe-oF namespace with id " + id);
        }
        return target;
    }

    protected StorageAccessRuleVO requireBlockAcl(final Long id, final StorageServiceInstance.Protocol protocol) {
        if (id == null) {
            throw new InvalidParameterValueException("Block ACL id is required");
        }
        final StorageAccessRuleVO rule = storageAccessRuleDao.findById(id);
        if (rule == null || rule.getResourceType() != StorageServiceInstance.AccessResourceType.BLOCK_TARGET) {
            throw new InvalidParameterValueException("Unable to find block ACL with id " + id);
        }
        requireBlockTarget(rule.getResourceId(), protocol);
        return rule;
    }

    protected List<StorageBlockTargetVO> listBlockTargets(final Long id, final Long instanceId, final StorageServiceInstance.Protocol protocol) {
        final List<StorageBlockTargetVO> targets = new ArrayList<>();
        if (id != null) {
            final StorageBlockTargetVO target = storageBlockTargetDao.findById(id);
            if (target != null && target.getProtocol() == protocol) {
                targets.add(target);
            }
        } else if (instanceId != null) {
            targets.addAll(storageBlockTargetDao.listByInstanceIdAndProtocol(instanceId, protocol));
        } else {
            targets.addAll(storageBlockTargetDao.listByProtocol(protocol));
        }
        return targets;
    }

    protected ListResponse<StorageAccessRuleResponse> listBlockAcls(final Long id, final Long targetId, final StorageServiceInstance.Protocol protocol) {
        final List<StorageAccessRuleVO> rules = new ArrayList<>();
        if (id != null) {
            final StorageAccessRuleVO rule = storageAccessRuleDao.findById(id);
            if (rule != null) {
                rules.add(rule);
            }
        } else if (targetId != null) {
            rules.addAll(storageAccessRuleDao.listByResource(StorageServiceInstance.AccessResourceType.BLOCK_TARGET, targetId));
        } else {
            rules.addAll(storageAccessRuleDao.listAll());
        }

        final List<StorageAccessRuleResponse> responses = new ArrayList<>();
        for (final StorageAccessRuleVO rule : rules) {
            if (rule.getResourceType() != StorageServiceInstance.AccessResourceType.BLOCK_TARGET) {
                continue;
            }
            final StorageBlockTargetVO target = storageBlockTargetDao.findById(rule.getResourceId());
            if (target == null || target.getProtocol() != protocol) {
                continue;
            }
            responses.add(createAclResponse(rule));
        }
        final ListResponse<StorageAccessRuleResponse> response = new ListResponse<>();
        response.setResponses(responses, responses.size());
        return response;
    }

    protected void validateVolume(final Long volumeId) {
        if (volumeId != null) {
            requireVolume(volumeId);
        }
    }

    protected void validateStorageServiceBackingVolume(final StorageServiceInstanceVO instance, final Long volumeId, final String resourceName) {
        if (volumeId == null) {
            return;
        }
        final VolumeVO volume = requireVolume(volumeId);
        final Long attachedVmId = volume.getInstanceId();
        if (attachedVmId != null && !attachedVmId.equals(instance.getVmId())) {
            throw new InvalidParameterValueException(resourceName + " backing volume " + volume.getUuid() +
                    " is attached to another VM. Select the current Storage Service backing volume or attach/import an existing volume " +
                    "into this Storage Service first.");
        }
    }

    protected void validateBackingVolumeUnused(final StorageServiceInstanceVO instance, final Long volumeId) {
        final List<String> users = new ArrayList<>();
        for (final StorageFileShareVO share : storageFileShareDao.listByInstanceIdAndProtocol(instance.getId(), StorageServiceInstance.Protocol.NFS)) {
            if (volumeId.equals(share.getVolumeId())) {
                users.add("NFS export " + share.getName());
            }
        }
        for (final StorageFileShareVO share : storageFileShareDao.listByInstanceIdAndProtocol(instance.getId(), StorageServiceInstance.Protocol.SMB)) {
            if (volumeId.equals(share.getVolumeId())) {
                users.add("SMB share " + share.getName());
            }
        }
        for (final StorageBlockTargetVO target : storageBlockTargetDao.listByInstanceIdAndProtocol(instance.getId(), StorageServiceInstance.Protocol.ISCSI)) {
            if (volumeId.equals(target.getVolumeId())) {
                users.add("iSCSI target " + target.getTargetName());
            }
        }
        for (final StorageBlockTargetVO target : storageBlockTargetDao.listByInstanceIdAndProtocol(instance.getId(), StorageServiceInstance.Protocol.NVME_OF)) {
            if (volumeId.equals(target.getVolumeId())) {
                users.add("NVMe-oF subsystem " + target.getTargetName());
            }
        }
        if (!users.isEmpty()) {
            throw new InvalidParameterValueException("Backing volume is still used by Storage Service resources: " + StringUtils.join(users, ", "));
        }
    }

    protected String resolveFileSharePath(final String path, final String name) {
        if (StringUtils.isNotBlank(path)) {
            return path.trim();
        }
        final String trimmedName = StringUtils.defaultIfBlank(name, "share").trim();
        final String safeName = StringUtils.defaultIfBlank(trimmedName.replaceAll("[^A-Za-z0-9._-]+", "-").replaceAll("^-+|-+$", ""), "share");
        return SharedFS.SharedFSPath + "/" + safeName;
    }

    protected String resolveNfsExportPath(final String path, final String name) {
        if (StringUtils.isNotBlank(path)) {
            return normalizeFileSharePath(path);
        }
        validateNfsExportName(name);
        return SharedFS.SharedFSPath + "/" + name.trim();
    }

    protected String resolveFileShareBackingPath(final StorageServiceInstanceVO instance, final VolumeVO volume, final String importMode,
            final String relativePath, final String path, final String name) {
        if (StringUtils.isBlank(relativePath)) {
            return resolveFileSharePath(path, name);
        }
        final String safeRelativePath = normalizeRelativeSharePath(relativePath);
        final String mountRoot = resolveFileShareVolumeMountRoot(instance, volume, path);
        return mountRoot + "/" + safeRelativePath;
    }

    protected String resolveFileShareVolumeMountRoot(final StorageServiceInstanceVO instance, final VolumeVO volume, final String path) {
        if (volume != null) {
            final String existingMountRoot = findKnownFileShareVolumeMountRoot(instance, volume.getId());
            if (StringUtils.isNotBlank(existingMountRoot)) {
                return existingMountRoot;
            }
            return "/srv/ablestack-storage/volumes/" + volume.getUuid();
        }
        return resolveFileSharePath(path, "share");
    }

    protected String findKnownFileShareVolumeMountRoot(final StorageServiceInstanceVO instance, final Long volumeId) {
        if (instance == null || volumeId == null) {
            return null;
        }
        final List<StorageFileShareVO> shares = new ArrayList<>();
        shares.addAll(storageFileShareDao.listByInstanceIdAndProtocol(instance.getId(), StorageServiceInstance.Protocol.NFS));
        shares.addAll(storageFileShareDao.listByInstanceIdAndProtocol(instance.getId(), StorageServiceInstance.Protocol.SMB));
        for (final StorageFileShareVO share : shares) {
            if (!volumeId.equals(share.getVolumeId())) {
                continue;
            }
            final JsonObject config = parseJsonObject(share.getConfigJson());
            final String mountRoot = getJsonString(config, "volumeMountPath");
            if (StringUtils.isNotBlank(mountRoot)) {
                return mountRoot;
            }
            final JsonObject inspection = getJsonObject(config, "lastInspection");
            final String inspectedMountRoot = getJsonString(inspection, "volumeMountPath");
            if (StringUtils.isNotBlank(inspectedMountRoot)) {
                return inspectedMountRoot;
            }
            final String mountPath = getJsonString(inspection, "mountPath");
            if (StringUtils.isNotBlank(mountPath)) {
                return mountPath;
            }
        }
        return null;
    }

    protected void validateNfsExportName(final String name) {
        final String value = StringUtils.trim(name);
        if (StringUtils.isBlank(value)) {
            throw new InvalidParameterValueException("NFS export name is required");
        }
        if (".".equals(value) || "..".equals(value) || value.contains("/") || value.contains("\\") || value.contains(" ")) {
            throw new InvalidParameterValueException("NFS export name must be a valid Linux directory name");
        }
        if (!value.matches("^[A-Za-z0-9._-]+$")) {
            throw new InvalidParameterValueException("NFS export name may contain only letters, numbers, dot, underscore, and hyphen");
        }
    }

    protected void validateNfsExportPath(final String path, final String name) {
        final String normalized = normalizeFileSharePath(path);
        validateFileSharePath(normalized, "NFS export");
        final String expected = SharedFS.SharedFSPath + "/" + StringUtils.trim(name);
        if (!expected.equals(normalized)) {
            throw new InvalidParameterValueException("NFS export internal backing path must be " + expected);
        }
        if (StringUtils.countMatches(normalized.substring(SharedFS.SharedFSPath.length()), "/") != 1) {
            throw new InvalidParameterValueException("NFS export internal backing path must be a direct child of " + SharedFS.SharedFSPath);
        }
    }

    protected String normalizeRelativeSharePath(final String relativePath) {
        final String normalized = StringUtils.defaultString(relativePath).trim().replace('\\', '/').replaceAll("/+", "/")
                .replaceAll("^/+", "").replaceAll("/+$", "");
        if (StringUtils.isBlank(normalized) || ".".equals(normalized) || normalized.contains("../") || normalized.endsWith("/..") || normalized.startsWith("..")) {
            throw new InvalidParameterValueException("File share relative path must be a non-empty relative path without traversal segments");
        }
        return normalized;
    }

    protected void validateFileShareFilesystem(final String filesystem, final String importMode) {
        final String mode = StringUtils.defaultString(importMode);
        if (!"FORMAT_EMPTY".equalsIgnoreCase(mode) && !"FORMAT_IF_EMPTY".equalsIgnoreCase(mode)) {
            return;
        }
        final String value = StringUtils.defaultIfBlank(filesystem, "xfs").trim().toLowerCase();
        if (!SUPPORTED_FILE_SHARE_FILESYSTEMS.contains(value)) {
            throw new InvalidParameterValueException("Storage Service backing volume filesystem must be xfs or ext4");
        }
    }

    protected void validateFileSharePath(final String path, final String resourceName) {
        if (StringUtils.isBlank(path)) {
            return;
        }
        final String trimmed = path.trim();
        final String normalized = StringUtils.removeEnd(trimmed, "/");
        if (!trimmed.startsWith("/") || trimmed.contains("/../") || trimmed.endsWith("/..") || trimmed.contains("//")) {
            throw new InvalidParameterValueException(resourceName + " path must be an absolute normalized path without traversal segments");
        }
        if ("/".equals(normalized) || SharedFS.SharedFSPath.equals(normalized)) {
            throw new InvalidParameterValueException(resourceName + " path must be a child directory. The legacy /export root cannot be shared");
        }
    }

    protected void validateFileSharePathAvailable(final StorageServiceInstanceVO instance, final String path, final Long currentShareId,
            final Long requestedVolumeId, final String resourceName) {
        if (StringUtils.isBlank(path)) {
            return;
        }
        final String normalizedPath = normalizeFileSharePath(path);
        final List<StorageFileShareVO> shares = new ArrayList<>();
        shares.addAll(storageFileShareDao.listByInstanceIdAndProtocol(instance.getId(), StorageServiceInstance.Protocol.NFS));
        shares.addAll(storageFileShareDao.listByInstanceIdAndProtocol(instance.getId(), StorageServiceInstance.Protocol.SMB));
        for (final StorageFileShareVO existing : shares) {
            if (currentShareId != null && currentShareId.equals(existing.getId())) {
                continue;
            }
            if (StringUtils.isBlank(existing.getPath())) {
                continue;
            }
            final String existingPath = normalizeFileSharePath(existing.getPath());
            if (normalizedPath.equals(existingPath)) {
                throw new InvalidParameterValueException(resourceName + " path is already used by another Storage Service share: " + path);
            }
            if (requestedVolumeId != null && existing.getVolumeId() != null && !requestedVolumeId.equals(existing.getVolumeId()) &&
                    (isSubPath(normalizedPath, existingPath) || isSubPath(existingPath, normalizedPath))) {
                throw new InvalidParameterValueException(resourceName + " path overlaps another mounted backing-volume path: " + path);
            }
        }
    }

    protected String normalizeFileSharePath(final String path) {
        return StringUtils.removeEnd(StringUtils.defaultString(path).trim(), "/");
    }

    protected boolean isSubPath(final String path, final String parent) {
        return StringUtils.isNotBlank(path) && StringUtils.isNotBlank(parent) && path.startsWith(parent + "/");
    }

    protected VolumeVO requireVolume(final Long volumeId) {
        if (volumeId == null) {
            throw new InvalidParameterValueException("Volume id is required");
        }
        final VolumeVO volume = volumeDao.findById(volumeId);
        if (volume == null) {
            throw new InvalidParameterValueException("Unable to find volume with id " + volumeId);
        }
        return volume;
    }

    protected VolumeVO waitForFileShareVolumeAttachable(final Long volumeId) {
        VolumeVO volume = requireVolume(volumeId);
        for (int attempt = 0; attempt < FILE_SHARE_VOLUME_READY_ATTEMPTS; attempt++) {
            final Volume.State state = volume.getState();
            if (state == Volume.State.Allocated || state == Volume.State.Ready || state == Volume.State.Uploaded) {
                return volume;
            }
            if (state == null || !state.isTransitional()) {
                throw new InvalidParameterValueException(String.format("Volume %s is not attachable. Current state: %s", volume.getUuid(), state));
            }
            try {
                Thread.sleep(FILE_SHARE_VOLUME_READY_INTERVAL_MS);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CloudRuntimeException("Interrupted while waiting for Storage Service backing volume to become attachable", e);
            }
            volume = requireVolume(volumeId);
        }
        throw new InvalidParameterValueException(String.format("Volume %s did not reach an attachable state before Storage Service attach", volume.getUuid()));
    }

    protected StorageServiceInstance.Protocol parseProtocol(final String protocol) {
        try {
            return StorageServiceInstance.Protocol.valueOf(protocol.toUpperCase());
        } catch (final IllegalArgumentException e) {
            throw new InvalidParameterValueException("Invalid Storage Service protocol: " + protocol);
        }
    }

    protected Integer normalizeStorageServiceProtocolPort(final StorageServiceInstance.Protocol protocol, final Integer port) {
        if (protocol == StorageServiceInstance.Protocol.NFS) {
            if (port != null && port != 2049) {
                throw new InvalidParameterValueException("NFS uses the service-wide port 2049 in the current Storage Service System VM");
            }
            return 2049;
        }
        return port;
    }

    protected void validateProtocolCanBeDeleted(final StorageServiceInstanceVO instance, final StorageServiceInstance.Protocol protocol) {
        if (protocol == StorageServiceInstance.Protocol.NFS &&
                !storageFileShareDao.listByInstanceIdAndProtocol(instance.getId(), StorageServiceInstance.Protocol.NFS).isEmpty()) {
            throw new InvalidParameterValueException("Delete NFS exports before disabling the NFS protocol");
        }
        if (protocol == StorageServiceInstance.Protocol.SMB) {
            if (!storageFileShareDao.listByInstanceIdAndProtocol(instance.getId(), StorageServiceInstance.Protocol.SMB).isEmpty()) {
                throw new InvalidParameterValueException("Delete SMB shares before disabling the SMB protocol");
            }
            final StorageIdentityDomainVO domain = storageIdentityDomainDao.findByInstanceId(instance.getId());
            if (domain != null && StringUtils.isNotBlank(domain.getDomainName())) {
                throw new InvalidParameterValueException("Leave the SMB AD domain before disabling the SMB protocol");
            }
        }
        if (protocol == StorageServiceInstance.Protocol.ISCSI &&
                !storageBlockTargetDao.listByInstanceIdAndProtocol(instance.getId(), StorageServiceInstance.Protocol.ISCSI).isEmpty()) {
            throw new InvalidParameterValueException("Delete iSCSI targets before disabling the iSCSI protocol");
        }
        if (protocol == StorageServiceInstance.Protocol.NVME_OF &&
                !storageBlockTargetDao.listByInstanceIdAndProtocol(instance.getId(), StorageServiceInstance.Protocol.NVME_OF).isEmpty()) {
            throw new InvalidParameterValueException("Delete NVMe-oF subsystems and namespaces before disabling the NVMe-oF protocol");
        }
    }

    protected List<String> parseNfsPrincipals(final String principal, final String principals) {
        final String rawValues = StringUtils.defaultIfBlank(principals, principal);
        if (StringUtils.isBlank(rawValues)) {
            throw new InvalidParameterValueException("NFS ACL principal is required");
        }
        final List<String> values = new ArrayList<>();
        final HashSet<String> seen = new HashSet<>();
        for (final String rawValue : StringUtils.split(rawValues, ',')) {
            final String value = StringUtils.trim(rawValue);
            if (StringUtils.isBlank(value) || seen.contains(value)) {
                continue;
            }
            if (StringUtils.containsWhitespace(value)) {
                throw new InvalidParameterValueException("NFS ACL principal must not contain whitespace: " + value);
            }
            seen.add(value);
            values.add(value);
        }
        if (values.isEmpty()) {
            throw new InvalidParameterValueException("NFS ACL principal is required");
        }
        return values;
    }

    protected StorageServiceInstance.PrincipalType parseNfsPrincipalType(final String principalType) {
        final String value = StringUtils.isBlank(principalType) ? StorageServiceInstance.PrincipalType.CIDR.name() : principalType.toUpperCase();
        try {
            final StorageServiceInstance.PrincipalType type = StorageServiceInstance.PrincipalType.valueOf(value);
            if (type != StorageServiceInstance.PrincipalType.CIDR && type != StorageServiceInstance.PrincipalType.IP_ADDRESS) {
                throw new InvalidParameterValueException("NFS ACL supports only CIDR or IP_ADDRESS principal types");
            }
            return type;
        } catch (final IllegalArgumentException e) {
            throw new InvalidParameterValueException("Invalid NFS ACL principal type: " + principalType);
        }
    }

    protected StorageServiceInstance.Permission parseNfsPermission(final String permission) {
        try {
            final StorageServiceInstance.Permission value = StorageServiceInstance.Permission.valueOf(permission.toUpperCase());
            if (value != StorageServiceInstance.Permission.READ_ONLY && value != StorageServiceInstance.Permission.READ_WRITE) {
                throw new InvalidParameterValueException("NFS ACL supports only READ_ONLY or READ_WRITE permissions");
            }
            return value;
        } catch (final IllegalArgumentException e) {
            throw new InvalidParameterValueException("Invalid NFS ACL permission: " + permission);
        }
    }

    protected StorageServiceInstance.PrincipalType parseSmbPrincipalType(final String principalType) {
        final String value = StringUtils.isBlank(principalType) ? StorageServiceInstance.PrincipalType.LOCAL_USER.name() : principalType.toUpperCase();
        try {
            final StorageServiceInstance.PrincipalType type = StorageServiceInstance.PrincipalType.valueOf(value);
            if (!isSmbPrincipalType(type)) {
                throw new InvalidParameterValueException("SMB ACL supports LOCAL_USER, LOCAL_GROUP, AD_USER, or AD_GROUP principal types");
            }
            return type;
        } catch (final IllegalArgumentException e) {
            throw new InvalidParameterValueException("Invalid SMB ACL principal type: " + principalType);
        }
    }

    protected boolean isSmbPrincipalType(final StorageServiceInstance.PrincipalType principalType) {
        return principalType == StorageServiceInstance.PrincipalType.LOCAL_USER ||
                principalType == StorageServiceInstance.PrincipalType.LOCAL_GROUP ||
                principalType == StorageServiceInstance.PrincipalType.AD_USER ||
                principalType == StorageServiceInstance.PrincipalType.AD_GROUP;
    }

    protected StorageServiceInstance.Permission parseSmbPermission(final String permission) {
        try {
            return StorageServiceInstance.Permission.valueOf(permission.toUpperCase());
        } catch (final IllegalArgumentException e) {
            throw new InvalidParameterValueException("Invalid SMB ACL permission: " + permission);
        }
    }

    protected StorageServiceInstance.Permission parseBlockPermission(final String permission) {
        final String value = StringUtils.isBlank(permission) ? StorageServiceInstance.Permission.READ_WRITE.name() : permission.toUpperCase();
        try {
            final StorageServiceInstance.Permission parsed = StorageServiceInstance.Permission.valueOf(value);
            if (parsed == StorageServiceInstance.Permission.ADMIN) {
                throw new InvalidParameterValueException("Block ACL supports only READ_ONLY or READ_WRITE permissions");
            }
            return parsed;
        } catch (final IllegalArgumentException e) {
            throw new InvalidParameterValueException("Invalid block ACL permission: " + permission);
        }
    }

    protected String buildNfsConfigJson(final String currentConfig, final Boolean readOnly, final Boolean rootSquash,
            final Boolean allSquash, final Integer anonUid, final Integer anonGid, final Integer ownerUid, final Integer ownerGid,
            final String mode, final Boolean recursivePermission, final Boolean sync, final Boolean secure, final String endpointMode, final String listenIps,
            final boolean applyWritableRootSquashDefaults) {
        final JsonObject config = parseJsonObject(currentConfig);
        if (!config.has("readOnly")) {
            config.addProperty("readOnly", false);
        }
        if (!config.has("rootSquash")) {
            config.addProperty("rootSquash", true);
        }
        if (!config.has("allSquash")) {
            config.addProperty("allSquash", false);
        }
        if (!config.has("sync")) {
            config.addProperty("sync", true);
        }
        if (!config.has("secure")) {
            config.addProperty("secure", true);
        }
        if (readOnly != null) {
            config.addProperty("readOnly", readOnly);
        }
        if (rootSquash != null) {
            config.addProperty("rootSquash", rootSquash);
        }
        if (allSquash != null) {
            config.addProperty("allSquash", allSquash);
        }
        if (anonUid != null) {
            validateNfsNumericPermissionValue("anonuid", anonUid);
            config.addProperty("anonUid", anonUid);
        }
        if (anonGid != null) {
            validateNfsNumericPermissionValue("anongid", anonGid);
            config.addProperty("anonGid", anonGid);
        }
        if (ownerUid != null) {
            validateNfsNumericPermissionValue("owneruid", ownerUid);
            config.addProperty("ownerUid", ownerUid);
        }
        if (ownerGid != null) {
            validateNfsNumericPermissionValue("ownergid", ownerGid);
            config.addProperty("ownerGid", ownerGid);
        }
        if (StringUtils.isNotBlank(mode)) {
            validateNfsMode(mode);
            config.addProperty("mode", mode);
        }
        if (recursivePermission != null) {
            config.addProperty("recursivePermission", recursivePermission);
        }
        if (sync != null) {
            config.addProperty("sync", sync);
        }
        if (secure != null) {
            config.addProperty("secure", secure);
        }
        if (endpointMode != null || listenIps != null) {
            final JsonArray parsedListenIps = parseNfsListenIps(listenIps);
            final String normalizedEndpointMode = normalizeNfsEndpointMode(endpointMode, parsedListenIps, false);
            config.addProperty("endpointMode", normalizedEndpointMode);
            if ("SELECTED".equals(normalizedEndpointMode)) {
                config.add("listenIps", parsedListenIps);
            } else {
                config.remove("listenIps");
            }
        } else if (!config.has("endpointMode")) {
            config.addProperty("endpointMode", StringUtils.isNotBlank(nfsListenIpsAsString(config)) ? "SELECTED" : "ALL");
        }
        applyNfsWritableRootSquashDefaults(config, applyWritableRootSquashDefaults);
        return GSON.toJson(config);
    }

    protected void applyNfsWritableRootSquashDefaults(final JsonObject config, final boolean enabled) {
        if (!enabled) {
            return;
        }
        final boolean readOnly = getBoolean(config, "readOnly", false);
        final boolean rootSquash = getBoolean(config, "rootSquash", true);
        if (readOnly || !rootSquash) {
            return;
        }
        if (!config.has("anonUid")) {
            config.addProperty("anonUid", NFS_ANONYMOUS_UID);
        }
        if (!config.has("anonGid")) {
            config.addProperty("anonGid", NFS_ANONYMOUS_GID);
        }
        if (!config.has("ownerUid")) {
            config.addProperty("ownerUid", getInt(config, "anonUid", NFS_ANONYMOUS_UID));
        }
        if (!config.has("ownerGid")) {
            config.addProperty("ownerGid", getInt(config, "anonGid", NFS_ANONYMOUS_GID));
        }
        if (!config.has("mode")) {
            config.addProperty("mode", NFS_WRITABLE_ROOT_SQUASH_MODE);
        }
        if (!config.has("recursivePermission")) {
            config.addProperty("recursivePermission", false);
        }
        if (!config.has("posixPolicy")) {
            config.addProperty("posixPolicy", "ANONYMOUS_WRITE");
        }
    }

    protected boolean getBoolean(final JsonObject object, final String key, final boolean defaultValue) {
        if (object == null || !object.has(key)) {
            return defaultValue;
        }
        final JsonElement value = object.get(key);
        return value != null && !value.isJsonNull() ? value.getAsBoolean() : defaultValue;
    }

    protected int getInt(final JsonObject object, final String key, final int defaultValue) {
        if (object == null || !object.has(key)) {
            return defaultValue;
        }
        final JsonElement value = object.get(key);
        return value != null && !value.isJsonNull() ? value.getAsInt() : defaultValue;
    }

    protected JsonArray parseNfsListenIps(final String listenIps) {
        final JsonArray result = new JsonArray();
        if (StringUtils.isBlank(listenIps)) {
            return result;
        }
        final HashSet<String> seen = new HashSet<>();
        for (final String rawValue : StringUtils.split(listenIps, ',')) {
            final String value = StringUtils.trim(rawValue);
            if (StringUtils.isBlank(value) || seen.contains(value)) {
                continue;
            }
            if (!isValidIpv4Address(value)) {
                throw new InvalidParameterValueException("Invalid NFS export listen IP: " + value);
            }
            seen.add(value);
            result.add(value);
        }
        return result;
    }

    protected String normalizeNfsEndpointMode(final String endpointMode, final JsonArray listenIps, final boolean legacySelectedWhenBlank) {
        final String value = StringUtils.isBlank(endpointMode) ? null : StringUtils.trim(endpointMode).toUpperCase();
        final boolean hasListenIps = listenIps != null && listenIps.size() > 0;
        if (value == null) {
            return hasListenIps || legacySelectedWhenBlank ? "SELECTED" : "ALL";
        }
        if (!"ALL".equals(value) && !"SELECTED".equals(value)) {
            throw new InvalidParameterValueException("Invalid NFS export endpoint mode: " + endpointMode);
        }
        if ("SELECTED".equals(value) && !hasListenIps) {
            throw new InvalidParameterValueException("NFS export endpoint mode SELECTED requires at least one listen IP");
        }
        return value;
    }

    protected boolean removeListenIpFromConfig(final JsonObject config, final String listenIp) {
        if (config == null || !config.has("listenIps") || !config.get("listenIps").isJsonArray()) {
            return false;
        }
        final JsonArray next = new JsonArray();
        boolean removed = false;
        for (final JsonElement element : config.getAsJsonArray("listenIps")) {
            if (element == null || element.isJsonNull()) {
                continue;
            }
            final String value = StringUtils.trim(element.getAsString());
            if (StringUtils.equals(value, listenIp)) {
                removed = true;
                continue;
            }
            if (StringUtils.isNotBlank(value)) {
                next.add(value);
            }
        }
        if (removed) {
            config.add("listenIps", next);
        }
        return removed;
    }

    protected String nfsListenIpsAsString(final JsonObject config) {
        if (config == null || !config.has("listenIps") || !config.get("listenIps").isJsonArray()) {
            return null;
        }
        final List<String> values = new ArrayList<>();
        for (final JsonElement element : config.getAsJsonArray("listenIps")) {
            if (element != null && !element.isJsonNull() && StringUtils.isNotBlank(element.getAsString())) {
                values.add(element.getAsString());
            }
        }
        return values.isEmpty() ? null : StringUtils.join(values, ',');
    }

    protected String nfsEndpointModeAsString(final JsonObject config) {
        return nfsEndpointModeAsString(config, null);
    }

    protected String nfsEndpointModeAsString(final JsonObject config, final String rawConfig) {
        if (config == null) {
            return nfsEndpointModeFromRawConfig(rawConfig);
        }
        final JsonElement endpointMode = config.get("endpointMode") == null ? config.get("endpointmode") : config.get("endpointMode");
        if (endpointMode != null && !endpointMode.isJsonNull() && StringUtils.isNotBlank(endpointMode.getAsString())) {
            final String value = StringUtils.trim(endpointMode.getAsString()).toUpperCase();
            if ("SELECTED".equals(value) || "ALL".equals(value)) {
                return value;
            }
        }
        final String rawEndpointMode = nfsEndpointModeFromRawConfig(rawConfig);
        if (StringUtils.isNotBlank(rawEndpointMode)) {
            return rawEndpointMode;
        }
        return StringUtils.isNotBlank(nfsListenIpsAsString(config)) ? "SELECTED" : "ALL";
    }

    protected String nfsSelectedListenIpsAsString(final JsonObject config) {
        return nfsSelectedListenIpsAsString(config, null);
    }

    protected String nfsSelectedListenIpsAsString(final JsonObject config, final String rawConfig) {
        if (!"SELECTED".equals(nfsEndpointModeAsString(config, rawConfig))) {
            return null;
        }
        final String value = nfsListenIpsAsString(config);
        return StringUtils.isNotBlank(value) ? value : nfsListenIpsFromRawConfig(rawConfig);
    }

    protected String nfsEndpointModeFromRawConfig(final String rawConfig) {
        if (StringUtils.isBlank(rawConfig)) {
            return "ALL";
        }
        final Matcher matcher = NFS_ENDPOINT_MODE_PATTERN.matcher(rawConfig);
        if (matcher.find()) {
            return matcher.group(1).toUpperCase();
        }
        return StringUtils.isNotBlank(nfsListenIpsFromRawConfig(rawConfig)) ? "SELECTED" : "ALL";
    }

    protected String nfsListenIpsFromRawConfig(final String rawConfig) {
        if (StringUtils.isBlank(rawConfig)) {
            return null;
        }
        final String lower = rawConfig.toLowerCase();
        final int keyIndex = lower.indexOf("\"listenips\"");
        if (keyIndex < 0) {
            return null;
        }
        final int arrayStart = rawConfig.indexOf('[', keyIndex);
        if (arrayStart < 0) {
            return null;
        }
        final int arrayEnd = rawConfig.indexOf(']', arrayStart);
        final String candidate = arrayEnd > arrayStart ? rawConfig.substring(arrayStart, arrayEnd + 1) : rawConfig.substring(arrayStart);
        final List<String> values = new ArrayList<>();
        final HashSet<String> seen = new HashSet<>();
        final Matcher matcher = IPV4_ADDRESS_PATTERN.matcher(candidate);
        while (matcher.find()) {
            final String value = matcher.group();
            if (seen.add(value)) {
                values.add(value);
            }
        }
        return values.isEmpty() ? null : StringUtils.join(values, ',');
    }

    protected void removeSecondaryListenAddress(final StorageServiceInstanceVO instance, final String listenIp) {
        if (instance.getVmId() == null) {
            return;
        }
        for (final NicVO nic : nicDao.listByVmId(instance.getVmId())) {
            final NicSecondaryIpVO secondaryIp = nicSecondaryIpDao.findByIp4AddressAndNicId(listenIp, nic.getId());
            if (secondaryIp != null) {
                nicSecondaryIpDao.remove(secondaryIp.getId());
                logger.info("Removed Storage Service listen IP [{}] from NIC [{}] for instance [{}]", listenIp, nic.getUuid(), instance.getUuid());
                return;
            }
        }
    }

    protected String buildSmbConfigJson(final String currentConfig, final Boolean readOnly, final Boolean browseable, final Boolean guestOk) {
        final JsonObject config = parseJsonObject(currentConfig);
        if (!config.has("readOnly")) {
            config.addProperty("readOnly", false);
        }
        if (!config.has("browseable")) {
            config.addProperty("browseable", true);
        }
        if (!config.has("guestOk")) {
            config.addProperty("guestOk", false);
        }
        if (readOnly != null) {
            config.addProperty("readOnly", readOnly);
        }
        if (browseable != null) {
            config.addProperty("browseable", browseable);
        }
        if (guestOk != null) {
            config.addProperty("guestOk", guestOk);
        }
        return GSON.toJson(config);
    }

    protected String buildFileShareAttachConfigJson(final String currentConfig, final String importMode, final VolumeVO volume,
            final JsonObject inspection) {
        final JsonObject config = parseJsonObject(currentConfig);
        config.addProperty("volumeMode", "EXISTING_VOLUME");
        config.addProperty("importMode", StringUtils.defaultIfBlank(importMode, "MOUNT_EXISTING").toUpperCase());
        config.addProperty("attachedVolumeUuid", volume.getUuid());
        config.addProperty("attachedVolumeName", volume.getName());
        if (inspection != null && inspection.entrySet() != null) {
            config.add("lastInspection", inspection);
            final String volumeMountPath = getJsonString(inspection, "volumeMountPath");
            if (StringUtils.isNotBlank(volumeMountPath)) {
                config.addProperty("volumeMountPath", volumeMountPath);
            }
            final String backingPath = getJsonString(inspection, "backingPath");
            if (StringUtils.isNotBlank(backingPath)) {
                config.addProperty("backingPath", backingPath);
            }
        }
        return GSON.toJson(config);
    }

    protected String buildFileShareDirectoryConfigJson(final String currentConfig, final VolumeVO volume, final String importMode,
            final Boolean createDirectory) {
        final JsonObject config = parseJsonObject(currentConfig);
        if (volume != null && StringUtils.isNotBlank(importMode)) {
            config.addProperty("volumeMountPath", "/srv/ablestack-storage/volumes/" + volume.getUuid());
        }
        config.remove("relativeSharePath");
        config.remove("relativesharepath");
        config.addProperty("createDirectory", createDirectory == null || Boolean.TRUE.equals(createDirectory));
        return GSON.toJson(config);
    }

    protected String buildFileShareResizeConfigJson(final String currentConfig, final JsonObject resizeResult, final Long quotaBytes) {
        final JsonObject config = parseJsonObject(currentConfig);
        if (quotaBytes != null) {
            config.addProperty("quotaBytes", quotaBytes);
        }
        if (resizeResult != null && resizeResult.entrySet() != null) {
            config.add("lastResize", resizeResult);
        }
        return GSON.toJson(config);
    }

    protected String buildNvmeOfPreparationResult(final String engine, final String transport, final String runtimeCapabilityProfileId,
            final Boolean validateOnly) {
        final JsonObject result = new JsonObject();
        result.addProperty("success", false);
        result.addProperty("status", "PREPARATION_REQUIRED");
        result.addProperty("engine", engine);
        result.addProperty("transport", StringUtils.defaultIfBlank(transport, "tcp"));
        result.addProperty("validateOnly", Boolean.TRUE.equals(validateOnly));
        if (runtimeCapabilityProfileId != null) {
            result.addProperty("runtimeCapabilityProfileId", runtimeCapabilityProfileId);
        }
        result.addProperty("vmRuntimeCapabilityRequired", true);
        return GSON.toJson(result);
    }

    protected String buildSmbAclConfigJson(final StorageServiceInstance.PrincipalType principalType, final String password) {
        final JsonObject config = new JsonObject();
        config.addProperty("localAccount", principalType == StorageServiceInstance.PrincipalType.LOCAL_USER);
        config.addProperty("passwordSupplied", principalType == StorageServiceInstance.PrincipalType.LOCAL_USER && StringUtils.isNotBlank(password));
        return GSON.toJson(config);
    }

    protected String buildIdentityDomainConfigJson(final String workgroup) {
        final JsonObject config = new JsonObject();
        config.addProperty("identityProvider", "active_directory");
        config.addProperty("workgroup", StringUtils.isBlank(workgroup) ? "WORKGROUP" : workgroup);
        return GSON.toJson(config);
    }

    protected String buildIscsiTargetConfigJson(final String currentConfig, final String backingPath, final Long lunSizeBytes) {
        final JsonObject config = parseJsonObject(currentConfig);
        config.addProperty("type", "target");
        if (backingPath != null) {
            config.addProperty("backingPath", backingPath);
        }
        if (lunSizeBytes != null) {
            config.addProperty("lunSizeBytes", lunSizeBytes);
        }
        return GSON.toJson(config);
    }

    protected void validateNfsNumericPermissionValue(final String name, final Integer value) {
        if (value < 0 || value > 65535) {
            throw new InvalidParameterValueException(name + " must be between 0 and 65535");
        }
    }

    protected void validateNfsMode(final String mode) {
        if (!mode.matches("^0?[0-7]{3,4}$")) {
            throw new InvalidParameterValueException("NFS export mode must be an octal value such as 0775");
        }
    }

    protected Long getJsonLong(final JsonObject object, final String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return null;
        }
        try {
            return object.get(key).getAsLong();
        } catch (final RuntimeException e) {
            return null;
        }
    }

    protected String getJsonString(final JsonObject object, final String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return null;
        }
        try {
            return object.get(key).getAsString();
        } catch (final RuntimeException e) {
            return null;
        }
    }

    protected JsonObject getJsonObject(final JsonObject object, final String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull() || !object.get(key).isJsonObject()) {
            return null;
        }
        return object.getAsJsonObject(key);
    }

    protected String buildIscsiAclConfigJson(final String currentConfig, final Boolean chapEnabled, final String chapUsername,
            final Boolean mutualChapEnabled, final String mutualChapUsername, final String chapSecret, final String mutualChapSecret) {
        final JsonObject config = parseJsonObject(currentConfig);
        if (chapEnabled != null) {
            config.addProperty("chapEnabled", chapEnabled);
            if (!chapEnabled) {
                config.remove("chapUsername");
            }
        }
        if (chapUsername != null) {
            config.addProperty("chapUsername", chapUsername);
        }
        if (mutualChapEnabled != null) {
            config.addProperty("mutualChapEnabled", mutualChapEnabled);
            if (!mutualChapEnabled) {
                config.remove("mutualChapUsername");
            }
        }
        if (mutualChapUsername != null) {
            config.addProperty("mutualChapUsername", mutualChapUsername);
        }
        if (chapEnabled == null) {
            config.addProperty("chapEnabled", config.has("chapUsername") || StringUtils.isNotBlank(chapSecret));
        }
        if (mutualChapEnabled == null) {
            config.addProperty("mutualChapEnabled", config.has("mutualChapUsername") || StringUtils.isNotBlank(mutualChapSecret));
        }
        return GSON.toJson(config);
    }

    protected String buildNvmeOfHostAclConfigJson(final String currentConfig, final Boolean dhChapEnabled, final Boolean dhChapCtrlEnabled,
            final String dhChapKey, final String dhChapCtrlKey) {
        final JsonObject config = parseJsonObject(currentConfig);
        if (dhChapEnabled != null) {
            config.addProperty("dhChapEnabled", dhChapEnabled);
        } else if (!config.has("dhChapEnabled")) {
            config.addProperty("dhChapEnabled", StringUtils.isNotBlank(dhChapKey));
        }
        if (dhChapCtrlEnabled != null) {
            config.addProperty("dhChapCtrlEnabled", dhChapCtrlEnabled);
        } else if (!config.has("dhChapCtrlEnabled")) {
            config.addProperty("dhChapCtrlEnabled", StringUtils.isNotBlank(dhChapCtrlKey));
        }
        return GSON.toJson(config);
    }

    protected String buildNvmeOfConfigJson(final String currentConfig, final String type, final Boolean allowAnyHost, final String backingPath,
            final String engine, final String transport, final Long namespaceSizeBytes) {
        final JsonObject config = parseJsonObject(currentConfig);
        config.addProperty("type", type);
        if ("subsystem".equals(type) && !config.has("allowAnyHost")) {
            config.addProperty("allowAnyHost", false);
        }
        if (allowAnyHost != null) {
            config.addProperty("allowAnyHost", allowAnyHost);
        }
        if (backingPath != null) {
            config.addProperty("backingPath", backingPath);
        }
        if ("namespace".equals(type) && namespaceSizeBytes != null) {
            config.addProperty("namespaceSizeBytes", namespaceSizeBytes);
        }
        if ("subsystem".equals(type)) {
            final String requestedEngine = StringUtils.defaultIfBlank(engine,
                    config.has("engine") ? config.get("engine").getAsString() : "KERNEL_NVMET").toUpperCase();
            if (!"KERNEL_NVMET".equals(requestedEngine) && !"SPDK".equals(requestedEngine)) {
                throw new InvalidParameterValueException("Unsupported NVMe-oF engine: " + engine);
            }
            config.addProperty("engine", requestedEngine);
            config.addProperty("engineState", "SPDK".equals(requestedEngine) ? "PREPARATION_REQUIRED" : "SUPPORTED");
            config.addProperty("transport", StringUtils.defaultIfBlank(transport,
                    config.has("transport") ? config.get("transport").getAsString() : "tcp"));
        }
        return GSON.toJson(config);
    }

    protected boolean isNvmeOfSubsystem(final StorageBlockTargetVO target) {
        final JsonObject config = parseJsonObject(target.getConfigJson());
        return "subsystem".equals(config.has("type") ? config.get("type").getAsString() : null);
    }

    protected boolean isNvmeOfNamespace(final StorageBlockTargetVO target) {
        final JsonObject config = parseJsonObject(target.getConfigJson());
        return "namespace".equals(config.has("type") ? config.get("type").getAsString() : null);
    }

    protected void updateNvmeOfSubsystemName(final StorageBlockTargetVO subsystem, final String newNqn) {
        final String oldNqn = subsystem.getTargetName();
        subsystem.setTargetName(newNqn);
        for (final StorageBlockTargetVO target : storageBlockTargetDao.listByInstanceIdAndProtocol(subsystem.getInstanceId(), StorageServiceInstance.Protocol.NVME_OF)) {
            if (target.getId() != subsystem.getId() && oldNqn.equals(target.getTargetName())) {
                target.setTargetName(newNqn);
                storageBlockTargetDao.update(target.getId(), target);
            }
        }
    }

    protected Map<Long, String> buildSecretMap(final long ruleId, final String password) {
        if (StringUtils.isBlank(password)) {
            return Collections.emptyMap();
        }
        final Map<Long, String> secrets = new HashMap<>();
        secrets.put(ruleId, password);
        return secrets;
    }

    protected Map<Long, JsonObject> buildChapSecretMap(final long ruleId, final String chapSecret, final String mutualChapSecret) {
        if (StringUtils.isBlank(chapSecret) && StringUtils.isBlank(mutualChapSecret)) {
            return Collections.emptyMap();
        }
        final JsonObject secrets = new JsonObject();
        if (StringUtils.isNotBlank(chapSecret)) {
            secrets.addProperty("chapSecret", chapSecret);
        }
        if (StringUtils.isNotBlank(mutualChapSecret)) {
            secrets.addProperty("mutualChapSecret", mutualChapSecret);
        }
        final Map<Long, JsonObject> secretMap = new HashMap<>();
        secretMap.put(ruleId, secrets);
        return secretMap;
    }

    protected Map<Long, JsonObject> buildNvmeOfSecretMap(final long ruleId, final String dhChapKey, final String dhChapCtrlKey) {
        if (StringUtils.isBlank(dhChapKey) && StringUtils.isBlank(dhChapCtrlKey)) {
            return Collections.emptyMap();
        }
        final JsonObject secrets = new JsonObject();
        if (StringUtils.isNotBlank(dhChapKey)) {
            secrets.addProperty("dhChapKey", dhChapKey);
        }
        if (StringUtils.isNotBlank(dhChapCtrlKey)) {
            secrets.addProperty("dhChapCtrlKey", dhChapCtrlKey);
        }
        final Map<Long, JsonObject> secretMap = new HashMap<>();
        secretMap.put(ruleId, secrets);
        return secretMap;
    }

    protected void ensureSmbProtocol(final StorageServiceInstanceVO instance) {
        ensureProtocol(instance, StorageServiceInstance.Protocol.SMB);
    }

    protected NicVO resolveProtocolListenAddress(final StorageServiceInstanceVO instance, final String listenIp) {
        if (StringUtils.isBlank(listenIp) || "0.0.0.0".equals(listenIp) || "::".equals(listenIp) || instance.getVmId() == null) {
            return null;
        }
        if (!isValidIpv4Address(listenIp)) {
            throw new InvalidParameterValueException("Invalid Storage Service listen IP: " + listenIp);
        }
        final List<NicVO> nics = nicDao.listByVmId(instance.getVmId());
        for (final NicVO nic : nics) {
            if (listenIp.equals(nic.getIPv4Address())) {
                return nic;
            }
            final NicSecondaryIpVO existingForNic = nicSecondaryIpDao.findByIp4AddressAndNicId(listenIp, nic.getId());
            if (existingForNic != null) {
                return nic;
            }
        }

        final NicVO targetNic = findTargetNicForListenAddress(instance, listenIp, nics);
        if (targetNic == null) {
            throw new InvalidParameterValueException("Storage Service listen IP must be in the same CIDR as one of the System VM NICs: " + listenIp);
        }

        final NicVO primaryConflict = nicDao.findByIp4AddressAndNetworkId(listenIp, targetNic.getNetworkId());
        if (primaryConflict != null && primaryConflict.getInstanceId() != instance.getVmId()) {
            throw new InvalidParameterValueException("Storage Service listen IP is already used by another NIC: " + listenIp);
        }
        final NicSecondaryIpVO secondaryConflict = nicSecondaryIpDao.findByIp4AddressAndNetworkId(listenIp, targetNic.getNetworkId());
        if (secondaryConflict != null) {
            if (secondaryConflict.getVmId() == instance.getVmId()) {
                return targetNic;
            }
            throw new InvalidParameterValueException("Storage Service listen IP is already used as a secondary IP: " + listenIp);
        }
        return targetNic;
    }

    protected void registerProtocolListenAddress(final StorageServiceInstanceVO instance, final String listenIp, final NicVO targetNic) {
        if (targetNic == null || StringUtils.isBlank(listenIp) || "0.0.0.0".equals(listenIp) || "::".equals(listenIp) || instance.getVmId() == null) {
            return;
        }
        if (listenIp.equals(targetNic.getIPv4Address())) {
            logger.info("Storage Service listen IP [{}] is already the primary IP on NIC [{}] for instance [{}]; skipping secondary IP registration",
                    listenIp, targetNic.getUuid(), instance.getUuid());
            return;
        }
        if (nicSecondaryIpDao.findByIp4AddressAndNicId(listenIp, targetNic.getId()) != null) {
            return;
        }
        if (!targetNic.getSecondaryIp()) {
            targetNic.setSecondaryIp(true);
            nicDao.update(targetNic.getId(), targetNic);
        }
        nicSecondaryIpDao.persist(new NicSecondaryIpVO(targetNic.getId(), listenIp, instance.getVmId(), instance.getAccountId(), instance.getDomainId(), targetNic.getNetworkId()));
        logger.info("Registered Storage Service listen IP [{}] as a secondary IP on NIC [{}] for instance [{}]", listenIp, targetNic.getUuid(), instance.getUuid());
    }

    protected void rollbackProtocolEnable(final StorageServiceProtocolVO protocolVO, final boolean created, final Boolean previousEnabled,
            final String previousListenIp, final Integer previousPort, final StorageServiceInstance.ResourceState previousState) {
        if (protocolVO == null) {
            return;
        }
        if (created) {
            storageServiceProtocolDao.remove(protocolVO.getId());
            return;
        }
        protocolVO.setEnabled(Boolean.TRUE.equals(previousEnabled));
        protocolVO.setListenIp(previousListenIp);
        protocolVO.setPort(previousPort);
        protocolVO.setState(previousState);
        storageServiceProtocolDao.update(protocolVO.getId(), protocolVO);
    }

    protected NicVO findTargetNicForListenAddress(final StorageServiceInstanceVO instance, final String listenIp, final List<NicVO> nics) {
        for (final NicVO nic : nics) {
            if (StringUtils.isNotBlank(nic.getIPv4Address()) && StringUtils.isNotBlank(nic.getIPv4Netmask()) &&
                    isSameIpv4Network(listenIp, nic.getIPv4Address(), nic.getIPv4Netmask())) {
                return nic;
            }
        }

        for (final NicVO nic : nics) {
            final NetworkVO network = networkDao.findById(nic.getNetworkId());
            if (network == null || StringUtils.isBlank(nic.getIPv4Address())) {
                continue;
            }
            if (isIpv4InCidr(listenIp, network.getNetworkCidr()) && isIpv4InCidr(nic.getIPv4Address(), network.getNetworkCidr())) {
                return nic;
            }
            if (isIpv4InCidr(listenIp, network.getCidr()) && isIpv4InCidr(nic.getIPv4Address(), network.getCidr())) {
                return nic;
            }
        }

        final DataCenterVO zone = dataCenterDao.findById(instance.getDataCenterId());
        final String zoneGuestCidr = zone == null ? null : zone.getGuestNetworkCidr();
        if (StringUtils.isNotBlank(zoneGuestCidr)) {
            for (final NicVO nic : nics) {
                if (StringUtils.isNotBlank(nic.getIPv4Address()) && isIpv4InCidr(listenIp, zoneGuestCidr) && isIpv4InCidr(nic.getIPv4Address(), zoneGuestCidr)) {
                    logger.debug("Resolved Storage Service listen IP [{}] against zone guest CIDR [{}] because NIC [{}] has no usable netmask",
                            listenIp, zoneGuestCidr, nic.getUuid());
                    return nic;
                }
            }
        }
        if (nics.size() == 1) {
            final NicVO nic = nics.get(0);
            logger.debug("Deferring Storage Service listen IP [{}] CIDR validation to the System VM guest because NIC [{}] has no DB CIDR evidence",
                    listenIp, nic.getUuid());
            return nic;
        }
        return null;
    }

    protected boolean isValidIpv4Address(final String value) {
        final String[] parts = StringUtils.split(value, '.');
        if (parts == null || parts.length != 4) {
            return false;
        }
        for (final String part : parts) {
            if (!StringUtils.isNumeric(part)) {
                return false;
            }
            final int number = Integer.parseInt(part);
            if (number < 0 || number > 255) {
                return false;
            }
        }
        return true;
    }

    protected boolean isSameIpv4Network(final String ip, final String baseIp, final String netmask) {
        if (!isValidIpv4Address(ip) || !isValidIpv4Address(baseIp) || !isValidIpv4Address(netmask)) {
            return false;
        }
        final long mask = ipv4ToLong(netmask);
        return (ipv4ToLong(ip) & mask) == (ipv4ToLong(baseIp) & mask);
    }

    protected boolean isIpv4InCidr(final String ip, final String cidr) {
        if (!isValidIpv4Address(ip) || StringUtils.isBlank(cidr)) {
            return false;
        }
        final String[] parts = StringUtils.split(cidr, '/');
        if (parts == null || parts.length != 2 || !isValidIpv4Address(parts[0]) || !StringUtils.isNumeric(parts[1])) {
            return false;
        }
        final int prefix = Integer.parseInt(parts[1]);
        if (prefix < 0 || prefix > 32) {
            return false;
        }
        final long mask = prefix == 0 ? 0L : (0xffffffffL << (32 - prefix)) & 0xffffffffL;
        return (ipv4ToLong(ip) & mask) == (ipv4ToLong(parts[0]) & mask);
    }

    protected long ipv4ToLong(final String value) {
        long result = 0;
        for (final String part : StringUtils.split(value, '.')) {
            result = (result << 8) + Integer.parseInt(part);
        }
        return result & 0xffffffffL;
    }

    protected void ensureProtocol(final StorageServiceInstanceVO instance, final StorageServiceInstance.Protocol protocolType) {
        StorageServiceProtocolVO protocol = storageServiceProtocolDao.findByInstanceIdAndProtocol(instance.getId(), protocolType);
        if (protocol == null) {
            protocol = new StorageServiceProtocolVO(instance.getId(), protocolType, true, null, null);
            protocol.setState(StorageServiceInstance.ResourceState.Ready);
            storageServiceProtocolDao.persist(protocol);
        } else if (!protocol.isEnabled()) {
            protocol.setEnabled(true);
            protocol.setState(StorageServiceInstance.ResourceState.Ready);
            storageServiceProtocolDao.update(protocol.getId(), protocol);
        }
    }

    protected JsonObject parseJsonObject(final String json) {
        if (StringUtils.isBlank(json)) {
            return new JsonObject();
        }
        try {
            return new JsonParser().parse(json).getAsJsonObject();
        } catch (final RuntimeException e) {
            logger.warn("Ignoring invalid Storage Service JSON config", e);
            return new JsonObject();
        }
    }

    protected JsonObject parseJsonObjectStrict(final String json, final String resourceName) {
        if (StringUtils.isBlank(json)) {
            return new JsonObject();
        }
        try {
            return new JsonParser().parse(json).getAsJsonObject();
        } catch (final RuntimeException e) {
            throw new CloudRuntimeException(resourceName + " has invalid Storage Service JSON config; refusing to apply desired state", e);
        }
    }

    protected void validateJsonObjectConfigOrThrow(final String json, final String resourceName) {
        if (!isJsonObjectConfigValid(json)) {
            throw new CloudRuntimeException(resourceName + " generated invalid Storage Service JSON config; refusing to store it");
        }
    }

    protected String buildFileShareErrorConfigJson(final String currentConfig, final RuntimeException failure) {
        final JsonObject config = isJsonObjectConfigValid(currentConfig) ? parseJsonObject(currentConfig) : new JsonObject();
        if (!isJsonObjectConfigValid(currentConfig)) {
            config.addProperty("invalidConfigDiscarded", true);
        }
        final JsonObject error = new JsonObject();
        error.addProperty("type", failure == null ? null : failure.getClass().getName());
        error.addProperty("message", failure == null ? null : failure.getMessage());
        error.addProperty("timestamp", System.currentTimeMillis());
        config.add("lastError", error);
        return GSON.toJson(config);
    }

    protected boolean isApplicableFileShareState(final StorageServiceInstance.ResourceState state) {
        return isApplicableResourceState(state);
    }

    protected boolean isApplicableResourceState(final StorageServiceInstance.ResourceState state) {
        return state == StorageServiceInstance.ResourceState.Ready || state == StorageServiceInstance.ResourceState.Updating;
    }

    protected void validateNfsExportBackingConfig(final StorageFileShareVO share, final JsonObject config) {
        if (share == null || share.getState() == StorageServiceInstance.ResourceState.Disabled ||
                share.getState() == StorageServiceInstance.ResourceState.Destroyed ||
                share.getState() == StorageServiceInstance.ResourceState.Error) {
            return;
        }
        final String backingPath = getJsonString(config, "backingPath");
        final String volumeMountPath = getJsonString(config, "volumeMountPath");
        if (StringUtils.isBlank(backingPath) || StringUtils.isBlank(volumeMountPath)) {
            throw new CloudRuntimeException("NFS export " + share.getUuid() +
                    " has no resolved backing volume path; refusing to expose a root filesystem directory");
        }
        if (!StringUtils.startsWith(backingPath, "/srv/ablestack-storage/volumes/")) {
            throw new CloudRuntimeException("NFS export " + share.getUuid() +
                    " backing path is outside the managed Storage Service volume area: " + backingPath);
        }
        if (!StringUtils.startsWith(backingPath, StringUtils.removeEnd(volumeMountPath, "/") + "/") &&
                !StringUtils.equals(backingPath, StringUtils.removeEnd(volumeMountPath, "/"))) {
            throw new CloudRuntimeException("NFS export " + share.getUuid() +
                    " backing path is not under the selected backing volume mount");
        }
    }

    protected boolean isJsonObjectConfigValid(final String json) {
        if (StringUtils.isBlank(json)) {
            return true;
        }
        try {
            new JsonParser().parse(json).getAsJsonObject();
            return true;
        } catch (final RuntimeException e) {
            return false;
        }
    }

    protected StorageServiceInstanceResponse createInstanceResponse(final StorageServiceInstanceVO instance) {
        final StorageServiceInstanceResponse response = new StorageServiceInstanceResponse();
        response.setId(instance.getUuid());
        response.setName(instance.getName());
        response.setDescription(instance.getDescription());
        final DataCenterVO zone = dataCenterDao.findById(instance.getDataCenterId());
        response.setZoneId(zone == null ? null : zone.getUuid());
        if (instance.getVmId() != null) {
            final VMInstanceVO vm = vmInstanceDao.findById(instance.getVmId());
            response.setVirtualMachineId(vm == null ? null : vm.getUuid());
        }
        if (instance.getServiceOfferingId() != null) {
            final ServiceOfferingVO offering = serviceOfferingDao.findById(instance.getServiceOfferingId());
            response.setServiceOfferingId(offering == null ? null : offering.getUuid());
        }
        response.setProvider(instance.getProvider());
        response.setState(instance.getState().name());
        response.setObjectName("storageserviceinstance");
        return response;
    }

    protected StorageServiceProtocolResponse createProtocolResponse(final StorageServiceProtocolVO protocol) {
        final StorageServiceProtocolResponse response = new StorageServiceProtocolResponse();
        response.setId(protocol.getUuid());
        final StorageServiceInstanceVO instance = storageServiceInstanceDao.findById(protocol.getInstanceId());
        response.setInstanceId(instance == null ? null : instance.getUuid());
        response.setProtocol(protocol.getProtocol().name());
        response.setEnabled(protocol.isEnabled());
        response.setListenIp(protocol.getListenIp());
        response.setPort(protocol.getPort());
        response.setState(protocol.getState().name());
        response.setObjectName("storageserviceprotocol");
        return response;
    }

    protected StorageNfsExportResponse createExportResponse(final StorageFileShareVO share) {
        final StorageNfsExportResponse response = new StorageNfsExportResponse();
        response.setId(share.getUuid());
        final StorageServiceInstanceVO instance = storageServiceInstanceDao.findById(share.getInstanceId());
        response.setInstanceId(instance == null ? null : instance.getUuid());
        response.setName(share.getName());
        response.setPath(share.getPath());
        if (share.getVolumeId() != null) {
            final VolumeVO volume = volumeDao.findById(share.getVolumeId());
            response.setVolumeId(volume == null ? null : volume.getUuid());
        }
        response.setFilesystem(share.getFilesystem());
        response.setQuotaBytes(share.getQuotaBytes());
        response.setState(share.getState().name());
        response.setConfig(share.getConfigJson());
        final boolean configValid = isJsonObjectConfigValid(share.getConfigJson());
        response.setConfigValid(configValid);
        if (!configValid) {
            response.setConfigError("INVALID_JSON_CONFIG");
        }
        final JsonObject config = parseJsonObject(share.getConfigJson());
        response.setEndpointMode(nfsEndpointModeAsString(config, share.getConfigJson()));
        response.setListenIps(nfsSelectedListenIpsAsString(config, share.getConfigJson()));
        response.setObjectName("storagenfsexport");
        return response;
    }

    protected StorageFileShareResponse createFileShareResponse(final StorageFileShareVO share) {
        final StorageFileShareResponse response = new StorageFileShareResponse();
        response.setId(share.getUuid());
        final StorageServiceInstanceVO instance = storageServiceInstanceDao.findById(share.getInstanceId());
        response.setInstanceId(instance == null ? null : instance.getUuid());
        response.setProtocol(share.getProtocol().name());
        response.setName(share.getName());
        response.setPath(share.getPath());
        if (share.getVolumeId() != null) {
            final VolumeVO volume = volumeDao.findById(share.getVolumeId());
            response.setVolumeId(volume == null ? null : volume.getUuid());
        }
        response.setFilesystem(share.getFilesystem());
        response.setQuotaBytes(share.getQuotaBytes());
        response.setState(share.getState().name());
        response.setConfig(share.getConfigJson());
        response.setObjectName("storagefileshare");
        return response;
    }

    protected StorageSmbShareResponse createSmbShareResponse(final StorageFileShareVO share) {
        final StorageSmbShareResponse response = new StorageSmbShareResponse();
        response.setId(share.getUuid());
        final StorageServiceInstanceVO instance = storageServiceInstanceDao.findById(share.getInstanceId());
        response.setInstanceId(instance == null ? null : instance.getUuid());
        response.setName(share.getName());
        response.setPath(share.getPath());
        if (share.getVolumeId() != null) {
            final VolumeVO volume = volumeDao.findById(share.getVolumeId());
            response.setVolumeId(volume == null ? null : volume.getUuid());
        }
        response.setFilesystem(share.getFilesystem());
        response.setQuotaBytes(share.getQuotaBytes());
        response.setState(share.getState().name());
        response.setConfig(share.getConfigJson());
        response.setObjectName("storagesmbshare");
        return response;
    }

    protected StorageIdentityDomainResponse createIdentityDomainResponse(final StorageIdentityDomainVO domain) {
        final StorageIdentityDomainResponse response = new StorageIdentityDomainResponse();
        response.setId(domain.getUuid());
        final StorageServiceInstanceVO instance = storageServiceInstanceDao.findById(domain.getInstanceId());
        response.setInstanceId(instance == null ? null : instance.getUuid());
        response.setDomainName(domain.getDomainName());
        response.setOrganizationalUnit(domain.getOrganizationalUnit());
        response.setDnsServers(domain.getDnsServers());
        response.setJoinState(domain.getJoinState().name());
        response.setHealthState(domain.getHealthState());
        response.setConfig(domain.getConfigJson());
        response.setObjectName("storageidentitydomain");
        return response;
    }

    protected StorageBlockTargetResponse createBlockTargetResponse(final StorageBlockTargetVO target, final String objectName) {
        final StorageBlockTargetResponse response = new StorageBlockTargetResponse();
        final JsonObject config = parseJsonObject(target.getConfigJson());
        final Long configuredSize = getJsonLong(config, "lunSizeBytes");
        Long volumeSize = null;
        response.setId(target.getUuid());
        final StorageServiceInstanceVO instance = storageServiceInstanceDao.findById(target.getInstanceId());
        response.setInstanceId(instance == null ? null : instance.getUuid());
        response.setProtocol(target.getProtocol().name());
        response.setTargetName(target.getTargetName());
        response.setLunOrNamespace(target.getLunOrNamespace());
        if (target.getVolumeId() != null) {
            final VolumeVO volume = volumeDao.findById(target.getVolumeId());
            if (volume != null) {
                response.setVolumeId(volume.getUuid());
                response.setVolumeName(volume.getName());
                volumeSize = volume.getSize();
                response.setVolumeSizeBytes(volumeSize);
            }
        }
        response.setLunSizeBytes(configuredSize);
        response.setEffectiveSizeBytes(configuredSize == null ? volumeSize : configuredSize);
        response.setBackingPath(getJsonString(config, "backingPath"));
        response.setState(target.getState().name());
        response.setConfig(target.getConfigJson());
        response.setObjectName(objectName);
        return response;
    }

    protected StorageAccessRuleResponse createAclResponse(final StorageAccessRuleVO rule) {
        final StorageAccessRuleResponse response = new StorageAccessRuleResponse();
        response.setId(rule.getUuid());
        response.setResourceType(rule.getResourceType().name());
        if (rule.getResourceType() == StorageServiceInstance.AccessResourceType.FILE_SHARE) {
            final StorageFileShareVO share = storageFileShareDao.findById(rule.getResourceId());
            response.setResourceId(share == null ? String.valueOf(rule.getResourceId()) : share.getUuid());
        } else if (rule.getResourceType() == StorageServiceInstance.AccessResourceType.BLOCK_TARGET) {
            final StorageBlockTargetVO target = storageBlockTargetDao.findById(rule.getResourceId());
            response.setResourceId(target == null ? String.valueOf(rule.getResourceId()) : target.getUuid());
        } else {
            response.setResourceId(String.valueOf(rule.getResourceId()));
        }
        response.setPrincipalType(rule.getPrincipalType().name());
        response.setPrincipal(rule.getPrincipal());
        response.setPermission(rule.getPermission().name());
        response.setState(rule.getState().name());
        response.setConfig(rule.getConfigJson());
        response.setObjectName("storageaccessrule");
        return response;
    }

    @Override
    public String getConfigComponentName() {
        return StorageServiceInstance.class.getSimpleName();
    }

    @Override
    public ConfigKey<?>[] getConfigKeys() {
        return new ConfigKey<?>[] {
                StorageServiceInstance.StorageServiceFeatureEnabled,
                StorageServiceInstance.StorageServiceCommandTimeout
        };
    }
}
