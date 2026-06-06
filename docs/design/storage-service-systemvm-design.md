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

The design keeps the upstream Apache ABLESTACK SharedFS API path as compatible
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

- Preserve the existing ABLESTACK SharedFS API as a compatibility surface.
- Build the expanded service as an independent ABLESTACK Storage Service API.
- Use System VM based service instances for protocol daemons and data paths.
- Use QEMU Guest Agent (QGA) as the standard command channel from the
  management server to the service VM.
- Make System VM template contents part of the product: all required runtime
  packages and storage control scripts must be preinstalled.
- Support both newly created backing volumes and operator-selected existing
  ABLESTACK volumes. Existing volumes must be attached, inspected, mounted, and
  exposed without destructive formatting unless the caller explicitly requests
  a force format mode.
- Treat capacity expansion as a first-class workflow: resize the ABLESTACK
  backing volume first, then rescan and grow the filesystem or block namespace
  from inside the Storage Service System VM through QGA.
- Treat file-share backing volume create/import as one logical lifecycle. A new
  backing volume must be created on an explicit primary storage, attached to the
  Storage Service System VM, inspected/formatted/mounted through QGA, and only
  then exposed as a Ready export/share. If any step fails, the partially created
  export/share row must be removed from the normal management surface. A volume
  created specifically by the current operation may be detached and expunged as
  compensation; an operator-selected existing volume must never be deleted by
  rollback.
- Treat ABLESTACK volume creation as asynchronous. When the UI creates a new
  backing volume for a file share, it must wait for `createVolume` job
  completion and confirm that the volume has reached an attachable state
  (`Allocated`, `Ready`, or `Uploaded`) before calling the Storage Service
  export/share API. The engine must also re-check the volume state before
  attaching it to the Storage Service System VM so direct API calls do not race
  the volume lifecycle.
- Treat attached backing-volume device identification as safety critical. The
  System VM must never infer a target device by selecting an arbitrary
  unmounted partition with an existing filesystem. `volume attach inspect` must
  identify the candidate from the ABLESTACK volume UUID/name/size and guest
  block inventory, exclude the root disk and all root-disk children, exclude
  already mounted devices unless they match the requested volume, and reject
  ambiguous candidates. In `FORMAT_EMPTY` mode, only an unmounted block device
  without an existing filesystem may be formatted; swap, root, boot, ISO/ROM,
  and previously mounted data devices are never valid format targets.
- Avoid storing passwords, API secrets, SSH passwords, AD join passwords, CHAP
  secrets, or similar credentials in plaintext files or logs.
- Prefer low-risk incremental implementation: first rebuild NFS on the new
  engine, then add SMB, then block protocols.

## Capacity Unit Policy

Mold follows the ABLESTACK storage API convention for volume and disk offering
sizes:

- Existing API fields such as `size`, `disksize`, and `rootdisksize` are
  documented in many places as `GB`.
- The implementation converts these values with `value * 1024 * 1024 * 1024`.
- `createDiskOffering` explicitly documents `1GB = 1,073,741,824 bytes`.

Therefore, for compatibility with existing Mold and ABLESTACK API behavior,
Storage Service must treat API `size` values for backing ABLESTACK volumes as
integer GiB values even when the legacy API description says `GB`.

Unit rules:

- Existing API compatibility:
  - Keep `createSharedFileSystem.size` and `resizeStorageFileShare.size` as
    integer GiB-compatible values passed through existing `size` semantics.
  - Do not introduce decimal `GB` interpretation for those fields.
- UI wording:
  - Label backing disk size as `Data disk size (GiB)` in English and
    `?곗씠???붿뒪???ш린(GiB)` in Korean for the expanded Storage Service UI.
  - When the UI must remain aligned with existing ABLESTACK wording, the help
    text should explain that Mold API `GB` storage fields are calculated as GiB
    using 1024-based units.
- New quota and protocol capacity fields:
  - Keep API and database storage in bytes: `quota_bytes`, `quotaBytes`,
    `lunSizeBytes`, and `namespaceSizeBytes`.
  - The UI must not ask operators to enter raw bytes by default.
  - The UI should provide a numeric input plus unit selector using IEC units:
    `B`, `MiB`, `GiB`, and `TiB`.
  - The UI converts the selected value to bytes before submitting the API
    request and shows the converted byte value in review text.
- Avoid mixed decimal/binary labels:
  - Do not label 1024-based values as plain `MB`, `GB`, or `TB` in new Storage
    Service UI fields.
  - Use `MiB`, `GiB`, and `TiB` for operator-entered capacity values.

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
  - `disconnectStorageServiceSession`
  - `listStorageServiceProtocolSummary`
- NFS
  - `createStorageNfsExport`
    - Accepts an optional cleanup marker for UI-created backing volumes. When
      enabled, create/apply failure removes the failed export record and attempts
      to detach and destroy only that newly created volume.
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

All Storage Service `config_json` columns must be `mediumtext`, not the
default JPA string width. These JSON documents carry endpoint binding, backing
volume mount metadata, authentication settings, runtime capability snapshots,
and service-specific desired state. A 255-byte truncation is a data corruption
defect because it can silently drop fields such as NFS `endpointMode` and
`listenIps`, causing the engine and UI to misrepresent a selected endpoint as
all endpoints. Upgrade scripts and live deployment checks must verify these
columns on:

- `storage_service_protocol.config_json`
- `storage_file_share.config_json`
- `storage_block_target.config_json`
- `storage_access_rule.config_json`
- `storage_identity_domain.config_json`

The API layer must not silently treat an invalid JSON configuration as a normal
default state. List/detail responses should expose configuration validity, log
the invalid row, and recover only protocol-critical fields that can be parsed
unambiguously from the stored raw value. Operators should recreate or update
affected rows after the DB type is corrected, because a truncated JSON payload
may have already lost fields that cannot be reconstructed.

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
  - `NEW_VOLUME`: ABLESTACK creates the backing volume for this share.
  - `EXISTING_VOLUME`: an existing ABLESTACK volume is attached to the
    Storage Service System VM and exposed.
- `importMode`
  - `MOUNT_EXISTING`: inspect and mount an existing filesystem. This is the
    default for existing volumes.
  - `FORMAT_EMPTY`: format only when no filesystem signature is detected.
    This is used by the UI when it creates a new data volume for a share.
  - `INSPECT_ONLY`: inspect the attached volume and persist runtime metadata
    without mounting it.
  - Destructive force-format is intentionally out of scope for the first
    implementation.
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
- `engineState`: `SUPPORTED`, `PLANNED`, or `PREPARATION_REQUIRED`
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

Storage Service monitoring cache:

- The System VM template must include and enable a lightweight monitoring
  service for Storage Service runtime state. The service should run inside the
  Storage Service System VM and periodically collect protocol health,
  inventory, capacity, and session information into local JSON files.
- The monitoring service avoids expensive on-demand QGA command execution for
  every UI refresh. The Management Server still reaches the System VM through
  the Mold Host Agent and QGA, but status APIs should prefer reading the latest
  cached JSON files instead of running full protocol discovery commands every
  time.
- Recommended service name:
  `ablestack-storage-monitor.service`
- Recommended executable:
  `/usr/local/bin/ablestack-storage-monitor`
- Recommended cache directory:
  `/run/ablestack-storage/monitor`
- Recommended cache files:
  - `health.json`: service, daemon, QGA-visible timestamp, and monitor status
  - `inventory.json`: NFS exports, SMB shares, iSCSI targets, NVMe-oF
    subsystems/namespaces, backing volumes, and protocol object counts
  - `sessions.json`: active client/session view by protocol, best effort for
    NFS
  - `capacity.json`: mounted filesystem usage, quota state, backing volume
    mapping, and block target capacity
  - `errors.json`: last collection errors by collector and protocol
- Cache files must be written atomically by writing to a temporary file and
  renaming it into place. Readers must never see partial JSON.
- Runtime collectors must normalize command output before writing JSON. In
  particular, rendered NFS `exportfs` option strings such as `anonuid\=65534`
  must be normalized to `anonuid=65534` before they are returned through
  `resultjson`; the management API and UI must never receive JSON text with
  invalid escape sequences.
- Management-server runtime status APIs must also normalize and re-serialize
  `resultjson` with HTML escaping disabled. This prevents otherwise valid
  runtime values such as NFS option strings containing `=` from being emitted
  through the API as invalid backslash escape sequences after double
  serialization.
- Each JSON file must include:
  - `schemaVersion`
  - `generatedAt`
  - `collector`
  - `status`
  - `staleAfterSeconds`
  - `errors`
- The monitoring interval should be configurable. A default interval around
  5-15 seconds is acceptable for UI responsiveness while avoiding excessive
  load inside the System VM.
- The monitor must collect data with low-cost commands where possible, for
  example `systemctl is-active`, `exportfs -v`, `showmount -e localhost`,
  `findmnt`, `df`, `lsblk`, Samba status commands, `targetcli`/LIO state,
  `nvmetcli` or configfs reads, and lightweight session commands.
- `health.json` and `inventory.json` must include the guest-visible IPv4
  addresses and interface/prefix evidence collected inside the System VM.
  The UI must merge this runtime endpoint evidence with ABLESTACK VM/NIC
  metadata and protocol/export desired-state IPs, because Cloud NIC metadata
  can lag or omit ConfigDrive/L2 runtime addresses even when the guest is
  serving storage traffic correctly.
- Expensive protocol discovery should be rate-limited or split into separate
  collectors so a slow SMB, iSCSI, or NVMe-oF command does not block all status
  files.
- The status APIs must check monitor health first:
  - if the monitor service is active and cache freshness is within
    `staleAfterSeconds`, return cached data;
  - if the monitor service is inactive but cache exists, return stale data with
    a clear stale warning;
  - if no cache exists, fall back to targeted QGA command execution only for the
    requested status scope, and mark the response as non-cached;
  - if QGA is unavailable, return the last cached data plus QGA/monitor error
    state when available.
- Desired-state apply commands such as export/share/target creation must still
  execute synchronously through the existing async job -> host Agent -> QGA ->
  `ablestack-storagectl` path. The monitoring cache is a read optimization, not
  the authority for configuration changes.
- After a successful desired-state apply, `ablestack-storagectl` should notify
  or trigger the monitor to refresh the affected collector immediately where
  practical. Otherwise the UI may show the last cached value until the next
  monitor interval.
- The System VM template build must install the monitor executable, systemd
  unit, logrotate or journald policy, cache directory creation, and service
  enablement. Fresh Storage Service System VMs should start the monitor during
  boot before protocol status APIs are used.
- Sensitive values such as SMB AD passwords, CHAP secrets, and DH-HMAC-CHAP
  keys must never be written to monitor cache files. Cache files may record
  authentication mode and enabled/disabled state only.
- The UI should display cache freshness where useful, especially on status
  tables and common detail status. A stale cache should be visible as a warning
  but should not block operators from seeing the last known state.

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
- Monitoring:
  - `ablestack-storage-monitor`
  - `ablestack-storage-monitor.service`
  - `/run/ablestack-storage/monitor` runtime cache directory
  - collectors for health, inventory, sessions, and capacity cache files
  - boot-time service enablement and journald/log retention policy

Template strategy:

1. Phase 1: add packages and storage control scripts to the common System VM
   template to minimize ABLESTACK template-selection changes.
2. Phase 2: introduce a dedicated `storageservicevmtemplate` build profile if
   image size, security scope, or operational separation requires it.

If a dedicated template is introduced, the manager must stop relying only on
`findSystemVMReadyTemplate(zoneId, hypervisor)` and select the Storage Service
template explicitly.

NVMe-oF template and service-offering strategy:

