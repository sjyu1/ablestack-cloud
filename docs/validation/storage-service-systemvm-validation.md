# Storage Service SystemVM Validation Plan

## Purpose

This document is the single validation record for the ABLESTACK Storage Service
SystemVM feature. It must be updated during every verification pass with:

- exact test environment
- executed commands or UI path
- observed result
- defects, regressions, and improvement items
- retest result after fixes

The document covers the current first implementation on `ablestack-diplo`,
including:

1. Storage capacity expansion for file services.
2. Serving NFS and SMB from existing CloudStack volumes attached to the Storage
   Service SystemVM.
3. NVMe-oF `KERNEL_NVMET` prerequisite validation.
4. NVMe-oF `SPDK` planned-state behavior, where Storage Service must not
   perform VM-level HugePage, NUMA, CPU pinning, memlock, SR-IOV, or PCI
   passthrough configuration.

## Validation Rules

- Do not store passwords, API keys, API secrets, or SSH credentials in this
  document.
- Record all dates with timezone.
- Record exact commit SHA for every test run.
- Prefer API-level tests first, then QGA/SystemVM behavior, then UI.
- Any failed step must produce one of:
  - a code fix
  - a design update
  - a documented limitation with an operator-visible error message
- SPDK must remain gated until VM Runtime Capability support is implemented.

## Test Environment Record

Create one row per validation pass.

| Run ID | Date/Time | Branch | Commit | Cloud | Zone | SystemVM Template | Tester | Result |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| STATIC-20260526-01 | 2026-05-26 Asia/Seoul | `codex/diplo-storage-service-design` | `610f2bdf78` | local build only | N/A | N/A | Codex | Pass |

## Current Static Verification Result

| Check | Command | Result | Notes |
| --- | --- | --- | --- |
| Diff whitespace check | `git diff --check` | Pass | CRLF warnings only on Windows checkout |
| SystemVM script syntax | `bash -n systemvm/debian/usr/local/bin/ablestack-storagectl` | Pass | Verified in RockyLinux-9.7 WSL |
| API module build | `mvn -pl api -DskipTests install` | Pass | Verified in WSL ext4 worktree |
| Server/schema build | `mvn -pl engine/schema,server -am -DskipTests install` | Pass | Verified in WSL ext4 worktree |

## Required Test Data

Prepare these resources before functional validation.

| ID | Resource | Requirement | Notes |
| --- | --- | --- | --- |
| TD-01 | Storage Service instance | Existing or newly created instance with `vmid` set | SystemVM must be running and QGA responsive |
| TD-02 | Unused data volume | Ready volume not attached to another VM | For new NFS/SMB share attach |
| TD-03 | Existing XFS volume | Volume with existing XFS filesystem and test files | For non-destructive import |
| TD-04 | Existing ext4 volume | Volume with existing ext4 filesystem and test files | For non-destructive import |
| TD-05 | NFS client VM | VM that can mount NFS from Storage Service IP | For export access validation |
| TD-06 | SMB client VM | Linux or Windows client that can mount SMB | For SMB access validation |
| TD-07 | NVMe-oF client VM | Linux client with `nvme-cli` | For kernel NVMe-oF discovery/connect |

## API Smoke Test Order

Use CloudMonkey, direct signed API calls, or the Mold UI API client. Replace all
placeholder values at runtime.

1. Enable feature flag if needed.
2. Create or locate a Storage Service instance.
3. Enable NFS, SMB, iSCSI, and NVMe-oF protocols as required.
4. Create one NFS export and one SMB share.
5. Attach an existing volume to the NFS export.
6. Attach an existing volume to the SMB share.
7. Resize one file share.
8. Validate runtime inventory, health, and sessions.
9. Validate NVMe-oF `KERNEL_NVMET` preparation.
10. Validate NVMe-oF `SPDK` returns `PREPARATION_REQUIRED`.

## Test Cases

### TC-01 Create Storage Service Instance

Goal: verify the instance model can track a running Storage Service SystemVM.

Steps:

1. Call `createStorageServiceInstance` with `zoneid`, `name`,
   `serviceofferingid`, and optionally `virtualmachineid`.
2. Call `listStorageServiceInstances`.

Expected:

- Response object is `storageserviceinstance`.
- State is `Running` when `virtualmachineid` is supplied.
- Zone, service offering, and VM IDs resolve to UUIDs.

Result:

| Run ID | Status | Evidence | Defect/Improvement |
| --- | --- | --- | --- |
|  | Not Run |  |  |

### TC-02 Enable NFS And SMB Protocols

Goal: verify protocol state is persisted and QGA desired-state apply is invoked.

Steps:

1. Call `enableStorageServiceProtocol protocol=NFS`.
2. Call `enableStorageServiceProtocol protocol=SMB`.
3. Call `listStorageServiceHealth`.
4. Call `listStorageServiceInventory`.

Expected:

- Protocol state is `Ready`.
- Health command returns QGA result.
- Inventory includes NFS/SMB desired-state files or empty managed state.

