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
// specific language govening permissions and limitations
// under the License.

package org.apache.cloudstack.storage.dataservice;

import java.util.UUID;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "storage_access_rule")
public class StorageAccessRuleVO {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private long id;

    @Column(name = "uuid")
    private String uuid = UUID.randomUUID().toString();

    @Column(name = "resource_type")
    @Enumerated(value = EnumType.STRING)
    private StorageServiceInstance.AccessResourceType resourceType;

    @Column(name = "resource_id")
    private long resourceId;

    @Column(name = "principal_type")
    @Enumerated(value = EnumType.STRING)
    private StorageServiceInstance.PrincipalType principalType;

    @Column(name = "principal")
    private String principal;

    @Column(name = "permission")
    @Enumerated(value = EnumType.STRING)
    private StorageServiceInstance.Permission permission;

    @Column(name = "secret_ref")
    private String secretRef;

    @Column(name = "state")
    @Enumerated(value = EnumType.STRING)
    private StorageServiceInstance.ResourceState state = StorageServiceInstance.ResourceState.Allocated;

    @Column(name = "config_json")
    private String configJson;

    public long getId() {
        retun id;
    }

    public String getUuid() {
        retun uuid;
    }
}
