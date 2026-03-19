package com.cloud.rack;

import org.apache.cloudstack.api.command.admin.rack.ListRackLayoutsCmd;
import org.apache.cloudstack.api.command.admin.rack.UpdateRackLayoutCmd;
import org.apache.cloudstack.api.response.ListResponse;
import org.apache.cloudstack.api.response.RackLayoutResponse;

import com.cloud.utils.component.PluggableService;

public interface RackLayoutService extends PluggableService {
    ListResponse<RackLayoutResponse> listRackLayouts(ListRackLayoutsCmd cmd);
    RackLayoutResponse updateRackLayout(UpdateRackLayoutCmd cmd);
}