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

package com.cloud.network.as;

import java.util.Date;
import java.util.UUID;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

import org.apache.cloudstack.api.Identity;
import org.apache.cloudstack.api.InternalIdentity;

import com.cloud.network.Network;
import com.cloud.utils.db.GenericDao;
import org.apache.cloudstack.utils.reflectiontostringbuilderutils.ReflectionToStringBuilderUtils;

@Entity
@Table(name = "counter")
public class CounterVO implements Counter, Identity, InternalIdentity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private long id;

    @Column(name = "source")
    @Enumerated(value = EnumType.STRING)
    private Source source;

    @Column(name = "name")
    private String name;

    @Column(name = "value")
    private String value;

    @Column(name = "uuid")
    private String uuid;

    @Column(name = GenericDao.REMOVED_COLUMN)
    Date removed;

    @Column(name = GenericDao.CREATED_COLUMN)
    Date created;

    @Column(name = "provider")
    private String provider;

    public CounterVO() {
    }

    public CounterVO(Source source, String name, String value, Network.Provider provider) {
        this.source = source;
        this.name = name;
        this.value = value;
        this.uuid = UUID.randomUUID().toString();
        this.provider = provider.getName();
    }

    @Override
    public String toString() {
        return String.format("Counter %s",
                ReflectionToStringBuilderUtils.reflectOnlySelectedFields(
                        this, "id", "uuid", "name"));
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getValue() {
        return value;
    }

    @Override
    public Source getSource() {
        return source;
    }

    @Override
    public long getId() {
        return id;
    }

    @Override
    public String getUuid() {
        return this.uuid;
    }

    public Date getRemoved() {
        return removed;
    }

    public Date getCreated() {
        return created;
    }

    @Override
    public String getProvider() {
        return provider;
    }
}
