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

public class StorageFileShareResponse extends BaseResponse {
    @SerializedName(ApiConstants.ID)
    @Param(description = "ID of the Storage Service file share")
    private String id;

    @SerializedName("instanceid")
    @Param(description = "Storage Service instance ID")
    private String instanceId;

    @SerializedName("protocol")
    @Param(description = "file service protocol")
    private String protocol;

    @SerializedName(ApiConstants.NAME)
    @Param(description = "file share name")
    private String name;

    @SerializedName(ApiConstants.PATH)
    @Param(description = "file share path inside the Storage Service System VM")
    private String path;

    @SerializedName(ApiConstants.VOLUME_ID)
    @Param(description = "backing volume ID")
    private String volumeId;

    @SerializedName(ApiConstants.FILESYSTEM)
    @Param(description = "filesystem type")
    private String filesystem;

    @SerializedName("quotabytes")
    @Param(description = "file share capacity limit in bytes")
    private Long quotaBytes;

    @SerializedName(ApiConstants.STATE)
    @Param(description = "file share state")
    private String state;

    @SerializedName("config")
    @Param(description = "file share configuration")
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

    public void setName(final String name) {
        this.name = name;
    }

    public void setPath(final String path) {
        this.path = path;
    }

    public void setVolumeId(final String volumeId) {
        this.volumeId = volumeId;
    }

    public void setFilesystem(final String filesystem) {
        this.filesystem = filesystem;
    }

    public void setQuotaBytes(final Long quotaBytes) {
        this.quotaBytes = quotaBytes;
    }

    public void setState(final String state) {
        this.state = state;
    }

    public void setConfig(final String config) {
        this.config = config;
    }
}