- `KERNEL_NVMET` mode belongs in the normal Storage Service System VM template.
- `SPDK` mode is kept as a Storage Service extension point, but it must not own
  HugePage, NUMA, CPU pinning, memlock, SR-IOV, or PCI passthrough controls.
  Those controls belong to a future VM Runtime Capability feature in the VM
  creation and management layer.
- Until the VM Runtime Capability feature exists, Storage Service should expose
  only SPDK design metadata and prerequisite reporting. It must reject actual
  SPDK enablement with a clear `PreparationRequired` state that points to the
  missing VM runtime capability support.

## NFS Design

NFS must be upgraded from the legacy fixed `/export` share to first-class export
management. In the expanded Storage Service design, `/export` is not a
user-facing NFS export. It is a legacy SharedFS compatibility root and may be
used only as an internal mount/root directory for existing lifecycle code while
the compatibility layer is being retired.

The Storage Service must never advertise `/export` itself through NFS or SMB.
The client-visible service name is always the protocol object name, not the
internal filesystem path:

- NFS clients mount `<service-ip>:/<export-name>`.
- SMB clients mount `\\<service-ip>\<share-name>`.
- iSCSI clients discover/login to the configured target IQN.
- NVMe-oF clients discover/connect to the configured subsystem NQN.

This is a cross-protocol rule, not an NFS-only rule. Regardless of where the
data is actually stored inside the Storage Service VM, the externally exposed
root identifier is the operator-defined protocol object name: export name for
NFS, share name for SMB, target IQN for iSCSI, and subsystem NQN for NVMe-oF.
UI connection guidance, API responses intended for operators, and SystemVM
runtime rendering must derive client connection examples from those protocol
object names. Internal backing directories or device paths may be shown for
administration, capacity, and troubleshooting, but they must not be presented
as the client root path.

The internal directory may be `/export/<share-name>` during the SharedFS
compatibility transition, or the preferred long-term path
`/srv/ablestack-storage/.../<share-name>`. Operators may choose the internal
directory, but the UI and API must reject the root path `/export` as an
internal share/export path. They should also normalize and validate
user-provided paths so that an operator cannot accidentally publish the entire
backing filesystem root. The UI must label this field as an internal/backing
directory and show the client-visible root name separately.

Features:

- export create/update/delete
- export enable/disable
- internal export directory management
- client-visible export name management
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

- Phase 1: one backing ABLESTACK volume per NFS export.
  - The volume size is the hard capacity boundary.
  - Resize maps to ABLESTACK volume resize plus filesystem grow.
  - Snapshot, backup, and delete are straightforward.
- Phase 2: multiple exports on one backing volume with XFS project quota.
  - Better density.
  - More complex reconciliation and recovery.

Runtime implementation:

- Mount export volumes under a controlled root, for example:
  `/srv/ablestack-storage/nfs/<share-uuid>`
- During the SharedFS compatibility transition, the legacy data disk may still
  be mounted at `/export`, but `/export` itself must not be rendered into
  `/etc/exports` or `/etc/exports.d`.
- User-facing NFS exports must be root-level service names, for example:
  `/nfs01` for export name `nfs01`. They must not expose the internal backing
  path such as `/export/nfs01` or
  `/srv/ablestack-storage/nfs/<share-uuid>`.
- `ablestack-storagectl` must create a controlled bind-mount alias from the
  internal backing path to the root-level client-visible export name, and render
  only the alias path into `/etc/exports.d`.
- The alias path is derived from the export name by allowing
  `[A-Za-z0-9_.-]` and normalizing other characters to `-`; names that would
  resolve to `/`, `/export`, or a path under `/export` are invalid.
- NFS ACL principal `0.0.0.0/0` or `::/0` is an operator-facing "allow all"
  CIDR value. Runtime exports must render it as `*` so Linux NFS export
  matching behaves as intended, while API/UI can keep showing the CIDR value
  the operator entered.
- Root Squash must remain the safe default, but the product must make the
  resulting POSIX permission behavior explicit. When `root_squash` is enabled,
  a client-side root process is mapped to the anonymous NFS UID/GID, so a
  `root:root 0755` backing directory is not writable by that client. This is
  correct Linux NFS behavior, not a connection failure.
- For operator-friendly behavior, a read/write NFS export with Root Squash
  enabled defaults to an anonymous-write POSIX profile when the operator does
  not specify POSIX values: `anonUid=65534`, `anonGid=65534`,
  `ownerUid=65534`, `ownerGid=65534`, `mode=0775`, and
  `recursivePermission=false`. The UI must show these effective defaults in
  the export dialog and tables. The SystemVM apply path must also compute the
  same effective defaults for older export configs so an existing export can be
  repaired by reapplying desired state.
- NFS export create/update APIs must support the following permission controls
  in the export config JSON:
  - `rootSquash`
  - `allSquash`
  - `anonUid`
  - `anonGid`
  - `ownerUid`
  - `ownerGid`
  - `mode`
  - `recursivePermission`
- NFS ACL create/update APIs may override the squashing and anonymous UID/GID
  behavior per principal with `rootSquash`, `allSquash`, `anonUid`, and
  `anonGid`.
- `ablestack-storagectl` must apply POSIX owner/mode to the internal backing
  directory before rendering `exportfs` rules. Recursive application is an
  explicit operator choice because it can be expensive and destructive on
  existing data.
- A recommended write-compatible profile for root-squashed test clients is:
  `rootSquash=true`, `anonUid=65534`, `anonGid=65534`, backing directory owner
  set to the same UID/GID, and a mode such as `0770` or `0775` depending on the
  sharing policy. The default profile uses `0775`.
  If every client identity should be collapsed to one shared identity, use
  `allSquash=true` with the same anonymous UID/GID instead of disabling Root
  Squash.
- `no_root_squash` is allowed only as an explicit advanced choice and the UI
  must make clear that it trusts client-side root.
- Protocol enablement may request a listen IP that is not currently configured
  on the Storage Service VM. The UI should offer "existing IP" and "new IP"
  modes. The final runtime validator is `ablestack-storagectl`: it must inspect
  current VM NIC IPv4 addresses, verify the requested listen IP is in the same
  CIDR as an existing NIC, and add it as a secondary address before applying
  the protocol desired state.
- Render export rules into:
  `/etc/exports.d/ablestack-<share-uuid>.exports`
- Do not render legacy cloud-init or SharedFS-generated `/export` rules when
  the expanded Storage Service feature is enabled. If an old template or
  cloud-init payload creates `/export` export rules, `sharedfsvm.sh` or
  `ablestack-storagectl` must remove or neutralize those rules before applying
  Storage Service desired state.
- Apply with:
  `exportfs -ra`
- Report state with:
  `exportfs -v`
  `df`
  `findmnt`

Path validation rules:

- Reject `/export`, `/export/`, `/`, empty paths, relative paths, and paths with
  traversal segments such as `..` for the internal/backing directory.
- Allow managed child backing paths under `/export/<name>` only for
  compatibility SharedFS-backed instances.
- Prefer native Storage Service paths under `/srv/ablestack-storage/...` for new
  protocol objects.
- Display a clear UI hint that the selected path is the internal directory. The
  client mount example must use the share/export name as the root export, for
  example `mount -t nfs <service-ip>:/nfs01 <mount-path>`.

## Existing Volume Attachment For File Services

The Storage Service must support exposing an existing ABLESTACK volume through
NFS or SMB. This is required for data migration, recovery, and converting an
existing data disk into a managed file service without copying data.

Supported attachment modes:

- New volume mode
  - The UI creates a new ABLESTACK data volume from the selected disk offering
    and passes the resulting `volumeid` to the Storage Service export/share API.
  - The manager attaches that volume to the Storage Service System VM before
    applying the desired state.
  - QGA runs `volume attach inspect` with `FORMAT_EMPTY`; the SystemVM formats
    the device only when it has no filesystem signature, then mounts and
    exports it.
- Existing volume mode
  - The caller passes an existing `volumeid`.
  - The manager validates that the volume is detached or can be safely detached
    from its current owner according to ABLESTACK volume rules.
  - The manager attaches the volume to the Storage Service System VM.
  - QGA discovers the device by stable disk metadata, probes filesystem
    signatures, and reports the result.
  - The default import mode is non-destructive `MOUNT_EXISTING`.

Existing volume safety rules:

- Never format a volume by default.
- If no supported filesystem is detected, fail with a clear error unless
  `FORMAT_EMPTY` was requested for a newly created empty volume.
- Destructive force-format is not exposed in this implementation. If it is
  added later, it must be explicit, async, and auditable.
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
   serial, WWN, or ABLESTACK-provided metadata.
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

## Runtime Endpoint Authority

ABLESTACK VM/NIC metadata can be incomplete or ambiguous for Storage Service
System VMs. In particular, a secondary address can be reported as the VM
`ipaddress` while the guest still has a different primary address. Therefore:

- The SystemVM monitor cache must include guest-visible IPv4 addresses from
  `ip addr`, including CIDR prefix and primary/secondary role.
- UI endpoint lists and duplicate checks must prefer this runtime evidence over
  API NIC metadata when the same IP appears in both sources.
- Protocol endpoint deletion must treat guest primary IPs as non-removable and
  allow only configured or guest secondary listen IPs to be removed.
- Backend CIDR preflight may use API NIC metadata as an optimization, but the
  final source of truth for a newly added listen IP is the SystemVM guest-side
  validation and application path.

## File Service Capacity Expansion

File-service resize must be an explicit async workflow that coordinates
ABLESTACK volume resize with guest-side filesystem growth.

API direction:

- `resizeStorageFileShare`
  - Parameters: `id`, `size`, `quotabytes`, `resizevolume`, optional
    `shrink=false`.
  - Applies to NFS exports and SMB shares.
  - The first implementation supports grow only.
  - `size` follows existing ABLESTACK volume resize semantics and is an
    integer GiB-compatible value.
  - `quotabytes` is a per-share file-service capacity limit in bytes. The UI
    must collect it through a value plus IEC unit selector and convert it to
    bytes before submission.
- Existing SharedFS `changeSharedFileSystemDiskOffering` remains the
  compatibility API and can later call this workflow internally.

Resize workflow:

1. Validate the target share and backing volume.
2. Validate the requested backing volume size, when supplied, is larger than
   the current volume size.
3. Ask ABLESTACK to resize the backing volume or change the disk offering only
   when `resizevolume=true`.
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

Capacity meaning:

- Backing disk size is the size of the ABLESTACK volume attached to the
  Storage Service System VM. It is the physical upper bound for the file share
  or block target.
- File share quota is a protocol resource limit:
  - NFS quota applies to the specific NFS export path.
  - SMB quota applies to the specific SMB share path.
  - If NFS and SMB expose different paths, they must have independent quota
    fields.
  - If NFS and SMB intentionally expose the same path, the UI should show that
    the quota is shared by both services.
- iSCSI and NVMe-oF should not use "file share quota" wording. They should use
  `LUN size` and `Namespace size`, stored as bytes in the expanded model and
  collected with the same IEC unit selector in the UI.
- Backing disk resize permission should be worded as `Allow data disk resize
  when required`, displayed in Korean as `필요 시 데이터 디스크 확장 허용`.
  This means the workflow may resize the ABLESTACK backing volume if a
  requested export/share/LUN/namespace size cannot fit in the current backing
  volume. It is not itself a quota.

Existing volume capacity behavior:

- When `EXISTING_VOLUME` is selected, the create dialog must not ask for a new
  data disk size.
- The UI must show the selected existing volume's actual size.
- If the selected volume size differs from a previously entered data disk size,
  the existing volume size wins and the new-disk size is ignored.
- If a service quota, LUN size, or namespace size is greater than the selected
  existing volume size:
  - fail validation when backing disk resize is not allowed;
  - attempt the ABLESTACK volume resize first when backing disk resize is
    allowed and the volume/disk offering permits resize.

