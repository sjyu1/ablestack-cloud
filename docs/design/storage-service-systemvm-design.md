# Storage Service System VM Design

## Purpose

This document captures the design direction for extending the existing
`ablestack-diplo` shared file system capability into a System VM based file and
block storage service platform.

The current SharedFS implementation provides a Storage VM that exposes one NFS
export. The target design expands this into a managed storage service that can
provide:

- NFS exports
- SMB shares, including Active Directory domain join
- iSCSI targets
- NVMe-oF subsystems and namespaces

The design keeps the upstream Apache CloudStack SharedFS API path as compatible
as possible and adds independent ABLESTACK APIs for the expanded service model.

## Current Baseline

The current SharedFS feature is built around these components:

- API and model:
  `api/src/main/java/org/apache/cloudstack/storage/sharedfs`
- API commands:
  `api/src/main/java/org/apache/cloudstack/api/command/user/storage/sharedfs`
- Server orchestration:
  `server/src/main/java/org/apache/cloudstack/storage/sharedfs/SharedFSServiceImpl.java`
- Storage VM provider:
  `plugins/storage/sharedfs/storagevm`
- Database:
  `cloud.shared_filesystem`
  `cloud.shared_filesystem_view`
- UI:
  `ui/src/config/section/storage.js`
  `ui/src/views/storage/*SharedFS*.vue`
- System VM setup:
  `systemvm/debian/opt/cloud/bin/setup/sharedfsvm.sh`
  `plugins/storage/sharedfs/storagevm/src/main/resources/conf/fsvm-init.yml`

Current behavior:

1. `createSharedFileSystem` validates the zone, network, disk offering, service
   offering, filesystem type, and account ownership.
2. A `shared_filesystem` row is created in `Allocated` state.
3. A System VM template is deployed as a `sharedfsvm` user VM type.
4. Cloud-init config formats the attached data disk, mounts it at `/export`,
   and exports it through NFS.
5. The resulting backing volume ID and VM ID are recorded in
   `shared_filesystem`, and state transitions to `Ready`.

## Design Principles

- Preserve the existing CloudStack SharedFS API as a compatibility surface.
- Build the expanded service as an independent ABLESTACK Storage Service API.
- Use System VM based service instances for protocol daemons and data paths.
- Use QEMU Guest Agent (QGA) as the standard command channel from the
  management server to the service VM.
- Make System VM template contents part of the product: all required runtime
  packages and storage control scripts must be preinstalled.
- Avoid storing passwords, API secrets, SSH passwords, AD join passwords, CHAP
  secrets, or similar credentials in plaintext files or logs.
- Prefer low-risk incremental implementation: first rebuild NFS on the new
  engine, then add SMB, then block protocols.

## High-Level Architecture

The expanded platform should introduce a new service model:

- `StorageServiceInstance`
  - A System VM that hosts one or more file/block protocol services.
  - Owns lifecycle, network attachment, account/domain/zone, service offering,
    and VM state.
- `StorageServiceProtocol`
  - Protocol configuration for `NFS`, `SMB`, `ISCSI`, or `NVME_OF`.
  - Tracks enabled state, listen address, port, and protocol-specific options.
- `StorageFileShare`
  - A file share exposed through NFS or SMB.
  - Owns backing volume, filesystem, export/share name, path, quota/size, and
    state.
- `StorageBlockTarget`
  - A block target exposed through iSCSI or NVMe-oF.
  - Owns backing volume, target identifier, LUN/namespace mapping, and state.
- `StorageAccessRule`
  - Access policy for file shares or block targets.
  - Covers NFS CIDR ACLs, SMB local/AD users and groups, iSCSI initiator IQNs
    and CHAP, and NVMe-oF host NQNs.
- `StorageIdentityDomain`
  - Identity integration state, mainly for SMB Active Directory domain join.
  - Tracks domain name, OU, DNS configuration, join state, and health.

The existing SharedFS API can later be implemented as a compatibility facade
over a `StorageServiceInstance` with one NFS `StorageFileShare`.

## API Direction

### Existing API To Preserve

The following upstream SharedFS APIs should remain in their current package and
keep their behavior as much as possible:

- `createSharedFileSystem`
- `listSharedFileSystems`
- `updateSharedFileSystem`
- `startSharedFileSystem`
- `stopSharedFileSystem`
- `restartSharedFileSystem`
- `changeSharedFileSystemDiskOffering`
- `changeSharedFileSystemServiceOffering`
- `destroySharedFileSystem`
- `recoverSharedFileSystem`
- `expungeSharedFileSystem`

These should remain under:

```text
org.apache.cloudstack.api.command.user.storage.sharedfs
```

### New Independent API

New ABLESTACK APIs should be placed under an independent package, for example:

```text
org.apache.cloudstack.api.command.user.storage.dataservice
```

Candidate APIs:

- Instance lifecycle
  - `createStorageServiceInstance`
  - `listStorageServiceInstances`
  - `updateStorageServiceInstance`
  - `startStorageServiceInstance`
  - `stopStorageServiceInstance`
  - `restartStorageServiceInstance`
  - `destroyStorageServiceInstance`
- Protocol management
  - `enableStorageServiceProtocol`
  - `disableStorageServiceProtocol`
  - `updateStorageServiceProtocol`
- NFS
  - `createStorageNfsExport`
  - `listStorageNfsExports`
  - `updateStorageNfsExport`
  - `deleteStorageNfsExport`
  - `createStorageNfsAcl`
  - `updateStorageNfsAcl`
  - `deleteStorageNfsAcl`
- SMB
  - `createStorageSmbShare`
  - `listStorageSmbShares`
  - `updateStorageSmbShare`
  - `deleteStorageSmbShare`
  - `createStorageSmbAcl`
  - `updateStorageSmbAcl`
  - `deleteStorageSmbAcl`
  - `joinStorageServiceToAdDomain`
  - `leaveStorageServiceFromAdDomain`
  - `listStorageServiceDomainStatus`
- iSCSI
  - `createStorageIscsiTarget`
  - `listStorageIscsiTargets`
  - `updateStorageIscsiTarget`
  - `deleteStorageIscsiTarget`
  - `createStorageIscsiAcl`
  - `updateStorageIscsiAcl`
  - `deleteStorageIscsiAcl`
- NVMe-oF
  - `createStorageNvmeOfSubsystem`
  - `listStorageNvmeOfSubsystems`
  - `updateStorageNvmeOfSubsystem`
  - `deleteStorageNvmeOfSubsystem`
  - `createStorageNvmeOfNamespace`
  - `deleteStorageNvmeOfNamespace`
  - `createStorageNvmeOfHostAcl`
  - `deleteStorageNvmeOfHostAcl`

## Database Model

Initial tables:

### `storage_service_instance`

Tracks the System VM service instance.

Important columns:

- `id`
- `uuid`
- `name`
- `description`
- `account_id`
- `domain_id`
- `data_center_id`
- `vm_id`
- `service_offering_id`
- `provider`
- `state`
- `created`
- `updated`
- `removed`

### `storage_service_protocol`

Tracks enabled protocol services per instance.

Important columns:

- `id`
- `uuid`
- `instance_id`
- `protocol`
- `enabled`
- `listen_ip`
- `port`
- `state`
- `config_json`

### `storage_file_share`

Tracks NFS exports and SMB shares.

Important columns:

- `id`
- `uuid`
- `instance_id`
- `protocol`
- `name`
- `path`
- `volume_id`
- `filesystem`
- `quota_bytes`
- `state`
- `config_json`

### `storage_block_target`

Tracks iSCSI targets and NVMe-oF subsystems/namespaces.

Important columns:

- `id`
- `uuid`
- `instance_id`
- `protocol`
- `target_name`
- `lun_or_namespace`
- `volume_id`
- `state`
- `config_json`

### `storage_access_rule`

Tracks ACLs and protocol-specific access rules.

Important columns:

- `id`
- `uuid`
- `resource_type`
- `resource_id`
- `principal_type`
- `principal`
- `permission`
- `secret_ref`
- `state`
- `config_json`

### `storage_identity_domain`

Tracks SMB identity integration.

Important columns:

- `id`
- `uuid`
- `instance_id`
- `domain_name`
- `organizational_unit`
- `dns_servers`
- `join_state`
- `health_state`
- `config_json`

## QGA Command Channel

