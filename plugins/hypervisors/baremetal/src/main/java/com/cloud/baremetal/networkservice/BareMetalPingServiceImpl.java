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
// Automatically generated by addcopyright.py at 01/29/2013
// Apache License, Version 2.0 (the "License"); you may not use this
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//
// Automatically generated by addcopyright.py at 04/03/2012
package com.cloud.baremetal.networkservice;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import org.apache.cloudstack.api.AddBaremetalPxeCmd;
import org.apache.cloudstack.api.AddBaremetalPxePingServerCmd;
import org.apache.cloudstack.api.ListBaremetalPxeServersCmd;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.cloud.agent.api.Answer;
import com.cloud.agent.api.baremetal.IpmISetBootDevCommand;
import com.cloud.agent.api.baremetal.IpmISetBootDevCommand.BootDev;
import com.cloud.agent.api.baremetal.PrepareCreateTemplateCommand;
import com.cloud.agent.api.baremetal.PreparePxeServerAnswer;
import com.cloud.agent.api.baremetal.PreparePxeServerCommand;
import com.cloud.baremetal.database.BaremetalPxeDao;
import com.cloud.baremetal.database.BaremetalPxeVO;
import com.cloud.baremetal.networkservice.BaremetalPxeManager.BaremetalPxeType;
import com.cloud.dc.DataCenterVO;
import com.cloud.dc.HostPodVO;
import com.cloud.deploy.DeployDestination;
import com.cloud.host.Host;
import com.cloud.host.HostVO;
import com.cloud.host.dao.HostDetailsDao;
import com.cloud.network.Network;
import com.cloud.network.PhysicalNetworkServiceProvider;
import com.cloud.network.dao.PhysicalNetworkDao;
import com.cloud.network.dao.PhysicalNetworkServiceProviderDao;
import com.cloud.network.dao.PhysicalNetworkServiceProviderVO;
import com.cloud.network.dao.PhysicalNetworkVO;
import com.cloud.resource.ResourceManager;
import com.cloud.resource.ServerResource;
import com.cloud.uservm.UserVm;
import com.cloud.utils.db.DB;
import com.cloud.utils.db.QueryBuilder;
import com.cloud.utils.db.SearchCriteria.Op;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.vm.NicProfile;
import com.cloud.vm.NicVO;
import com.cloud.vm.ReservationContext;
import com.cloud.vm.VirtualMachineProfile;

public class BareMetalPingServiceImpl extends BareMetalPxeServiceBase implements BaremetalPxeService {
    protected static Logger logger = LogManager.getLogger(BareMetalPingServiceImpl.class);
    @Inject
    ResourceManager _resourceMgr;
    @Inject
    PhysicalNetworkDao _physicalNetworkDao;
    @Inject
    PhysicalNetworkServiceProviderDao _physicalNetworkServiceProviderDao;
    @Inject
    HostDetailsDao _hostDetailsDao;
    @Inject
    BaremetalPxeDao _pxeDao;

    @Override
    public boolean prepare(VirtualMachineProfile profile, NicProfile pxeNic, Network network, DeployDestination dest, ReservationContext context) {
        QueryBuilder<BaremetalPxeVO> sc = QueryBuilder.create(BaremetalPxeVO.class);
        sc.and(sc.entity().getDeviceType(), Op.EQ, BaremetalPxeType.PING.toString());
        sc.and(sc.entity().getPodId(), Op.EQ, dest.getPod().getId());
        BaremetalPxeVO pxeVo = sc.find();
        if (pxeVo == null) {
            throw new CloudRuntimeException("No PING PXE server found in pod: " + dest.getPod().getId() + ", you need to add it before starting VM");
        }
        long pxeServerId = pxeVo.getHostId();

        String mac = pxeNic.getMacAddress();
        String ip = pxeNic.getIPv4Address();
        String gateway = pxeNic.getIPv4Gateway();
        String mask = pxeNic.getIPv4Netmask();
        String dns = pxeNic.getIPv4Dns1();
        if (dns == null) {
            dns = pxeNic.getIPv4Dns2();
        }

        try {
            String tpl = profile.getTemplate().getUrl();
            assert tpl != null : "How can a null template get here!!!";
            PreparePxeServerCommand cmd =
                new PreparePxeServerCommand(ip, mac, mask, gateway, dns, tpl, profile.getVirtualMachine().getInstanceName(), dest.getHost().getName());
            PreparePxeServerAnswer ans = (PreparePxeServerAnswer)_agentMgr.send(pxeServerId, cmd);
            if (!ans.getResult()) {
                logger.warn("Unable tot program PXE server: " + pxeVo.getId() + " because " + ans.getDetails());
                return false;
            }

            IpmISetBootDevCommand bootCmd = new IpmISetBootDevCommand(BootDev.pxe);
            Answer anw = _agentMgr.send(dest.getHost().getId(), bootCmd);
            if (!anw.getResult()) {
                logger.warn("Unable to set host: " + dest.getHost().getId() + " to PXE boot because " + anw.getDetails());
            }

            return anw.getResult();
        } catch (Exception e) {
            logger.warn("Cannot prepare PXE server", e);
            return false;
        }
    }