Failure handling:

- If ABLESTACK volume resize succeeds but filesystem grow fails, mark the
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
- CHAP enable/disable and mutual CHAP enable/disable per initiator ACL
- CHAP user names stored in the desired-state model, with CHAP secrets passed
  only in the async API/QGA runtime payload
- target session listing
- LUN resize

Capacity is controlled by the backing volume size. When an operator supplies an
explicit raw `backingPath`, the SystemVM may expose that block device directly
through a LIO block backstore only if the device is not mounted. When the
Storage Service is using the default SharedFS data disk, the disk can already be
mounted by the legacy `/export` initialization path. In that case iSCSI must
create a managed file-backed LUN under the backing volume, for example
`/export/.ablestack-storage/iscsi/<target-uuid>.img`, and expose it through a
LIO `fileio` backstore. This avoids unmounting or destructively reformatting the
SharedFS backing disk while still presenting a block LUN to clients.

The QGA apply path must fail the async operation if:

- `targetcli` is missing while enabled targets exist
- the backing block device or file-backed LUN cannot be resolved or created
- target/LUN/portal creation fails
- TCP 3260 is not listening after target application and service restart

The runtime monitor cache must include iSCSI listen status, target inventory,
and the latest generated timestamp so the UI can render fast status without
running expensive target inspection on every page refresh.

## NVMe-oF Design

NVMe-oF should start with TCP transport.

Features:

- subsystem NQN create/update/delete
- namespace create/delete
- backing volume mapping
- host NQN ACL
- optional DH-HMAC-CHAP host authentication and controller authentication for
  host ACLs, with keys passed only in the async API/QGA runtime payload
- discovery information
- namespace resize

DH-HMAC-CHAP enforcement is mandatory when requested. `ablestack-storagectl`
must verify that the selected SystemVM kernel/configfs exposes the required
`dhchap_key` and `dhchap_ctrl_key` attributes before reporting a host ACL as
applied. If the template/kernel cannot enforce the requested authentication,
the QGA apply command must fail and the affected ACL must remain `Error`
instead of being reported as `Ready`.

DH-HMAC-CHAP is capability gated. `ablestack-storagectl health` and
`ablestack-storagectl inventory` must report
`capabilities.nvmeof.dhChapSupported` and
`capabilities.nvmeof.dhChapCtrlSupported` by probing the live
`/sys/kernel/config/nvmet/hosts/<sample-host>/dhchap_key` and
`dhchap_ctrl_key` attributes. The UI must disable DH-HMAC-CHAP host and
controller authentication controls when those attributes are missing, while
still displaying that DH-HMAC-CHAP authentication is unsupported by the current
SystemVM kernel/configfs. For create workflows where the target SystemVM does
not exist yet, the current Storage Service SystemVM template baseline is treated
as unsupported until a future template exposes the capability.

DH-HMAC-CHAP secrets are one-time runtime inputs only. They may exist in the API
request, async job payload, and QGA apply payload long enough to apply configfs,
but must be removed before writing SystemVM desired-state files, monitor cache
files, runtime API result JSON, UI tables, logs, events, or validation records.
Persisted ACL configuration may retain only enabled flags and non-secret
identity fields.

The SystemVM session monitor should track first-seen and last-seen timestamps
in its local runtime cache. NVMe-oF sessions should include the subsystem NQN
when it can be unambiguously derived from the active subsystem state; when it
cannot be derived, the UI must show an unknown value rather than implying that
no subsystem is attached.

For NVMe-oF TCP sessions, the monitor may derive the live connection set from
`ss` because kernel target sessions are transport-level connections. The
collector must then enrich those rows from
`/etc/ablestack-storage/nvmeof-subsystems.json`:

- if the runtime request includes a matching `resourceId` or subsystem NQN,
  assign that subsystem to the session rows;
- if exactly one active subsystem is present, assign that subsystem NQN and
  resource ID to all NVMe-oF TCP session rows;
- if more than one active subsystem exists and the session cannot be mapped
  unambiguously, leave the subsystem field unknown instead of inventing a
  mapping.

The session cache key should stay stable for each TCP peer/local tuple so the
monitor can retain `connectedAt` and update `lastSeen` across polling cycles.

Runtime can use `nvmetcli` if available, or manage Linux `configfs`
directly through `ablestack-storagectl`.

The first implementation uses the shared `storage_block_target` table for both
subsystems and namespaces. A subsystem row owns the NQN and host ACLs, and
namespace rows reuse the same NQN with their namespace ID and backing volume.

NVMe-oF desired-state lifecycle consistency:

- The API/engine may use transient DB states such as `Creating` or `Updating`
  while an async job is applying a subsystem, namespace, or host ACL.
- Transient states must not be persisted into the System VM desired-state files
  after a successful apply. The QGA payload should represent the final intended
  runtime state for the object being reconciled.
- For the current state model, the successful runtime state is `Ready` when a
  Storage Service System VM exists and `Allocated` when the object is stored but
  cannot yet be applied because no System VM is mapped.
- If QGA apply fails, the engine must persist the affected API object as
  `Error` and return the failure to the async job. This prevents stale
  `Creating`/`Updating` rows from being shown as pending forever and prevents
  the System VM state file from disagreeing with API state after a later
  successful reapply.
- Host ACL secret values remain runtime-only: DH-HMAC-CHAP keys may be present
  in the one-time QGA payload but must not be returned by list APIs or stored in
  UI state.

### NVMe-oF Engine Modes And VM Preparation

NVMe-oF should support two target engines:

- `KERNEL_NVMET`
  - Default first implementation.
  - Uses Linux kernel `nvmet` with configfs and TCP transport.
  - Requires kernel modules such as `nvmet`, `nvmet-tcp`, and `configfs`.
  - Does not require guest hugepages by design.
- `SPDK`
  - Planned high-performance mode.
  - Uses an SPDK NVMe-oF target process.
  - Requires future VM Runtime Capability support for HugePage, NUMA, CPU
    pinning, memlock, and optional PCI passthrough or SR-IOV/VF assignment.
  - Stays in this Storage Service design only as protocol metadata,
    prerequisite reporting, and a later integration target.

The API `prepareStorageServiceNvmeOfVm` should prepare or validate the System VM
for the requested engine before NVMe-oF subsystems are enabled.

Parameters:

- `instanceid`
- `engine`: `KERNEL_NVMET` or `SPDK`
- `transport`: initially `tcp`
- `runtimecapabilityprofileid`: future parameter, accepted only after VM
  Runtime Capability support is implemented
- `validateonly`: report missing prerequisites without changing the VM

Management-side VM preparation:

1. Validate the Storage Service System VM service offering.
2. For `KERNEL_NVMET`, ensure the template has `nvme-cli`, `nvmetcli` when
   available, and a kernel with NVMe target modules.
3. For `SPDK`, do not attempt guest-side HugePage or NUMA changes from Storage
   Service. Return `PreparationRequired` until a VM Runtime Capability profile
   can be attached to the System VM.
4. Generate a desired capability document in `StorageServiceProtocol.config_json`.
5. Send QGA `nvmeof prepare` to the System VM only for supported engine states.

Deferred SPDK integration:

1. VM management adds Runtime Capability profiles for HugePage, NUMA, CPU
   pinning, memlock, and optional passthrough or SR-IOV.
2. Storage Service accepts `runtimecapabilityprofileid` for SPDK requests and
   validates that the target System VM was created or restarted with that
   profile.
3. The System VM template verifies SPDK runtime packages, `setup.sh`
   prerequisites, `hugetlbfs`, service limits, and SPDK target service state.
4. QGA `nvmeof prepare` reports SPDK readiness only after the VM-level runtime
   profile is present and active.

The first implementation must keep `KERNEL_NVMET` as the only enabled NVMe-oF
engine. The UI may show `SPDK` as planned or unavailable, but it must not offer
HugePage, CPU, NUMA, memlock, SR-IOV, or PCI passthrough controls inside the
Storage Service workflow.

## UI Design

The UI must extend the existing `Shared FileSystems` menu, creation dialog, and
detail page. The first implementation must not introduce a completely separate
required menu for the expanded Storage Service workflow. Operators should start
from the existing `Shared FileSystems` list and use the existing
`Create Shared FileSystem` action to create a SharedFS-backed Storage Service.
The detail page is reserved for post-creation operations and ongoing management.

The UI implementation must follow the existing Vue and Ant Design Vue patterns
used by the ABLESTACK UI. The SharedFS extension must reuse the current
section/action, detail tab, async job polling, status, metric, and event tab
conventions instead of introducing an unrelated visual system.

Styles must be compatible with both normal and dark modes. New components should
use existing Ant Design Vue component states and local ABLESTACK UI styling
conventions. Local SharedFS extension styles must use theme-friendly borders,
backgrounds, inherited text colors, and restrained neutral surfaces so colors,
forms, status indicators, disabled states, lists, and runtime JSON remain
readable in both modes.

SharedFS creation dialog extension:

- The existing `Create Shared FileSystem` dialog becomes the primary creation
  workflow for Storage Service-backed SharedFS instances.
- The dialog must allow the operator to select one or more initial services:
  `NFS`, `SMB`, `iSCSI`, and `NVME_OF`.
- At least one service must be selected. For backward compatibility, `NFS`
  remains the default selection when the expanded Storage Service feature is
  enabled and no explicit protocol selection is supplied.
- The dialog must create the SharedFS/SystemVM resource and then create or
  reconcile the corresponding Storage Service model through async Cloud APIs.
- Protocol-specific desired state is submitted after the SharedFS VM exists and
  the Storage Service instance is available. The UI must poll each async job and
  present creation progress without running any storage command directly.

Creation dialog structure:

- `Owner type`
  - stays at the top of the dialog, before the main creation form, because
    ownership affects all subsequent resource choices
  - is collapsed by default for operators because the default owner is usually
    correct and rarely edited during normal creation
  - the collapsed header must keep the section title as `Owner type` and append
    the currently selected type/domain/account or project summary in
    parentheses, for example `Owner type (Account / ROOT / admin)`
  - expanding the section exposes the existing owner selector without changing
    its behavior
- `Basic information`
  - name, description, zone, network, filesystem, service offering, disk
    offering, data disk size in GiB for new-volume mode, and custom IOPS
  - data disk size represents the ABLESTACK backing volume size, not an
    NFS/SMB share quota or an iSCSI/NVMe-oF protocol object size
  - when existing-volume mode is selected, data disk size is disabled or hidden
    and the selected existing volume's actual size is shown instead
- `Volume and backing capacity`
  - must appear immediately after `Basic information` and before protocol
    service configuration
  - owns only physical/backing storage decisions: new data volume, existing
    detached ABLESTACK volume, selected volume size, import mode, backing
    filesystem, and whether backing data disk resize is allowed
  - detached candidate volumes must be selected from a dropdown, not typed as a
    raw volume ID, and the UI must display the selected volume's actual size
  - the UI label for resize permission should be `Allow data disk resize when
    required` / `필요 시 데이터 디스크 확장 허용`
  - the help text must explain that this controls whether the ABLESTACK
    backing volume may be resized to satisfy a larger export/share/LUN/namespace
    request; it is not the same as the service capacity limit
  - service-level quota, LUN size, and namespace size do not belong in this
    section; they belong to the owning protocol section below
- `Service selection`
  - compact selectable service cards or checkboxes for NFS, SMB, iSCSI, and
    NVMe-oF
  - desktop card layout must show one or two cards per row only; four services
    should be shown as a balanced two-by-two grid, not a three-plus-one layout
  - clear selected and unavailable states
  - a visible validation message when no service is selected