All post-boot storage operations should be applied through QGA. Cloud-init
should be limited to bootstrap.

Management server flow:

1. API request is validated.
2. Desired state is stored in the database.
3. An async job starts.
4. The manager writes a JSON payload into the service VM through QGA.
5. The manager executes `ablestack-storagectl apply <payload>` through QGA
   `guest-exec`.
6. The manager polls QGA for command completion.
7. The manager reads the result JSON.
8. Database state, operation status, and event details are updated.

Required management-side abstraction:

```text
StorageServiceGuestCommandDispatcher
StorageServiceGuestCommand
StorageServiceGuestCommandResult
StorageServiceQgaClient
```

The QGA client should support:

- guest file write
- guest command execution
- timeout and retry
- masked logging for sensitive payload fields
- structured JSON result parsing

## System VM Runtime

The System VM template must include a storage control tool:

```text
/usr/local/bin/ablestack-storagectl
```

Recommended command model:

```text
ablestack-storagectl apply <payload.json>
ablestack-storagectl health
ablestack-storagectl inventory
ablestack-storagectl nfs export apply <payload.json>
ablestack-storagectl smb share apply <payload.json>
ablestack-storagectl smb domain join <payload.json>
ablestack-storagectl iscsi target apply <payload.json>
ablestack-storagectl nvmeof subsystem apply <payload.json>
```

The tool should be idempotent:

- Reapplying the same payload should be safe.
- Partial failures should return structured error details.
- Existing OS-level config should be reconciled to desired state.
- All generated files should be under predictable directories, for example:
  - `/etc/exports.d/ablestack-*.exports`
  - `/etc/samba/ablestack-shares.d/*.conf`
  - `/etc/target/ablestack-*.json`
  - `/etc/nvmet/ablestack-*.json`

## System VM Template Build

Current template build path:

- Packer wrapper:
  `tools/appliance/build.sh`
- Packer definitions:
  `tools/appliance/systemvmtemplate/template-base_*`
- Package installation:
  `tools/appliance/systemvmtemplate/scripts/install_systemvm_packages.sh`
- SystemVM services:
  `tools/appliance/systemvmtemplate/scripts/configure_systemvm_services.sh`
- Runtime files copied into the template:
  `systemvm/debian`

Current package baseline already includes:

- `qemu-guest-agent`
- `cloud-init`
- `nfs-common`
- `nfs-server`
- `xfsprogs`
- `samba-common`
- `cifs-utils`

Required additions for the expanded Storage Service VM:

- Common:
  - `jq`
  - `python3-yaml`
  - `quota`
  - `acl`
  - `parted`
  - `lvm2`
- SMB and AD:
  - `samba`
  - `smbclient`
  - `winbind`
  - `krb5-user`
  - `realmd`
  - `sssd`
  - `adcli`
  - `libnss-winbind`
  - `libpam-winbind`
- iSCSI:
  - `targetcli-fb`
- NVMe-oF:
  - `nvme-cli`
  - `nvmetcli` if available in the target Debian repository

Template strategy:

1. Phase 1: add packages and storage control scripts to the common System VM
   template to minimize CloudStack template-selection changes.
2. Phase 2: introduce a dedicated `storageservicevmtemplate` build profile if
   image size, security scope, or operational separation requires it.

If a dedicated template is introduced, the manager must stop relying only on
`findSystemVMReadyTemplate(zoneId, hypervisor)` and select the Storage Service
template explicitly.

## NFS Design

NFS must be upgraded from one fixed `/export` share to first-class export
management.

Features:

- export create/update/delete
- export enable/disable
- export path management
- export-level backing volume
- export-level capacity limit
- export-level usage reporting
- CIDR/IP ACL management
- `rw` / `ro`
- `root_squash` / `no_root_squash`
- `sync` / `async`
- `secure` / `insecure`
- optional `anonuid` and `anongid`

Capacity strategy:

- Phase 1: one backing CloudStack volume per NFS export.
  - The volume size is the hard capacity boundary.
  - Resize maps to CloudStack volume resize plus filesystem grow.
  - Snapshot, backup, and delete are straightforward.
- Phase 2: multiple exports on one backing volume with XFS project quota.
  - Better density.
  - More complex reconciliation and recovery.

