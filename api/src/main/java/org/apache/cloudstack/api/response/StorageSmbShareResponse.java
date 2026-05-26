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

public class StorageSmbShareResponse extends BaseResponse {
    @SerializedName(ApiConstants.ID)
    @Param(description = "ID of the SMB share")
    private String id;

    @SerializedName("instanceid")
    @Param(description = "Storage Service instance ID")
    private String instanceId;

    @SerializedName(ApiConstants.NAME)
    @Param(description = "SMB share name")
    private String name;

    @SerializedName(ApiConstants.PATH)
    @Param(description = "SMB share path inside the Storage Service System VM")
    private String path;

    @SerializedName(ApiConstants.VOLUME_ID)
    @Param(description = "backing volume ID")
    private String volumeId;

    @SerializedName(ApiConstants.FILESYSTEM)
    @Param(description = "filesystem type")
    private String filesystem;

    @SerializedName("quotabytes")
    @Param(description = "share capacity limit in bytes")
    private Long quotaBytes;

    @SerializedName(ApiConstants.STATE)
    @Param(description = "SMB share state")
    private String state;

    @SerializedName("config")
    @Param(description = "SMB share configuration")
    private String config;

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
}