- `NFS`
  - visible when NFS is selected
  - initial export name, path, read/write policy, root squash, sync/secure
    flags, allowed CIDR ACL, and export capacity limit
  - export capacity limit is scoped to this NFS export path and is entered as a
    number plus `B`/`MiB`/`GiB`/`TiB`, then submitted as `quotabytes`
- `SMB`
  - visible when SMB is selected
  - initial share name, path, guest/browse/read-only flags, SMB ACL intent, and
    share capacity limit
  - share capacity limit is scoped to this SMB share path and is entered as a
    number plus `B`/`MiB`/`GiB`/`TiB`, then submitted as `quotabytes`
  - local account mode by default
  - local account mode must collect an initial local user name, password,
    password confirmation, and ACL permission. The password is submitted only in
    the asynchronous ACL request and must never appear in the review panel,
    result tables, monitor cache, browser storage, or API logs.
  - optional Active Directory domain join section with domain, username, target
    OU, DNS/server hints, and sensitive password submission
- `iSCSI`
  - visible when iSCSI is selected
  - initial target name, target IQN, LUN size, LUN or backing volume, initiator
    IQN ACL, CHAP use, CHAP user/secret, mutual CHAP use, and mutual CHAP
    user/secret
  - LUN size is entered as a number plus `B`/`MiB`/`GiB`/`TiB`; it is not a
    file share quota
  - the review panel shows only whether CHAP and mutual CHAP are enabled, never
    the secret values
- `NVMe-oF`
  - visible when NVMe-oF is selected
  - initial subsystem NQN, namespace size, namespace or backing volume, engine
    selection, host NQN ACL, optional DH-HMAC-CHAP host/controller
    authentication, and prerequisite-gated SPDK state
  - namespace size is entered as a number plus `B`/`MiB`/`GiB`/`TiB`; it is not
    a file share quota
  - DH-HMAC-CHAP keys are available only when a host NQN ACL is configured, and
    the review panel shows only whether authentication is enabled
  - if the current SystemVM template/kernel capability does not support
    DH-HMAC-CHAP, the host and controller authentication switches are disabled
    and the dialog shows an explicit unsupported message
- `Existing volume`
  - this is part of `Volume and backing capacity`, not a protocol sub-section
  - supports attaching an existing ABLESTACK volume to the new service
  - import mode must distinguish inspect-only, mount-existing, and format/new
    workflows so destructive operations require an explicit confirmation
- `Capacity`
  - do not use one global capacity section for both physical backing disk and
    service limits
  - physical backing disk controls belong to `Volume and backing capacity`
  - service capacity controls belong to the owning protocol section:
    NFS export capacity limit, SMB share capacity limit, iSCSI LUN size, and
    NVMe-oF namespace size
- `Review`
  - summarizes selected services, network, storage, identity, access rules,
    destructive actions, and known prerequisites before submission
  - review items must prioritize value readability over fixed two-column
    alignment. Labels and values should be stacked within the narrow review
    panel so long SharedFS names, network names, volume labels, IQNs, NQNs, and
    other identifiers can use the full panel width.
  - review values must allow safe wrapping for long identifiers with no natural
    whitespace. Do not truncate critical identifiers unless a tooltip or copy
    path preserves the full value.

Dialog usability rules:

- The dialog must be sectioned and progressive. It should not present every
  protocol field at once.
- The dialog layout must use a two-column operator workflow on desktop:
  - the left column is a persistent review/summary panel for selected services,
    name, zone, network, filesystem, identity mode, existing-volume mode, and
    import mode
  - the right column contains the actual configuration sections
  - on narrow/mobile viewports the columns collapse to a single column
- The review panel is intentionally narrow. It must avoid fixed label/value
  columns that leave the value column too small. Each item should render as a
  compact label above a full-width value with `overflow-wrap` behavior suitable
  for long technical identifiers.
- The dialog action area must stay fixed or sticky at the bottom of the modal
  with `Cancel` and `OK` actions so long configuration sections do not hide the
  final decision controls.
- The dialog content height must never exceed the visible browser viewport. The
  modal body should use a bounded flex layout where owner selection, alert, and
  action areas are fixed-height regions, and only the central review/config
  region scrolls. The action buttons must not overlap protocol sections.
- Sections should be ordered by operator decision flow: identity and placement,
  backing volume/capacity, service selection, service-specific configuration,
  access, then review.
- Required fields should appear only when their owning service is selected.
- Disabled or prerequisite-gated options must explain the reason in place.
- Advanced fields should be collapsed by default and use existing Ant Design
  Vue controls such as collapse panels, alerts, checkboxes, switches, selects,
  and input groups.
- Info and warning alerts must remain legible in dark mode, including the alert
  icon, message text, border, and background. Avoid high-contrast filled alert
  surfaces that hide icons or make labels unreadable.
- The primary action should stay disabled until the minimum valid configuration
  is present. Validation errors must scroll to the first failing section.
- Async creation progress should show the current phase: SharedFS creation,
  Storage Service model creation, protocol enablement, export/share/target
  creation, ACL creation, and final refresh.
- The dialog must take an immutable setup snapshot at submit time and use that
  snapshot for all dependent asynchronous protocol/share/ACL jobs. Follow-up
  setup must not read mutable modal form state after the modal is closed or
  after password fields are cleared.
- The creation dialog must not display final Storage Service success merely
  because the SharedFS VM creation job succeeded. It must either complete and
  verify the selected initial service setup jobs or show a clear partial-create
  error with a retry path in `File Service Management`.
- Initial service setup must be treated as one UI-managed asynchronous
  transaction with explicit phases:
  `CREATE_SHAREDFS`, `RESOLVE_SHAREDFS`, `RESOLVE_STORAGE_SERVICE_INSTANCE`,
  `ENABLE_PROTOCOLS`, `CREATE_PROTOCOL_RESOURCES`, `CREATE_ACCESS_RULES`, and
  `VERIFY_RUNTIME_STATE`.
- The UI must wait for the complete setup transaction before closing the create
  dialog or showing a final success message. A successful `createSharedFileSystem`
  job alone is only a partial result.
- After `createSharedFileSystem` completes, the UI should reload the created
  SharedFS by ID and use that fresh response as the preferred source for VM,
  backing volume, zone, network, and account information. This reload is a
  freshness step, not the only gate for follow-up setup. If the reload returns
  a different wrapper shape or temporarily returns no row, the UI must fall back
  to the completed async job result and continue setup when the SharedFS ID and
  VM ID are already available.
- The setup code must accept both current and legacy response wrappers, including
  `sharedfs`, `sharedfilesystem`, `sharedfilesystems`, singular objects, arrays,
  and direct job-result objects. It must fail visibly only when the created
  SharedFS ID or the VM ID required to map the Storage Service instance cannot
  be resolved from either the async job result or the follow-up list response.
- The SharedFS creation result and initial Storage Service setup result must be
  reported separately. SharedFS VM creation success followed by protocol/share/
  ACL setup failure is `Created / setup incomplete`, not a failed VM creation
  and not full success.
- The creation dialog is an input collection surface only. After the operator
  presses `OK` and the create request is accepted, the dialog must close
  immediately. Long-running work must continue as background asynchronous setup
  with a persistent top notification or task banner that reports the current
  phase and final result.
- Background setup notification phases must be operator-readable:
  `SharedFS creation accepted`, `SharedFS VM creation`, `Storage Service
  instance resolution`, `protocol enablement`, `share/export/target creation`,
  `ACL/account/domain application`, and `runtime verification`.
- Partial failure notification must say which part failed. For example,
  `SharedFS was created, but SMB access rule application failed`. The detail
  page must remain reachable so the operator can inspect the partially created
  service and retry management actions.
- The UI must guarantee that selected service follow-up APIs are attempted after
  SharedFS creation: `enableStorageServiceProtocol` first, then the selected
  export/share/target and ACL/account/domain operations. A response parsing
  mismatch in the SharedFS refresh path must never silently skip these calls.
- Protocol enablement, share/export/target creation, ACL creation, and final
  verification errors must not be swallowed. If any selected initial service is
  not configured, the result is `Created / setup incomplete`, not success.
- Runtime monitor/cache verification is not a blocking creation gate. The final
  initial-setup result must be based on successful asynchronous API jobs and QGA
  desired-state application for protocol/export/share/target/ACL resources.
  Monitor cache inventory may lag immediately after apply, so a stale or empty
  cache row must not produce the partial-create error notification. The UI may
  poll the cache briefly for display freshness and log a non-blocking pending
  state, but API object existence and QGA job success remain the correctness
  gates.
- When creating a new initial backing data disk, the create dialog must require
  an explicit primary storage selection and send it as `storageid` together
  with disk offering and size. Existing-volume flows do not require `storageid`
  because the selected volume already determines its storage location. The
  current programmatic SharedFS VM deployment path uses
  `createAdvancedVirtualMachine`, which does not expose a primary-storage pool
  placement argument; therefore full placement enforcement requires a follow-up
  backend refactor of that deployment path or a split create-volume/attach
  workflow. Until that refactor, `storageid` is the UI/API contract for operator
  intent and compatibility, while normal ABLESTACK allocation errors still
  remain possible.
- SMB local account application inside the Storage Service System VM must be
  idempotent. For a local user ACL:
  - if the Linux user already exists, update/enable the Samba password only;
  - if the Linux user does not exist but a same-name group exists, create the
    user with that existing primary group instead of failing;
  - if neither user nor group exists, create the user normally;
  - reject empty or unsafe local user names before invoking OS account tools;
  - repeated application of the same ACL must not fail because of existing
    users, groups, or Samba passdb entries.
- SMB ACL desired-state rows must not mislead operators after guest-side apply
  failures. The backend should either roll back the row or mark it `Error` with
  `last_error`; the UI must display only actually applied rows as `Ready`.
- `File Service Management` must expose a retry action for incomplete initial
  setup. Retry APIs and UI orchestration must be idempotent: enabling an already
  enabled protocol or creating an already existing desired object should return
  the existing state or reconcile it rather than creating duplicates.
- The progress UI should show each setup phase and the current service being
  configured. This is required so operators can distinguish VM creation delays
  from Storage Service protocol setup failures.
- Sensitive fields, especially SMB AD credentials, must not be persisted in UI
  state beyond submission and must not be shown in review text.
- SPDK may appear only as planned or prerequisite-gated. HugePage, CPU pinning,
  NUMA, memlock, SR-IOV, and PCI passthrough must not appear in this creation
  dialog because they belong to the future VM Runtime Capability feature.
- The creation dialog must explicitly support normal and dark mode. Section
  containers, collapse content, review panels, service cards, form labels, input
  backgrounds, borders, warning/info surfaces, and sticky footer surfaces must
  use theme-friendly inherited colors and restrained neutral surfaces. The UI
  must not leak a hard-coded light background into dark mode.

SharedFS detail extension:

- Storage Service creation controls must not be the primary workflow in the
  detail tab. If an existing legacy SharedFS resource has no mirror, the detail
  tab may show a reconcile action for administrative recovery only.
- The detail page must split service operation and service monitoring into two
  separate tabs:
  - `File Service Management`, displayed in Korean as `?뚯씪 ?쒕퉬??愿由?.
  - `File Service Status`, displayed in Korean as `?뚯씪 ?쒕퉬???곹깭`.
- The `?뚯씪 ?쒕퉬??愿由? tab owns configuration and state-changing workflows:
  - mirrored Storage Service instance summary, provider, VM ID, and service IPs
  - protocol enablement for NFS, SMB, iSCSI, and NVMe-oF listen IP and port
  - NFS export, capacity limit, root squash, sync/secure flags, and ACL creation
  - SMB share, capacity limit, guest/browse/read-only flags, ACL creation, and
    Active Directory domain join form
  - iSCSI target creation/listing and initiator ACL creation
  - NVMe-oF preparation, subsystem creation/listing, and host NQN ACL creation
  - existing ABLESTACK volume attach/import to a file share with inspect-only
    or mount-existing mode
  - file share capacity expansion and optional backing ABLESTACK volume resize
