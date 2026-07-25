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

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.cloudstack.utils.qemu.QemuCommand;
import org.libvirt.Connect;
import org.libvirt.Domain;
import org.libvirt.DomainInfo.DomainState;
import org.libvirt.LibvirtException;

import com.cloud.agent.api.Answer;
import com.cloud.agent.api.GetVmGuestNetworkStateAnswer;
import com.cloud.agent.api.GetVmGuestNetworkStateCommand;
import com.cloud.agent.api.VmGuestDnsState;
import com.cloud.agent.api.VmGuestNetworkInterface;
import com.cloud.agent.api.VmGuestNetworkSectionStatus;
import com.cloud.agent.api.VmGuestNetworkState;
import com.cloud.hypervisor.kvm.resource.LibvirtComputingResource;
import com.cloud.hypervisor.kvm.resource.QemuGuestDnsFallback;
import com.cloud.hypervisor.kvm.resource.QemuGuestDnsParser;
import com.cloud.hypervisor.kvm.resource.QemuGuestDnsParser.DnsParseResult;
import com.cloud.hypervisor.kvm.resource.QemuGuestNetworkStateParser;
import com.cloud.hypervisor.kvm.resource.QemuGuestNetworkStateParser.RouteParseResult;
import com.cloud.hypervisor.kvm.resource.QemuGuestRouteFallback;
import com.cloud.hypervisor.kvm.resource.QemuGuestRouteFallback.FallbackResult;
import com.cloud.resource.CommandWrapper;
import com.cloud.resource.ResourceWrapper;

