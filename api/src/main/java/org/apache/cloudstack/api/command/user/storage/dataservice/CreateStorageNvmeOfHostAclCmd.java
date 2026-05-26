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

package org.apache.cloudstack.api.command.user.storage.dataservice;

import javax.inject.Inject;

import org.apache.cloudstack.acl.RoleType;
import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.ApiErrorCode;
import org.apache.cloudstack.api.BaseAsyncCmd;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.ServerApiException;
import org.apache.cloudstack.api.command.user.UserCmd;
import org.apache.cloudstack.api.response.StorageAccessRuleResponse;
import org.apache.cloudstack.api.response.StorageBlockTargetResponse;
import org.apache.cloudstack.storage.dataservice.StorageService;

@APICommand(name = "createStorageNvmeOfHostAcl",
        responseObject = StorageAccessRuleResponse.class,
        description = "Creates an NVMe-oF host ACL.",
        requestHasSensitiveInfo = false,
        responseHasSensitiveInfo = false,
        since = "4.21.0",
        authorized = {RoleType.Admin, RoleType.ResourceAdmin, RoleType.DomainAdmin, RoleType.User})
public class CreateStorageNvmeOfHostAclCmd extends BaseAsyncCmd implements UserCmd {
    @Inject
    StorageService storageService;

    @Parameter(name = "subsystemid", type = CommandType.UUID, entityType = StorageBlockTargetResponse.class, required = true, description = "NVMe-oF subsystem ID")
    private Long subsystemId;

    @Parameter(name = "hostnqn", type = CommandType.STRING, required = true, description = "NVMe host NQN")
    private String hostNqn;

    public Long getSubsystemId() {
        return subsystemId;
    }

    public String getHostNqn() {
        return hostNqn;
    }

    @Override
    public long getEntityOwnerId() {
        return 0;
    }

    @Override
    public String getEventType() {
        return "STORAGE.NVMEOF.HOSTACL.CREATE";
    }

    @Override
    public String getEventDescription() {
        return "Creating Storage Service NVMe-oF host ACL for subsystem " + subsystemId;
    }

    @Override
    public void execute() {
        StorageAccessRuleResponse response = storageService.createStorageNvmeOfHostAcl(this);
        if (response == null) {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, "Failed to create NVMe-oF host ACL");
        }
        response.setResponseName(getCommandName());
        setResponseObject(response);
    }
}