- The `?뚯씪 ?쒕퉬???곹깭` tab owns monitoring and read-only runtime views:
  - QGA-backed health result
  - service IPs and endpoint summary
  - active NFS/SMB/iSCSI/NVMe-oF object counts
  - runtime inventory
  - active sessions
  - SMB domain status
- Tab labels, form labels, button labels, descriptions, warnings, and empty
  states must use i18n keys. The Korean UI should avoid English text except
  protocol names, technical identifiers, acronyms, command values, or other
  terms that should remain untranslated such as `NFS`, `SMB`, `iSCSI`,
  `NVMe-oF`, `IQN`, `NQN`, `SPDK`, and `QGA`.

SharedFS detail page redesign:

This subsection supersedes the earlier split between `File Service Management`
and `File Service Status`. The detailed UI must now be protocol-oriented while
keeping common service state in the first details tab.

- The legacy `Access` tab must be removed from the expanded Storage Service
  UI. It exposes only the old NFS mount pattern and can show the deprecated
  `/export` root, which conflicts with the new explicit export/share model.
- The previous `File Service Management` and `File Service Status` tabs must be
  replaced by protocol-oriented tabs:
  - `NFS`
  - `SMB`
  - `iSCSI`
  - `NVMe-oF`
- The first `Details` tab remains the common overview tab. It must show both
  the existing SharedFS details and an expanded Storage Service overview:
  - active service types, for example `NFS`, `SMB`, `iSCSI`, and `NVMe-oF`
  - Storage Service instance state, provider, service VM ID, and service VM name
  - service VM power state, host, template, service offering, and data disk
    summary
  - QGA health, last health check status, and last command timestamp
  - service endpoint IPs and ports
  - common capacity summary: backing data disk size, attached volume, and
    whether data disk resize is allowed
  - aggregate object counts such as number of exports, shares, targets,
    namespaces, ACLs, and active sessions
  - warnings for partial setup, missing QGA, missing protocol packages, stale
    desired state, or legacy `/export` root exposure
- Common state belongs in `Details`; protocol-specific state belongs in the
  protocol tab. Do not repeat common SystemVM/QGA status cards in every
  protocol tab unless a protocol-specific error needs to be highlighted.
- Each protocol tab must follow one consistent information architecture:
  - service status: protocol enabled state, daemon/runtime status, listen IP,
    port, last apply result, and last inventory refresh
  - resource inventory:
    - NFS: exports
    - SMB: shares and domain membership summary
    - iSCSI: targets, LUNs, and initiator rules
    - NVMe-oF: subsystems, namespaces, engine state, and host rules
  - capacity and backing storage: backing volume, mount or block path, quota,
    LUN size, namespace size, used/free values when available, and resize
    eligibility
  - connection guidance:
    - NFS mount and `showmount` examples for the selected export path
    - SMB UNC and Linux CIFS mount examples for the selected share
    - iSCSI discovery/login examples for the selected target
    - NVMe-oF discovery/connect examples for the selected subsystem
  - access allow list: CIDR, SMB account/group, initiator IQN, host NQN, CHAP,
    mutual CHAP, or DH-HMAC-CHAP state as appropriate
  - active sessions: client, protocol, local/peer addresses, state, associated
    resource when known, age when available, and last refresh time
- All state-changing operations in protocol tabs must use action buttons that
  open modals. Inline state-changing forms should be avoided. This keeps NFS,
  SMB, iSCSI, and NVMe-oF workflows consistent and easier to test.
- Required action modal families:
  - service enable/disable or protocol setting update
  - create/update/delete export/share/target/subsystem/namespace
  - create/update/delete access rule
  - attach existing ABLESTACK volume
  - resize backing data disk or protocol capacity
  - AD join/leave for SMB
  - prepare or validate NVMe-oF engine prerequisites
  - disconnect active session
- Action modals must reuse the completed SharedFS creation modal visual
  language:
  - sectioned layout with clear titles and restrained borders
  - Ant Design Vue form controls and validation states
  - normal and dark mode compatible backgrounds, labels, borders, alerts,
    selected cards, disabled states, tables, and sticky footers
  - fixed footer with `Cancel` and `OK` actions for complex modals
  - no hard-coded light surfaces that leak into dark mode
  - no low-contrast inherited text for radio labels, checkbox labels, table
    text, alert icons, empty states, or command snippets
  - service command examples should use code blocks or copyable text surfaces
    whose background and foreground colors are legible in dark mode
- Protocol tab layout and table standard:
  - This standard applies to all Storage Service protocol tabs: `NFS`, `SMB`,
    `iSCSI`, and `NVMe-oF`.
  - Each protocol tab must fill the available detail-tab width and should avoid
    small isolated panels that leave large unused empty space. Low-data states
    must still render the same sections and tables with clear empty states.
  - The top area should contain a compact protocol summary and right-aligned
    action toolbar. All action buttons must include an icon and open a modal for
    state-changing operations.
  - Dense repeated information must be rendered as tables, not as card lists.
    Use tables for exports/shares/targets/subsystems, ACLs, backing volumes,
    active sessions, and protocol-specific runtime status where multiple rows or
    comparable fields are possible.
  - Non-repeated guidance, warnings, and one-off summaries may use cards. Cards
    must be full-width or part of an intentional grid and must not appear as
    small floating islands.
  - Tables must follow the existing ABLESTACK network-tab table look and feel:
    compact row height, restrained borders, readable header contrast, and
    normal/dark mode compatible background and hover states.
  - Tables with many columns must use internal horizontal scrolling rather than
    breaking the tab layout. The tab itself should remain stable while the table
    body scrolls inside its own boundary.
  - Table scrollbars must be visually small and must match the normal and dark
    mode visual tone.
  - Important identifier columns must remain visible with fixed columns when
    the table scrolls horizontally. At minimum, the resource name or primary
    identifier should be fixed to the left, and row actions should be fixed to
    the right when actions are present.
  - Long values such as volume IDs, share paths, export paths, IP/CIDR lists,
    IQNs, NQNs, session identifiers, command examples, and error strings must
    use single-line ellipsis in table cells with a tooltip that shows the full
    value. Where copying is useful, the tooltip or cell control should expose a
    copy action.
  - Avoid wrapping long protocol identifiers across multiple table lines by
    default. Wrapping may be used only in expanded-row details or explicit
    detail drawers.
  - Tables should support expanded rows or detail drawers for secondary fields
    that would otherwise create too many columns.
  - Empty tables must still display headers and a localized empty message so
    operators can understand which data category is absent.
  - Numeric capacity fields should show human-readable IEC units in the table
    and keep exact byte values available in a tooltip or detail view.
  - Status columns should use compact tags or badges with theme-compatible
    colors. Do not rely only on color; include readable status text.
  - Any inline error, warning, or partial-state indicator in a table must be
    readable in dark mode, including icon, tag text, and tooltip text.
  - Pagination should be avoided for small protocol inventories, but large
    session or ACL tables may paginate inside the table boundary.
  - The same column behavior should be used consistently across service tabs:
    fixed resource identifier, scrollable secondary fields, fixed actions,
    ellipsis plus tooltip for long values, compact scrollbar, and dark-mode
    safe colors.
- NFS tab table standard:
  - `NFS exports` table: export name, client-visible mount root, internal path,
    service IP/port, permission, ACL summary, quota/capacity, backing volume,
    runtime state, and actions.
    The only row action in this table should be file-share capacity expansion;
    ACL creation belongs to the ACL table action area. Do not duplicate the
    same capacity action in both the section header and each row.
  - `NFS ACLs` table: export name, principal or CIDR, permission, root squash,
    all squash, anonymous UID/GID, sync, secure port, runtime state, and
    actions.
    The section action area must include NFS ACL creation. ACL rows are mapped
    to exports by the Storage Service export ID, including `resourceid` fields
    returned by access-rule APIs.
  - NFS action modals must be vertical forms, not wide horizontal rows. The
    protocol enable modal must include existing/new listen IP mode selection.
    All action modals must be centered in the browser viewport horizontally
    and vertically. The modal header and footer remain fixed, and only the
    body scrolls when the form is taller than the viewport.
    Field help must be provided through required markers, tooltip icons, and
    validation messages. Persistent explanatory text below every normal input
    should be avoided because it makes the dialog noisy; inline help is reserved
    for actual validation errors or exceptional warnings.
    Existing listen IP choices must be deduplicated across NIC primary and
    secondary IP data. When the operator adds a new listen IP, the backend must
    validate that it is in the same CIDR as one Storage Service NIC, reject IP
    conflicts, persist it as a secondary IP for that NIC, and the SystemVM
    runtime must open the selected TCP service port when applying the desired
    protocol state. CIDR validation must not depend only on the NIC `netmask`
    column, because L2/ConfigDrive networks may not expose that value reliably.
    The resolver should try NIC netmask first, then network CIDR or reserved
    network CIDR, then the zone guest CIDR. Runtime application still validates
    the actual guest NIC prefix through the SystemVM command before adding the
    listen IP. If the Cloud DB cannot provide usable CIDR evidence and the
    Storage Service System VM has exactly one NIC, the backend may select that
    NIC as the candidate and defer the final CIDR decision to the SystemVM
    guest command. In that path, the backend must persist the protocol desired
    state first, let the SystemVM apply command validate and add the listen IP,
    and only then register the Cloud secondary IP. If the guest apply fails,
    the backend must rollback the protocol desired state and avoid leaving a
    stale secondary IP row.
    If the selected listen IP is already the primary IP of the candidate
    Storage Service NIC, the backend must not create a duplicate secondary-IP
    record; it should treat the primary address as already registered.
    Initial SharedFS creation must verify NFS runtime state by the
    client-visible export root name, not by the internal backing path. The UI
    must tolerate monitor-cache lag by polling the runtime inventory for a
    bounded period after the export and ACL API jobs complete. A configured
    all-CIDR ACL such as `0.0.0.0/0` or `::/0` must be compared with the
    SystemVM-rendered NFS wildcard `*`.
    The NFS export modal must include an "NFS access permission" area for Root
    Squash, All Squash, anonymous UID/GID, POSIX owner UID/GID, directory mode,
    and recursive apply. The NFS ACL modal must show export options by export
    name only; after selection, it shows the internal backing path and
    client-visible mount root as read-only context. NFS ACL option groups must
    use compact boxed sections with normal-sized labels so Root Squash, All
    Squash, sync, secure port, and anonymous UID/GID mapping are visually
    consistent with the export dialog.
    The NFS export modal must also include endpoint binding. Operators can
    expose an export through all configured Storage Service endpoints or a
    selected subset of endpoint IPs. The create/update API stores the selected
    endpoint intent as `endpointMode=ALL|SELECTED` in the export configuration
    and returns `endpointmode` in list/detail responses so the UI can show
    endpoint-to-export mapping after refresh. `SELECTED` requires one or more
    selected endpoint IPs and persists them as `listenIps`; `ALL` removes
    export-level `listenIps` and means the export is exposed through every
    configured Storage Service NFS endpoint. Existing rows that have
    `listenIps` but no `endpointMode` are interpreted as `SELECTED`; existing
    rows that have neither value are interpreted as `ALL`.
    The create dialog must default to `SELECTED` with no endpoint preselected
    so the operator makes an explicit endpoint decision. The UI confirmation
    path and backend API must both reject `SELECTED` without at least one IP.
    The backend must persist and return this endpoint intent from a valid
    `mediumtext` configuration payload. If the payload is invalid, the response
    must expose `configvalid=false`; it may recover `endpointMode` and complete
    IPv4 values from the raw payload for display, but it must not silently
    coerce the export to `ALL`.
    Because the Linux kernel NFS server does not provide a first-class
    per-export/per-listen-IP visibility model in the current SystemVM design,
    endpoint binding is treated as Storage Service metadata and desired-state
    intent in this phase. Strong per-endpoint export isolation, if required,
    must be handled later through a dedicated firewall/netns/nfsd policy design.
    Endpoint selection lists must be built from the merged endpoint model:
    ABLESTACK VM NIC IPs, secondary IPs, SystemVM runtime monitor IPs, protocol
    listen IPs, and export-level explicit `listenIps`, de-duplicated by IP.
    When an operator chooses "new IP", the UI must reject an IP that is already
    present in this merged model and show an explicit duplicate message instead
    of reporting a silent success for an idempotent no-op.
    The NFS export table and connection guidance must show all selected
    endpoint IPs for each export. Generic connection examples must remain
    export-name based, for example `<endpoint-ip>:/<export-name>`, instead of
    exposing the internal backing path.
  - After a protocol action modal closes, the UI must keep the current protocol
    tab and update only the affected protocol data, runtime summary, sessions,
    ACLs, and backing-volume rows. It must not navigate back to the Details tab
    or mask/blur the whole tab area for a routine data refresh. Action refresh
    code must snapshot the current protocol tab and wide-layout query state
    before the async job starts, suppress parent resource full-fetch behavior
    during async job completion when the protocol tab can refresh its own data,
    then restore those route query values through router replace after the job
    completes.
  - `Backing volumes` table: volume name, volume ID, size, used/free when
    available, disk offering, storage pool, filesystem, attached export, and
    ABLESTACK volume state.
    Physical backing volume expansion is a row-level action because it targets
    one concrete backing volume. Do not duplicate it in the section header.
    If an export references a stale or legacy volume ID, the UI should prefer
    the current SharedFS/SystemVM backing volume that belongs to the active
    Storage Service VM and keep the raw ID visible only as secondary evidence.
    Creation workflows must not rely on a stale page resource or list-row
    cache for the backing volume ID. After `createSharedFileSystem` completes,
    the UI must reload the created SharedFS by ID, use that response as the
    authoritative VM/volume source for initial protocol creation, and fail the
    setup if the current backing volume cannot be resolved.
    Backend create/update APIs for NFS, SMB, iSCSI, and NVMe-oF volume-backed
    resources must reject a volume that is already attached to a different VM.
    Detached volumes are allowed only for the explicit attach/import workflow;
    volumes already attached to the current Storage Service System VM are valid.
  - `NFS sessions` table: client IP or session identifier, connection state,
    connection time or age when available, associated export when known, local
    endpoint, and disconnect action when supported.
    Session rows should only expose session disconnect actions. Empty session
    tables must use the standard Ant Design empty state with an icon instead of
    plain `Nodata` text.
  - Storage Service read APIs may be newer than the UI permission cache in an
    upgraded management server. Protocol tab reads should try `listStorage*`
    status and inventory APIs directly, then normalize both exact object
    wrappers and array wrappers so exports, ACLs, runtime health, and backing
    volume rows do not disappear because of wrapper naming drift.
  - For NFS ACL display, the UI may request `listStorageNfsAcls` without an
    `exportid` filter and then map rows client-side by `resourceid`/export ID.
    This avoids losing visible ACL state when an upgraded API has entity
    reference drift on the filtered list parameter.
  - `NFS status` table or compact cards: daemon state, exportfs apply state,
    last desired-state apply result, last inventory refresh, and any
    protocol-specific warning. Runtime status should be read from the
    System VM monitoring cache whenever the cache is fresh, not from a full
    on-demand QGA inventory command on every UI refresh.
    The summary card must show the service endpoint and the last monitoring
    cache refresh time whenever the information is available.
  - NFS connection guidance must be representative, not tied to one export row.
    It should show examples such as
    `mount -t nfs <service-ip>:/<export-name> <local-mount-path>` and
    `showmount -e <service-ip>`, using the actual service IP but leaving the
    export name as a placeholder because multiple exports can exist.