@ResourceWrapper(handles = GetVmGuestNetworkStateCommand.class)
public final class LibvirtGetVmGuestNetworkStateCommandWrapper
        extends CommandWrapper<GetVmGuestNetworkStateCommand, Answer, LibvirtComputingResource> {
    private static final String INTERFACES_SECTION = "interfaces";
    private static final String DNS_SECTION = "dns";
    private static final String ROUTES_SECTION = "routes";
    private static final int MAX_SECTION_DETAILS_LENGTH = 255;

    private final QemuGuestNetworkStateParser parser;
    private final QemuGuestRouteFallback routeFallback;
    private final QemuGuestDnsFallback dnsFallback;

    public LibvirtGetVmGuestNetworkStateCommandWrapper() {
        this(new QemuGuestNetworkStateParser(), null, null);
    }

    LibvirtGetVmGuestNetworkStateCommandWrapper(QemuGuestNetworkStateParser parser) {
        this(parser, null, null);
    }

    LibvirtGetVmGuestNetworkStateCommandWrapper(QemuGuestNetworkStateParser parser,
            QemuGuestRouteFallback routeFallback) {
        this(parser, routeFallback, null);
    }

    LibvirtGetVmGuestNetworkStateCommandWrapper(QemuGuestNetworkStateParser parser,
            QemuGuestRouteFallback routeFallback, QemuGuestDnsFallback dnsFallback) {
        this.parser = parser;
        this.routeFallback = routeFallback == null ? new QemuGuestRouteFallback(parser) : routeFallback;
        this.dnsFallback = dnsFallback == null
                ? new QemuGuestDnsFallback(new QemuGuestDnsParser()) : dnsFallback;
    }

    @Override
    public Answer execute(GetVmGuestNetworkStateCommand command, LibvirtComputingResource resource) {
        Map<String, VmGuestNetworkState> states = new LinkedHashMap<>();
        Map<String, String> errors = new LinkedHashMap<>();

        for (String vmName : command.getVmNames()) {
            collectVmState(command, resource, vmName, states, errors);
        }
        return new GetVmGuestNetworkStateAnswer(command, states, errors);
    }

    private void collectVmState(GetVmGuestNetworkStateCommand command, LibvirtComputingResource resource,
            String vmName, Map<String, VmGuestNetworkState> states, Map<String, String> errors) {
        VmGuestNetworkState state = new VmGuestNetworkState(vmName);
        state.setObservedAt(System.currentTimeMillis());
        state.putSectionStatus(DNS_SECTION, new VmGuestNetworkSectionStatus("NOT_DUE"));
        state.putSectionStatus(INTERFACES_SECTION, new VmGuestNetworkSectionStatus("NOT_DUE"));
        state.putSectionStatus(ROUTES_SECTION, new VmGuestNetworkSectionStatus("NOT_DUE"));
        states.put(vmName, state);
        boolean collectInterfaces = command.shouldCollectInterfaces(vmName);
        boolean collectRoutes = command.shouldCollectRoutes(vmName);
        boolean collectDns = command.shouldCollectDns(vmName);
        long collectionStarted = System.nanoTime();
        long interfacesElapsedMs = -1L;
        long routesElapsedMs = -1L;
        long dnsElapsedMs = -1L;
        if (!collectInterfaces && !collectRoutes && !collectDns) {
            state.setStatus("OK");
            return;
        }

        Domain domain = null;
        try {
            LibvirtUtilitiesHelper helper = resource.getLibvirtUtilitiesHelper();
            Connect connection = helper.getConnectionByVmName(vmName);
            domain = resource.getDomain(connection, vmName);
            if (domain == null) {
                recordFailure(state, errors, vmName, collectInterfaces, collectRoutes, collectDns,
                        "VM domain was not found");
                return;
            }
            DomainState domainState = domain.getInfo().state;
            if (domainState != DomainState.VIR_DOMAIN_RUNNING) {
                recordFailure(state, errors, vmName, collectInterfaces, collectRoutes, collectDns,
                        "VM is not running: " + domainState);
                return;
            }

            boolean interfaceCapabilityEnabled = collectInterfaces
                    && command.hasCachedInterfaceCapability(vmName);
            boolean capabilitiesRequired = collectRoutes
                    || (collectDns && command.isExecFallbackEnabled())
                    || (collectInterfaces && !interfaceCapabilityEnabled);
            if (capabilitiesRequired) {
                String guestInfo = domain.qemuAgentCommand(
                        QemuCommand.buildQemuCommand(QemuCommand.AGENT_INFO, null), command.getTimeoutSeconds(), 0);
                interfaceCapabilityEnabled = parser.parseCapabilities(guestInfo, state);
                state.setAgentConnected(true);
            }

            GuestContext guestContext = new GuestContext();
            if (collectInterfaces) {
                long sectionStarted = System.nanoTime();
                collectInterfaces(command, domain, vmName, state, interfaceCapabilityEnabled);
                interfacesElapsedMs = elapsedMillis(sectionStarted);
            }
            if (collectRoutes) {
                long sectionStarted = System.nanoTime();
                collectRoutes(command, domain, state, guestContext);
                routesElapsedMs = elapsedMillis(sectionStarted);
            }
            if (collectDns) {
                long sectionStarted = System.nanoTime();
                collectDns(command, domain, state, guestContext);
                dnsElapsedMs = elapsedMillis(sectionStarted);
            }
            updateOverallStatus(state, collectInterfaces, collectRoutes, collectDns);
            if ("UNAVAILABLE".equals(state.getStatus())) {
                errors.put(vmName, "All requested guest network sections are unavailable");
            }
        } catch (Exception e) {
            recordFailure(state, errors, vmName, collectInterfaces, collectRoutes, collectDns,
                    e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            logger.debug("Unable to collect isolated guest network state for VM [{}]", vmName, e);
        } finally {
            logger.debug("Guest network collection metrics for VM [{}]: status={}, totalMs={}, "
                            + "interfacesMs={}, routesMs={}, dnsMs={}",
                    vmName, state.getStatus(), elapsedMillis(collectionStarted),
                    interfacesElapsedMs, routesElapsedMs, dnsElapsedMs);
            freeDomain(domain, vmName);
        }
    }

    private long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }

    private void collectInterfaces(GetVmGuestNetworkStateCommand command, Domain domain,
            String vmName, VmGuestNetworkState state, boolean capabilityEnabled) {
        if (!capabilityEnabled) {
            state.putSectionStatus(INTERFACES_SECTION,
                    new VmGuestNetworkSectionStatus("UNSUPPORTED",
                            "QGA interface command is disabled or unavailable"));
            return;
        }
        try {
            String interfacesJson = domain.qemuAgentCommand(
                    QemuCommand.buildQemuCommand(QemuCommand.AGENT_NETWORK_GET_INTERFACES, null),
                    command.getTimeoutSeconds(), 0);
            state.setAgentConnected(true);
            List<VmGuestNetworkInterface> interfaces = parser.parseInterfaces(
                    interfacesJson, command.getCloudNicIdsForVm(vmName));
            state.setInterfaces(interfaces);
            state.putSectionStatus(INTERFACES_SECTION,
                    new VmGuestNetworkSectionStatus(interfaces.isEmpty() ? "EMPTY" : "OK",
                            "guest-network-get-interfaces"));
        } catch (Exception e) {
            state.putSectionStatus(INTERFACES_SECTION,
                    new VmGuestNetworkSectionStatus("UNAVAILABLE", message(e)));
        }
    }

    private void collectRoutes(GetVmGuestNetworkStateCommand command, Domain domain,
            VmGuestNetworkState state, GuestContext guestContext) {
        boolean standardRoute = Boolean.TRUE.equals(
                state.getCapabilities().get(QemuCommand.AGENT_NETWORK_GET_ROUTE));
        Exception standardFailure = null;
        if (standardRoute) {
            try {
                String routesJson = domain.qemuAgentCommand(
                        QemuCommand.buildQemuCommand(QemuCommand.AGENT_NETWORK_GET_ROUTE, null),
                        command.getTimeoutSeconds(), 0);
                ensureOutputLimit(routesJson, command.getMaxExecOutputBytes());
                RouteParseResult result = parser.parseRoutes(routesJson);
                state.setAgentConnected(true);
                state.setRoutes(result.getRoutes());
                state.putSectionStatus(ROUTES_SECTION,
                        sectionStatus(result.isTruncated() ? "PARTIAL"
                                        : result.getRoutes().isEmpty() ? "EMPTY" : "OK",
                                "guest-network-get-route", result.isTruncated(), result.getOriginalCount()));
                return;
            } catch (Exception e) {
                standardFailure = e;
            }
        }

        boolean guestExec = Boolean.TRUE.equals(state.getCapabilities().get(QemuCommand.AGENT_EXEC));
        boolean osInfo = Boolean.TRUE.equals(state.getCapabilities().get(QemuCommand.AGENT_GET_OSINFO));
        if (!command.isExecFallbackEnabled() || !guestExec || !osInfo) {
            state.putSectionStatus(ROUTES_SECTION,
                    new VmGuestNetworkSectionStatus(standardFailure == null ? "UNSUPPORTED" : "UNAVAILABLE",
                            standardFailure != null
                                    ? "Standard QGA route collection failed: " + message(standardFailure)
                                    : command.isExecFallbackEnabled()
                                    ? "QGA route, guest-exec, or OS information capability is unavailable"
                                    : "QGA route command is unavailable and guest-exec fallback is disabled"));
            return;
        }
        try {
            FallbackResult result = routeFallback.collect(
                    (request, timeout) -> domain.qemuAgentCommand(request, timeout, 0),
                    getOsId(command, domain, guestContext),
                    command.getTimeoutSeconds(), command.getMaxExecOutputBytes());
            state.setAgentConnected(true);
            state.setRoutes(result.getRoutes());
            state.putSectionStatus(ROUTES_SECTION,
                    sectionStatus(result.isTruncated() ? "PARTIAL"
                                    : result.getRoutes().isEmpty() ? "EMPTY" : "OK",
                            result.getSource(), result.isTruncated(), result.getOriginalCount()));
        } catch (UnsupportedOperationException e) {
            state.putSectionStatus(ROUTES_SECTION,
                    new VmGuestNetworkSectionStatus("UNSUPPORTED", message(e)));
        } catch (Exception e) {
            state.putSectionStatus(ROUTES_SECTION,
                    new VmGuestNetworkSectionStatus("UNAVAILABLE", message(e)));
        }
    }

    private void collectDns(GetVmGuestNetworkStateCommand command, Domain domain,
            VmGuestNetworkState state, GuestContext guestContext) {
        boolean guestExec = Boolean.TRUE.equals(state.getCapabilities().get(QemuCommand.AGENT_EXEC));
        boolean osInfo = Boolean.TRUE.equals(state.getCapabilities().get(QemuCommand.AGENT_GET_OSINFO));
        if (!command.isExecFallbackEnabled() || !guestExec || !osInfo) {
            state.putSectionStatus(DNS_SECTION,
                    new VmGuestNetworkSectionStatus("UNSUPPORTED",
                            command.isExecFallbackEnabled()
                                    ? "QGA guest-exec or OS information capability is unavailable"
                                    : "DNS guest-exec fallback is disabled"));
            return;
        }
        try {
            DnsParseResult result = dnsFallback.collect(
                    (request, timeout) -> domain.qemuAgentCommand(request, timeout, 0),
                    getOsId(command, domain, guestContext),
                    command.getTimeoutSeconds(), command.getMaxExecOutputBytes());
            VmGuestDnsState dns = result.getState();
            state.setAgentConnected(true);
            state.setDns(dns);
            state.putSectionStatus(DNS_SECTION,
                    dnsSectionStatus(result.isTruncated() ? "PARTIAL"
                                    : result.isEmpty() ? "EMPTY" : "OK",
                            dns.getSource(), result.isTruncated(), result.getOriginalCount()));
        } catch (UnsupportedOperationException e) {
            state.putSectionStatus(DNS_SECTION,
                    new VmGuestNetworkSectionStatus("UNSUPPORTED", message(e)));
        } catch (Exception e) {
            state.putSectionStatus(DNS_SECTION,
                    new VmGuestNetworkSectionStatus("UNAVAILABLE", message(e)));
        }
    }

    private String getOsId(GetVmGuestNetworkStateCommand command, Domain domain,
            GuestContext context) throws LibvirtException {
        if (context.osId == null) {
            String osInfoJson = domain.qemuAgentCommand(
                    QemuCommand.buildQemuCommand(QemuCommand.AGENT_GET_OSINFO, null),
                    command.getTimeoutSeconds(), 0);
            context.osId = parser.parseOsId(osInfoJson);
        }
        return context.osId;
    }

    private VmGuestNetworkSectionStatus sectionStatus(String status, String details,
            boolean truncated, int originalCount) {
        String sectionDetails = truncated
                ? details + "; truncated to " + QemuGuestNetworkStateParser.MAX_ROUTES + " routes"
                : details;
        VmGuestNetworkSectionStatus section = new VmGuestNetworkSectionStatus(status, sectionDetails);
        section.setTruncated(truncated);
        section.setOriginalCount(originalCount);
        return section;
    }

    private VmGuestNetworkSectionStatus dnsSectionStatus(String status, String details,
            boolean truncated, int originalCount) {
        String sectionDetails = truncated
                ? details + "; DNS records truncated to bounded limits"
                : details;
        VmGuestNetworkSectionStatus section = new VmGuestNetworkSectionStatus(status, sectionDetails);
        section.setTruncated(truncated);
        section.setOriginalCount(originalCount);
        return section;
    }

    private void updateOverallStatus(VmGuestNetworkState state,
            boolean collectInterfaces, boolean collectRoutes, boolean collectDns) {
        int success = 0;
        int partial = 0;
        int unavailable = 0;
        int requested = 0;
        if (collectInterfaces) {
            requested++;
            String status = state.getSectionStatuses().get(INTERFACES_SECTION).getStatus();
            success += isSuccess(status) ? 1 : 0;
            partial += "PARTIAL".equals(status) ? 1 : 0;
            unavailable += "UNAVAILABLE".equals(status) ? 1 : 0;
        }
        if (collectRoutes) {
            requested++;
            String status = state.getSectionStatuses().get(ROUTES_SECTION).getStatus();
            success += isSuccess(status) ? 1 : 0;
            partial += "PARTIAL".equals(status) ? 1 : 0;
            unavailable += "UNAVAILABLE".equals(status) ? 1 : 0;
        }
        if (collectDns) {
            requested++;
            String status = state.getSectionStatuses().get(DNS_SECTION).getStatus();
            success += isSuccess(status) ? 1 : 0;
            partial += "PARTIAL".equals(status) ? 1 : 0;
            unavailable += "UNAVAILABLE".equals(status) ? 1 : 0;
        }
        if (partial > 0) {
            state.setStatus("PARTIAL");
        } else if (success == requested) {
            state.setStatus("OK");
        } else if (success > 0) {
            state.setStatus("PARTIAL");
        } else if (unavailable > 0) {
            state.setStatus("UNAVAILABLE");
        } else {
            state.setStatus("UNSUPPORTED");
        }
    }

    private boolean isSuccess(String status) {
        return "OK".equals(status) || "EMPTY".equals(status);
    }

    private void ensureOutputLimit(String value, int maxBytes) {
        if (value != null && value.getBytes(StandardCharsets.UTF_8).length > maxBytes) {
            throw new IllegalArgumentException("QGA route output exceeds limit");
        }
    }

    private String message(Exception e) {
        String details = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        if (details.length() <= MAX_SECTION_DETAILS_LENGTH) {
            return details;
        }
        return details.substring(0, MAX_SECTION_DETAILS_LENGTH - 3) + "...";
    }

    private void recordFailure(VmGuestNetworkState state, Map<String, String> errors,
            String vmName, boolean collectInterfaces, boolean collectRoutes,
            boolean collectDns, String details) {
        state.setStatus("UNAVAILABLE");
        if (collectInterfaces) {
            state.putSectionStatus(INTERFACES_SECTION,
                    new VmGuestNetworkSectionStatus("UNAVAILABLE", details));
        }
        if (collectRoutes) {
            state.putSectionStatus(ROUTES_SECTION,
                    new VmGuestNetworkSectionStatus("UNAVAILABLE", details));
        }
        if (collectDns) {
            state.putSectionStatus(DNS_SECTION,
                    new VmGuestNetworkSectionStatus("UNAVAILABLE", details));
        }
        errors.put(vmName, details);
    }

    private static final class GuestContext {
        private String osId;
    }

    private void freeDomain(Domain domain, String vmName) {
        if (domain == null) {
            return;
        }
        try {
            domain.free();
        } catch (LibvirtException e) {
            logger.trace("Ignoring domain free failure for VM [{}]", vmName, e);
        }
    }
}
