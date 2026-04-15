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
package com.cloud.agent.api;

import com.cloud.agent.api.to.DataStoreTO;

public class AblestackV2KConvertInstanceCommand extends Command {

    private String vmName;
    private String vcenter;
    private String username;
    private String password;
    private DataStoreTO targetStorageLocation;
    private String splitMode;
    private String targetFormat;
    private String targetStorage;
    private String targetMapJson;

    public AblestackV2KConvertInstanceCommand() {
    }

    public AblestackV2KConvertInstanceCommand(String vmName, String vcenter, String username, String password,
                                              DataStoreTO targetStorageLocation, String splitMode) {
        this.vmName = vmName;
        this.vcenter = vcenter;
        this.username = username;
        this.password = password;
        this.targetStorageLocation = targetStorageLocation;
        this.splitMode = splitMode;
    }

    public String getVmName() {
        return vmName;
    }

    public String getVcenter() {
        return vcenter;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public DataStoreTO getTargetStorageLocation() {
        return targetStorageLocation;
    }

    public String getSplitMode() {
        return splitMode;
    }

    public String getTargetFormat() {
        return targetFormat;
    }

    public void setTargetFormat(String targetFormat) {
        this.targetFormat = targetFormat;
    }

    public String getTargetStorage() {
        return targetStorage;
    }

    public void setTargetStorage(String targetStorage) {
        this.targetStorage = targetStorage;
    }

    public String getTargetMapJson() {
        return targetMapJson;
    }

    public void setTargetMapJson(String targetMapJson) {
        this.targetMapJson = targetMapJson;
    }

    @Override
    public boolean executeInSequence() {
        return false;
    }
}