- SMB tab table standard:
  - The SMB tab must use the same visual density, table behavior, action
    placement, dark-mode handling, fixed-column rules, tooltip rules, and empty
    states as the NFS tab standard.
  - The top summary card must show the SMB service endpoint, authentication
    mode, AD domain or workgroup when applicable, domain join state, daemon
    state for `smbd`/`nmbd`/`winbind`, monitor cache state, last monitoring
    cache refresh, and warning state when the desired SMB configuration has not
    been applied.
  - SMB connection guidance must be representative rather than tied to one
    share row. It should show examples such as `\\<service-ip>\<share-name>`,
    `net use * \\<service-ip>\<share-name> /user:<user>`, and
    `smbclient //<service-ip>/<share-name> -U <user>`, using the actual service
    IP and placeholder share/user values because multiple shares and identity
    modes can exist.
  - `SMB shares` table: share name, client-visible UNC root, internal path,
    service IP/port, browseable flag, guest access flag, read-only flag,
    quota/capacity, backing volume, runtime state, and actions.
    Share row actions should include share edit, share disable/delete when
    supported, and share capacity expansion. Do not duplicate the same action in
    both the section header and each row.
  - `SMB access and accounts` table: share name, principal type, principal
    name, permission, authentication mode, AD/local source, runtime state, and
    actions. The section action area must include local user creation, SMB ACL
    creation, and password rotation when supported. Passwords and secrets must
    never be displayed, cached, or written to the monitor cache.
  - `SMB identity` section: authentication mode, workgroup, AD domain, DNS
    hints, target OU, join state, winbind state, and last join/apply result.
    AD join, AD leave, and domain connectivity test actions belong here and
    must open modals. These controls must not be mixed into the share table.
  - `Backing volumes` table: volume name, volume ID, size, used/free when
    available, disk offering, storage pool, filesystem, attached SMB share, and
    ABLESTACK volume state. Physical backing volume expansion remains a
    row-level action because it targets one concrete backing volume.
  - `SMB sessions` table: client address or session identifier, user, share,
    SMB dialect/protocol version when available, connection state, connection
    time or age when available, local endpoint, tree/session ID when available,
    and disconnect action. Session rows should expose only session disconnect
    actions.
  - SMB runtime data should be built from Storage Service APIs and the
    System VM monitoring cache. The UI should normalize both exact object
    wrappers and array wrappers so shares, ACLs/accounts, sessions, and backing
    volume rows remain visible after API wrapper drift in upgraded systems.
  - If the SMB tab detects `protocol enabled` but no desired share row after a
    create workflow that selected SMB, it must show a setup-incomplete warning
    and expose the initial setup retry action instead of rendering an apparently
    empty healthy SMB service.
- iSCSI tab table standard:
  - The iSCSI tab must use the same full-width protocol-tab layout, table
    density, fixed-column behavior, ellipsis/tooltip handling, row-level action
    placement, compact scrollbars, and dark-mode table styling as the NFS and
    SMB tabs.
  - The top summary card must show the iSCSI endpoint, TCP 3260 listen state,
    target service state, monitor cache state, last monitoring cache refresh,
    target count, LUN count, ACL count, and warning state when the desired iSCSI
    configuration has not been applied.
  - iSCSI connection guidance must be representative rather than tied to one
    target row. It should show examples such as
    `iscsiadm -m discovery -t sendtargets -p <service-ip>` and
    `iscsiadm -m node -T <target-iqn> -p <service-ip> --login`, using the actual
    service IP and placeholders for target-specific values because multiple
    targets can exist.
  - `iSCSI targets` table: target IQN, LUN number, endpoint, backing volume,
    exposed LUN size, authentication mode, runtime state, and actions. Target
    actions must be row-level and must not be duplicated in the section header.
  - `iSCSI access` table: target IQN, allowed initiator IQN, authentication
    mode, CHAP user name when configured, mutual CHAP user name when configured,
    permission, runtime state, and actions. Secrets must never be displayed,
    cached, or written to monitor cache files.
  - `Backing volumes` table: volume name, volume ID, LUN/file-backed path when
    known, size, used/free when available, disk offering, storage pool,
    filesystem, attached iSCSI target, and ABLESTACK volume state. Physical
    backing volume expansion remains a row-level action because it targets one
    concrete backing volume.
  - `iSCSI sessions` table: initiator IQN or client address, target IQN, TPG or
    session identifier, connection state, connection time or age when available,
    endpoint, and disconnect action. Session rows should expose only session
    disconnect actions.
  - iSCSI runtime data should be built from Storage Service APIs and the
    System VM monitoring cache. The UI should normalize both exact object
    wrappers and array wrappers so targets, ACLs, sessions, and backing volume
    rows remain visible after API wrapper drift in upgraded systems.
- NVMe-oF tab table standard:
  - The NVMe-oF tab follows the same table standard as the iSCSI tab, replacing
    target/LUN terms with subsystem/namespace terms.
  - `NVMe-oF subsystems` table: subsystem NQN, namespace ID, endpoint, backing
    volume, namespace size, authentication mode, runtime state, and actions.
  - `NVMe-oF access` table: subsystem NQN, host NQN, authentication mode,
    DH-HMAC-CHAP user/key status where applicable, runtime state, and actions.
    Secrets must never be displayed, cached, or written to monitor cache files.
  - `NVMe-oF sessions` table: host NQN or client address, subsystem NQN,
    controller/session identifier, connection state, endpoint, and disconnect
    action.
- Protocol tab refresh behavior:
  - Initial loading may use a page spinner, but subsequent status refreshes
    must not clear the existing tab data or re-render the whole tab. The UI
    should update only the changed collections after new API responses arrive.
    Parent page loading state must not keep a protocol tab covered by a global
    disabled overlay after the tab has its own data.
  - Table empty states, row text, fixed columns, horizontal scrollbars, and
    action columns must be checked in dark mode. Missing i18n keys must never
    leak to the visible UI.
  - Table body cells and fixed columns must not hard-code light-theme dark text
    colors. They should inherit the active ABLESTACK theme foreground color so
    values remain visible under `body.dark-mode` and normal mode.
- Active sessions must be visible per protocol. Session termination must be
  provided through a modal that explains protocol-specific limits:
  - SMB sessions can usually be terminated explicitly.
  - NFS session termination is best effort because NFSv3 is weakly sessioned.
  - iSCSI and NVMe-oF termination depends on target/transport capabilities and
    may require ACL disable plus desired-state reapply.
- `listStorageServiceSessions` should accept `instanceid`, `protocol`,
  `resourceid`, `client`, and `state` filters for UI tab use.
- `disconnectStorageServiceSession` should be asynchronous and return a runtime
  response or job result containing protocol, target session, command output,
  and whether termination was complete or best effort.
- `listStorageServiceProtocolSummary` should be added if the UI cannot build
  the protocol cards efficiently from existing list, health, inventory, and
  session APIs. The response should aggregate protocol enabled state, endpoint,
  runtime service state, resource counts, ACL counts, capacity summary, and
  session count. This API should prefer the System VM monitoring cache and
  include cache freshness, monitor service state, and stale-cache warning fields
  so the UI can render status quickly and honestly.

The Mold UI must submit Cloud API requests asynchronously and poll async jobs
only. The UI must never run storage commands directly. Commands continue through
Management Server, async job, Mold Host Agent on the host running the Storage
Service System VM, QGA, and `ablestack-storagectl` inside the service VM.
Read-only status requests may use the same QGA channel to read monitoring cache
files, but they should not execute expensive live discovery commands unless the
cache is missing, stale, or explicitly refreshed by the operator.

