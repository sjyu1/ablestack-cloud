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

public class StorageServiceProtocolResponse extends BaseResponse {
    @SerializedName(ApiConstants.ID)
    @Param(description = "ID of the Storage Service protocol")
    private String id;

    @SerializedName("instanceid")
    @Param(description = "Storage Service instance ID")
    private String instanceId;

    @SerializedName("protocol")
    @Param(description = "storage protocol")
    private String protocol;

    @SerializedName("enabled")
    @Param(description = "whether the protocol is enabled")
    private Boolean enabled;

    @SerializedName("listenip")
    @Param(description = "protocol listen IP")
    private String listenIp;

    @SerializedName("port")
    @Param(description = "protocol port")
    private Integer port;

    @SerializedName(ApiConstants.STATE)
    @Param(description = "protocol state")
    private String state;

    @SerializedName("protocolmode")
    @Param(description = "NFS protocol mode when protocol is NFS")
    private String protocolMode;

    @SerializedName("config")
    @Param(description = "protocol configuration JSON")
    private String config;

    public void setId(String id) {
        this.id = id;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public void setListenIp(String listenIp) {
        this.listenIp = listenIp;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public void setState(String state) {
        this.state = state;
    }

    public void setProtocolMode(String protocolMode) {
        this.protocolMode = protocolMode;
    }

    public void setConfig(String config) {
        this.config = config;
    }
}
