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
- Support both newly created backing volumes and operator-selected existing
  CloudStack volumes. Existing volumes must be attached, inspected, mounted, and
  exposed without destructive formatting unless the caller explicitly requests
  a force format mode.
- Treat capacity expansion as a first-class workflow: resize the CloudStack
  backing volume first, then rescan and grow the filesystem or block namespace
  from inside the Storage Service System VM through QGA.
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
- Operations
  - `listStorageServiceHealth`
  - `listStorageServiceInventory`
  - `listStorageServiceSessions`
- NFS
  - `createStorageNfsExport`
  - `listStorageNfsExports`
  - `updateStorageNfsExport`
  - `deleteStorageNfsExport`
  - `attachStorageVolumeToFileShare`
  - `detachStorageVolumeFromFileShare`
  - `resizeStorageFileShare`
  - `createStorageNfsAcl`
  - `updateStorageNfsAcl`
  - `deleteStorageNfsAcl`
- SMB
  - `createStorageSmbShare`
  - `listStorageSmbShares`
  - `updateStorageSmbShare`
  - `deleteStorageSmbShare`
  - `attachStorageVolumeToFileShare`
  - `detachStorageVolumeFromFileShare`
  - `resizeStorageFileShare`
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
  - `listStorageIscsiAcls`
- NVMe-oF
  - `createStorageNvmeOfSubsystem`
  - `listStorageNvmeOfSubsystems`
  - `updateStorageNvmeOfSubsystem`
  - `deleteStorageNvmeOfSubsystem`
  - `prepareStorageServiceNvmeOfVm`
  - `createStorageNvmeOfNamespace`
  - `resizeStorageNvmeOfNamespace`
  - `deleteStorageNvmeOfNamespace`
  - `createStorageNvmeOfHostAcl`
  - `deleteStorageNvmeOfHostAcl`
  - `listStorageNvmeOfHostAcls`

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

`config_json` should also carry the volume attachment/import policy:

- `volumeMode`
  - `NEW_VOLUME`: CloudStack creates the backing volume for this share.
  - `EXISTING_VOLUME`: an existing CloudStack volume is attached to the
    Storage Service System VM and exposed.
- `importMode`
  - `USE_EXISTING_FS`: inspect and mount an existing filesystem. This is the
    default for existing volumes.
  - `FORMAT_IF_EMPTY`: format only if no filesystem signature is detected.
  - `FORCE_FORMAT`: destructive format, allowed only with explicit API input.
- `mountOptions`
- `partition`
- `fsUuid`
- `projectQuotaId` for XFS project quota based capacity enforcement.

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

For NVMe-oF, `config_json` should distinguish:

- `type`: `subsystem` or `namespace`
- `engine`: `KERNEL_NVMET` or `SPDK`
- `allowAnyHost`
- `backingPath`
- `transport`: initially `tcp`
- `namespaceSizeBytes`

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

The Mold UI must only submit asynchronous Cloud API requests and poll the async
job result. It must not execute storage commands directly. The actual command
execution path is:

```text
Mold UI
  -> Management Server API
  -> Management Server async job
  -> Mold Host Agent on the host running the Storage Service System VM
  -> QGA guest command/file operation
  -> ablestack-storagectl inside the Storage Service System VM
```

The management server is responsible for validation, desired-state persistence,
job orchestration, and state reconciliation. The host-side Mold Agent is
responsible for interacting with the hypervisor/QGA channel for the specific
Storage Service System VM running on that host.

Command flow:

1. API request is validated.
2. Desired state is stored in the database.
3. An async job starts.
4. The manager locates the host currently running the Storage Service System VM.
5. The manager sends a storage service command to that Mold Host Agent.
6. The Mold Host Agent writes a JSON payload into the service VM through QGA.
7. The Mold Host Agent executes `ablestack-storagectl apply <payload>` through
   QGA `guest-exec`.
8. The Mold Host Agent polls QGA for command completion and reads the result
   JSON.
9. The manager receives the structured result from the Mold Host Agent.
10. Database state, operation status, and event details are updated.

Required management-side and agent-side abstractions:

```text
StorageServiceGuestCommandDispatcher
StorageServiceGuestCommand
StorageServiceGuestCommandResult
StorageServiceHostCommand
StorageServiceHostCommandWrapper
StorageServiceQgaClient
```

The QGA client lives on the Mold Host Agent side and should support:

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
ablestack-storagectl smb domain leave <payload.json>
ablestack-storagectl iscsi target apply <payload.json>
ablestack-storagectl volume attach inspect <payload.json>
ablestack-storagectl filesystem resize <payload.json>
ablestack-storagectl nvmeof prepare <payload.json>
ablestack-storagectl nvmeof subsystem apply <payload.json>
```

The tool should be idempotent:

- Reapplying the same payload should be safe.
- Partial failures should return structured error details.
- Existing OS-level config should be reconciled to desired state.
- Existing-volume import and filesystem resize commands must be non-destructive
  unless the payload explicitly requests a destructive mode.
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

NVMe-oF template and service-offering strategy:

- `KERNEL_NVMET` mode belongs in the normal Storage Service System VM template.
- `SPDK` mode should be exposed through a separate advanced service offering or
  a dedicated Storage Service template profile because it can require reserved
  hugepages, larger memory, CPU pinning, NUMA hints, and optional SR-IOV or PCI
  passthrough.
- The manager must validate host and offering capability before accepting SPDK
  mode. If the current VM cannot satisfy the request, it should return a clear
  `PreparationRequired` state and recommended offering/template changes instead
  of partially enabling NVMe-oF.

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

## Existing Volume Attachment For File Services

The Storage Service must support exposing an existing CloudStack volume through
NFS or SMB. This is required for data migration, recovery, and converting an
existing data disk into a managed file service without copying data.

Supported attachment modes:

- New volume mode
  - The Storage Service API creates a new CloudStack data volume.
  - The manager attaches it to the Storage Service System VM.
  - QGA formats, mounts, and exports it.
- Existing volume mode
  - The caller passes an existing `volumeid`.
  - The manager validates that the volume is detached or can be safely detached
    from its current owner according to CloudStack volume rules.
  - The manager attaches the volume to the Storage Service System VM.
  - QGA discovers the device by stable disk metadata, probes filesystem
    signatures, and reports the result.
  - The default import mode is non-destructive `USE_EXISTING_FS`.

Existing volume safety rules:

- Never format a volume by default.
- If no supported filesystem is detected, fail with a clear `IMPORT_REQUIRED`
  state unless `FORMAT_IF_EMPTY` was requested and the device is empty.
- `FORCE_FORMAT` must be explicit and must be an async operation with an audit
  event.
- If the filesystem is dirty or requires repair, return a blocked state and
  expose the needed operator action. Do not run destructive repairs
  automatically in the first implementation.
- NFS/SMB ACLs must be rendered before the service is advertised to clients.
- On apply failure, the manager should roll back the export/share and leave the
  volume attached but unexported for inspection, or detach it if no filesystem
  changes were made.

Guest-side attach workflow:

1. QGA receives `volume attach inspect` with expected volume UUID/device hints.
2. `ablestack-storagectl` resolves the Linux device using `/dev/disk/by-id`,
   serial, WWN, or CloudStack-provided metadata.
3. The tool runs `blkid`, `lsblk`, and `findmnt` to detect signatures and mount
   state.
4. The tool mounts the filesystem under:
   `/srv/ablestack-storage/volumes/<volume-uuid>`
5. File shares use subpaths under that mount, for example:
   `/srv/ablestack-storage/volumes/<volume-uuid>/shares/<share-name>`
6. The resulting device, filesystem UUID, mount path, and capacity are stored in
   `storage_file_share.config_json`.

Initial filesystem support should be `xfs` and `ext4`. XFS is preferred for new
file-service volumes because it supports online grow and project quotas.

## File Service Capacity Expansion

File-service resize must be an explicit async workflow that coordinates
CloudStack volume resize with guest-side filesystem growth.

API direction:

- `resizeStorageFileShare`
  - Parameters: `id`, `size`, optional `shrink=false`.
  - Applies to NFS exports and SMB shares.
  - The first implementation supports grow only.
- Existing SharedFS `changeSharedFileSystemDiskOffering` remains the
  compatibility API and can later call this workflow internally.

Resize workflow:

1. Validate the target share and backing volume.
2. Validate the requested size is larger than the current volume size.
3. Ask CloudStack to resize the backing volume or change the disk offering.
4. Send a QGA `filesystem resize` operation to the Storage Service System VM.
5. Inside the VM, rescan the block device:
   - `echo 1 > /sys/class/block/<dev>/device/rescan` where available
   - `partprobe` when partitions are used
6. Grow the filesystem:
   - XFS: `xfs_growfs <mountpoint>`
   - ext4: `resize2fs <device>` after ensuring the block device is resized
7. If XFS project quota is used, update the project hard limit to
   `quota_bytes`.
8. Return health, size, used bytes, filesystem, and mount path in the async job
   result.

Failure handling:

- If CloudStack volume resize succeeds but filesystem grow fails, mark the
  share `ResizeError`, keep the service mounted, and expose a retry action.
- Shrink is not supported in the first implementation because it requires
  filesystem-specific offline steps and has high data-loss risk.
- Multi-share-on-one-volume capacity is enforced through XFS project quota; the
  volume grow and quota grow are separate state transitions.

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

iSCSI should use Linux LIO through `targetcli-fb`. The first implementation
stores the desired state in `storage_block_target` and applies target/LUN/ACL
configuration through `ablestack-storagectl iscsi target apply <payload.json>`.
Secrets such as CHAP passwords are runtime-only payload fields and are not
persisted in the database.

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

The first implementation uses the shared `storage_block_target` table for both
subsystems and namespaces. A subsystem row owns the NQN and host ACLs, and
namespace rows reuse the same NQN with their namespace ID and backing volume.

### NVMe-oF Engine Modes And VM Preparation

NVMe-oF should support two target engines:

- `KERNEL_NVMET`
  - Default first implementation.
  - Uses Linux kernel `nvmet` with configfs and TCP transport.
  - Requires kernel modules such as `nvmet`, `nvmet-tcp`, and `configfs`.
  - Does not require guest hugepages by design.
- `SPDK`
  - Optional high-performance mode.
  - Uses an SPDK NVMe-oF target process.
  - Requires hugepages and DPDK/SPDK runtime preparation.
  - May require CPU pinning, NUMA placement, memlock limits, and optional PCI
    passthrough or SR-IOV/VF assignment for high-throughput networking.

The API `prepareStorageServiceNvmeOfVm` should prepare or validate the System VM
for the requested engine before NVMe-oF subsystems are enabled.

Parameters:

- `instanceid`
- `engine`: `KERNEL_NVMET` or `SPDK`
- `transport`: initially `tcp`
- `hugepagesmib`: required only for `SPDK`
- `cpuset`: optional dedicated vCPU set for SPDK pollers
- `numanode`: optional NUMA placement hint
- `memlock`: optional process memlock limit, default `unlimited` for SPDK
- `networkmode`: `VIRTIO`, `SRIOV_VF`, or `PCI_PASSTHROUGH`
- `validateonly`: report missing prerequisites without changing the VM

Management-side VM preparation:

1. Validate the Storage Service System VM service offering.
2. For `KERNEL_NVMET`, ensure the template has `nvme-cli`, `nvmetcli` when
   available, and a kernel with NVMe target modules.
3. For `SPDK`, select or update a service offering that provides:
   - enough memory for normal OS use plus requested hugepages
   - optional CPU pinning or host affinity
   - optional NUMA-aware placement
   - optional SR-IOV VF or PCI passthrough network capability
4. Generate a desired capability document in `StorageServiceProtocol.config_json`.
5. Send QGA `nvmeof prepare` to the System VM.

Guest-side SPDK preparation:

1. Install or verify SPDK runtime packages and `setup.sh` prerequisites in the
   template.
2. Reserve hugepages:
   - runtime: write to `/sys/kernel/mm/hugepages/.../nr_hugepages`
   - persistent: render `/etc/sysctl.d/ablestack-spdk-hugepages.conf`
3. Mount `hugetlbfs` at `/dev/hugepages` if not already mounted.
4. Apply `LimitMEMLOCK=infinity` to the SPDK service unit.
5. Optionally bind selected PCI devices to a userspace driver only when the
   operator requested passthrough/SR-IOV and CloudStack attached the device to
   the VM.
6. Start or reload the SPDK NVMe-oF service.
7. Return a readiness report with hugepage totals/free count, NUMA node,
   transport, NIC mode, and engine version.

The first implementation should keep `KERNEL_NVMET` as the default and expose
`SPDK` as an explicit advanced mode. If the System VM runs on normal virtio
networking, SPDK can still be prepared for lab use, but the UI should clearly
mark it as "accelerated mode prerequisites not fully satisfied" unless CPU,
hugepages, and network placement checks pass.

## UI Design

Add a new Storage section, tentatively named `Storage Services` or
`File & Block Services`.

The UI implementation must follow the existing Vue and Ant Design Vue patterns
used by the CloudStack UI. New views should reuse the current section/action,
resource table, details tab, popup form, async job polling, status, metric, and
event tab conventions instead of introducing an unrelated visual system.

Styles must be compatible with both normal and dark modes. New components should
use existing theme tokens, Ant Design Vue component states, and local CloudStack
UI styling conventions so that colors, borders, backgrounds, hover states,
disabled states, charts, tables, forms, and status indicators remain readable in
both modes.

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

Implementation note:

- The existing SharedFS lifecycle remains the compatibility authority for VM
  deployment, volume creation, and UI/API responses.
- When `storage.service.feature.enabled` is enabled, SharedFS lifecycle changes
  are mirrored into the new Storage Service model by matching the SharedFS
  `vm_id` and `volume_id`.
- The compatibility mirror creates or updates one `StorageServiceInstance`, one
  enabled NFS `StorageServiceProtocol`, one NFS `StorageFileShare`, and a
  default IPv4 CIDR ACL equivalent to the current SharedFS open NFS export.
- Compatibility mirror failures are logged and do not change existing SharedFS
  API behavior.

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

- Add QGA-backed runtime health, inventory, and active session checks.
- Add metrics and health checks.
- Add event/audit details.
- Add backup/snapshot/resize integration.
- Add upgrade and template compatibility checks.
- Evaluate dedicated Storage Service System VM template profile.

### Phase 7: Existing Volumes, Resize, And NVMe-oF VM Preparation

- Add explicit existing-volume import APIs for NFS and SMB file shares.
- Implement QGA `volume attach inspect` and non-destructive mount discovery.
- Implement `resizeStorageFileShare` and QGA `filesystem resize` for XFS and
  ext4 grow.
- Add XFS project quota support for multi-share-on-one-volume capacity
  enforcement.
- Add `prepareStorageServiceNvmeOfVm` for `KERNEL_NVMET` validation and SPDK
  hugepage/service preparation.
- Add UI workflows for:
  - selecting existing CloudStack volumes
  - choosing import mode
  - reviewing detected filesystem and mount status
  - resizing file services
  - selecting NVMe-oF engine mode and viewing prerequisite checks.

Recommended implementation order:

1. Existing volume import model and API validation.
2. QGA volume inspection and mount workflow.
3. File share resize API and filesystem grow workflow.
4. NVMe-oF `KERNEL_NVMET` prerequisite validation.
5. SPDK preparation API and template/service-offering capability checks.
6. UI integration and end-to-end validation.

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
- Whether SPDK mode should be supported in the same Storage Service System VM
  template or require a dedicated high-performance template and service
  offering.
- Whether existing volumes that are currently attached to another VM should be
  automatically detached by the Storage Service workflow or require an explicit
  operator detach step first.

## References

- SPDK System Configuration User Guide:
  <https://spdk.io/doc/system_configuration.html>
- SPDK NVMe-oF Target documentation:
  <https://spdk.io/doc/nvmf.html>
- SPDK Getting Started guide:
  <https://spdk.io/doc/getting_started.html>
- Red Hat Enterprise Linux NVMe-oF configfs workflow:
  <https://docs.redhat.com/en/documentation/red_hat_enterprise_linux/9/html/managing_storage_devices/configuring-nvme-over-fabrics-using-nvme-rdma_managing-storage-devices>