SPDK NVMe-oF may be visible as a planned or prerequisite-gated engine, but the
SharedFS UI must not expose HugePage, CPU pinning, NUMA, memlock, SR-IOV, or PCI
passthrough controls. Those remain future VM Runtime Capability features outside
the Storage Service workflow.

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
- The compatibility mirror creates or updates one `StorageServiceInstance` and
  one enabled NFS `StorageServiceProtocol`, but it must not publish the legacy
  `/export` root as an open NFS export.
- For compatibility instances that need an initial file export, the mirror or
  UI setup must create an explicit child export such as `/export/<share-name>`
  with an explicit ACL. The old open-root behavior is not carried forward into
  the expanded Storage Service model.
- Existing cloud-init and SharedFS bootstrap logic that formats and mounts the
  data disk at `/export` must be changed so it no longer writes or enables an
  NFS export for `/export` when the expanded Storage Service feature is active.
  Storage Service desired state, applied through the Mold host Agent and QGA, is
  the only authority that may render NFS export files.
- Compatibility mirror failures are logged and do not change existing SharedFS
  API behavior.
- All Storage Service desired-state rows must carry explicit `created`
  timestamps in the VO layer and the schema must provide `DEFAULT
  CURRENT_TIMESTAMP` for fresh installations. UI-created service workflows must
  not depend on database implicit defaults being present or absent.
- UI creation must treat the workflow as two visible phases:
  1. SharedFS/SystemVM creation through the existing asynchronous
     `createSharedFileSystem` job.
  2. Initial Storage Service setup through asynchronous protocol/export/share/
     target/ACL jobs.
- The UI must poll every initial Storage Service async job to completion before
  using returned object IDs, creating dependent ACLs, or showing final Storage
  Service setup success.
- The UI must verify the selected service objects after setup:
  NFS export and optional ACL, SMB share plus SMB local ACL when local identity
  mode is selected, iSCSI target and optional ACL, and NVMe-oF subsystem and
  optional host ACL. If verification fails, the UI must report a partial-create
  error and the detail page must remain available for retry/reconciliation.

### Phase 4: SMB

- Implement SMB local user/share mode.
- Implement SMB ACL management.
- Create-dialog local SMB creation must run in this order: enable SMB protocol,
  create SMB share, create `LOCAL_USER` ACL with one-time password payload,
  verify share and ACL API state, then verify runtime inventory/cache state.
- All SMB APIs that accept `shareid` must resolve UUIDs through the same
  `StorageFileShare` entity reference used by generic file-share APIs. The
  SMB-specific response object must therefore carry
  `@EntityReference(StorageFileShare.class)` so `createStorageSmbAcl`,
  `listStorageSmbAcls`, `updateStorageSmbShare`, `deleteStorageSmbShare`, and
  `listStorageSmbShares id=...` cannot fail during API parameter conversion.
- If ACL creation fails after the share has already been created, the workflow
  is a partial creation, not a complete failure. The UI must keep the detail
  page usable, show the SMB share row, and provide a retry path for ACL
  creation from the SMB protocol tab.
- Implement AD domain join/leave/status.
- Implement AD user/group ACLs.

### Phase 5: Block Protocols

- Implement iSCSI target/LUN/ACL/CHAP.
- Implement NVMe-oF subsystem/namespace/host ACL and DH-HMAC-CHAP host ACL
  authentication.
- Add session and discovery views.

### Phase 6: Operations

- Add QGA-backed runtime health, inventory, and active session checks.
- Add metrics and health checks.
- Add event/audit details.
- Add backup/snapshot/resize integration.
- Add upgrade and template compatibility checks.
- Evaluate dedicated Storage Service System VM template profile.

### Phase 7: Existing Volumes, Resize, And NVMe-oF Kernel Preparation

Confirmed next implementation scope:

1. Storage capacity expansion for file services.
2. Service activation from existing ABLESTACK volumes attached to the Storage
   Service System VM.
3. NVMe-oF SPDK code adjustment so any VM-level runtime resource handling is
   removed from Storage Service and left as a dependency on the future VM
   Runtime Capability feature.

- Add explicit existing-volume import APIs for NFS and SMB file shares.
- Implement QGA `volume attach inspect` and non-destructive mount discovery.
- Implement `resizeStorageFileShare` and QGA `filesystem resize` for XFS and
  ext4 grow.
- Add XFS project quota support for multi-share-on-one-volume capacity
  enforcement.
- Add `prepareStorageServiceNvmeOfVm` for `KERNEL_NVMET` validation.
- Keep SPDK as a planned engine state that returns prerequisite information and
  `PreparationRequired` until VM Runtime Capability support is available.
- Add UI workflows for:
  - selecting existing ABLESTACK volumes
  - choosing import mode
  - reviewing detected filesystem and mount status
  - resizing file services
  - selecting NVMe-oF `KERNEL_NVMET` mode and viewing prerequisite checks
  - showing SPDK as planned or unavailable without exposing VM runtime controls.

Recommended implementation order:

1. Existing volume import model and API validation.
2. QGA volume inspection and mount workflow.
3. File share resize API and filesystem grow workflow.
4. NVMe-oF `KERNEL_NVMET` prerequisite validation.
5. SPDK planned-state response and VM Runtime Capability dependency message.
6. Remove or gate any SPDK code path that attempts HugePage, NUMA, CPU pinning,
   memlock, SR-IOV, PCI passthrough, or other VM-level runtime changes from
   Storage Service.
7. UI integration and end-to-end validation.

### iSCSI LUN Backing And Runtime Inventory Rules

- iSCSI desired state must carry the ABLESTACK backing volume UUID, name, and
  size in addition to the database row ID. SystemVM scripts must not use
  numeric database IDs as disk-match tokens because they can accidentally match
  unrelated local device identifiers.
- The SystemVM must resolve the backing disk by volume UUID/name/serial and
  fail the apply operation when no guest disk matches. It must not fall back to
  the root filesystem or `/var/lib/ablestack-storage` for iSCSI LUN images.
- When a file-backed iSCSI LUN is used, the LUN image is created only below the
  matched backing disk mountpoint, for example
  `<data-mount>/.ablestack-storage/iscsi/<target-uuid>.img`.
- If the operator leaves LUN size blank, the effective LUN size is the backing
  volume size. If the operator sets a LUN size, that configured size is shown
  separately from the effective/runtime size.
- `listStorageIscsiTargets` and the QGA desired-state payload must expose
  volume name, volume size, configured LUN size, effective LUN size, and
  configured backing path where present.
- The monitor cache inventory must enrich iSCSI target rows with runtime
  backstore type, runtime backing path, and runtime file size from target state
  so the UI can render quickly without issuing expensive live QGA commands on
  every refresh.
- The iSCSI tab follows the shared Storage Service table standard: important
  identity columns are fixed, wide runtime paths scroll inside the table, long
  values are shortened with tooltip support, and dark-mode table/empty states
  remain readable.

### NVMe-oF Reconcile And Namespace Backing Rules

- NVMe-oF `KERNEL_NVMET` desired-state apply must be idempotent. The SystemVM
  must reconcile the requested state with `/sys/kernel/config/nvmet` instead of
  rewriting every configfs attribute on every request.
- A configured NVMe-oF port must write `addr_trtype`, `addr_adrfam`,
  `addr_traddr`, and `addr_trsvcid` only before subsystem links are active. If
  the port already has linked subsystems, Host ACL or namespace updates must not
  rewrite those active port attributes.
- Host ACL creation must be independent from port and namespace creation:
  create or reuse `/sys/kernel/config/nvmet/hosts/<hostNqn>`, then create the
  `allowed_hosts/<hostNqn>` symlink if it does not already exist. Existing
  symlinks are treated as success; unexpected non-symlink paths are an error.
- Namespace updates must not rewrite `device_path` while the namespace is
  enabled. If the desired backing device changes, the SystemVM must disable the
  namespace, update `device_path`, and re-enable it. If the device is unchanged,
  no configfs write is needed.
- NVMe-oF namespace desired state must carry enough ABLESTACK volume metadata
  to resolve the guest disk by volume UUID, volume name, or safe serial token.
  Numeric-only or broad fallback tokens must not be used.
- When the selected ABLESTACK backing volume is already mounted by the SharedFS
  compatibility path, NVMe-oF must not expose the mounted block device directly.
  It should create a managed namespace image below the mounted data disk, for
  example `/export/.ablestack-storage/nvmeof/<namespace-uuid>.img`, attach it
  to a loop device, and set the kernel namespace `device_path` to that loop
  device.
- The SystemVM must refuse to place NVMe-oF namespace backing files on the root
  filesystem when the intended ABLESTACK data volume cannot be resolved.
- The Management Server should mark Host ACL rows `Ready` only after the QGA
  desired-state apply succeeds. Failed apply attempts must surface a clear
  operator error and must not leave UI/API state that looks complete.

### NFS Lifecycle CRUD And Deletion Safety Rules

- Storage Service protocol activation and full protocol deletion are
  protocol-level lifecycle operations. Protocol deletion disables the protocol
  desired state on the SystemVM and removes the protocol row only after the QGA
  apply succeeds.
- NFS endpoint removal is a separate endpoint-level lifecycle operation. It
  removes one selected listen IP from protocol/export desired state and, when
  that IP is a Cloud secondary IP for the Storage Service VM, removes the
  secondary-IP record after QGA desired-state reapply. The UI must label this
  action as endpoint removal, show the selected endpoint before confirmation,
  and require the operator to type the selected IP before deletion is enabled.
  The primary NIC IP is not removable through this endpoint action.
- Protocol deletion is allowed only after dependent resources are removed:
  NFS exports for NFS, SMB shares and AD domain state for SMB, iSCSI targets
  for iSCSI, and NVMe-oF subsystems/namespaces for NVMe-oF. This prevents a
  protocol from being disabled while visible shares or block targets still
  exist.
- NFS currently uses the SystemVM kernel NFS server service-wide TCP port
  `2049`. Multiple listen IPs may be registered, but the UI and API must not
  imply per-IP NFS port overrides. Non-2049 NFS port requests are rejected by
  the Management Server.
- The NFS `secure` export option is exposed as "Require privileged port", not
  as a generic "secure port". It means Linux NFS clients must use a source port
  below 1024. This is an access-control compatibility option, not encryption.
- NFS export desired-state rendering must de-duplicate effective
  `(client-visible export root, principal)` entries before writing
  `/etc/exports.d`. Duplicate ACL rows in the database or repeated UI submits
  must not cause `exportfs: duplicated export entries` to break unrelated NFS
  exports during full desired-state reapply.
- NFS export rows support create, edit, resize, and delete from the NFS tab
  table action column. Edit reuses the create dialog with the selected export
  values prefilled, including client-visible export name, internal backing
  path, selected endpoints, capacity limit, and POSIX/root-squash options.
- NFS export creation supports three backing-volume modes: use the current
  SharedFS backing volume, attach an existing detached ABLESTACK data volume,
  or create a new ABLESTACK data volume by selecting a disk offering and a GiB
  size. The UI starts from a dropdown, never a raw volume ID field.
- Deleting an NFS export removes the export, ACL rows, desired-state entry, and
  client-visible alias mount, but does not detach or destroy the backing
  ABLESTACK data volume. A backing volume is a reusable storage container and
  can host multiple export directories.
- When NFS export creation uses a currently attached backing volume, the UI
  must require an explicit current-volume selection whenever more than one
  attached backing volume is available; in that case the selector has no
  default value. The selected volume ID is sent to the API with no import mode,
  so the Management Server records the mapping and reapplies NFS desired state
  without reattaching, reformatting, or remounting that volume.
- The NFS tab backing-volume table must list all data volumes attached to the
  Storage Service System VM, including volumes whose previous export was
  deleted and therefore no longer has a file-share row. The table must show the
  ABLESTACK volume identity and any attached export names; row actions that
  require a file-share row stay disabled when a volume currently has no export.