Runtime implementation:

- Mount export volumes under a controlled root, for example:
  `/srv/ablestack-storage/nfs/<share-uuid>`
- Render export rules into:
  `/etc/exports.d/ablestack-<share-uuid>.exports`
- Apply with:
  `exportfs -ra`
- Report state with:
  `exportfs -v`
  `df`
  `findmnt`

## SMB Design

SMB must support both local authentication and AD domain backed authentication.

Features:

- SMB protocol enable/disable
- SMB share create/update/delete
- local user/group ACLs
- AD user/group ACLs
- read/write/admin permissions
- guest access policy, disabled by default
- AD domain join and leave
- AD domain health checks

AD join requirements:

- DNS server validation
- time synchronization validation
- Kerberos configuration
- `realm` or `adcli` join
- winbind or sssd configuration
- NSS/PAM integration only where required
- no plaintext persistence of join credentials

Sensitive fields, such as AD join passwords, must be accepted as runtime
secrets and masked in logs, events, QGA payload traces, and async job details.

## iSCSI Design

iSCSI should use Linux LIO through `targetcli-fb`.

Features:

- target IQN create/update/delete
- LUN create/delete
- backing volume mapping
- initiator IQN ACL
- CHAP and mutual CHAP
- target session listing
- LUN resize

Capacity is controlled by the backing volume size.

## NVMe-oF Design

NVMe-oF should start with TCP transport.

Features:

- subsystem NQN create/update/delete
- namespace create/delete
- backing volume mapping
- host NQN ACL
- discovery information
- namespace resize

Runtime can use `nvmetcli` if available, or manage Linux `configfs`
directly through `ablestack-storagectl`.

## UI Design

Add a new Storage section, tentatively named `Storage Services` or
`File & Block Services`.

Views:

- Instances
  - service instance list, state, protocols, IPs, account, zone
- Instance detail tabs
  - Overview
  - Protocols
  - NFS Exports
  - SMB Shares
  - Block Targets
  - ACLs
  - AD Domain
  - Sessions
  - Metrics
  - Events

Existing `Shared FileSystems` should remain visible and compatible. It may later
link to the underlying NFS export in the new UI, but should not require users to
adopt the new UI immediately.

## Implementation Phases

### Phase 1: Foundation

- Add the new DB model and DAO/query classes.
- Add the new API package and manager skeleton.
- Add QGA guest command dispatcher abstractions.
- Add `ablestack-storagectl` skeleton to `systemvm/debian`.
- Extend System VM template package list for storage runtime dependencies.

### Phase 2: NFS on the New Engine

- Implement Storage Service instance lifecycle.
- Implement NFS protocol enablement.
- Implement NFS export CRUD.
- Implement NFS ACL CRUD.
- Implement export-level backing volume and capacity limit.
- Implement usage/status reporting through QGA.

### Phase 3: SharedFS Compatibility

- Keep existing SharedFS API paths.
- Map `createSharedFileSystem` to one Storage Service instance and one NFS
  export, or keep the old path until the new NFS engine is stable.
- Preserve response shape and existing UI behavior.

### Phase 4: SMB

- Implement SMB local user/share mode.
- Implement SMB ACL management.
- Implement AD domain join/leave/status.
- Implement AD user/group ACLs.

### Phase 5: Block Protocols

- Implement iSCSI target/LUN/ACL/CHAP.
- Implement NVMe-oF subsystem/namespace/host ACL.
- Add session and discovery views.

### Phase 6: Operations

- Add metrics and health checks.
- Add event/audit details.
- Add backup/snapshot/resize integration.
- Add upgrade and template compatibility checks.
- Evaluate dedicated Storage Service System VM template profile.

## Open Decisions

- Whether Phase 1 should use the common System VM template or immediately add a
  dedicated Storage Service template.
- Whether Storage Service instances should support one protocol per VM or mixed
  protocols in one VM.
- Whether HA should be active/passive in the first version or deferred.
- Whether NFS multi-export quota should wait for XFS project quota support or be
  implemented only through one-volume-per-export initially.
- Whether SMB identity should standardize on winbind, sssd, or support both.
- Whether NVMe-oF should require `nvmetcli` or use configfs directly.

