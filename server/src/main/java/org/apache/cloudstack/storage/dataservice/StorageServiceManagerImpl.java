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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import org.apache.cloudstack.api.ApiCommandResourceType;
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
import org.apache.cloudstack.api.command.user.storage.dataservice.ListStorageServiceInstancesCmd;
import org.apache.cloudstack.api.command.user.storage.dataservice.ListStorageSmbAclsCmd;
import org.apache.cloudstack.api.command.user.storage.dataservice.ListStorageSmbSharesCmd;
import org.apache.cloudstack.api.command.user.storage.dataservice.UpdateStorageIscsiAclCmd;
import org.apache.cloudstack.api.command.user.storage.dataservice.UpdateStorageIscsiTargetCmd;
import org.apache.cloudstack.api.command.user.storage.dataservice.UpdateStorageNfsAclCmd;
import org.apache.cloudstack.api.command.user.storage.dataservice.UpdateStorageNfsExportCmd;
import org.apache.cloudstack.api.command.user.storage.dataservice.UpdateStorageNvmeOfSubsystemCmd;
import org.apache.cloudstack.api.command.user.storage.dataservice.UpdateStorageSmbAclCmd;
import org.apache.cloudstack.api.command.user.storage.dataservice.UpdateStorageSmbShareCmd;
import org.apache.cloudstack.api.response.ListResponse;
import org.apache.cloudstack.api.response.StorageAccessRuleResponse;
import org.apache.cloudstack.api.response.StorageBlockTargetResponse;
import org.apache.cloudstack.api.response.StorageIdentityDomainResponse;
import org.apache.cloudstack.api.response.StorageNfsExportResponse;
import org.apache.cloudstack.api.response.StorageServiceInstanceResponse;
import org.apache.cloudstack.api.response.StorageServiceProtocolResponse;
import org.apache.cloudstack.api.response.StorageSmbShareResponse;
import org.apache.cloudstack.context.CallContext;
import org.apache.cloudstack.framework.config.ConfigKey;
import org.apache.cloudstack.framework.config.Configurable;
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
import com.cloud.service.ServiceOfferingVO;
import com.cloud.service.dao.ServiceOfferingDao;
import com.cloud.storage.VolumeVO;
import com.cloud.storage.dao.VolumeDao;
import com.cloud.user.Account;
import com.cloud.user.dao.AccountDao;
import com.cloud.utils.component.ManagerBase;
import com.cloud.utils.component.PluggableService;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.vm.VMInstanceVO;
import com.cloud.vm.dao.VMInstanceDao;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class StorageServiceManagerImpl extends ManagerBase implements StorageService, PluggableService, Configurable {
    private static final Gson GSON = new Gson();

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
    private ServiceOfferingDao serviceOfferingDao;
    @Inject
    private VMInstanceDao vmInstanceDao;
    @Inject
    private VolumeDao volumeDao;

    @Override
    public List<Class<?>> getCommands() {
        final List<Class<?>> commands = new ArrayList<>();
        if (!StorageServiceInstance.StorageServiceFeatureEnabled.value()) {
            return commands;
        }
        commands.add(CreateStorageServiceInstanceCmd.class);
        commands.add(ListStorageServiceInstancesCmd.class);
        commands.add(EnableStorageServiceProtocolCmd.class);
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
        StorageServiceProtocolVO protocolVO = storageServiceProtocolDao.findByInstanceIdAndProtocol(instance.getId(), protocol);
        if (protocolVO == null) {
            protocolVO = new StorageServiceProtocolVO(instance.getId(), protocol, true, cmd.getListenIp(), cmd.getPort());
            protocolVO.setState(StorageServiceInstance.ResourceState.Ready);
            protocolVO = storageServiceProtocolDao.persist(protocolVO);
        } else {
            protocolVO.setEnabled(true);
            protocolVO.setListenIp(cmd.getListenIp());
            protocolVO.setPort(cmd.getPort());
            protocolVO.setState(StorageServiceInstance.ResourceState.Ready);
            storageServiceProtocolDao.update(protocolVO.getId(), protocolVO);
        }

        if (protocol == StorageServiceInstance.Protocol.NFS) {
            applyNfsDesiredState(instance);
        } else if (protocol == StorageServiceInstance.Protocol.SMB) {
            applySmbDesiredState(instance);
        } else if (protocol == StorageServiceInstance.Protocol.ISCSI) {
            applyIscsiDesiredState(instance);
        } else if (protocol == StorageServiceInstance.Protocol.NVME_OF) {
            applyNvmeOfDesiredState(instance);
        }
        return createProtocolResponse(protocolVO);
    }

    @Override
    public StorageNfsExportResponse createStorageNfsExport(final CreateStorageNfsExportCmd cmd) {
        final StorageServiceInstanceVO instance = requireInstance(cmd.getInstanceId());
        validateVolume(cmd.getVolumeId());
        StorageFileShareVO share = new StorageFileShareVO(instance.getId(), StorageServiceInstance.Protocol.NFS, cmd.getName(), cmd.getPath(),
                cmd.getVolumeId(), cmd.getFilesystem(), cmd.getQuotaBytes(), StorageServiceInstance.ResourceState.Creating,
                buildNfsConfigJson(null, cmd.getReadOnly(), cmd.getRootSquash(), cmd.getSync(), cmd.getSecure()));
        share = storageFileShareDao.persist(share);
        if (StringUtils.isBlank(share.getPath())) {
            share.setPath("/srv/ablestack-storage/nfs/" + share.getUuid());
        }
        share.setState(instance.getVmId() == null ? StorageServiceInstance.ResourceState.Allocated : StorageServiceInstance.ResourceState.Ready);
        storageFileShareDao.update(share.getId(), share);
        applyNfsDesiredState(instance);
        return createExportResponse(share);
    }

    @Override
    public StorageNfsExportResponse updateStorageNfsExport(final UpdateStorageNfsExportCmd cmd) {
        final StorageFileShareVO share = requireNfsExport(cmd.getId());
        final StorageServiceInstanceVO instance = requireInstance(share.getInstanceId());
        if (cmd.getName() != null) {
            share.setName(cmd.getName());
        }
        if (cmd.getPath() != null) {
            share.setPath(cmd.getPath());
        }
        if (cmd.getVolumeId() != null) {
            validateVolume(cmd.getVolumeId());
            share.setVolumeId(cmd.getVolumeId());
        }
        if (cmd.getFilesystem() != null) {
            share.setFilesystem(cmd.getFilesystem());
        }
        if (cmd.getQuotaBytes() != null) {
            share.setQuotaBytes(cmd.getQuotaBytes());
        }
        share.setConfigJson(buildNfsConfigJson(share.getConfigJson(), cmd.getReadOnly(), cmd.getRootSquash(), cmd.getSync(), cmd.getSecure()));
        share.setState(StorageServiceInstance.ResourceState.Updating);
        storageFileShareDao.update(share.getId(), share);
        applyNfsDesiredState(instance);
        share.setState(instance.getVmId() == null ? StorageServiceInstance.ResourceState.Allocated : StorageServiceInstance.ResourceState.Ready);
        storageFileShareDao.update(share.getId(), share);
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
        StorageAccessRuleVO rule = new StorageAccessRuleVO(StorageServiceInstance.AccessResourceType.FILE_SHARE, share.getId(),
                principalType, cmd.getPrincipal(), permission, StorageServiceInstance.ResourceState.Creating,
                buildNfsConfigJson(null, null, cmd.getRootSquash(), cmd.getSync(), cmd.getSecure()));
        rule = storageAccessRuleDao.persist(rule);
        rule.setState(instance.getVmId() == null ? StorageServiceInstance.ResourceState.Allocated : StorageServiceInstance.ResourceState.Ready);
        storageAccessRuleDao.update(rule.getId(), rule);
        applyNfsDesiredState(instance);
        return createAclResponse(rule);
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
        rule.setConfigJson(buildNfsConfigJson(rule.getConfigJson(), null, cmd.getRootSquash(), cmd.getSync(), cmd.getSecure()));
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
        if (cmd.getId() != null) {
            final StorageAccessRuleVO rule = storageAccessRuleDao.findById(cmd.getId());
            if (rule != null) {
                rules.add(rule);
            }
        } else if (cmd.getExportId() != null) {
            rules.addAll(storageAccessRuleDao.listByResource(StorageServiceInstance.AccessResourceType.FILE_SHARE, cmd.getExportId()));
        } else {
            rules.addAll(storageAccessRuleDao.listAll());
        }

        final List<StorageAccessRuleResponse> responses = new ArrayList<>();
        for (final StorageAccessRuleVO rule : rules) {
            if (rule.getResourceType() != StorageServiceInstance.AccessResourceType.FILE_SHARE) {
                continue;
            }
            responses.add(createAclResponse(rule));
        }
        final ListResponse<StorageAccessRuleResponse> response = new ListResponse<>();
        response.setResponses(responses, responses.size());
        return response;
    }

    @Override
    public StorageSmbShareResponse createStorageSmbShare(final CreateStorageSmbShareCmd cmd) {
        final StorageServiceInstanceVO instance = requireInstance(cmd.getInstanceId());
        validateVolume(cmd.getVolumeId());
        StorageFileShareVO share = new StorageFileShareVO(instance.getId(), StorageServiceInstance.Protocol.SMB, cmd.getName(), cmd.getPath(),
                cmd.getVolumeId(), cmd.getFilesystem(), cmd.getQuotaBytes(), StorageServiceInstance.ResourceState.Creating,
                buildSmbConfigJson(null, cmd.getReadOnly(), cmd.getBrowseable(), cmd.getGuestOk()));
        share = storageFileShareDao.persist(share);
        if (StringUtils.isBlank(share.getPath())) {
            share.setPath("/srv/ablestack-storage/smb/" + share.getUuid());
        }
        share.setState(instance.getVmId() == null ? StorageServiceInstance.ResourceState.Allocated : StorageServiceInstance.ResourceState.Ready);
        storageFileShareDao.update(share.getId(), share);
        applySmbDesiredState(instance);
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
            share.setPath(cmd.getPath());
        }
        if (cmd.getVolumeId() != null) {
            validateVolume(cmd.getVolumeId());
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
        applySmbDesiredState(instance);
        share.setState(instance.getVmId() == null ? StorageServiceInstance.ResourceState.Allocated : StorageServiceInstance.ResourceState.Ready);
        storageFileShareDao.update(share.getId(), share);
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
        applySmbDesiredState(instance, buildSecretMap(rule.getId(), cmd.getPassword()));
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
        applySmbDesiredState(instance, buildSecretMap(rule.getId(), cmd.getPassword()));
        rule.setState(instance.getVmId() == null ? StorageServiceInstance.ResourceState.Allocated : StorageServiceInstance.ResourceState.Ready);
        storageAccessRuleDao.update(rule.getId(), rule);
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
    public StorageBlockTargetResponse createStorageIscsiTarget(final CreateStorageIscsiTargetCmd cmd) {
        final StorageServiceInstanceVO instance = requireInstance(cmd.getInstanceId());
        validateVolume(cmd.getVolumeId());
        ensureProtocol(instance, StorageServiceInstance.Protocol.ISCSI);
        StorageBlockTargetVO target = new StorageBlockTargetVO(instance.getId(), StorageServiceInstance.Protocol.ISCSI, cmd.getTargetName(),
                StringUtils.defaultIfBlank(cmd.getLun(), "0"), cmd.getVolumeId(), StorageServiceInstance.ResourceState.Creating,
                buildIscsiTargetConfigJson(null, cmd.getBackingPath()));
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
            validateVolume(cmd.getVolumeId());
            target.setVolumeId(cmd.getVolumeId());
        }
        target.setConfigJson(buildIscsiTargetConfigJson(target.getConfigJson(), cmd.getBackingPath()));
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
                buildIscsiAclConfigJson(null, cmd.getChapUsername(), cmd.getMutualChapUsername(), cmd.getChapSecret(), cmd.getMutualChapSecret()));
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
        rule.setConfigJson(buildIscsiAclConfigJson(rule.getConfigJson(), cmd.getChapUsername(), cmd.getMutualChapUsername(), cmd.getChapSecret(), cmd.getMutualChapSecret()));
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
                null, null, StorageServiceInstance.ResourceState.Creating, buildNvmeOfConfigJson(null, "subsystem", cmd.getAllowAnyHost(), null));
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
        subsystem.setConfigJson(buildNvmeOfConfigJson(subsystem.getConfigJson(), "subsystem", cmd.getAllowAnyHost(), null));
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
        validateVolume(cmd.getVolumeId());
        StorageBlockTargetVO namespace = new StorageBlockTargetVO(instance.getId(), StorageServiceInstance.Protocol.NVME_OF, subsystem.getTargetName(),
                StringUtils.defaultIfBlank(cmd.getNamespaceId(), "1"), cmd.getVolumeId(), StorageServiceInstance.ResourceState.Creating,
                buildNvmeOfConfigJson(null, "namespace", null, cmd.getBackingPath()));
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
                StorageServiceInstance.ResourceState.Creating, null);
        rule = storageAccessRuleDao.persist(rule);
        rule.setState(instance.getVmId() == null ? StorageServiceInstance.ResourceState.Allocated : StorageServiceInstance.ResourceState.Ready);
        storageAccessRuleDao.update(rule.getId(), rule);
        applyNvmeOfDesiredState(instance);
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

        final JsonArray exports = new JsonArray();
        for (final StorageFileShareVO share : storageFileShareDao.listByInstanceIdAndProtocol(instance.getId(), StorageServiceInstance.Protocol.NFS)) {
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
            export.add("config", parseJsonObject(share.getConfigJson()));

            final JsonArray acls = new JsonArray();
            for (final StorageAccessRuleVO rule : storageAccessRuleDao.listByResource(StorageServiceInstance.AccessResourceType.FILE_SHARE, share.getId())) {
                final JsonObject acl = new JsonObject();
                acl.addProperty("id", rule.getId());
                acl.addProperty("uuid", rule.getUuid());
                acl.addProperty("principalType", rule.getPrincipalType().name());
                acl.addProperty("principal", rule.getPrincipal());
                acl.addProperty("permission", rule.getPermission().name());
                acl.addProperty("state", rule.getState().name());
                acl.add("config", parseJsonObject(rule.getConfigJson()));
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
                Collections.singleton("chapSecret")));
        if (!result.isSuccess()) {
            throw new CloudRuntimeException("Failed to apply iSCSI desired state on Storage Service System VM: " + result.getDetails());
        }
    }

    protected void applyNvmeOfDesiredState(final StorageServiceInstanceVO instance) {
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
                    hosts.add(createBlockAclJson(rule));
                }
            }
            subsystem.add("namespaces", namespaces);
            subsystem.add("hosts", hosts);
            subsystems.add(subsystem);
        }
        payload.add("subsystems", subsystems);

        final StorageServiceGuestCommandResult result = guestCommandDispatcher.dispatch(new StorageServiceGuestCommand(instance.getVmId(),
                "nvmeof subsystem apply", GSON.toJson(payload), StorageServiceInstance.StorageServiceCommandTimeout.value(), Collections.emptySet()));
        if (!result.isSuccess()) {
            throw new CloudRuntimeException("Failed to apply NVMe-oF desired state on Storage Service System VM: " + result.getDetails());
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
        targetJson.addProperty("id", target.getId());
        targetJson.addProperty("uuid", target.getUuid());
        targetJson.addProperty("protocol", target.getProtocol().name());
        targetJson.addProperty("targetName", target.getTargetName());
        targetJson.addProperty("lunOrNamespace", target.getLunOrNamespace());
        if (target.getVolumeId() != null) {
            targetJson.addProperty("volumeId", target.getVolumeId());
        }
        targetJson.addProperty("state", target.getState().name());
        targetJson.add("config", parseJsonObject(target.getConfigJson()));
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
        if (volumeId != null && volumeDao.findById(volumeId) == null) {
            throw new InvalidParameterValueException("Unable to find volume with id " + volumeId);
        }
    }

    protected StorageServiceInstance.Protocol parseProtocol(final String protocol) {
        try {
            return StorageServiceInstance.Protocol.valueOf(protocol.toUpperCase());
        } catch (final IllegalArgumentException e) {
            throw new InvalidParameterValueException("Invalid Storage Service protocol: " + protocol);
        }
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
            final Boolean sync, final Boolean secure) {
        final JsonObject config = parseJsonObject(currentConfig);
        if (!config.has("readOnly")) {
            config.addProperty("readOnly", false);
        }
        if (!config.has("rootSquash")) {
            config.addProperty("rootSquash", true);
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
        if (sync != null) {
            config.addProperty("sync", sync);
        }
        if (secure != null) {
            config.addProperty("secure", secure);
        }
        return GSON.toJson(config);
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

    protected String buildIscsiTargetConfigJson(final String currentConfig, final String backingPath) {
        final JsonObject config = parseJsonObject(currentConfig);
        config.addProperty("type", "target");
        if (backingPath != null) {
            config.addProperty("backingPath", backingPath);
        }
        return GSON.toJson(config);
    }

    protected String buildIscsiAclConfigJson(final String currentConfig, final String chapUsername, final String mutualChapUsername,
            final String chapSecret, final String mutualChapSecret) {
        final JsonObject config = parseJsonObject(currentConfig);
        if (chapUsername != null) {
            config.addProperty("chapUsername", chapUsername);
        }
        if (mutualChapUsername != null) {
            config.addProperty("mutualChapUsername", mutualChapUsername);
        }
        config.addProperty("chapEnabled", config.has("chapUsername") || StringUtils.isNotBlank(chapSecret));
        config.addProperty("mutualChapEnabled", config.has("mutualChapUsername") || StringUtils.isNotBlank(mutualChapSecret));
        return GSON.toJson(config);
    }

    protected String buildNvmeOfConfigJson(final String currentConfig, final String type, final Boolean allowAnyHost, final String backingPath) {
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

    protected void ensureSmbProtocol(final StorageServiceInstanceVO instance) {
        ensureProtocol(instance, StorageServiceInstance.Protocol.SMB);
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
        response.setObjectName("storagenfsexport");
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
        response.setId(target.getUuid());
        final StorageServiceInstanceVO instance = storageServiceInstanceDao.findById(target.getInstanceId());
        response.setInstanceId(instance == null ? null : instance.getUuid());
        response.setProtocol(target.getProtocol().name());
        response.setTargetName(target.getTargetName());
        response.setLunOrNamespace(target.getLunOrNamespace());
        if (target.getVolumeId() != null) {
            final VolumeVO volume = volumeDao.findById(target.getVolumeId());
            response.setVolumeId(volume == null ? null : volume.getUuid());
        }
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
