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
import org.apache.cloudstack.storage.dataservice.StorageBlockTarget;

import com.cloud.serializer.Param;
import com.google.gson.annotations.SerializedName;

@EntityReference(value = StorageBlockTarget.class)
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

    @SerializedName("volumename")
    @Param(description = "backing volume name")
    private String volumeName;

    @SerializedName("volumesizebytes")
    @Param(description = "backing volume size in bytes")
    private Long volumeSizeBytes;

    @SerializedName("lunsizebytes")
    @Param(description = "configured LUN or namespace size in bytes")
    private Long lunSizeBytes;

    @SerializedName("effectivesizebytes")
    @Param(description = "effective LUN or namespace size in bytes")
    private Long effectiveSizeBytes;

    @SerializedName("backingpath")
    @Param(description = "configured backing path inside the Storage Service System VM")
    private String backingPath;

    @SerializedName("endpointmode")
    @Param(description = "endpoint exposure mode")
    private String endpointMode;

    @SerializedName("listenerports")
    @Param(description = "comma-separated listener port groups")
    private String listenerPorts;

    @SerializedName("endpoints")
    @Param(description = "effective listener endpoints")
    private String endpoints;

    @SerializedName("targetgroupkey")
    @Param(description = "target group key, such as iSCSI target IQN")
    private String targetGroupKey;

    @SerializedName("targetluns")
    @Param(description = "comma-separated LUNs or namespaces that share the same target group")
    private String targetLuns;

    @SerializedName("targetluncount")
    @Param(description = "number of LUNs or namespaces that share the same target group")
    private Integer targetLunCount;

    @SerializedName("aclcount")
    @Param(description = "number of access rules that apply to the target group")
    private Integer aclCount;

    @SerializedName("backstoretype")
    @Param(description = "runtime LIO backstore type")
    private String backstoreType;

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

    public void setVolumeName(final String volumeName) {
        this.volumeName = volumeName;
    }

    public void setVolumeSizeBytes(final Long volumeSizeBytes) {
        this.volumeSizeBytes = volumeSizeBytes;
    }

    public void setLunSizeBytes(final Long lunSizeBytes) {
        this.lunSizeBytes = lunSizeBytes;
    }

    public void setEffectiveSizeBytes(final Long effectiveSizeBytes) {
        this.effectiveSizeBytes = effectiveSizeBytes;
    }

    public void setBackingPath(final String backingPath) {
        this.backingPath = backingPath;
    }

    public void setEndpointMode(final String endpointMode) {
        this.endpointMode = endpointMode;
    }

    public void setListenerPorts(final String listenerPorts) {
        this.listenerPorts = listenerPorts;
    }

    public void setEndpoints(final String endpoints) {
        this.endpoints = endpoints;
    }

    public void setTargetGroupKey(final String targetGroupKey) {
        this.targetGroupKey = targetGroupKey;
    }

    public void setTargetLuns(final String targetLuns) {
        this.targetLuns = targetLuns;
    }

    public void setTargetLunCount(final Integer targetLunCount) {
        this.targetLunCount = targetLunCount;
    }

    public void setAclCount(final Integer aclCount) {
        this.aclCount = aclCount;
    }

    public void setBackstoreType(final String backstoreType) {
        this.backstoreType = backstoreType;
    }

    public void setState(final String state) {
        this.state = state;
    }

    public void setConfig(final String config) {
        this.config = config;
    }
}
