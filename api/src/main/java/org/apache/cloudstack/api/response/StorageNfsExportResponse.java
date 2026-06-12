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
import org.apache.cloudstack.api.EntityReference;
import org.apache.cloudstack.storage.dataservice.StorageFileShare;

import com.cloud.serializer.Param;
import com.google.gson.annotations.SerializedName;

@EntityReference(value = StorageFileShare.class)
public class StorageNfsExportResponse extends BaseResponse {
    @SerializedName(ApiConstants.ID)
    @Param(description = "ID of the NFS export")
    private String id;

    @SerializedName("instanceid")
    @Param(description = "Storage Service instance ID")
    private String instanceId;

    @SerializedName(ApiConstants.NAME)
    @Param(description = "NFS export name")
    private String name;

    @SerializedName(ApiConstants.PATH)
    @Param(description = "NFS export path inside the Storage Service System VM")
    private String path;

    @SerializedName(ApiConstants.VOLUME_ID)
    @Param(description = "backing volume ID")
    private String volumeId;

    @SerializedName(ApiConstants.FILESYSTEM)
    @Param(description = "filesystem type")
    private String filesystem;

    @SerializedName("quotabytes")
    @Param(description = "export capacity limit in bytes")
    private Long quotaBytes;

    @SerializedName(ApiConstants.STATE)
    @Param(description = "NFS export state")
    private String state;

    @SerializedName("config")
    @Param(description = "NFS export configuration")
    private String config;

    @SerializedName("listenips")
    @Param(description = "comma separated listen IPs that expose this NFS export")
    private String listenIps;

    @SerializedName("listenerports")
    @Param(description = "comma separated NFS listener group ports that expose this NFS export")
    private String listenerPorts;

    @SerializedName("endpointmode")
    @Param(description = "NFS export endpoint exposure mode: ALL or SELECTED")
    private String endpointMode;

    @SerializedName("protocolmode")
    @Param(description = "NFS protocol mode: V4_ONLY or V3V4_DUAL")
    private String protocolMode;

    @SerializedName("configvalid")
    @Param(description = "true if the stored NFS export configuration JSON is valid")
    private Boolean configValid;

    @SerializedName("configerror")
    @Param(description = "stored NFS export configuration error when configvalid is false")
    private String configError;

    public void setId(String id) {
        this.id = id;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public void setVolumeId(String volumeId) {
        this.volumeId = volumeId;
    }

    public void setFilesystem(String filesystem) {
        this.filesystem = filesystem;
    }

    public void setQuotaBytes(Long quotaBytes) {
        this.quotaBytes = quotaBytes;
    }

    public void setState(String state) {
        this.state = state;
    }

    public void setConfig(String config) {
        this.config = config;
    }

    public void setListenIps(String listenIps) {
        this.listenIps = listenIps;
    }

    public void setListenerPorts(String listenerPorts) {
        this.listenerPorts = listenerPorts;
    }

    public void setEndpointMode(String endpointMode) {
        this.endpointMode = endpointMode;
    }

    public void setProtocolMode(String protocolMode) {
        this.protocolMode = protocolMode;
    }

    public void setConfigValid(final Boolean configValid) {
        this.configValid = configValid;
    }

    public void setConfigError(final String configError) {
        this.configError = configError;
    }
}
