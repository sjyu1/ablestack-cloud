//
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
//

package com.cloud.hypervisor.kvm.resource.wrapper;

import java.util.HashMap;
import java.util.List;

import org.apache.log4j.Logger;
import org.libvirt.Connect;
import org.libvirt.LibvirtException;

import com.cloud.agent.api.Answer;
import com.cloud.agent.api.GetVmDiskStatsAnswer;
import com.cloud.agent.api.GetVmDiskStatsCommand;
import com.cloud.agent.api.VmDiskStatsEntry;
import com.cloud.hypervisor.kvm.resource.LibvirtComputingResource;
import com.cloud.resource.CommandWrapper;
import com.cloud.resource.ResourceWrapper;

@ResourceWrapper(handles =  GetVmDiskStatsCommand.class)
public final class LibvirtGetVmDiskStatsCommandWrapper extends CommandWrapper<GetVmDiskStatsCommand, Answer, LibvirtComputingResource> {

    private static final Logger s_logger = Logger.getLogger(LibvirtGetVmDiskStatsCommandWrapper.class);

    @Override
    public Answer execute(final GetVmDiskStatsCommand command, final LibvirtComputingResource libvirtComputingResource) {
        final List<String> vmNames = command.getVmNames();
        final LibvirtUtilitiesHelper libvirtUtilitiesHelper = libvirtComputingResource.getLibvirtUtilitiesHelper();

        try {
            final HashMap<String, List<VmDiskStatsEntry>> vmDiskStatsNameMap = new HashMap<String, List<VmDiskStatsEntry>>();
            final Connect conn = libvirtUtilitiesHelper.getConnection();
            for (final String vmName : vmNames) {
                final List<VmDiskStatsEntry> statEntry = libvirtComputingResource.getVmDiskStat(conn, vmName);
                if (statEntry == null) {
                    continue;
                }

                vmDiskStatsNameMap.put(vmName, statEntry);
            }
            return new GetVmDiskStatsAnswer(command, "", command.getHostName(), vmDiskStatsNameMap);
        } catch (final LibvirtException e) {
            s_logger.debug("Can't get vm disk stats: " + e.toString());
            return new GetVmDiskStatsAnswer(command, null, null, null);
        }
    }
}