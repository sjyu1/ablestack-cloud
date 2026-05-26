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
- Functional test cases `TC-01` through `TC-12` must not be marked `Pass` until
  all required preparation stages `P-00` through `P-08` are complete or the test
  case explicitly states that it is an API/DB-only dry run.

## Validation Flow Overview

The validation is split into preparation stages and functional test cases.

Preparation stages are mandatory because the current implementation depends on:

- an updated Management Server and API set
- KVM host agent support for `StorageServiceHostCommand`
- a Storage Service SystemVM template that includes QGA, service packages, and
  `/usr/local/bin/ablestack-storagectl`
- a running Storage Service SystemVM or equivalent test VM
- client VMs and data volumes for protocol-level validation

Overall sequence:

| Order | ID | Type | Name | Required Before |
| --- | --- | --- | --- | --- |
| 0 | P-00 | Preparation | Repository and build artifact readiness | Any deployment |
| 1 | P-01 | Preparation | Management Server deployment readiness | API tests |
| 2 | P-02 | Preparation | KVM host agent deployment readiness | QGA/SystemVM tests |
| 3 | P-03 | Preparation | Storage Service SystemVM template build readiness | Storage Service VM creation |
| 4 | P-04 | Preparation | Storage Service SystemVM package verification | Protocol tests |
| 5 | P-05 | Preparation | Cloud environment readiness | API and VM lifecycle tests |
| 6 | P-06 | Preparation | Test volume readiness | Existing-volume and resize tests |
| 7 | P-07 | Preparation | Client VM readiness | NFS/SMB/NVMe-oF client tests |
| 8 | P-08 | Preparation | Observability and rollback readiness | Any destructive or stateful test |
| 9-20 | TC-01..TC-12 | Functional | Feature validation scenarios | Release readiness |

## Test Environment Record

Create one row per validation pass.

| Run ID | Date/Time | Branch | Commit | Cloud | Zone | SystemVM Template | Tester | Result |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| STATIC-20260526-01 | 2026-05-26 Asia/Seoul | `codex/diplo-storage-service-design` | `610f2bdf78` | local build only | N/A | N/A | Codex | Pass |
| P00-20260526-01 | 2026-05-26 Asia/Seoul | `codex/diplo-storage-service-design` | `1d67d683c609` | local build only | N/A | N/A | Codex | Pass |

## Current Static Verification Result

| Check | Command | Result | Notes |
| --- | --- | --- | --- |
| Diff whitespace check | `git diff --check` | Pass | CRLF warnings only on Windows checkout |
| SystemVM script syntax | `bash -n systemvm/debian/usr/local/bin/ablestack-storagectl` | Pass | Verified in RockyLinux-9.7 WSL |
| API module build | `mvn -pl api -DskipTests install` | Pass | Verified in WSL ext4 worktree |
| Server/schema build | `mvn -pl engine/schema,server -am -DskipTests install` | Pass | Verified in WSL ext4 worktree |

## Current Readiness Status

As of 2026-05-26, static code/build verification, Europa forward-porting, and
Management Server preflight checks have been completed. No real Storage
Service functional validation can be marked `Pass` yet because the aligned
Europa artifacts still need to be deployed, and the host deployment, Storage
Service SystemVM template, test volumes, and client VMs are not prepared.

| ID | Area | Current Status | Impact |
| --- | --- | --- | --- |
| P-00 | Repository and build artifact readiness | Complete | `ablestack-europa` is synchronized with upstream and aligned `4.22.0.0-SNAPSHOT` API/server/schema/KVM artifacts were built in the WSL ext4 worktree |
| P-01 | Management Server deployment readiness | Ready To Retry | Management Server is reachable, DB access and backup succeeded, and aligned `4.22.0.0-SNAPSHOT` artifacts are now available; deployment, DB migration, restart, and API registration checks still need to run |
| P-02 | KVM host agent deployment readiness | Not Started | QGA command path cannot be validated yet |
| P-03 | Storage Service SystemVM template build readiness | Not Started | Storage Service VM cannot provide NFS/SMB/iSCSI/NVMe-oF services yet |
| P-04 | Storage Service SystemVM package verification | Not Started | Runtime package/script presence is unknown |
| P-05 | Cloud environment readiness | Not Started | Zone/host/network/service offering readiness is unknown |
| P-06 | Test volume readiness | Not Started | Existing-volume import and resize tests cannot run yet |
| P-07 | Client VM readiness | Not Started | Client-side NFS/SMB/NVMe-oF access tests cannot run yet |
| P-08 | Observability and rollback readiness | Not Started | Stateful tests should not begin yet |