- The NFS export create/edit dialog exposes a single operator-facing path:
  `internal backing path`. The separate `directory inside backing volume` field
  is removed because it conflicts with the Storage Service path model.
- NFS export names must follow Linux directory naming rules: letters, numbers,
  `.`, `_`, and `-` only; no slash, no whitespace, no empty value, and no `.`
  or `..`. The default internal backing path is generated from the export name
  as `/export/<export-name>`.
- NFS internal backing paths must stay exactly one level below `/export`.
  `/export`, `/export/`, paths outside `/export`, and nested paths such as
  `/export/<name>/<child>` are rejected by both UI validation and the API.
- Existing/current backing-volume import separates the private volume mount
  root from the operator-facing export path. The SystemVM mounts the selected
  volume at a stable private path such as
  `/srv/ablestack-storage/volumes/<volume-uuid>`, resolves the corresponding
  `export/<export-name>` directory inside that mounted volume, and stores the
  effective private path in config/inspection as `backingPath`. NFS publishes
  only the `/export/<export-name>` alias.
- Existing or current backing volumes must be inspected before the export is
  applied. The SystemVM records the detected filesystem, filesystem UUID,
  private mount root, and effective backing path in `lastInspection`.
  Existing-volume import supports only `xfs` and `ext4`; an unsupported or
  undetected filesystem fails before the export/share is marked Ready.
- New backing-volume creation must expose a filesystem selector in the UI. The
  selected value is passed for format workflows and is limited to `xfs` or
  `ext4`. Current SharedFS backing volumes use `FORMAT_IF_EMPTY`: an existing
  supported filesystem is reused, and an empty device is formatted with the
  selected filesystem.
- The directory creation policy is explicit. `Create directory if missing` is
  enabled by default. If disabled and the corresponding `export/<export-name>`
  directory is absent inside the selected backing volume, the operation fails
  and the error is returned to the UI.
- Cloud-init must not mount `/export`, format data disks, register `/export` in
  `/etc/fstab`, resize filesystems, or publish legacy NFS exports. It is limited
  to bootstrapping the Storage Service System VM so QGA, persistent DHCP, and
  monitor services are available. All Storage Service state changes, including
  initial NFS export creation requested at SharedFS creation time, are applied
  through Mold API -> engine -> host agent -> QGA -> `ablestack-storagectl`.
- The NFS backing-volume table exposes a row-level detach action for unused
  backing volumes. The action is disabled when any NFS export, SMB share, iSCSI
  target, or NVMe-oF namespace still references the volume. Detach unmounts the
  volume from the Storage Service System VM and removes fstab markers, but it
  never deletes the ABLESTACK volume or its data. Operators must delete the
  detached volume separately from the Volumes menu when data deletion is
  desired.
- NFS ACL rows support create, edit, and delete from the ACL table action
  column. Create accepts either one CIDR/IP or multiple comma-separated
  CIDR/IP values and persists one ACL row per value. Edit is intentionally
  single-row only.
- Destructive operations use a dark-mode-compatible confirmation dialog.
  Operators must type the displayed resource name before the OK button becomes
  active. Delete actions refresh only the affected protocol tab data and must
  preserve the current tab and wide-layout state.
- Capacity expansion and backing volume expansion dialogs must use the same
  vertical action-modal standard as NFS export and ACL dialogs: centered modal,
  fixed header/footer, scrollable body inside the browser viewport, required
  markers, tooltip labels, and no horizontal row-only layout.
- The UI remains based on Vue Ant Design and follows the established Storage
  Service table pattern: fixed important columns, compact row action buttons
  with icons, internal table scrolling, ellipsis/tooltip handling for long
  values, and explicit dark-mode colors for warning and delete states.

## Open Decisions

## TC-04A NFS Backing Volume And Runtime Validation Refinement

The TC-04A retest exposed a case where Storage Service DB rows looked ready
while the System VM runtime was not authoritative: the monitor cache was empty,
`ablestack-storagectl` in the generated System VM image could be empty or
unusable, and an NFS export backed by a newly created ABLESTACK data volume
could remain detached.

The refined design is:

- NFS export create/update must treat an explicitly selected backing volume as
  a full lifecycle operation, not metadata only. When `importmode` is provided,
  the Management Server attaches the volume to the Storage Service System VM,
  runs QGA `volume attach inspect`, persists the inspected filesystem and mount
  path, then applies NFS desired state.
- `importmode=MOUNT_EXISTING` mounts only volumes that already have a supported
  filesystem. `importmode=FORMAT_EMPTY` is reserved for UI-created empty
  volumes and must refuse to format a device that already has a filesystem
  signature. `INSPECT_ONLY` may record metadata without mounting.
- The Management Server must reject duplicate file-share paths and must reject
  overlapping backing-volume paths when different volumes would be mounted
  over the same path tree. The System VM must also reject an exact mount target
  already occupied by another device.
- `importmode` is optional for initial SharedFS compatibility flows that use
  the already mounted primary SharedFS data volume. If it is omitted, the NFS
  export keeps the requested child path and does not re-inspect the existing
  primary `/export` mount as the share path.
- The System VM template build must fail if `/usr/local/bin/ablestack-storagectl`
  or `/usr/local/bin/ablestack-storage-monitor` is missing or empty. Fresh
  Storage Service System VMs must unmask and enable
  `ablestack-storage-monitor.service`; the setup script must refuse to continue
  with an empty storage control binary.
- Runtime status APIs and the UI must distinguish DB readiness from runtime
  readiness. Empty monitor cache or invalid runtime JSON is a runtime
  unavailable state, not proof that an export is active.
- NFS ACL create remains a multi-CIDR workflow. NFS ACL edit is a single-row
  workflow and the UI must show a single principal input instead of a tag list.
  This prevents the operator from entering multiple principals into an update
  operation that can only update one ACL row.
- Storage Service file-share mounts must be boot-safe. The physical backing
  volume mount and the client-visible export alias are separate concepts. A
  backing volume is mounted only at its internal backing path, while a
  client-visible NFS root such as `/nfs02` is a bind mount alias to that backing
  path and is the only path rendered into `/etc/exports.d`.
- `ablestack-storagectl volume attach inspect` must persist explicit backing
  volume mounts in `/etc/fstab` using `UUID=<filesystem-uuid>` rather than
  `/dev/sdX`. The entry must include an `ablestack-storage:` marker with the
  share UUID and `backing` role so it can be updated safely.
- `ablestack-storagectl nfs export apply` must persist NFS alias bind mounts in
  `/etc/fstab` using `none bind,nofail,x-systemd.requires-mounts-for=<backing
  path>`. The entry must include an `ablestack-storage:` marker with the export
  UUID and `alias` role.
- Deleted or disabled exports must remove their managed alias fstab entry and
  unmount the alias when it is mounted. Managed fstab operations must never
  touch root, boot, ISO, swap, or unrelated operator entries.
- After changing managed fstab entries, the System VM must run
  `systemctl daemon-reload` when systemd is present and verify mount state with
  `findmnt` before running `exportfs -ra`.
- The fstab update path must be covered by an execution-level regression test,
  not only Python compilation. Python heredoc blocks run as independent
  interpreters, so every block that calls managed fstab helpers must import its
  own runtime dependencies such as `tempfile`. A missing import must fail the
  template validation before deployment.
- The monitor capacity collector must read
  `/etc/ablestack-storage/nfs-export-aliases.json` and include every managed
  backing path and alias path instead of scanning only `/export`. UI capacity
  tables then receive all exports, including exports backed by additional
  volumes.
- Runtime readiness checks should flag an export as degraded when its DB row is
  Ready but either the backing mount or the client-visible alias mount is
  missing.
- Storage Service desired-state JSON must be treated as authoritative data, not
  optional decoration. All `config_json` columns for Storage Service protocol,
  file share, block target, access rule, and identity-domain rows must be
  physically verified as `MEDIUMTEXT` in upgraded 22.x environments. The entity
  mapping must also declare the column as a large object or `MEDIUMTEXT`, and
  must set `@Column.length` to the `MEDIUMTEXT` capacity (`16777215`). Mold's
  `GenericDaoBase` uses the JPA `Column.length` value while binding String
  fields; if the length is omitted, the default `255` is applied even when the
  physical DB column is `MEDIUMTEXT`, which truncates Storage Service desired
  state JSON before it reaches the System VM.
- The Management Server must strictly parse NFS export and ACL `config_json`
  before building QGA desired-state payloads. If a stored config is truncated,
  invalid JSON, or missing the resolved `backingPath` and `volumeMountPath` for
  an active export, the manager must fail before sending the QGA command. It
  must not silently replace the missing backing path with `/export/<name>`.
- `ablestack-storagectl nfs export apply` must enforce the same guard inside
  the System VM. `config.backingPath` and `config.volumeMountPath` are required
  for active NFS exports. The backing path must be under
  `/srv/ablestack-storage/volumes/<volume-uuid>`, the volume mount must already
  be mounted, and `/export/<export-name>` must be a bind mount alias to that
  backing path before `exportfs` is run.
- The System VM must refuse to hide a non-empty root-filesystem directory with a
  bind mount. If `/export/<export-name>` already exists on rootfs and contains
  data, the command fails and reports that manual recovery is required. Empty
  stale alias directories may be reused as bind mount targets.
- The create SharedFS dialog's initial capacity controls must use a stable
  numeric-input plus unit-select layout. The numeric input must not depend on
  Ant Design compact input-group sizing because the dark modal layout can
  collapse the input and leave only the unit selector visible.
- Existing damaged exports whose `config_json` was already truncated cannot be
  trusted as repair sources. After the DB and runtime guards are deployed, the
  operator should delete/recreate the export or run a controlled repair that
  unexports the stale rootfs path, removes only an empty stale alias, regenerates
  the full backing config, bind-mounts `/export/<name>`, persists fstab, and
  reapplies `exportfs`.
- Initial NFS export creation failures must preserve enough evidence for
  operation and UI diagnosis. When a create request reaches DB persistence but
  the QGA desired-state apply fails, the file-share row is kept in `Error`
  state with a valid `config_json.lastError` object instead of being deleted.
  Created temporary volumes may still be cleaned up when the request explicitly
  marks them as disposable, but the failed export metadata remains visible.
- Desired-state generation must include only active resources. NFS exports and
  ACLs in `Ready` or `Updating` state are sent to the System VM; rows in
  `Error`, `Disabled`, `Destroyed`, or other non-active states are excluded so a
  failed row cannot poison all later NFS reconciliation.
- The Management Server must validate generated Storage Service JSON before
  writing it to the database. Invalid generated JSON is rejected immediately.
  If an apply failure has to be recorded, the preserved error record must itself
  be valid JSON so list APIs and the UI can show the failed object and reason.
- The NFS tab must surface failed export rows through an operator-facing alert
  before the export table. This keeps the normal table focused on usable
  exports while still showing why the initial service setup produced a partial
  warning.

These rules preserve the existing SharedFS API surface while making the Storage
Service runtime state auditable through QGA and the monitor cache.

- Whether Phase 1 should use the common System VM template or immediately add a
  dedicated Storage Service template.
- Whether Storage Service instances should support one protocol per VM or mixed
  protocols in one VM.
- Whether HA should be active/passive in the first version or deferred.
- Whether NFS multi-export quota should wait for XFS project quota support or be
  implemented only through one-volume-per-export initially.
- Whether SMB identity should standardize on winbind, sssd, or support both.
- Whether NVMe-oF should require `nvmetcli` or use configfs directly.
- Whether SPDK mode should later use the same Storage Service System VM
  template or require a dedicated high-performance template after VM Runtime
  Capability profiles are implemented.
- Whether VM Runtime Capability should be modeled only through service offering
  details first or receive normalized profile tables before SPDK is enabled.
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