    @Override
    public boolean prepareCreateTemplate(Long pxeServerId, UserVm vm, String templateUrl) {
        List<NicVO> nics = _nicDao.listByVmId(vm.getId());
        if (nics.size() != 1) {
            throw new CloudRuntimeException("Wrong nic number " + nics.size() + " of vm " + vm.getId());
        }

        /* use last host id when VM stopped */
        Long hostId = (vm.getHostId() == null ? vm.getLastHostId() : vm.getHostId());
        HostVO host = _hostDao.findById(hostId);
        DataCenterVO dc = _dcDao.findById(host.getDataCenterId());
        NicVO nic = nics.get(0);
        String mask = nic.getIPv4Netmask();
        String mac = nic.getMacAddress();
        String ip = nic.getIPv4Address();
        String gateway = nic.getIPv4Gateway();
        String dns = dc.getDns1();
        if (dns == null) {
            dns = dc.getDns2();
        }

        try {
            PrepareCreateTemplateCommand cmd = new PrepareCreateTemplateCommand(ip, mac, mask, gateway, dns, templateUrl);
            Answer ans = _agentMgr.send(pxeServerId, cmd);
            return ans.getResult();
        } catch (Exception e) {
            logger.debug("Prepare for creating baremetal template failed", e);
            return false;
        }
    }