## Preparation Stages

### P-00 Repository And Build Artifact Readiness

Goal: verify the source branch can produce deployable artifacts.

Steps:

1. Confirm local `ablestack-diplo`, `origin/ablestack-diplo`, and
   `upstream/ablestack-diplo` are synchronized.
2. Confirm the work branch is based on the updated local `ablestack-diplo`.
3. Build API, schema, server, KVM plugin, and SystemVM package/script artifacts
   needed for deployment.
4. Record artifact paths and commit SHA.

Expected:

- Static build succeeds.
- Deployable jars/scripts are available.
- No unrelated local files are included in deployment.

Result:

| Run ID | Branch | Commit | Artifact | Status | Evidence | Defect/Improvement |
| --- | --- | --- | --- | --- | --- | --- |
| STATIC-20260526-01 | `codex/diplo-storage-service-design` | `610f2bdf78` | API/server/schema build | Pass | Maven build passed in WSL ext4 worktree |  |
| P00-20260526-01 | `codex/diplo-storage-service-design` | `1d67d683c609` | Base sync and deployable build artifacts | Pass | `ablestack-diplo`, `origin/ablestack-diplo`, and `upstream/ablestack-diplo` all at `849146ebdecd`; work branch is based on that commit; static checks and Maven builds passed |  |
| P00-20260526-02 | `codex/europa-storage-service` | `a0701701661` | Europa forward-port and deployable `4.22` build artifacts | Pass | `ablestack-europa`, `origin/ablestack-europa`, and `upstream/ablestack-europa` all at `7eb3b6eeaa`; work branch was created from local `ablestack-europa`; Storage Service commits were forward-ported from diplo; `git diff --check` passed; WSL builds passed for API, engine/schema plus server, and KVM plugin | Europa port required one API compatibility fix: `finalyzeAccountId` was corrected to `finalizeAccountId` |

P00-20260526-01 artifact candidates:

| Area | Artifact |
| --- | --- |
| API | `/root/work/ablestack-cloud-p00/api/target/cloud-api-4.21.0.0-SNAPSHOT.jar` |
| Schema | `/root/work/ablestack-cloud-p00/engine/schema/target/cloud-engine-schema-4.21.0.0-SNAPSHOT.jar` |
| Server | `/root/work/ablestack-cloud-p00/server/target/cloud-server-4.21.0.0-SNAPSHOT.jar` |
| Agent | `/root/work/ablestack-cloud-p00/agent/target/cloud-agent-4.21.0.0-SNAPSHOT.jar` |
| KVM plugin | `/root/work/ablestack-cloud-p00/plugins/hypervisors/kvm/target/cloud-plugin-hypervisor-kvm-4.21.0.0-SNAPSHOT.jar` |
| SystemVM control script | `/root/work/ablestack-cloud-p00/systemvm/debian/usr/local/bin/ablestack-storagectl` |
| SystemVM setup script | `/root/work/ablestack-cloud-p00/systemvm/debian/opt/cloud/bin/setup/common.sh` |

P00-20260526-02 artifact candidates:

| Area | Artifact |
| --- | --- |
| API | `/root/work/ablestack-cloud/api/target/cloud-api-4.22.0.0-SNAPSHOT.jar` |
| Schema | `/root/work/ablestack-cloud/engine/schema/target/cloud-engine-schema-4.22.0.0-SNAPSHOT.jar` |
| Server | `/root/work/ablestack-cloud/server/target/cloud-server-4.22.0.0-SNAPSHOT.jar` |
| Agent | `/root/work/ablestack-cloud/agent/target/cloud-agent-4.22.0.0-SNAPSHOT.jar` |
| KVM plugin | `/root/work/ablestack-cloud/plugins/hypervisors/kvm/target/cloud-plugin-hypervisor-kvm-4.22.0.0-SNAPSHOT.jar` |
| SystemVM control script | `/root/work/ablestack-cloud/systemvm/debian/usr/local/bin/ablestack-storagectl` |
| SystemVM setup script | `/root/work/ablestack-cloud/systemvm/debian/opt/cloud/bin/setup/common.sh` |

P00-20260526-01 executed checks:

| Check | Command | Result |
| --- | --- | --- |
| Fetch origin | `git fetch origin` | Pass |
| Fetch upstream | `git fetch upstream` | Pass |
| Local vs upstream base | `git rev-list --left-right --count ablestack-diplo...upstream/ablestack-diplo` | Pass, `0 0` |
| Local vs origin base | `git rev-list --left-right --count ablestack-diplo...origin/ablestack-diplo` | Pass, `0 0` |
| Work branch ancestry | `git merge-base --is-ancestor ablestack-diplo HEAD` | Pass |
| Diff whitespace check | `git diff --check` | Pass |
| Storage control script syntax | `bash -n systemvm/debian/usr/local/bin/ablestack-storagectl` | Pass |
| API build | `mvn -pl api -DskipTests install` | Pass |
| Server/schema build | `mvn -pl engine/schema,server -am -DskipTests install` | Pass |
| KVM plugin build | `mvn -pl plugins/hypervisors/kvm -am -DskipTests install` | Pass |

P00-20260526-02 executed checks:

| Check | Command | Result |
| --- | --- | --- |
| Fetch origin | `git fetch origin` | Pass |
| Fetch upstream | `git fetch upstream` | Pass |
| Local vs upstream base | `git rev-list --left-right --count ablestack-europa...upstream/ablestack-europa` | Pass, `0 0` |
| Local vs origin base | `git rev-list --left-right --count ablestack-europa...origin/ablestack-europa` | Pass, `0 0` |
| Work branch base | `git switch -c codex/europa-storage-service` from local `ablestack-europa` | Pass |
| Diplo forward-port | `git cherry-pick` Storage Service design, implementation, and validation commits | Pass, with Spring context tail conflicts resolved by preserving Europa beans and adding Storage Service beans |
| Diff whitespace check | `git diff --check` | Pass |
| API build | `mvn -pl api -DskipTests install` | Pass |
| Server/schema build | `mvn -pl engine/schema,server -am -DskipTests install` | Pass |
| KVM plugin build | `mvn -pl plugins/hypervisors/kvm -am -DskipTests install` | Pass |

### P-01 Management Server Deployment Readiness

Goal: prepare the Management Server so Storage Service APIs and managers are
available in the target cloud.

Steps:

1. Back up the current Management Server deployment.
2. Deploy updated API/server/schema artifacts.
3. Apply `schema-Diplo-After.sql` changes if the target database does not
   already contain the Storage Service tables.
4. Restart Management Server.
5. Verify API discovery includes:
   - `createStorageServiceInstance`
   - `enableStorageServiceProtocol`
   - `attachStorageVolumeToFileShare`
   - `resizeStorageFileShare`
   - `prepareStorageServiceNvmeOfVm`
6. Set or confirm `storage.service.feature.enabled=true`.

Expected:

- Management Server starts normally.
- New Storage Service APIs are registered.
- Existing SharedFS APIs still exist.
- No `503` or schema-related startup error occurs.

Result:

| Run ID | Host | Artifact/Commit | DB Migration | Status | Evidence | Defect/Improvement |
| --- | --- | --- | --- | --- | --- | --- |
|  |  |  |  | Not Run |  |  |
| P01-20260526-01 | `10.10.22.10` | local branch `4.21.0.0-SNAPSHOT`; target runtime `4.22.0.0-SNAPSHOT` | Not applied | Blocked | TCP 8080 reachable; TCP 10022 reachable; TCP 22 closed; unauthenticated API returned HTTP 401; SSH hostname check succeeded for `10.10.22.10` (`ccvm`) and host nodes `10.10.22.1` (`ablecube22-1`), `10.10.22.2` (`ablecube22-2`), `10.10.22.3` (`ablecube22-3`); `mold` and `mold-usage` are active; current management jars are `cloud-api/core/server-4.22.0.0-SNAPSHOT.jar`; DB password decryption and DB connectivity succeeded without printing secrets; expected Storage Service tables count is `0`; expected Storage Service configuration keys are absent; current `4.22` jars do not contain the new Storage Service API/manager classes; backup created at `/root/codex-backups/storage-service-p01-20260526-170947` with SHA-256 records for the existing API/core/server jars | Deployment, DB migration, Management Server restart, and API registration checks were intentionally not executed because replacing or mixing `4.21` artifacts into the shared `4.22` environment is unsafe. Prepare aligned `4.22` build artifacts or approve a minimal class-level patch plan against the existing `4.22` jars before continuing P-01. |
| P01-20260526-02 | `10.10.22.10` | `codex/europa-storage-service` `a0701701661`, `4.22.0.0-SNAPSHOT` artifacts | Not applied | Ready To Retry | Aligned `4.22` API, schema, server, agent, and KVM plugin artifacts are available in `/root/work/ablestack-cloud`; no additional Management Server files, DB schema, or services were changed during this source alignment pass | Continue P-01 by deploying the aligned `4.22` artifacts, applying the Storage Service schema changes, restarting `mold`, and verifying API registration. |

