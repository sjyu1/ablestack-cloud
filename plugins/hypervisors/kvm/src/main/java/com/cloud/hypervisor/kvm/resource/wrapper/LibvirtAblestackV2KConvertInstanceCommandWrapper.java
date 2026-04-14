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
package com.cloud.hypervisor.kvm.resource.wrapper;

import com.cloud.agent.api.AblestackV2KConvertInstanceCommand;
import com.cloud.agent.api.Answer;
import com.cloud.agent.api.to.DataStoreTO;
import com.cloud.agent.api.to.NfsTO;
import com.cloud.hypervisor.kvm.resource.LibvirtComputingResource;
import com.cloud.hypervisor.kvm.storage.KVMStoragePool;
import com.cloud.hypervisor.kvm.storage.KVMStoragePoolManager;
import com.cloud.resource.CommandWrapper;
import com.cloud.resource.ResourceWrapper;
import com.cloud.utils.script.OutputInterpreter;
import com.cloud.utils.script.Script;
import org.apache.cloudstack.storage.to.PrimaryDataStoreTO;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@ResourceWrapper(handles = AblestackV2KConvertInstanceCommand.class)
public class LibvirtAblestackV2KConvertInstanceCommandWrapper extends CommandWrapper<AblestackV2KConvertInstanceCommand, Answer, LibvirtComputingResource> {

    @Override
    public Answer execute(AblestackV2KConvertInstanceCommand cmd, LibvirtComputingResource serverResource) {
        List<String> missingParams = new ArrayList<>();
        if (StringUtils.isBlank(cmd.getVmName())) {
            missingParams.add("vmName");
        }
        if (StringUtils.isBlank(cmd.getVcenter())) {
            missingParams.add("vcenter");
        }
        if (StringUtils.isBlank(cmd.getUsername())) {
            missingParams.add("username");
        }
        if (StringUtils.isBlank(cmd.getPassword())) {
            missingParams.add("password");
        }
        if (cmd.getTargetStorageLocation() == null) {
            missingParams.add("targetStorageLocation");
        }
        if (StringUtils.isBlank(cmd.getTargetFormat())) {
            missingParams.add("targetFormat");
        }
        if (StringUtils.isBlank(cmd.getTargetStorage())) {
            missingParams.add("targetStorage");
        }
        if (StringUtils.equals(cmd.getTargetStorage(), "rbd") && StringUtils.isBlank(cmd.getTargetMapJson())) {
            missingParams.add("targetMapJson");
        }
        if (!missingParams.isEmpty()) {
            return new Answer(cmd, false, "Missing required parameter(s) for ablestack_v2k command: " + String.join(", ", missingParams));
        }

        final long timeout = (long) cmd.getWait() * 1000;
        final KVMStoragePoolManager storagePoolMgr = serverResource.getStoragePoolMgr();
        final KVMStoragePool targetStoragePool = getTargetStoragePool(cmd.getTargetStorageLocation(), storagePoolMgr);
        final String targetStoragePath = getTargetStoragePath(cmd, targetStoragePool);

        Script script = new Script("ablestack_v2k", timeout, logger);
        script.add("run");
        script.add("--vcenter", cmd.getVcenter());
        script.add("--username", cmd.getUsername());
        script.add("--password", cmd.getPassword());
        script.add("--dst", targetStoragePath);
        script.add("--split", StringUtils.defaultIfBlank(cmd.getSplitMode(), "phase1"));
        script.add("--target-format", cmd.getTargetFormat());
        script.add("--target-storage", cmd.getTargetStorage());
        if (StringUtils.isNotBlank(cmd.getTargetMapJson())) {
            script.add("--target-map-json", cmd.getTargetMapJson());
        }
        script.add("--vm", cmd.getVmName());

        String logPrefix = String.format("(%s) ablestack_v2k run progress", cmd.getVmName());
        OutputInterpreter.LineByLineOutputLogger outputLogger = new OutputInterpreter.LineByLineOutputLogger(logger, logPrefix);
        String result = script.execute(outputLogger);
        int exitValue = script.getExitValue();
        if (exitValue != 0) {
            return new Answer(cmd, false, StringUtils.defaultIfBlank(result,
                    String.format("ablestack_v2k command failed with exit code %d", exitValue)));
        }
        return new Answer(cmd, true, "ablestack_v2k command started successfully");
    }

    private KVMStoragePool getTargetStoragePool(DataStoreTO targetStorageLocation, KVMStoragePoolManager storagePoolMgr) {
        if (targetStorageLocation instanceof NfsTO) {
            NfsTO nfsTO = (NfsTO) targetStorageLocation;
            return storagePoolMgr.getStoragePoolByURI(nfsTO.getUrl());
        }
        PrimaryDataStoreTO primaryDataStoreTO = (PrimaryDataStoreTO) targetStorageLocation;
        return storagePoolMgr.getStoragePool(primaryDataStoreTO.getPoolType(), primaryDataStoreTO.getUuid());
    }

    protected String getTargetStoragePath(AblestackV2KConvertInstanceCommand cmd, KVMStoragePool targetStoragePool) {
        if (StringUtils.equals(cmd.getTargetStorage(), "rbd")) {
            return "/var/lib/libvirt/images" + File.separator + cmd.getVmName();
        }
        return targetStoragePool.getLocalPath();
    }
}
