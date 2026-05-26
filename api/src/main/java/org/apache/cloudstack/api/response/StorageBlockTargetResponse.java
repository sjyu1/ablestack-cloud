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

public class StorageBlockTargetResponse extends BaseResponse {
    @SerializedName(ApiConstants.ID)
    @Param(description = "the ID of the block target")
    private String id;

    @SerializedName("instanceid")
    @Param(description = "the ID of the Storage Service instance")
    private String instanceId;

    @SerializedName("protocol")
    @Param(description = "block protocol")
    private String protocol;

    @SerializedName("targetname")
    @Param(description = "iSCSI target IQN or NVMe-oF subsystem NQN")
    private String targetName;

    @SerializedName("lunornamespace")
    @Param(description = "iSCSI LUN ID or NVMe-oF namespace ID")
    private String lunOrNamespace;

    @SerializedName(ApiConstants.VOLUME_ID)
    @Param(description = "backing volume ID")
    private String volumeId;

    @SerializedName(ApiConstants.STATE)
    @Param(description = "target state")
    private String state;

    @SerializedName("config")
    @Param(description = "protocol-specific configuration JSON")
    private String config;

    public void setId(final String id) {
        this.id = id;
    }

    public void setInstanceId(final String instanceId) {
        this.instanceId = instanceId;
    }

    public void setProtocol(final String protocol) {
        this.protocol = protocol;
    }

    public void setTargetName(final String targetName) {
        this.targetName = targetName;
    }

    public void setLunOrNamespace(final String lunOrNamespace) {
        this.lunOrNamespace = lunOrNamespace;
    }

    public void setVolumeId(final String volumeId) {
        this.volumeId = volumeId;
    }

    public void setState(final String state) {
        this.state = state;
    }

    public void setConfig(final String config) {
        this.config = config;
    }
}
