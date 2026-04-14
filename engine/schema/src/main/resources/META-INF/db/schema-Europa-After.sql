-- Licensed to the Apache Software Foundation (ASF) under one
-- or more contributor license agreements.  See the NOTICE file
-- distributed with this work for additional information
-- regarding copyright ownership.  The ASF licenses this file
-- to you under the Apache License, Version 2.0 (the
-- "License"); you may not use this file except in compliance
-- with the License.  You may obtain a copy of the License at
--
--   http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing,
-- software distributed under the License is distributed on an
-- "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
-- KIND, either express or implied.  See the License for the
-- specific language governing permissions and limitations
-- under the License.

--;
-- Schema upgrade from ablestack-allo to ablestack-bronto
--;

-- Add v2k_step column to import_vm_task table for ablestack-v2k workflow status persistence
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.import_vm_task','v2k_step', 'varchar(32) DEFAULT ''None'' COMMENT "Ablestack-v2k importing VM task step"');
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.import_vm_task','cluster_id', 'bigint unsigned COMMENT "Cluster ID used by the import task"');
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.import_vm_task','service_offering_id', 'bigint unsigned COMMENT "Service offering ID used by the import task"');
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.import_vm_task','v2k_target_storage_pool_id', 'bigint unsigned COMMENT "Primary storage pool ID used as ablestack-v2k target"');
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.import_vm_task','source_cluster_name', 'varchar(255) COMMENT "Source VMware cluster name used by the import task"');
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.import_vm_task','source_host_name', 'varchar(255) COMMENT "Source VMware host name used by the import task"');
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.import_vm_task','vcenter_id', 'bigint unsigned COMMENT "Existing vCenter ID used by the import task"');
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.import_vm_task','vcenter_username', 'varchar(255) COMMENT "vCenter username used by the import task"');
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.import_vm_task','vcenter_password', 'varchar(255) COMMENT "vCenter password used by the import task"');
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.import_vm_task','service_offering_details', 'text COMMENT "Serialized custom service offering details used by the import task"');
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.import_vm_task','nic_network_map', 'text COMMENT "Serialized NIC selection map used by the import task, including network and optional IP address"');
