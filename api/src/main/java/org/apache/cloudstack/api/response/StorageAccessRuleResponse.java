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

package org.apache.cloudstack.api.response;

import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.BaseResponse;

import com.cloud.serializer.Param;
import com.google.gson.annotations.SerializedName;

public class StorageAccessRuleResponse extends BaseResponse {
    @SerializedName(ApiConstants.ID)
    @Param(description = "ID of the access rule")
    private String id;

    @SerializedName("resourcetype")
    @Param(description = "access rule resource type")
    private String resourceType;

    @SerializedName("resourceid")
    @Param(description = "access rule resource ID")
    private String resourceId;

    @SerializedName("principaltype")
    @Param(description = "principal type")
    private String principalType;

    @SerializedName("principal")
    @Param(description = "principal value")
    private String principal;

    @SerializedName("permission")
    @Param(description = "access permission")
    private String permission;

    @SerializedName(ApiConstants.STATE)
    @Param(description = "access rule state")
    private String state;

    @SerializedName("config")
    @Param(description = "access rule configuration")
    private String config;

    public void setId(String id) {
        this.id = id;
    }

    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }

    public void setResourceId(String resourceId) {
        this.resourceId = resourceId;
    }

    public void setPrincipalType(String principalType) {
        this.principalType = principalType;
    }

    public void setPrincipal(String principal) {
        this.principal = principal;
    }

    public void setPermission(String permission) {
        this.permission = permission;
    }

    public void setState(String state) {
        this.state = state;
    }

    public void setConfig(String config) {
        this.config = config;
    }
}
