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

import com.cloud.agent.api.AblestackV2KStatusAnswer;
import com.cloud.agent.api.AblestackV2KStatusCommand;
import com.cloud.agent.api.Answer;
import com.cloud.hypervisor.kvm.resource.LibvirtComputingResource;
import com.cloud.resource.CommandWrapper;
import com.cloud.resource.ResourceWrapper;
import com.cloud.utils.script.OutputInterpreter;
import com.cloud.utils.script.Script;
import org.apache.commons.lang3.StringUtils;

@ResourceWrapper(handles = AblestackV2KStatusCommand.class)
public class LibvirtAblestackV2KStatusCommandWrapper extends CommandWrapper<AblestackV2KStatusCommand, Answer, LibvirtComputingResource> {

    @Override
    public Answer execute(AblestackV2KStatusCommand cmd, LibvirtComputingResource serverResource) {
        if (StringUtils.isBlank(cmd.getVmName())) {
            return new AblestackV2KStatusAnswer(cmd, false, "Missing vm name for ablestack_v2k status command");
        }

        final long timeout = (long) cmd.getWait() * 1000;
        Script script = new Script("ablestack_v2k", timeout, logger);
        script.add("status");
        script.add("--vm", cmd.getVmName());

        OutputInterpreter.AllLinesParser parser = new OutputInterpreter.AllLinesParser();
        String result = script.execute(parser);
        int exitValue = script.getExitValue();
        if (exitValue != 0) {
            return new AblestackV2KStatusAnswer(cmd, false,
                    StringUtils.defaultIfBlank(result, parser.getLines()));
        }

        String output = StringUtils.defaultIfBlank(parser.getLines(), StringUtils.defaultString(result));
        String[] lines = output.split("\\r?\\n");
        String dataLine = null;
        for (String line : lines) {
            String trimmedLine = StringUtils.trimToEmpty(line);
            if (StringUtils.isBlank(trimmedLine)) {
                continue;
            }
            if (trimmedLine.startsWith("VM ") || trimmedLine.startsWith("-")) {
                continue;
            }
            dataLine = trimmedLine;
        }

        if (StringUtils.isBlank(dataLine)) {
            return new AblestackV2KStatusAnswer(cmd, false, "Unable to parse ablestack_v2k status output: no data line found");
        }

        String[] columns = dataLine.split("\\s{2,}");
        if (columns.length < 6) {
            return new AblestackV2KStatusAnswer(cmd, false,
                    String.format("Unable to parse ablestack_v2k status output for VM %s", cmd.getVmName()));
        }

        String phase = columns[1];
        String migrationState = columns[2];
        String migrationStep = columns[3];
        String syncPhysical = columns[4];
        String workdir = String.join(" ", java.util.Arrays.copyOfRange(columns, 5, columns.length));

        return new AblestackV2KStatusAnswer(cmd, true, "OK", phase, migrationState, migrationStep, syncPhysical, workdir);
    }
}