### P-02 KVM Host Agent Deployment Readiness

Goal: prepare every target KVM host that may run the Storage Service SystemVM.

Steps:

1. Identify candidate hosts in the target zone/cluster.
2. Back up the deployed KVM plugin or agent jar on each candidate host.
3. Deploy the code that includes `StorageServiceHostCommand` handling and the
   QGA guest-exec wrapper.
4. Restart the host agent service.
5. Verify the agent reconnects to Management Server.

Expected:

- Host agent is running and connected.
- Storage Service QGA command wrapper is available.
- Existing VM operations on the host are not regressed.

Result:

| Run ID | Host | Agent Service | Artifact/Commit | Status | Evidence | Defect/Improvement |
| --- | --- | --- | --- | --- | --- | --- |
|  |  |  |  | Not Run |  |  |

### P-03 Storage Service SystemVM Template Build Readiness

Goal: produce or identify a SystemVM template that can run Storage Service
protocol services.

Required packages and files:

| Area | Requirement |
| --- | --- |
| QGA | `qemu-guest-agent` installed and enabled |
| Control script | `/usr/local/bin/ablestack-storagectl` installed and executable |
| NFS | `nfs-kernel-server` or equivalent, `exportfs` |
| SMB | `samba`, `smbd`, `nmbd`, `smbpasswd`, `testparm` |
| AD domain join | `winbind`, `net`, Kerberos/Samba AD join dependencies |
| iSCSI | `targetcli-fb` or equivalent `targetcli` |
| NVMe-oF kernel | `nvme-cli`, kernel `configfs`, `nvmet`, `nvmet-tcp` support |
| Filesystem grow | `xfs_growfs`, `resize2fs`, `findmnt`, `lsblk` |
| Diagnostics | `ss`, `systemctl`, useful logging tools |

Steps:

1. Locate the current SystemVM template build process.
2. Add Storage Service package requirements to the template build profile.
3. Add `ablestack-storagectl` to the template.
4. Build the template.
5. Register the template in the target zone.
6. Record template name, ID, checksum, and build commit.

Expected:

- Template is registered and usable by the target zone.
- Required packages and scripts are present before functional tests begin.

Result:

| Run ID | Template Name | Template ID | Build Commit | Status | Evidence | Defect/Improvement |
| --- | --- | --- | --- | --- | --- | --- |
|  |  |  |  | Not Run |  |  |

### P-04 Storage Service SystemVM Package Verification

Goal: verify the running Storage Service SystemVM actually contains and runs the
required components.

Steps:

1. Deploy or start a VM from the Storage Service-ready SystemVM template.
2. Verify QGA is active from the host.
3. Inside the VM, verify:
   - `/usr/local/bin/ablestack-storagectl`
   - `qemu-guest-agent`
   - `exportfs`
   - `smbd`
   - `net`
   - `targetcli`
   - `nvme`
   - `xfs_growfs`
   - `resize2fs`
4. Run `ablestack-storagectl health`.
5. Run `ablestack-storagectl inventory`.

Expected:

- QGA guest-exec works.
- `health` returns structured JSON.
- Missing packages are documented before protocol tests begin.

Result:

| Run ID | VM | Host | QGA | Script | Packages | Status | Evidence | Defect/Improvement |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
|  |  |  |  |  |  | Not Run |  |  |

### P-05 Cloud Environment Readiness

Goal: verify the target cloud can host and manage Storage Service resources.

Steps:

1. Confirm target zone, pod, cluster, and hosts are healthy.
2. Confirm primary storage has enough capacity.
3. Confirm network connectivity from client VMs to the Storage Service SystemVM
   for:
   - NFS TCP/UDP 2049 as required
   - SMB TCP 445
   - iSCSI TCP 3260
   - NVMe-oF TCP 4420
4. Confirm suitable service offering exists for the Storage Service SystemVM.
5. Confirm test account/domain/project state is active.

Expected:

- No infrastructure blocker exists before functional tests.
- Network/firewall policy allows protocol-level tests.

Result:

| Run ID | Zone | Cluster | Storage | Network | Service Offering | Status | Evidence | Defect/Improvement |
| --- | --- | --- | --- | --- | --- | --- | --- |
|  |  |  |  |  |  | Not Run |  |  |

### P-06 Test Volume Readiness

Goal: prepare volumes that can safely be attached to the Storage Service
SystemVM.

Steps:

1. Create one unused blank data volume for basic attach testing.
2. Create one XFS volume with known test files.
3. Create one ext4 volume with known test files.
4. Create one volume suitable for resize testing.
5. Confirm none of the volumes are attached to another VM before import.
6. Record volume IDs, filesystem type, initial size, and test-file checksum.