Result:

| Run ID | Protocol | Status | Evidence | Defect/Improvement |
| --- | --- | --- | --- | --- |
|  | NFS | Not Run |  |  |
|  | SMB | Not Run |  |  |

### TC-03 Attach Existing XFS Volume To NFS Export

Goal: verify non-destructive existing-volume import for NFS.

Steps:

1. Create an NFS export with a stable name and no backing volume.
2. Call `attachStorageVolumeToFileShare` with the export ID, existing XFS
   `volumeid`, `path`, `filesystem=xfs`, and `importmode=MOUNT_EXISTING`.
3. Call `listStorageNfsExports`.
4. Inside the SystemVM, verify the volume is mounted at the expected path.
5. From NFS client, mount the export and verify existing test files.

Expected:

- Existing filesystem is not formatted.
- `config.lastInspection` records device path, filesystem, mount path, and
  import mode.
- NFS export remains `Ready`.
- Existing files are visible from client.

Result:

| Run ID | Volume | Export | Status | Evidence | Defect/Improvement |
| --- | --- | --- | --- | --- | --- |
|  |  |  | Not Run |  |  |

### TC-04 Attach Existing ext4 Volume To SMB Share

Goal: verify non-destructive existing-volume import for SMB.

Steps:

1. Create an SMB share with a stable name and no backing volume.
2. Call `attachStorageVolumeToFileShare` with the share ID, existing ext4
   `volumeid`, `path`, `filesystem=ext4`, and `importmode=MOUNT_EXISTING`.
3. Add SMB ACL for a local user or AD user according to test environment.
4. Call `listStorageSmbShares`.
5. From SMB client, mount the share and verify existing test files.

Expected:

- Existing filesystem is not formatted.
- SMB desired state is regenerated after mount.
- ACL behavior matches configured permission.
- Existing files are visible from client.

Result:

| Run ID | Volume | Share | ACL Type | Status | Evidence | Defect/Improvement |
| --- | --- | --- | --- | --- | --- | --- |
|  |  |  |  | Not Run |  |  |

### TC-05 Inspect-Only Existing Volume

Goal: verify operators can inspect a volume without mounting/exporting it.

Steps:

1. Call `attachStorageVolumeToFileShare` with `importmode=INSPECT_ONLY`.
2. Call list API for the file share.
3. Verify SystemVM did not mount the device.

Expected:

- `config.lastInspection` records detected device and filesystem.
- Share remains manageable.
- No NFS/SMB client access is exposed from the inspected-only volume.

Result:

| Run ID | Volume | Share | Status | Evidence | Defect/Improvement |
| --- | --- | --- | --- | --- | --- |
|  |  |  | Not Run |  |  |

### TC-06 Resize NFS File Share

Goal: verify storage capacity expansion for NFS.

Steps:

1. Choose an NFS export backed by a mounted volume.
2. Call `resizeStorageFileShare` with `resizevolume=true`, new `size`, and
   optionally `quotabytes`.
3. Verify CloudStack volume size changed.
4. Verify SystemVM filesystem size changed.
5. Verify NFS client sees the expanded capacity.

Expected:

- CloudStack backing volume resize succeeds.
- QGA `filesystem resize` succeeds.
- `config.lastResize` records device path, filesystem, mount path, and target
  size/quota.
- Export returns to `Ready`.

Result:

| Run ID | Export | Old Size | New Size | Status | Evidence | Defect/Improvement |
| --- | --- | --- | --- | --- | --- | --- |
|  |  |  |  | Not Run |  |  |

### TC-07 Resize SMB File Share Quota Only

Goal: verify share capacity limit can change without volume resize.

Steps:

1. Choose an SMB share backed by a mounted volume.
2. Call `resizeStorageFileShare` with `resizevolume=false` and new
   `quotabytes`.
3. Verify SMB desired state is reapplied.
4. Verify quota state JSON changes in SystemVM inventory.

Expected:

- Volume size is unchanged.
- Share quota metadata changes.
- SMB share remains accessible.

Result:

| Run ID | Share | Old Quota | New Quota | Status | Evidence | Defect/Improvement |
| --- | --- | --- | --- | --- | --- | --- |
|  |  |  |  | Not Run |  |  |

### TC-08 Resize Failure And Retry

Goal: verify partial failure does not silently corrupt service state.

Steps:

1. Force a filesystem resize failure using an unsupported filesystem or invalid
   mount path in a controlled test.
2. Call `resizeStorageFileShare`.
3. Verify API error and share state.
4. Fix the underlying issue.
5. Retry `resizeStorageFileShare`.

Expected:

- Failure returns clear error details.
- Share is marked `Error` if guest filesystem grow fails.
- Existing mount/export is not removed by the failed resize.
- Retry returns share to `Ready`.

Result:

| Run ID | Share | Failure Trigger | Status | Evidence | Defect/Improvement |
| --- | --- | --- | --- | --- | --- |
|  |  |  | Not Run |  |  |

### TC-09 NVMe-oF Kernel Preparation

