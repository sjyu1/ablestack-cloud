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

package org.apache.cloudstack.storage.dataservice;

import org.apache.cloudstack.api.command.user.storage.dataservice.CreateStorageNfsAclCmd;
import org.apache.cloudstack.api.command.user.storage.dataservice.CreateStorageNfsExportCmd;
import org.apache.cloudstack.api.command.user.storage.dataservice.CreateStorageServiceInstanceCmd;
import org.apache.cloudstack.api.command.user.storage.dataservice.DeleteStorageNfsAclCmd;
import org.apache.cloudstack.api.command.user.storage.dataservice.DeleteStorageNfsExportCmd;
import org.apache.cloudstack.api.command.user.storage.dataservice.EnableStorageServiceProtocolCmd;
import org.apache.cloudstack.api.command.user.storage.dataservice.ListStorageNfsAclsCmd;
import org.apache.cloudstack.api.command.user.storage.dataservice.ListStorageNfsExportsCmd;
import org.apache.cloudstack.api.command.user.storage.dataservice.ListStorageServiceInstancesCmd;
import org.apache.cloudstack.api.command.user.storage.dataservice.UpdateStorageNfsAclCmd;
import org.apache.cloudstack.api.command.user.storage.dataservice.UpdateStorageNfsExportCmd;
import org.apache.cloudstack.api.response.ListResponse;
import org.apache.cloudstack.api.response.StorageAccessRuleResponse;
import org.apache.cloudstack.api.response.StorageNfsExportResponse;
import org.apache.cloudstack.api.response.StorageServiceInstanceResponse;
import org.apache.cloudstack.api.response.StorageServiceProtocolResponse;

public interface StorageService {
    StorageServiceInstanceResponse createStorageServiceInstance(CreateStorageServiceInstanceCmd cmd);

    ListResponse<StorageServiceInstanceResponse> listStorageServiceInstances(ListStorageServiceInstancesCmd cmd);

    StorageServiceProtocolResponse enableStorageServiceProtocol(EnableStorageServiceProtocolCmd cmd);

    StorageNfsExportResponse createStorageNfsExport(CreateStorageNfsExportCmd cmd);

    StorageNfsExportResponse updateStorageNfsExport(UpdateStorageNfsExportCmd cmd);

    boolean deleteStorageNfsExport(DeleteStorageNfsExportCmd cmd);

    ListResponse<StorageNfsExportResponse> listStorageNfsExports(ListStorageNfsExportsCmd cmd);

    StorageAccessRuleResponse createStorageNfsAcl(CreateStorageNfsAclCmd cmd);

    StorageAccessRuleResponse updateStorageNfsAcl(UpdateStorageNfsAclCmd cmd);

    boolean deleteStorageNfsAcl(DeleteStorageNfsAclCmd cmd);

    ListResponse<StorageAccessRuleResponse> listStorageNfsAcls(ListStorageNfsAclsCmd cmd);
}