Expected:

- Volumes are safe to attach.
- Existing-volume tests can prove non-destructive import.
- Resize tests have known before/after size.

Result:

| Run ID | Volume | Filesystem | Initial Size | Attached To | Test Data | Status | Evidence |
| --- | --- | --- | --- | --- | --- | --- | --- |
|  |  |  |  |  |  | Not Run |  |

### P-07 Client VM Readiness

Goal: prepare protocol clients for end-to-end access tests.

Steps:

1. Prepare an NFS client VM with mount utilities.
2. Prepare an SMB client VM with Linux CIFS tools or Windows SMB access.
3. Prepare an NVMe-oF client VM with `nvme-cli`.
4. Verify each client can route to the Storage Service network.
5. Record client VM IDs and IPs.

Expected:

- Clients can reach the Storage Service SystemVM IP.
- Required client tools are installed.

Result:

| Run ID | Client | Protocol | Tools | Network Reachability | Status | Evidence |
| --- | --- | --- | --- | --- | --- | --- |
|  |  | NFS |  |  | Not Run |  |
|  |  | SMB |  |  | Not Run |  |
|  |  | NVME_OF |  |  | Not Run |  |

### P-08 Observability And Rollback Readiness

Goal: make every state-changing test recoverable.

Steps:

1. Confirm log locations:
   - Management Server log
   - Host agent log
   - SystemVM `/var/log/ablestack-storagectl.log`
   - protocol service logs
2. Prepare rollback plan for:
   - Management Server artifact restore
   - host agent artifact restore
   - SystemVM template rollback
   - test volume detach/recovery
3. Confirm no production workload volumes are used.

Expected:

- Failures can be diagnosed and rolled back.
- Test volumes and client VMs are clearly identified.

Result:

| Run ID | Logs Verified | Rollback Verified | Test Resources Isolated | Status | Evidence |
| --- | --- | --- | --- | --- | --- |
|  |  |  |  | Not Run |  |

## Required Test Data

Prepare these resources after `P-00` through `P-08` are complete.

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

Do not start this order as a real functional validation until preparation
stages `P-00` through `P-08` are complete. Before then, only API/DB dry-run
checks are allowed and results must be marked as `Dry Run`, not `Pass`.

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

Preparation dependency:

- Full validation requires `P-01` through `P-05`.
- Without a Storage Service-ready SystemVM template, this test can only verify
  API registration and DB persistence with no `virtualmachineid`.

Steps:

1. Call `createStorageServiceInstance` with `zoneid`, `name`,
   `serviceofferingid`, and optionally `virtualmachineid`.
2. Call `listStorageServiceInstances`.

Expected:

- Response object is `storageserviceinstance`.
- State is `Running` when `virtualmachineid` is supplied.
- State is `Allocated` when no SystemVM is attached.
- Zone, service offering, and VM IDs resolve to UUIDs.
- QGA and runtime operations are not expected to work until the SystemVM
  template and host agent preparation stages are complete.

Result:

| Run ID | Mode | Status | Evidence | Defect/Improvement |
| --- | --- | --- | --- | --- |
|  | Dry Run or Full | Not Run |  |  |

### TC-02 Enable NFS And SMB Protocols

Goal: verify protocol state is persisted and QGA desired-state apply is invoked.

Preparation dependency:

- Requires `P-01` through `P-05`.
- If the SystemVM template or host agent is not prepared, this test must remain
  `Not Run`.

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

Preparation dependency:

- Requires `P-01` through `P-07`.
- Requires prepared XFS test volume from `P-06`.

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

Preparation dependency:

- Requires `P-01` through `P-07`.
- Requires prepared ext4 test volume from `P-06`.

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

Preparation dependency:

- Requires `P-01` through `P-08`.
- Requires a dedicated resize test volume from `P-06`.

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

Preparation dependency:

- Requires `P-01` through `P-07`.
- Requires NVMe-oF kernel packages and modules from `P-03` and `P-04`.

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

Preparation dependency:

- Requires `P-01` and deployed UI changes for API-driven workflows.
- Full protocol workflow validation also requires `P-02` through `P-08`.

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

1. P-00 through P-08 pass in the target `ablestack-diplo` environment.
2. TC-01 through TC-11 pass in at least one real `ablestack-diplo` environment.
3. TC-12 passes for the implemented UI surface.
4. All High/Critical defects are fixed and retested.
5. Remaining limitations are documented in this file and in operator-facing API
   or UI messages.
6. SPDK remains gated until VM Runtime Capability support is implemented.
