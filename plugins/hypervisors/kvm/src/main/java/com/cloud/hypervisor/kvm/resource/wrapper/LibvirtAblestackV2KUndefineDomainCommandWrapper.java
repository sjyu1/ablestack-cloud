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

import com.cloud.agent.api.AblestackV2KUndefineDomainCommand;
import com.cloud.agent.api.Answer;
import com.cloud.hypervisor.kvm.resource.LibvirtComputingResource;
import com.cloud.resource.CommandWrapper;
import com.cloud.resource.ResourceWrapper;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.utils.script.Script;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;

@ResourceWrapper(handles = AblestackV2KUndefineDomainCommand.class)
public class LibvirtAblestackV2KUndefineDomainCommandWrapper extends CommandWrapper<AblestackV2KUndefineDomainCommand, Answer, LibvirtComputingResource> {
    private static final String ABLESTACK_V2K_WORKDIR_BASE = "/var/lib/ablestack-v2k";

    @Override
    public Answer execute(AblestackV2KUndefineDomainCommand cmd, LibvirtComputingResource serverResource) {
        if (StringUtils.isBlank(cmd.getDomainName())) {
            return new Answer(cmd, false, "Missing domain name for ablestack-v2k undefine command");
        }

        final long timeout = (long) Math.max(cmd.getWait(), 30) * 1000;
        Script script = new Script("virsh", timeout, logger);
        script.add("undefine");
        if (cmd.isRemoveNvram()) {
            script.add("--nvram");
        }
        script.add("--domain", cmd.getDomainName());

        String result = script.execute();
        int exitValue = script.getExitValue();
        String details = StringUtils.defaultIfBlank(result, String.format("Failed to undefine domain %s (exit=%d)", cmd.getDomainName(), exitValue));
        boolean undefined = exitValue == 0 || StringUtils.containsIgnoreCase(details, "domain not found");
        String domainResultMessage = exitValue == 0
                ? String.format("Undefined domain %s", cmd.getDomainName())
                : String.format("Domain %s is already undefined", cmd.getDomainName());

        try {
            String cleanupMessage = cleanupAblestackV2KWorkdir(cmd.getDomainName());
            if (undefined) {
                return new Answer(cmd, true, String.format("%s; %s", domainResultMessage, cleanupMessage));
            }
            return new Answer(cmd, false, String.format("%s; %s", details, cleanupMessage));
        } catch (CloudRuntimeException e) {
            if (undefined) {
                return new Answer(cmd, false, String.format("%s; %s", domainResultMessage, e.getMessage()));
            }
            return new Answer(cmd, false, String.format("%s; %s", details, e.getMessage()));
        }
    }

    private String cleanupAblestackV2KWorkdir(String domainName) {
        File baseDir = new File(ABLESTACK_V2K_WORKDIR_BASE);
        File vmWorkdir = new File(baseDir, domainName);
        try {
            String baseCanonicalPath = baseDir.getCanonicalPath();
            String vmCanonicalPath = vmWorkdir.getCanonicalPath();
            if (!StringUtils.startsWith(vmCanonicalPath, baseCanonicalPath + File.separator)) {
                throw new CloudRuntimeException(String.format("Invalid ablestack-v2k workdir path for domain %s: %s", domainName, vmCanonicalPath));
            }
            if (!vmWorkdir.exists()) {
                return String.format("Workdir %s does not exist", vmCanonicalPath);
            }
            FileUtils.deleteDirectory(vmWorkdir);
            return String.format("Removed workdir %s", vmCanonicalPath);
        } catch (IOException e) {
            throw new CloudRuntimeException(String.format("Failed to remove ablestack-v2k workdir for domain %s", domainName), e);
        }
    }
}