    @Override
    @DB
    public BaremetalPxeVO addPxeServer(AddBaremetalPxeCmd cmd) {
        AddBaremetalPxePingServerCmd pcmd = (AddBaremetalPxePingServerCmd)cmd;

        PhysicalNetworkVO pNetwork = null;
        long zoneId;

        if (cmd.getPhysicalNetworkId() == null || cmd.getUrl() == null || cmd.getUsername() == null || cmd.getPassword() == null) {
            throw new IllegalArgumentException("At least one of the required parameters(physical network id, url, username, password) is null");
        }

        pNetwork = _physicalNetworkDao.findById(cmd.getPhysicalNetworkId());
        if (pNetwork == null) {
            throw new IllegalArgumentException("Could not find phyical network with ID: " + cmd.getPhysicalNetworkId());
        }
        zoneId = pNetwork.getDataCenterId();

        PhysicalNetworkServiceProviderVO ntwkSvcProvider =
            _physicalNetworkServiceProviderDao.findByServiceProvider(pNetwork.getId(), BaremetalPxeManager.BAREMETAL_PXE_SERVICE_PROVIDER.getName());
        if (ntwkSvcProvider == null) {
            throw new CloudRuntimeException("Network Service Provider: " + BaremetalPxeManager.BAREMETAL_PXE_SERVICE_PROVIDER.getName() +
                " is not enabled in the physical network: " + cmd.getPhysicalNetworkId() + "to add this device");
        } else if (ntwkSvcProvider.getState() == PhysicalNetworkServiceProvider.State.Shutdown) {
            throw new CloudRuntimeException("Network Service Provider: " + ntwkSvcProvider.getProviderName() + " is in shutdown state in the physical network: " +
                cmd.getPhysicalNetworkId() + "to add this device");
        }

        HostPodVO pod = _podDao.findById(cmd.getPodId());
        if (pod == null) {
            throw new IllegalArgumentException("Could not find pod with ID: " + cmd.getPodId());
        }

        List<HostVO> pxes = _resourceMgr.listAllUpAndEnabledHosts(Host.Type.BaremetalPxe, null, cmd.getPodId(), zoneId);
        if (pxes.size() != 0) {
            throw new IllegalArgumentException("Already had a PXE server in Pod: " + cmd.getPodId() + " zone: " + zoneId);
        }

        String storageServerIp = pcmd.getPingStorageServerIp();
        if (storageServerIp == null) {
            throw new IllegalArgumentException("No IP for storage server specified");
        }
        String pingDir = pcmd.getPingDir();
        if (pingDir == null) {
            throw new IllegalArgumentException("No direcotry for storage server specified");
        }
        String tftpDir = pcmd.getTftpDir();
        if (tftpDir == null) {
            throw new IllegalArgumentException("No TFTP directory specified");
        }

        String cifsUsername = pcmd.getPingStorageServerUserName();
        if (cifsUsername == null || cifsUsername.equalsIgnoreCase("")) {
            cifsUsername = "xxx";
        }
        String cifsPassword = pcmd.getPingStorageServerPassword();
        if (cifsPassword == null || cifsPassword.equalsIgnoreCase("")) {
            cifsPassword = "xxx";
        }

        URI uri;
        try {
            uri = new URI(cmd.getUrl());
        } catch (Exception e) {
            logger.debug(e);
            throw new IllegalArgumentException(e.getMessage());
        }
        String ipAddress = uri.getHost();

        String guid = getPxeServerGuid(Long.toString(zoneId) + "-" + pod.getId(), BaremetalPxeType.PING.toString(), ipAddress);

        ServerResource resource = null;
        Map params = new HashMap<String, String>();
        params.put(BaremetalPxeService.PXE_PARAM_ZONE, Long.toString(zoneId));
        params.put(BaremetalPxeService.PXE_PARAM_POD, String.valueOf(pod.getId()));
        params.put(BaremetalPxeService.PXE_PARAM_IP, ipAddress);
        params.put(BaremetalPxeService.PXE_PARAM_USERNAME, cmd.getUsername());
        params.put(BaremetalPxeService.PXE_PARAM_PASSWORD, cmd.getPassword());
        params.put(BaremetalPxeService.PXE_PARAM_PING_STORAGE_SERVER_IP, storageServerIp);
        params.put(BaremetalPxeService.PXE_PARAM_PING_ROOT_DIR, pingDir);
        params.put(BaremetalPxeService.PXE_PARAM_TFTP_DIR, tftpDir);
        params.put(BaremetalPxeService.PXE_PARAM_PING_STORAGE_SERVER_USERNAME, cifsUsername);
        params.put(BaremetalPxeService.PXE_PARAM_PING_STORAGE_SERVER_PASSWORD, cifsPassword);
        params.put(BaremetalPxeService.PXE_PARAM_GUID, guid);

        resource = new BaremetalPingPxeResource();
        try {
            resource.configure("PING PXE resource", params);
        } catch (Exception e) {
            logger.debug(e);
            throw new CloudRuntimeException(e.getMessage());
        }

        Host pxeServer = _resourceMgr.addHost(zoneId, resource, Host.Type.BaremetalPxe, params);
        if (pxeServer == null) {
            throw new CloudRuntimeException("Cannot add PXE server as a host");
        }

        BaremetalPxeVO vo = new BaremetalPxeVO();
        vo.setHostId(pxeServer.getId());
        vo.setNetworkServiceProviderId(ntwkSvcProvider.getId());
        vo.setPodId(pod.getId());
        vo.setPhysicalNetworkId(pcmd.getPhysicalNetworkId());
        vo.setDeviceType(BaremetalPxeType.PING.toString());
        _pxeDao.persist(vo);
        return vo;
    }

    @Override
    public BaremetalPxeResponse getApiResponse(BaremetalPxeVO vo) {
        return null;
    }

    @Override
    public List<BaremetalPxeResponse> listPxeServers(ListBaremetalPxeServersCmd cmd) {
        return null;
    }

    @Override
    public String getPxeServiceType() {
        return BaremetalPxeManager.BaremetalPxeType.PING.toString();
    }
}