Goal: verify `KERNEL_NVMET` prerequisite validation.

Steps:

1. Call `prepareStorageServiceNvmeOfVm engine=KERNEL_NVMET transport=tcp`.
2. Call `enableStorageServiceProtocol protocol=NVME_OF`.
3. Create subsystem, namespace, and host ACL.
4. From NVMe-oF client, run discovery and connect.

Expected:

- Preparation returns `status=ok` when kernel modules/configfs are available.
- Subsystem desired state is applied through configfs.
- Client can discover/connect to namespace when ACL allows it.

Result:

| Run ID | Subsystem NQN | Namespace | Client | Status | Evidence | Defect/Improvement |
| --- | --- | --- | --- | --- | --- | --- |
|  |  |  |  | Not Run |  |  |

### TC-10 NVMe-oF SPDK Gating

Goal: verify Storage Service does not perform VM-level runtime configuration.

Steps:

1. Call `prepareStorageServiceNvmeOfVm engine=SPDK`.
2. Create or update NVMe-oF subsystem with `engine=SPDK`.
3. Call `listStorageNvmeOfSubsystems`.
4. Check SystemVM logs and desired state.

Expected:

- Prepare API returns `status=PREPARATION_REQUIRED`.
- Response explains VM Runtime Capability dependency.
- Subsystem config contains `engine=SPDK` and
  `engineState=PREPARATION_REQUIRED`.
- Storage Service does not configure HugePage, NUMA, CPU pinning, memlock,
  SR-IOV, or PCI passthrough.
- `ablestack-storagectl nvmeof subsystem apply` skips SPDK subsystems instead
  of applying them through kernel nvmet.

Result:

| Run ID | API | Status | Evidence | Defect/Improvement |
| --- | --- | --- | --- | --- |
|  | `prepareStorageServiceNvmeOfVm` | Not Run |  |  |
|  | `create/updateStorageNvmeOfSubsystem` | Not Run |  |  |

### TC-11 Runtime Health, Inventory, And Sessions

Goal: verify operational visibility.

Steps:

1. Call `listStorageServiceHealth`.
2. Call `listStorageServiceInventory`.
3. Create active NFS/SMB/iSCSI/NVMe-oF sessions where available.
4. Call `listStorageServiceSessions`.

Expected:

- Health shows QGA, service, command, and desired-state status.
- Inventory reports active NFS exports, SMB shares, quotas, iSCSI targets, and
  NVMe-oF subsystems.
- Sessions include protocol, state, local endpoint, and peer endpoint.

Result:

| Run ID | Operation | Status | Evidence | Defect/Improvement |
| --- | --- | --- | --- | --- |
|  | Health | Not Run |  |  |
|  | Inventory | Not Run |  |  |
|  | Sessions | Not Run |  |  |

### TC-12 Mold UI Validation

Goal: verify UI can drive the same API workflows without owning storage logic.

Steps:

1. Open Storage Service UI.
2. Create/list NFS exports and SMB shares.
3. Attach existing volume to a file share.
4. Resize a file share.
5. Open NVMe-oF preparation view.
6. Verify SPDK is shown as planned or unavailable, not as a HugePage/NUMA
   configuration form.
7. Repeat in normal and dark mode.

Expected:

- UI uses existing Vue Ant Design Vue and Mold UI patterns.
- UI submits async APIs only.
- Normal and dark mode styles remain readable.
- No VM runtime controls are exposed inside Storage Service SPDK workflow.

Result:

| Run ID | Mode | Workflow | Status | Evidence | Defect/Improvement |
| --- | --- | --- | --- | --- | --- |
|  | Light |  | Not Run |  |  |
|  | Dark |  | Not Run |  |  |

## Regression Checklist

Run this after every fix.

| Check | Status | Notes |
| --- | --- | --- |
| Existing SharedFS APIs still follow upstream CloudStack behavior | Not Run |  |
| Existing Storage Service APIs still compile and register | Pass | Static build only |
| NFS desired-state apply still works after attach/resize changes | Not Run |  |
| SMB desired-state apply still works after attach/resize changes | Not Run |  |
| iSCSI target apply still works | Not Run |  |
| NVMe-oF kernel apply skips SPDK subsystems | Not Run |  |
| SPDK does not configure VM-level resources | Not Run |  |
| UI normal mode remains usable | Not Run |  |
| UI dark mode remains usable | Not Run |  |

## Defect And Improvement Log

Use this table for every issue found during validation.

| ID | Date/Time | Run ID | Severity | Area | Symptom | Root Cause | Fix Commit | Retest |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
|  |  |  |  |  |  |  |  |  |

## Release Readiness Criteria

The feature is ready for the next integration step only when:

1. TC-01 through TC-11 pass in at least one real `ablestack-diplo` environment.
2. TC-12 passes for the implemented UI surface.
3. All High/Critical defects are fixed and retested.
4. Remaining limitations are documented in this file and in operator-facing API
   or UI messages.
5. SPDK remains gated until VM Runtime Capability support is implemented.
