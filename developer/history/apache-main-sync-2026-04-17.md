# Apache Main Sync History - 2026-04-17

## Scope

- Repository: `ablestack-cloud`
- Goal:
  - Reflect `apache/cloudstack:main` changes into `local/main`
  - Propagate validated changes to `local/ablestack-europa` by `cherry-pick`
- Working baselines:
  - `apache/main`: `2d6280b9da` (`2026-04-17 04:35:25 +0530`)
  - `upstream/main`: `a873fb1ff4` (`2026-02-27 16:05:07 +0900`)
  - `origin/main`: `c6263fbf1c` (`2025-12-11 18:41:37 +0900`)
  - `ablestack-europa`: `661722858d` (`2026-04-16 09:03:28 +0900`)
- Common ancestor between `upstream/main` and `apache/main`:
  - `da85858e93`

## Range Summary

- Apache-only commits since `upstream/main` baseline:
  - `162` commits total
  - `150` non-merge commits
- Net diff size for `upstream/main..apache/main`:
  - `576` files changed
  - `36908` insertions
  - `7302` deletions
- Europa overlap hotspots with Apache delta:
  - `plugins`
  - `server`
  - `api`
  - `engine`
  - `ui`

## Operating Rules

- Do not mirror Apache merge commits as-is.
- Rebuild changes into local commits grouped by feature or risk boundary.
- Every local commit must include:
  - change summary
  - source Apache commit SHA list
  - expected functional impact
  - minimum verification result
  - Europa cherry-pick notes
- If a conflict happens during Europa cherry-pick:
  - record the conflict file and conflict reason here
  - resolve from the Europa branch perspective while preserving the Apache fix intent
  - separate adaptation-only changes into follow-up commits when needed

## Batch Plan

| Batch | Theme | Source pattern / examples | Europa risk | Status |
| --- | --- | --- | --- | --- |
| B00 | Metadata / CI / docs housekeeping | `.asf.yaml`, `.github/*`, `README`, pre-commit, codespell | Low | Planned |
| B01 | Resource limits / quota / reservation | `[22.0]`, `[20.3]`, quota summary, secondary storage limits | High | Planned |
| B02 | Backup / volume / snapshot / import flows | backup, restore, import VM, storage pool, snapshot chain | High | Planned |
| B03 | Network / VPC / LB / NSX / VR | static route, HAProxy, load balancer, NSX, VPC cleanup | High | Planned |
| B04 | Hypervisor / KVM / VMware / CKS | NIC enable/disable, Headlamp, SharedMountPoint, migration | Medium | Planned |
| B05 | UI / UX / config defaults | UI bug fixes, default language, hidden settings | Medium | Planned |
| B06 | Async jobs / account / user / API ergonomics | async job query, API key restructure, account/domain safeguards | Medium | Planned |

## Commit Record Template

### Commit ID: `TBD`

- Local branch: `main` or `ablestack-europa`
- Local commit: `TBD`
- Source Apache commits:
  - `TBD`
- Summary:
  - `TBD`
- Functional impact:
  - `TBD`
- Validation:
  - `TBD`
- Europa cherry-pick status:
  - `Pending`
- Conflict notes:
  - `None`
- Resolution notes:
  - `None`

## Applied Records

### Record 001 - EL10 python six compatibility packaging fix

- Local branch: `main`
- Local commit: `Pending commit creation`
- Source Apache commits:
  - `80ee7f183f` Fix six package incompatiblity with EL10 (#12799)
- Summary:
  - Add EL packaging requirements for `python3-six` and `python3-protobuf`
  - Bundle compatible `mysql_connector_python` wheels for both Python 3.6 and Python 3.8+
  - Install the matching wheel in `%post management` based on detected Python version
- Functional impact:
  - Prevent EL10 package installation/runtime issues caused by Python dependency mismatch
  - Preserve EL8 compatibility by keeping the Python 3.6-compatible connector path
- Validation:
  - Apache patch applied cleanly on `main` with no manual conflict resolution
  - Staged diff only touches `packaging/el8/cloud.spec`
- Europa cherry-pick status:
  - `Applied with manual conflict resolution`
- Conflict notes:
  - `main` change targeted `packaging/el8/cloud.spec`, but Europa mapped the patch onto `packaging/centos7/cloud.spec`
  - Europa already carried a custom `%post management` step for `pip3 install urllib3`
- Resolution notes:
  - Keep the Europa `centos7` spec path and preserve `pip3 install urllib3`
  - Adopt the Apache fix intent by switching to RPM-provided `python3-six` and `python3-protobuf`
  - Use Python-version-based `mysql_connector_python` wheel selection to cover both Python 3.6 and Python 3.8+ runtimes

### Record 002 - xcpng integration test cleanup hardening

- Local branch: `main`
- Local commit: `Pending commit creation`
- Source Apache commits:
  - `7cdcf571fa` Fix xcpng test failures (#12812)
- Summary:
  - Wrap zone, pod, and network preparation/cleanup flows in `try/finally`
  - Re-enable disabled resources even when intermediate test steps fail
  - Reduce cascading failures across integration test scenarios
- Functional impact:
  - No runtime product behavior change
  - Improves repeatability of xcpng-related integration tests by preventing leaked disabled resources
- Validation:
  - Apache patch applied cleanly on `main` with no manual conflict resolution
  - `python3 -m py_compile test/integration/component/maint/test_redundant_router_deployment_planning.py test/integration/smoke/test_public_ip_range.py`
- Europa cherry-pick status:
  - `Applied cleanly`
- Conflict notes:
  - `None`
- Resolution notes:
  - `Cherry-pick applied on europa without manual edits`

### Record 003 - async jobs filtering by resource type without resource id

- Local branch: `main`
- Local commit: `Pending commit creation`
- Source Apache commits:
  - `38abe2df0b` Allow list async jobs by resource type alone (#13011)
- Summary:
  - Allow `listAsyncJobs` to filter by `resourceType` without requiring `resourceId`
  - Only apply the `instanceUuid` filter when a valid `resourceId` is supplied
  - Clarify the validation error when `resourceId` is used without `resourceType`
- Functional impact:
  - Expands `listAsyncJobs` API usability for callers that want job lists for a resource class without a specific resource UUID
  - Prevents unnecessary validation failure when only `resourceType` is provided
- Validation:
  - Apache patch required manual conflict resolution on `main` because the surrounding `QueryManagerImpl` method had drifted
  - `mvn` / `mvnw` unavailable in this workspace, so targeted `server` module compile could not be run
- Europa cherry-pick status:
  - `Applied cleanly`
- Conflict notes:
  - `None on europa`
- Resolution notes:
  - `Cherry-pick applied on europa without manual edits`

### Record 004 - backup list keyword filter correction

- Local branch: `main`
- Local commit: `Pending commit creation`
- Source Apache commits:
  - `86c9f7bd94` Fix backup list
- Summary:
  - Keep backup name keyword filtering inside the existing `and` condition chain instead of opening a new `or` group
  - Preserve the `backupOfferingId` filter when listing backups with a keyword
- Functional impact:
  - Prevents `listBackups` keyword searches from returning rows that bypass the selected backup offering constraint
  - Narrows results to the intended offering-scoped backup set
- Validation:
  - Apache patch applied cleanly on `main`
  - Cached diff is limited to a single logical change in `BackupManagerImpl`
- Europa cherry-pick status:
  - `Applied cleanly`
- Conflict notes:
  - `None on europa`
- Resolution notes:
  - `Cherry-pick applied on europa without manual edits`

### Record 005 - countVgpuVMs prepared statement ordering fix

- Local branch: `main`
- Local commit: `Pending commit creation`
- Source Apache commits:
  - `6516f7f1aa` Fix query execution in countVgpuVMs (#12713)
- Summary:
  - Delay preparation and parameter binding of the second vGPU count query until after the legacy query has finished executing
  - Avoid mixing statement preparation/binding across the two query paths
- Functional impact:
  - Prevents erroneous query execution in `countVgpuVMs`
  - Improves correctness of aggregated vGPU VM counting used by scheduling or capacity-related paths
- Validation:
  - Apache patch applied cleanly on `main`
  - Cached diff is limited to prepared statement ordering changes in `VMInstanceDaoImpl`
- Europa cherry-pick status:
  - `Applied cleanly`
- Conflict notes:
  - `None on europa`
- Resolution notes:
  - `Cherry-pick applied on europa without manual edits`

### Record 006 - storage pool reorder logging and random compatibility

- Local branch: `main`
- Local commit: `Pending commit creation`
- Source Apache commits:
  - `161b4177c2` Add logs for storage pools reordering (#10419)
- Summary:
  - Improve storage pool allocator logging around reordering, shuffle, disk provisioning, and search start/end
  - Treat `userconcentratedpod_random` the same as `random` in the volume allocation reorder path
- Functional impact:
  - Improves observability when debugging allocator decisions and storage pool ordering
  - Preserves random reordering behavior for configurations that still use `userconcentratedpod_random`
- Validation:
  - Apache cherry-pick required manual conflict resolution on `main`
  - Verified the resolved method keeps the Apache condition for `userconcentratedpod_random` and the expanded logging changes
- Europa cherry-pick status:
  - `Applied cleanly`
- Conflict notes:
  - `None on europa`
- Resolution notes:
  - `Europa received the same resolved logic before main was backfilled`

### Record 007 - managed storage restore host null guard

- Local branch: `main`
- Local commit: `Pending commit creation`
- Source Apache commits:
  - `84676afd5c` Check for null host before proceeding with VM volume operations in managed storage while restoring VM (#12879)
- Summary:
  - Guard managed-storage restore cleanup when the VM host lookup returns `null`
  - Skip detach/delete command construction instead of dereferencing a missing host
- Functional impact:
  - Prevents restore-time failures caused by null host dereference during managed storage volume handling
  - Allows the restore flow to exit this cleanup path safely when the previous host record is unavailable
- Validation:
  - Applied as a focused manual port on `main` to avoid unrelated formatting churn from the Apache patch
  - Logic inspected in `handleManagedStorage`
- Europa cherry-pick status:
  - `Applied cleanly`
- Conflict notes:
  - `None on europa`
- Resolution notes:
  - `Cherry-pick applied on europa without manual edits`

### Record 008 - ACL metadata for backup-based restore and create APIs

- Local branch: `main`
- Local commit: `Pending commit creation`
- Source Apache commits:
  - `24fd440ee7` Fix create VM from backup
  - `8ce1c9876e` fix restore volume from backup and attach
- Summary:
  - Add `@ACL` metadata to `backupId` in `CreateVMFromBackupCmd`
  - Add `@ACL` metadata to `backupId`, `volumeUuid`, and `vmId` in `RestoreVolumeFromBackupAndAttachToVMCmd`
- Functional impact:
  - Improves API ACL enforcement and parameter-level access checks for backup-driven restore/create flows
  - Aligns backup resource parameters with existing ACL-aware command patterns
- Validation:
  - Both Apache patches applied cleanly on `main`
  - Cached diff is limited to ACL annotations and one import addition
- Europa cherry-pick status:
  - `Applied cleanly`
- Conflict notes:
  - `None on europa`
- Resolution notes:
  - `Cherry-pick applied on europa without manual edits`

### Record 009 - block backup deletion during pending restore/create jobs

- Local branch: `main`
- Local commit: `Pending commit creation`
- Source Apache commits:
  - `7ba5240b31` Block backup deletion while create-VM-from-backup or restore jobs are in progress (#12792)
- Summary:
  - Check for pending async jobs tied to a backup before allowing deletion
  - Block deletion while create-from-backup or restore flows are still running
  - Add unit coverage for the pending-job rejection path
- Functional impact:
  - Prevents destructive races between backup deletion and active backup restore/create operations
  - Reduces the chance of partial restore/create failures caused by deleting the source backup mid-flight
- Validation:
  - `BackupManagerImpl` change applied cleanly on `main`
  - `BackupManagerTest` needed a small mock-field merge to accommodate the new `AsyncJobManager` dependency
- Europa cherry-pick status:
  - `Applied cleanly`
- Conflict notes:
  - None on europa
- Resolution notes:
  - Cherry-pick applied on europa without manual edits

### Record 010 - preload backup architecture during create-from-backup

- Local branch: `main`
- Local commit: `Pending commit creation`
- Source Apache commits:
  - `1ff9eec997` Load arch data for backup from template during create instance from backup (#12801)
- Summary:
  - Load the backup source template or ISO architecture before opening the create-from-backup flow
  - Pre-fill `selectedArchitecture` from backup metadata instead of resetting to the zone default
  - Pass the fetched backup architecture through `CreateVMFromBackup` into `DeployVMFromBackup`
- Functional impact:
  - Prevents backup-based instance creation from silently defaulting to the wrong architecture on multi-arch zones
  - Keeps create-from-backup requests aligned with the source template or ISO architecture during restore-driven provisioning
- Validation:
  - Applied cleanly on `main` with changes limited to `DeployVMFromBackup.vue` and `CreateVMFromBackup.vue`
  - Frontend build or lint verification has not been run in this environment yet
- Europa cherry-pick status:
  - `Applied cleanly`
- Conflict notes:
  - None on europa
- Resolution notes:
  - Cherry-pick applied on europa without manual edits

### Record 011 - honor backup command timeout for NAS create and restore

- Local branch: `main`
- Local commit: `Pending commit creation`
- Source Apache commits:
  - `68bd056306` Support timeout configuration for Create and Restore NAS backup (#12964)
- Summary:
  - Use `command.getWait()` as a millisecond timeout for NAS backup create and restore operations
  - Fall back to `commands.timeout` from `LibvirtComputingResource` when the command-specific wait is not set
  - Update restore-side unit mocks so `rsync` failures are asserted through the timeout-aware script path
- Functional impact:
  - Prevents NAS backup create and restore flows from timing out too early when backup operations legitimately run longer
  - Aligns KVM backup execution with the configured command timeout behavior already used by other libvirt command wrappers
- Validation:
  - `LibvirtTakeBackupCommandWrapper` and the restore tests applied cleanly on `main`
  - `LibvirtRestoreBackupCommandWrapper` required a manual port because the Apache patch was based on a newer block-device helper structure than the current branch
  - Maven-based Java test execution could not be run because `mvn`/`mvnw` are not available in this environment
- Europa cherry-pick status:
  - `Resolved with manual merge`
- Conflict notes:
  - Europa already extends the restore path with `cacheMode` handling, and the Apache timeout variable rewrite overlapped with that customization while the branch still uses the older RBD-only helper structure
- Resolution notes:
  - Kept the Europa-specific `cacheMode` flow intact, then manually ported the Apache timeout semantics so `rsync` uses the timeout-aware script overload and `QemuImg` consumes milliseconds directly without changing the existing restore behavior

### Record 012 - clear backup schedule references before schedule deletion

- Local branch: `main`
- Local commit: `Pending commit creation`
- Source Apache commits:
  - `27e4d979f1` Clean up backup references to their schedules when the schedules are deleted (#12401)
- Summary:
  - Null out `backups.backup_schedule_id` before removing a backup schedule row
  - Move backup schedule response construction out of `BackupScheduleDaoImpl` and into `ApiResponseHelper`
  - Drop the unused `backup_interval_type` column from `cloud.backups`
- Functional impact:
  - Prevents deleted schedules from leaving stale schedule references behind on existing backups
  - Keeps backup schedule API responses working without coupling DAO code to VM lookup concerns
  - Removes an unused schema column so the backup table matches current runtime behavior
- Validation:
  - Java-side DAO and API response changes applied cleanly on `main`
  - The schema upgrade file required a manual merge because this branch does not yet include unrelated Apache `vm_template` updates that were present in the parent context of the patch
  - Database migration and Maven-based Java tests could not be run because no DB harness and no `mvn`/`mvnw` are available in this environment
- Europa cherry-pick status:
  - `Resolved with manual merge`
- Conflict notes:
  - Europa's `schema-42200to42210.sql` is further behind `main` and lacks surrounding upstream statements, so applying the full hunk would have pulled unrelated schema updates together with the backup cleanup change
- Resolution notes:
  - Kept the Java-side cleanup changes as-is, then manually added only the `backup_interval_type` drop statement to the local schema file so unrelated schema updates remain isolated to their own sync commits

### Record 013 - reserve backup and bucket limits during create and delete operations

- Local branch: `main`
- Local commit: `Pending commit creation`
- Source Apache commits:
  - `19b4ef1069` server: reserve backup, bucket resource limits during operations
- Summary:
  - Wrap backup create/delete resource checks with `CheckedReservation` for `backup` and `backup_storage`
  - Reserve `bucket` and `object_storage` limits during bucket allocation and deletion paths
  - Extend unit coverage for reservation-aware backup and bucket workflows
- Functional impact:
  - Prevents concurrent backup and bucket operations from passing limit checks and only failing after counters are updated too late
  - Keeps backup and object storage counters aligned with the actual success or failure of create/delete operations
- Validation:
  - `BucketApiServiceImpl` and its tests applied cleanly on `main`
  - `BackupManagerImpl` and `BackupManagerTest` required a manual merge because this branch already carries local backup safety changes around pending restore/create jobs
  - Maven-based Java test execution could not be run because `mvn`/`mvnw` are not available in this environment
- Europa cherry-pick status:
  - `Resolved with manual merge`
- Conflict notes:
  - Europa keeps a provider-specific backup lookup (`getBackupProvider(offering.getProvider())`) in delete flow, while the `main` patch context used zone-based provider selection around the same block
- Resolution notes:
  - Preserved the local pending backup job protection and Europa provider selection, then wrapped the delete path with the Apache reservation-based limit checks

### Record 014 - review fixes for backup and bucket reservation flow

- Local branch: `main`
- Local commit: `Pending commit creation`
- Source Apache commits:
  - `13842a626d` Address reviews
- Summary:
  - Let bucket delete and backup delete paths propagate `ResourceAllocationException`
  - Simplify backup reservation error handling so scheduled-backup alerts trigger only for actual limit exceptions
  - Tighten retention-cleanup method signatures and related unit tests for the reservation-aware backup flow
- Functional impact:
  - Prevents reservation failures from being swallowed inside generic runtime exceptions on backup and bucket operations
  - Keeps scheduled backup limit alerts focused on real quota violations instead of unrelated runtime failures
- Validation:
  - All review follow-up files applied cleanly on `main` except `DeleteBucketCmd`
  - `DeleteBucketCmd` needed a small manual merge to keep the local event-detail formatting while adding `ResourceAllocationException` to the API contract
  - Maven-based Java test execution could not be run because `mvn`/`mvnw` are not available in this environment
- Europa cherry-pick status:
  - `Applied cleanly`
- Conflict notes:
  - None on europa
- Resolution notes:
  - Cherry-pick applied on europa without manual edits

### Record 015 - validate bucket quota growth with reservations during update

- Local branch: `main`
- Local commit: `Pending commit creation`
- Source Apache commits:
  - `2511fdffaa` Implement limit validations on updateBucket
- Summary:
  - Move bucket quota-delta handling into a dedicated `updateBucketQuota` helper
  - Use `CheckedReservation` when bucket quota increases so `object_storage` growth is reserved before counters are incremented
  - Keep quota decreases as immediate counter decrements and allocated-size adjustments
- Functional impact:
  - Closes the remaining gap where `updateBucket` could increase object-storage quota without reservation-aware limit protection
  - Makes bucket quota updates consistent with the create/delete reservation model introduced in the previous two commits
- Validation:
  - Applied cleanly on `main`
  - Logic review confirms quota increases now reserve `object_storage` before incrementing counts
  - Maven-based Java test execution could not be run because `mvn`/`mvnw` are not available in this environment
- Europa cherry-pick status:
  - `Applied cleanly`
- Conflict notes:
  - None on europa
- Resolution notes:
  - Cherry-pick applied on europa without manual edits

### Record 016 - enforce secondary storage limits during download flows

- Local branch: `main`
- Local commit: `Pending commit creation`
- Source Apache commits:
  - `03dfe4d1f3` secondary storage resource limit for download
- Summary:
  - Track `LIMIT_REACHED` as a first-class download error state and stop persisting bogus size values for failed downloads
  - Use actual downloaded bytes as the fallback template size signal during secondary storage download progress
  - Recalculate secondary storage counts after template registration callbacks so final counters match persisted store-ref state
- Functional impact:
  - Prevents template and volume downloads from silently overrunning secondary storage limits during in-progress updates
  - Keeps template store size accounting consistent when download answers only report physical size or fail due to limit exhaustion
- Validation:
  - All download-state, resource-limit, and secondary-storage manager files applied cleanly on `main`
  - `HypervisorTemplateAdapter` required a manual merge because the local branch still used an older callback structure that increments counts before recalculation
  - Maven-based Java test execution could not be run because `mvn`/`mvnw` are not available in this environment
- Europa cherry-pick status:
  - `Applied cleanly`
- Conflict notes:
  - None on europa
- Resolution notes:
  - Cherry-pick applied on europa without manual edits

### Record 017 - enforce secondary storage limits during upload flows

- Local branch: `main`
- Local commit: `Pending commit creation`
- Source Apache commits:
  - `81a8ac8e1f` secondary storage resource limit for upload
- Summary:
  - Add abort-aware upload status polling so the management server can stop uploads after limit failures
  - Reserve `secondary_storage` usage during template and volume upload progress updates
  - Keep upload channels and SSVM-side state in sync when uploads are aborted or fail due to limit exhaustion
- Functional impact:
  - Prevents template and volume uploads from continuing after the management server detects secondary storage quota exhaustion
  - Makes upload-side secondary storage accounting consistent with the download-side reservation flow
- Validation:
  - Upload command, SSVM resource handler, and secondary-storage resource changes applied cleanly on `main`
  - `ImageStoreUploadMonitorImpl` required a manual merge because the current branch did not yet carry the reservation/account helper dependencies used by the Apache upload monitor changes
  - Maven-based Java test execution could not be run because `mvn`/`mvnw` are not available in this environment
- Europa cherry-pick status:
  - `Applied cleanly`
- Conflict notes:
  - None on europa
- Resolution notes:
  - Cherry-pick applied on europa without manual edits

### Record 018 - follow up secondary storage limit review fixes

- Local branch: `main`
- Local commit: `Pending commit creation`
- Source Apache commits:
  - `23b19a9776` review comments
- Summary:
  - Fix null-size and null-account edge cases in download and upload limit updates
  - Add size guards around template copy reservations so secondary storage reservations are only created when template size is known
  - Carry the same Apache review cleanup for project reservation code paths that live in the same upstream commit
- Functional impact:
  - Prevents limit-check helpers from miscounting when DB size fields or owner lookups are temporarily null
  - Avoids unnecessary reservation attempts for zone copy operations when template size has not been resolved yet
- Validation:
  - `DownloadListener` and `ImageStoreUploadMonitorImpl` review fixes applied cleanly on `main`
  - `ProjectManagerImpl` and `TemplateManagerImpl` required manual merges because the current branch had older pre-review reservation blocks in those exact sections
  - Maven-based Java test execution could not be run because `mvn`/`mvnw` are not available in this environment
- Europa cherry-pick status:
  - `Applied cleanly`
- Conflict notes:
  - None on europa
- Resolution notes:
  - Cherry-pick applied on europa without manual edits

### Record 019 - guard snapshot copy reservations against concurrency races

- Local branch: `main`
- Local commit: `Pending commit creation`
- Source Apache commits:
  - `8608b4edd0` Fix snapshot copy resource limit concurrency
- Summary:
  - Wrap snapshot copy-to-zone flow with `CheckedReservation` instead of a separate pre-check
  - Pass an explicit `shouldCheckResourceLimits` flag so snapshot-chain copies do not double-reserve secondary storage
  - Update snapshot copy tests to reflect reservation-based behavior instead of direct `checkResourceLimit` mocking
- Functional impact:
  - Prevents concurrent snapshot copy operations from passing standalone checks and then racing on secondary storage quota updates
  - Keeps snapshot copy reservations aligned with the real copy lifecycle, including chain-copy and KVM incremental snapshot cases
- Validation:
  - Applied cleanly on `main`
  - Snapshot manager and snapshot copy test updates are limited to reservation flow and test expectation changes
  - Maven-based Java test execution could not be run because `mvn`/`mvnw` are not available in this environment
- Europa cherry-pick status:
  - `Applied cleanly`
- Conflict notes:
  - None on europa
- Resolution notes:
  - Cherry-pick applied on europa without manual edits

### Record 020 - reserve start VM limits with host-tag aware reservations

- Local branch: `main`
- Local commit: `Pending commit creation`
- Source Apache commits:
  - `4bcd509193` Fix resource limit reservation and check during StartVirtualMachine
- Summary:
  - Extract the deployment-heavy portion of `startVirtualMachine` into a helper so reservation handling wraps only the limit-sensitive path
  - Replace standalone `checkVmResourceLimit` usage with `CheckedReservation` blocks for `user_vm`, `cpu`, and `memory` during running-only resource counting
  - Preserve current branch GPU accounting by reserving `gpu` resources when the service offering declares GPU capacity
- Functional impact:
  - Prevents concurrent `StartVirtualMachine` requests from passing a pre-check and then overshooting runtime VM resource limits during actual deployment
  - Aligns host-tag-aware start reservations with the same tagged resource accounting already used by deploy and destroy flows
  - Keeps GPU quota behavior symmetric on this branch so start reservations do not weaken existing GPU limit enforcement
- Validation:
  - `UserVmManagerImpl` is the only touched source file on `main`
  - The Apache patch required a manual merge because the local branch still carried the monolithic start method and a different VM details DAO field name
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `Resolved with manual merge`
- Conflict notes:
  - The Apache patch split `startVirtualMachine` into a helper while the local branch still had the older inline deployment flow, so the full method body conflicted
  - The extracted helper referenced `userVmDetailsDao`, but this branch uses `vmInstanceDetailsDao`
  - The pre-existing local `checkVmResourceLimit` path already covered GPU limits, while the Apache reservation patch only reserved VM, CPU, and memory
  - On `ablestack-europa`, the legacy inline start path also carried a branch-specific disaster recovery start guard, which collided with the helper extraction during cherry-pick
- Resolution notes:
  - Kept the Apache helper extraction and reservation structure, then adapted the helper to the local DAO field name
  - Added a conditional GPU reservation to preserve the branch's existing resource-limit coverage and keep start/destroy accounting behavior symmetric
  - Moved the Europa disaster recovery start validation into `startVirtualMachineUnchecked` so the branch-only guard remains enforced after the method split

### Record 021 - reserve extension-managed resource details

- Local branch: `main`
- Local commit: `Pending commit creation`
- Source Apache commits:
  - `95816b44e9` extensions: allow reserved resource details
- Summary:
  - Add `reservedresourcedetails` support to create/update/list extension API and UI flows so operators can declare extension-owned VM detail keys
  - Persist reserved detail names in extension hidden details, including built-in defaults for matching in-built extensions such as Proxmox
  - Extend VM detail filtering and VM update validation so non-admin users cannot view or mutate extension-reserved detail keys on extension-backed instances
  - Expose template extension linkage in `user_vm_view` and `UserVmJoinVO` so response filtering can decide which extension reservations apply
- Functional impact:
  - Lets extension authors reserve metadata keys that must stay under extension control instead of being visible or editable by tenants
  - Prevents end-user VM detail APIs and responses from leaking or overwriting extension-managed identifiers such as hypervisor-side instance metadata
  - Surfaces the reserved-detail configuration through the extension admin UI and API so the policy is manageable without direct DB edits
- Validation:
  - Most Apache files applied cleanly on `main`, including API constants, extension manager, schema view, and UI changes
  - `UserVmJoinDaoImpl` and `UserVmJoinDaoImplTest` required a manual merge because this branch already carried deploy-as-is response handling through `VMTemplateDao`
  - Maven and UI build execution have not been run yet in this environment by request
- Europa cherry-pick status:
  - `Resolved with manual merge`
- Conflict notes:
  - `UserVmJoinDaoImpl` already injected `VMTemplateDao` for deploy-as-is allowed-details handling in the same field block where Apache added `ExtensionHelper`
  - The corresponding unit test mock block diverged for the same reason
  - On `ablestack-europa`, the same DAO also keeps a branch-specific `VbmcDao` import and field block, so the helper injection landed in the middle of an existing local wiring section
- Resolution notes:
  - Kept the existing deploy-as-is response behavior and injected `ExtensionHelper` alongside `VMTemplateDao`
  - Preserved the Apache reserved-detail filtering logic without changing the branch-specific allowed-details response path
  - Retained the Europa `VbmcDao` wiring and restored `@Inject` on `ExtensionHelper` so the local DAO dependencies stay intact after the cherry-pick

### Record 022 - harden PVLAN VM setup against null inputs

- Local branch: `main`
- Local commit: `Pending commit creation`
- Source Apache commits:
  - `e10c066cc1` Fix NPE during VM setup for pvlan
- Summary:
  - Guard `setupVmForPvlan` against null NIC profiles and null broadcast URIs before dereferencing them
  - Skip PVLAN setup cleanly when the broadcast URI scheme is not `pvlan`
  - Return early when the target host lookup fails instead of continuing into agent command construction
- Functional impact:
  - Prevents VM deploy/start/stop paths from failing with a null-pointer exception when PVLAN metadata is incomplete or absent
  - Turns bad or missing PVLAN state into an explicit skip path with diagnostic logging, which is safer for mixed network environments
- Validation:
  - Applied cleanly on `main`
  - The change is limited to `UserVmManagerImpl.setupVmForPvlan`
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `Applied cleanly`
- Conflict notes:
  - `None observed on main`
- Resolution notes:
  - Cherry-pick applied on europa without manual edits

### Record 023 - validate VM compute details only when the offering allows it

- Local branch: `main`
- Local commit: `Pending commit creation`
- Source Apache commits:
  - `c6936889f5` server: prevent adding vm compute details when not applicable
- Summary:
  - Tighten `validateCustomParameters` so empty custom-parameter maps only fail for dynamic offerings, not for fixed offerings
  - Use `isCustomCpuSpeedSupported()` when validating CPU speed overrides and surface a clearer fixed-speed error message
  - Reject CPU, memory, and CPU speed detail updates up front in `verifyVmLimits` when the current offering is not dynamic
  - Add unit coverage for fixed-offering rejection and constrained custom-offering CPU speed validation
- Functional impact:
  - Prevents update flows from silently treating fixed offerings like custom offerings when VM compute detail keys are present
  - Stops non-applicable VM compute detail writes earlier, with more accurate error messages for operators and API callers
  - Keeps dynamic offering validation aligned with the offering's real CPU-speed customization capability on this branch
- Validation:
  - `UserVmManagerImpl` picked up the functional change on `main`
  - `UserVmManagerImplTest` and `KVMGuruTest` required manual merge because this branch already had additional test scaffolding and uses `isLimitCpuUse()` in the KVM guru path
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `Resolved with manual merge`
- Conflict notes:
  - `UserVmManagerImpl` import blocks diverged because the local branch already carried lease and snapshot-policy support alongside newer Apache scheduling and reservation imports
  - `KVMGuruTest` conflicted because upstream expected `getLimitCpuUse()` while this branch still exercises `isLimitCpuUse()`
  - `UserVmManagerImplTest` had a long trailing test block unique to this branch, so the upstream helper tests for dynamic offering validation landed inside an end-of-file conflict
  - On `ablestack-europa`, `validateCustomParameters` still used the older `serviceOffering.isCustomized()` gate, so the opening condition of the method conflicted with the Apache dynamic-offering guard
- Resolution notes:
  - Kept the Apache validation logic, but preserved local imports needed by lease and snapshot-policy features
  - Retained the Apache config-key preservation in `KVMGuruTest` while binding it to the branch's `isLimitCpuUse()` API
  - Kept all existing local tests and appended the new dynamic-offering validation cases after the current tail section
  - Replaced the Europa-era empty-map/customized check with the Apache `MapUtils.isEmpty(customParameters) && serviceOffering.isDynamic()` guard while keeping branch-specific imports such as `ManagementServer`

### Record 024 - fix template type handling during ISO upload

- Local branch: `main`
- Local commit: `Pending commit creation`
- Source Apache commits:
  - `c3d6a8cff7` server: fix templatetype during iso upload
- Summary:
  - Treat `GetUploadParamsForIsoCmd` as a user-template upload path so template-type validation returns `TemplateType.USER`
  - Switch the user-VM system-template guard to `TemplateType.SYSTEM.equals(...)` to avoid dereferencing a null template type
- Functional impact:
  - Prevents ISO upload-parameter requests from being misclassified as unsupported template-type operations
  - Removes a null-sensitive comparison in the user VM deploy path, making template-type validation safer when template metadata is incomplete
- Validation:
  - Applied cleanly on `main`
  - The change is limited to `TemplateManagerImpl.validateTemplateType` and the system-template gate in `UserVmManagerImpl`
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `Resolved with manual merge`
- Conflict notes:
  - `None observed on main`
  - On `ablestack-europa`, `TemplateManagerImpl.validateTemplateType` already handled ISO upload requests and template upload defaults in a branch-local shape, so the Apache early return collided with existing logic
- Resolution notes:
  - Kept the Apache null-safe `TemplateType.SYSTEM.equals(...)` guard in `UserVmManagerImpl`
  - Realigned `TemplateManagerImpl.validateTemplateType` to the Apache early-return structure for ISO uploads without duplicating the Europa branch's already-present upload defaulting logic

### Record 025 - default upload template types and backfill null template records

- Local branch: `main`
- Local commit: `Pending commit creation`
- Source Apache commits:
  - `470812100e` server: set template type to ROUTING or USER if template type is not specified when upload a template
- Summary:
  - Default `GetUploadParamsForTemplateCmd` uploads to `ROUTING` or `USER` when `templatetype` is omitted, using the existing `isrouting` flag
  - Reject non-admin upload-parameter requests for non-user template types with a specific API error
  - Backfill `cloud.vm_template.type` from `NULL` to `USER` in the 4.22.0 to 4.22.1 schema migration
- Functional impact:
  - Makes template upload-parameter requests behave like template registration, so omitted `templatetype` values no longer fall through as `null`
  - Prevents inconsistent permission handling between upload-parameter generation and later template registration paths
  - Reduces runtime ambiguity for older template rows that still carry a null `type`
- Validation:
  - `TemplateManagerImpl` picked up the functional change on `main`
  - `schema-42200to42210.sql` required a manual merge because this branch already dropped `backup_interval_type` in the same tail section
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `Resolved with manual merge`
- Conflict notes:
  - The schema migration tail diverged on `main` because local backup cleanup SQL replaced the upstream context around the new `vm_template.type` backfill
  - On `ablestack-europa`, `TemplateManagerImpl.validateTemplateType` had already been realigned by `Record 024`, but it still carried a branch-local `GetUploadParamsForIsoCmd` fallback later in the method, so the Apache upload-defaulting hunk overlapped with existing logic
- Resolution notes:
  - Kept the local `backup_interval_type` drop and added only the Apache null-type backfill, without reintroducing unrelated upstream migration context
  - Preserved the Apache non-admin error for disallowed template upload requests and removed the now-redundant Europa-era ISO fallback branch after keeping the earlier ISO early return

### Record 026 - show full network offering labels in add-tier dropdown

- Local branch: `main`
- Local commit: `Pending commit creation`
- Source Apache commits:
  - `120a43648b` set width of dropdown select items for Network Offering during add tier dialog
- Summary:
  - Add a `title` attribute to network offering select options in `VpcTiersTab.vue`
  - Reformat the option markup for readability while keeping the same displayed label text
- Functional impact:
  - Lets operators see the full network offering label on hover when the add-tier dropdown truncates long entries
  - Improves network offering selection confidence in crowded UI environments without changing API or backend behavior
- Validation:
  - Applied cleanly on `main`
  - The change is limited to `ui/src/views/network/VpcTiersTab.vue`
  - UI build execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `Applied cleanly`
- Conflict notes:
  - `None observed on main`
- Resolution notes:
  - Cherry-pick applied on europa without manual edits

### Record 027 - guard create-network global action when zone context is absent

- Local branch: `main`
- Local commit: `Pending commit creation`
- Source Apache commits:
  - `db83622956` ui: fix create network from global create menu
- Summary:
  - Use optional chaining when reading `resource.zoneid` in `CreateNetwork.vue`
  - Keep the zone filter only for deploy-VM and backup entry points, but avoid dereferencing an absent `resource`
- Functional impact:
  - Prevents the global create-network action from throwing when it is opened without a preselected zone context
  - Keeps zone-scoped behavior unchanged for deploy and backup entry paths while making the generic menu entry safer
- Validation:
  - Applied cleanly on `main`
  - The change is limited to `ui/src/views/network/CreateNetwork.vue`
  - UI build execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `Applied cleanly`
- Conflict notes:
  - `None observed on main`
- Resolution notes:
  - Cherry-pick applied on europa without manual edits

### Record 028 - route template-zone deletion back to the template list

- Local branch: `main`
- Local commit: `Pending commit creation`
- Source Apache commits:
  - `7aa0558c5b` ui: avoid 404 after deleting template zones
- Summary:
  - Redirect `TemplateZones.vue` to `/template` when the delete flow leaves the current detail view without remaining rows
  - Keep the surrounding table and modal behavior unchanged while avoiding a dead back-navigation path
- Functional impact:
  - Prevents the UI from landing on a stale detail route after the last template-zone entry is removed
  - Gives operators a predictable post-delete destination instead of depending on browser history state
- Validation:
  - Applied cleanly on `main`
  - The change is limited to `ui/src/views/image/TemplateZones.vue`
  - UI build execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `Resolved with manual merge`
- Conflict notes:
  - `None observed on main`
  - On `ablestack-europa`, the Apache hunk overlapped with a branch-local bulk-delete alert markup tweak that appends `&nbsp` after the selected-item counter
- Resolution notes:
  - Kept the Europa alert-spacing markup and the existing local table/view adjustments, while preserving the Apache `/template` redirect in `handleCancel()` to avoid the stale detail-route 404

### Record 029 - show security group selection for Basic zones and owner-scoped SG lists

- Local branch: `main`
- Local commit: `Pending commit creation`
- Source Apache commits:
  - `71daf84c9e` Show security group selection in Basic zone VM deployment and fix SG listing for cross-domain deployments
- Summary:
  - Always show the security-group step for Basic zones in `DeployVM.vue`
  - Pass the selected owner context (`domainId`, `account`, `projectId`) into `SecurityGroupSelection.vue`
  - Refresh the listed security groups when the owner context changes, and query `listSecurityGroups` with that owner context instead of always using the current session defaults
- Functional impact:
  - Restores security-group selection during VM deployment in Basic zones where the UI previously hid the step
  - Prevents cross-domain deployments from showing the wrong security-group list when the VM owner differs from the logged-in operator
  - Clears stale security-group selections when the deployment owner changes, reducing accidental carry-over between accounts or projects
- Validation:
  - Applied cleanly on `main`
  - The change is limited to `ui/src/views/compute/DeployVM.vue` and `ui/src/views/compute/wizard/SecurityGroupSelection.vue`
  - UI build execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `Applied cleanly`
- Conflict notes:
  - `None observed on main`
- Resolution notes:
  - Cherry-pick applied on europa without manual edits

### Record 030 - allow configurable default UI language

- Local branch: `main`
- Local commit: `Pending commit creation`
- Source Apache commits:
  - `ed575cc0a1` New config.json variable to set the ACS default language
- Summary:
  - Allow `defaultLanguage` as a GUI theme primitive property and add the sample key to `ui/public/config.json`
  - Use `defaultLanguage` when initializing `TranslationMenu.vue` if the user has no saved `LOCALE`
  - Let `guiTheme.js` propagate a theme- or config-provided default language into runtime config and local storage, then load that language pack
- Functional impact:
  - Makes the initial UI locale configurable through static config and dynamic GUI theme customization, instead of hard-coding a single fallback in the header component
  - Aligns first-load language selection across login, theme application, and later locale switches
  - Gives operators a supported way to preseed the portal language for new browsers or cleared storage sessions
- Validation:
  - Applied cleanly on `main`
  - The change is limited to `GuiThemeServiceImpl`, `ui/public/config.json`, `TranslationMenu.vue`, and `ui/src/utils/guiTheme.js`
  - UI build execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `Resolved with manual merge`
- Conflict notes:
  - `None observed on main`
  - On `ablestack-europa`, `TranslationMenu.vue` already carried a branch-local Korean default (`ko_KR`) and `ui/public/config.json` had additional local keys after the announcement banner, so the upstream default-language addition overlapped with existing locale policy and tail JSON structure
- Resolution notes:
  - Kept the Apache `defaultLanguage` support in GUI theme handling and runtime locale initialization
  - Set `ui/public/config.json` to `defaultLanguage: "ko_KR"` on europa so the new feature preserves the branch's existing default language policy
  - Updated `TranslationMenu.vue` to prefer saved `LOCALE`, then `vueProps.$config?.defaultLanguage`, and finally fall back to `ko_KR`

### Record 031 - block account and domain deletion when delete-protected VMs remain

- Local branch: `main`
- Local commit: `Pending commit creation`
- Source Apache commits:
  - `b196e97cc3` Prevent deletion of account and domain if either of them has deleted protected instance
- Summary:
  - Add DAO helpers to find active delete-protected VMs by account and by a set of domain IDs
  - Validate account deletion in `AccountManagerImpl` before destructive cleanup starts
  - Validate domain deletion in `DomainManagerImpl` across the full domain hierarchy, including child domains
  - Extend `DomainManagerImplTest` stubs so delete-domain tests continue to model an empty delete-protected VM result set
- Functional impact:
  - Prevents operators from deleting an account or domain while delete-protected instances still exist beneath it
  - Makes delete protection effective beyond direct VM delete calls by enforcing the same guard on higher-level ownership cleanup paths
  - Limits false positives to active VMs only by filtering out removed instances in the DAO layer
- Validation:
  - Applied cleanly on `main`
  - The change is limited to `VMInstanceDao`, `VMInstanceDaoImpl`, `AccountManagerImpl`, `DomainManagerImpl`, and `DomainManagerImplTest`
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `Resolved with manual merge`
- Conflict notes:
  - `None observed on main`
  - On `ablestack-europa`, `VMInstanceDao` and `VMInstanceDaoImpl` already carried the branch's `listByIdsIncludingRemoved` API, so the Apache DAO hunk overlapped with an existing method declaration and implementation block
- Resolution notes:
  - Kept the existing europa `listByIdsIncludingRemoved` contract and implementation
  - Added only the new delete-protection DAO methods and search builders required by the Apache account/domain deletion guard
  - Preserved the Apache service-layer validations in `AccountManagerImpl` and `DomainManagerImpl` without changing the surrounding europa deletion flow

### Record 032 - unhide and centralize JavaScript interpretation gating

- Local branch: `main`
- Local commit: `Pending commit creation`
- Source Apache commits:
  - `9f57a4dd19` Unhide setting `js.interpretation.enabled`
- Summary:
  - Move `js.interpretation.enabled` ownership from `ManagementService` into `JsInterpreterHelper` as a normal configurable system setting
  - Unhide the setting during the `4.22.1.0 -> 4.23.0.0` upgrade by migrating its stored value, category, component, and dynamic flag
  - Replace scattered `ManagementService`/`QuotaService` checks with `JsInterpreterHelper.ensureInterpreterEnabledIfParameterProvided(...)` in host, storage, secondary-storage selector, and quota tariff flows
  - Always register the secondary storage selector commands, but gate their JS-bearing parameters at validation time instead of hiding the commands entirely
- Functional impact:
  - Makes `js.interpretation.enabled` visible and manageable as a standard system setting instead of a hidden internal knob
  - Centralizes enable/disable enforcement for JS-backed parameters, reducing drift between quota, host-tag, storage-pool, and selector validation paths
  - Preserves safety when JS interpretation is disabled by rejecting only the parameters that require it, rather than removing whole APIs from discovery
- Validation:
  - `QuotaResponseBuilderImpl` required a manual merge on `main` because this branch still had the older `_quotaService.isJsInterpretationEnabled()` activation-rule guard in the same field block where Apache now keeps the quota-summary role set
  - The final `main` state uses `jsInterpreterHelper` for activation-rule validation and removes the old quota-service helper path
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `Resolved with manual merge`
- Conflict notes:
  - `QuotaResponseBuilderImpl` conflicted on `main` because the local branch still carried `checkActivationRulesAllowed()` and `_quotaService.isJsInterpretationEnabled()`-based gating
  - On `ablestack-europa`, `ManagementService` and `ManagementServerImpl` still carried the older `JsInterpretationEnabled` constant and `checkJsInterpretationAllowedIfNeededForParameterValue()` path
  - `ResourceManagerImpl` conflicted because europa's `updateHost(...)` signature already had a branch-specific `migrationIp` parameter while Apache added the new helper call in the shorter signature
  - `QuotaResponseBuilderImpl` also conflicted on europa for the same reason as `main`: the branch still had `_quotaService.isJsInterpretationEnabled()`-based activation-rule validation
- Resolution notes:
  - Dropped the old quota-service activation-rule helper and kept the Apache `jsInterpreterHelper.ensureInterpreterEnabledIfParameterProvided(...)` checks as the single validation path
  - Removed `JsInterpretationEnabled` and the old management-service validation hook from the europa API/service layer, while keeping the branch's wider API surface intact
  - Preserved the europa `updateHost(..., migrationIp)` signature and inserted the Apache helper guard at method entry instead of reverting the local parameter extension
  - Kept the europa `getConfigKeys()` shape and removed only the legacy JS interpretation config entry, so the helper-owned config becomes the single source of truth

### Record 033 - support async job lookup by resource

- Local branch: `main`
- Local commit: `893222e873`
- Source Apache commits:
  - `47c5bb8ee7` Support list/query async jobs by resource (#12983)
- Summary:
  - Add `resourceId` and `resourceType` filters to `listAsyncJobs` and `queryAsyncJobResult`
  - Make `ApiCommandResourceType.fromString(...)` case-insensitive for resource-driven async job lookups
  - Add `ResourceIdSupport` to centralize resource UUID parsing, resource-type validation, and access checks for async job resource filters
  - Extend `AsyncJobDao` and `AsyncJobDaoImpl` so async jobs can be resolved by either job id or `(resource type, resource id)`
- Functional impact:
  - Allows operators to locate async jobs even when they only know the backing resource UUID and type, not the async job UUID
  - Makes resource-type matching more tolerant of casing differences in API clients
  - Preserves the earlier local `Record 003` behavior that allows `listAsyncJobs` to filter by `resourceType` alone without requiring `resourceId`
- Validation:
  - Apache cherry-pick required manual conflict resolution on `main` in `QueryManagerImpl` because this branch already carries the `Record 003` extension that permits `resourceType`-only async job listing
  - `ApiResponseHelper` merged cleanly after reconciling import drift and retaining the local `Site2SiteVpnManager` injection block
  - Cherry-pick to `ablestack-europa` applied cleanly with no additional manual conflict resolution
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `Applied cleanly on ablestack-europa; local commit pending creation`
- Conflict notes:
  - `QueryManagerImpl` conflicted on `main` because Apache now expects `resourceType` and `resourceId` together for list filtering, while this branch intentionally already supports `resourceType` without `resourceId`
  - `ApiResponseHelper` conflicted on `main` in the import/injection region due unrelated local drift near the same hunk
- Resolution notes:
  - Kept the Apache `queryAsyncJobResult`, DAO lookup, case-insensitive resource-type parsing, and shared resource helper changes
  - Preserved the local `listAsyncJobs` type-only filter so this sync does not silently regress `Record 003`

### Record 034 - remove unused console proxy command port config

- Local branch: `main`
- Local commit: `f0f0218dcb`
- Source Apache commits:
  - `feb6076930` Remove unused config consoleproxy.cmd.port (#12807)
- Summary:
  - Remove the unused `ConsoleProxyCmdPort` config key from `ConsoleProxyManager`
  - Stop exposing `consoleproxy.cmd.port` through `ConsoleProxyManagerImpl.getConfigKeys()`
  - Delete stale `consoleproxy.cmd.port` rows during the `4.22.0.0 -> 4.22.1.0` schema upgrade
- Functional impact:
  - Removes an unused console proxy setting from the surfaced configuration set
  - Cleans obsolete configuration data during upgrade without changing active console proxy behavior
- Validation:
  - Apache cherry-pick required a manual merge on `main` only in `schema-42200to42210.sql` because this branch already re-ordered nearby upgrade statements for `backup_interval_type` removal and `vm_template.type` backfill
  - The resolved schema keeps the local statement order and adds only the `consoleproxy.cmd.port` cleanup delete
  - Cherry-pick to `ablestack-europa` applied cleanly with no additional manual conflict resolution
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `Applied cleanly on ablestack-europa; local commit pending creation`
- Conflict notes:
  - `schema-42200to42210.sql` conflicted on `main` because Apache inserts the config cleanup next to statements this branch already moved and de-duplicated earlier
- Resolution notes:
  - Preserved the local upgrade order, avoided duplicating the existing `backups.backup_interval_type` drop, and inserted only the new configuration cleanup

### Record 035 - update password reset mail template default value

- Local branch: `main`
- Local commit: `21c8b313df`
- Source Apache commits:
  - `5013cf2af6` Fix user password reset mail template value (#12882)
- Summary:
  - Update the `user.password.reset.mail.template` upgrade SQL to the new `{{{resetLink}}}` format
  - Migrate only legacy template values that still use the old `http://{{{resetLink}}}` or `{{{domainUrl}}}{{{resetLink}}}` placeholders
  - Use `CONCAT_WS('\n', ...)` so the stored template matches the multiline string expected by the newer password reset flow
- Functional impact:
  - Aligns upgraded deployments with the current password reset mail rendering logic
  - Avoids leaving stale default template values that generate the wrong reset URL in notification emails
- Validation:
  - Apache cherry-pick required a manual merge on `main` only in `schema-42200to42210.sql` because this branch already carries later upgrade statements in the same tail region
  - The resolved schema keeps the existing `backup_interval_type` removal and `consoleproxy.cmd.port` cleanup, then appends only the password reset template update block
  - Cherry-pick to `ablestack-europa` applied cleanly with no additional manual conflict resolution
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `Applied cleanly on ablestack-europa; local commit pending creation`
- Conflict notes:
  - `schema-42200to42210.sql` conflicted on `main` because Apache still includes an adjacent `backup_interval_type` drop that is already present in this branch
- Resolution notes:
  - Reused the existing schema tail order and inserted only the new template migration SQL to avoid duplicate DDL

### Record 036 - allow service offering lookup across cluster host tags

- Local branch: `main`
- Local commit: `75874d825d`
- Source Apache commits:
  - `b5858029bb` Fix listing service offerings with different host tags (#12919)
- Summary:
  - Add `HostTagsDao.listByClusterId(...)` so service offering search can inspect all host tags defined on hosts inside a VM's current cluster
  - Introduce `allow.different.host.tags.offerings.for.vm.scale` and register it through `UserVmManagerImpl.getConfigKeys()`
  - When the new setting is enabled, extend scale-offering host-tag matching to include any tag found in the VM's current cluster instead of requiring the current offering's exact tag set
  - Surface `hosttags` and `storagetags` columns in the compute offering wizard when the offering payload includes those fields
- Functional impact:
  - Lets operators list compatible target offerings for VM scale even when the offering uses a different host-tag subset that is still valid within the VM's cluster
  - Keeps the previous strict host-tag behavior by default unless the new advanced setting is enabled
  - Makes host and storage tag differences visible in the UI during compute offering selection
- Validation:
  - Apache cherry-pick applied cleanly on `main` with no manual conflict resolution
  - The change is limited to host-tag DAO/query plumbing, one new config key registration, two focused `QueryManagerImplTest` cases, and compute-offering wizard column rendering
  - Cherry-pick to `ablestack-europa` required manual conflict resolution in `UserVmManagerImpl` and `ComputeOfferingSelection.vue`
  - Maven/UI build execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `Applied on ablestack-europa after manual conflict resolution; local commit pending creation`
- Conflict notes:
  - `UserVmManagerImpl` conflicted on `ablestack-europa` because the local branch already exposes `EnableVmNetwokFilterAllowAllTraffic` in `getConfigKeys()`, while Apache appends `AllowDifferentHostTagsOfferingsForVmScale` in the same list tail
  - `ComputeOfferingSelection.vue` conflicted on `ablestack-europa` because the local branch already added the `kvdo` column and selection payload, while Apache adds optional `hosttags` and `storagetags` columns in the same header/table-source block
- Resolution notes:
  - Preserved the europa `EnableVmNetwokFilterAllowAllTraffic` config exposure and appended `AllowDifferentHostTagsOfferingsForVmScale` to the same config list
  - Kept the europa `kvdo` column/selection flow and added Apache's `hosttags` and `storagetags` display columns alongside it

### Record 037 - exclude group snapshots from account snapshot resource counts

- Local branch: `main`
- Local commit: `97a11d6e18`
- Source Apache commits:
  - `7b467496cb` Do not include snapshots with Group type in snapshots resource count (#12945)
- Summary:
  - Exclude `Snapshot.Type.GROUP` entries from `CountSnapshotsByAccount`
  - Apply the exclusion only to snapshot resource counting, leaving the rest of snapshot DAO behavior untouched
- Functional impact:
  - Prevents group snapshots from inflating per-account snapshot resource counts
  - Aligns snapshot quota/accounting behavior with the expectation that grouped snapshots should not be counted like regular per-volume snapshots
- Validation:
  - Apache cherry-pick applied cleanly on `main` with no manual conflict resolution
  - Cached diff is limited to the `CountSnapshotsByAccount` search builder and `countSnapshotsForAccount(...)`
  - Cherry-pick to `ablestack-europa` applied cleanly with no additional manual conflict resolution
- Europa cherry-pick status:
  - `Applied cleanly on ablestack-europa; local commit pending creation`
- Conflict notes:
  - `None observed on main`
- Resolution notes:
  - `N/A`

### Record 038 - avoid KVM domain lookup for non-running VM checks

- Local branch: `main`
- Local commit: `78a25f2a85`
- Source Apache commits:
  - `273699cf56` kvm: fix wrong CheckVirtualMachineAnswer when vm does not exist (#12928)
- Summary:
  - Only call `domainLookupByName(...)` when the VM power state is `PowerOn`
  - Preserve the paused-domain special case for powered-on VMs, but avoid libvirt domain inspection for powered-off or missing VMs
  - Add focused wrapper tests that cover running, paused, powered-off, unknown-state, null-VNC, and libvirt-exception paths
- Functional impact:
  - Prevents `CheckVirtualMachineCommand` from returning the wrong answer path when the VM no longer exists in libvirt
  - Reduces false failures for non-running KVM VMs by skipping unnecessary domain lookup
  - Improves regression coverage for the wrapper's error handling and paused-VM behavior
- Validation:
  - Apache cherry-pick applied cleanly on `main` with no manual conflict resolution
  - Cached diff is limited to the KVM check wrapper and its new unit test class
  - Cherry-pick to `ablestack-europa` applied cleanly with no additional manual conflict resolution
- Europa cherry-pick status:
  - `Applied cleanly on ablestack-europa; local commit pending creation`
- Conflict notes:
  - `None observed on main`
- Resolution notes:
  - `N/A`

### Record 039 - rollback disk snapshots on VM snapshot failure

- Local branch: `main`
- Local commit: `d1ebe5062b`
- Source Apache commits:
  - `d75acb6efc` Fix rollback disk snapshots on instance snapshot failure (#12949)
- Summary:
  - Add each created disk snapshot to the rollback list before invoking the snapshot strategy
  - Guard rollback cleanup against `null` `SnapshotInfo` and missing `SnapshotVO` rows
  - Keep the VM snapshot unit test aligned with the renamed rollback list parameter
- Functional impact:
  - Prevents partially created per-volume snapshots from being left behind when a later VM snapshot disk step fails
  - Makes rollback cleanup tolerant of partially persisted or already-removed snapshot metadata during failure handling
  - Reduces orphaned snapshot state in KVM VM snapshot error paths
- Validation:
  - Apache cherry-pick applied cleanly on `main` with no manual conflict resolution
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `Applied on ablestack-europa after manual conflict resolution; local commit pending creation`
- Conflict notes:
  - `StorageVMSnapshotStrategy.createDiskSnapshot(...)` conflicted on `ablestack-europa` because the local branch already calls `snapshotInfo.setVmSnapshotName(vmSnapshot.getName())` at the same insertion point where Apache adds rollback registration
- Resolution notes:
  - Preserved the europa `snapshotInfo.setVmSnapshotName(...)` behavior and added Apache's early `snapshotsForRollback.add(snapshotInfo)` registration alongside it

### Record 040 - include hidden image-store refs when resolving Xen snapshot chains

- Local branch: `main`
- Local commit: `bb635f652f`
- Source Apache commits:
  - `2a60305792` Fix snapshot chaining on Xen (#12597)
- Summary:
  - Add a DAO method that lists snapshot-store refs by snapshot id, role, and a set of states
  - Update `DefaultSnapshotStrategy.getSnapshotImageStoreRef(...)` to consider both `Ready` and `Hidden` image-store refs
  - Align the unit test with the new DAO method and remove the redundant null-path stubbing
- Functional impact:
  - Preserves Xen incremental snapshot chain lookup even when a parent snapshot is hidden on secondary storage
  - Reduces the chance of losing the expected parent chain and falling back to an incorrect full backup path
  - Keeps snapshot image-store lookup limited to the target zone while widening the acceptable persisted states
- Validation:
  - Apache cherry-pick required manual conflict resolution on `main` only in `SnapshotDataStoreDaoImpl` because this branch already carried a local `idStateNeqSearch` builder for non-destroyed snapshot lookups
  - The resolved DAO keeps the local `idStateNeqSearch` behavior and adds Apache's `idEqRoleEqStateInSearch` path for `Ready`/`Hidden` image-store lookup
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `Applied cleanly on ablestack-europa; local commit pending creation`
- Conflict notes:
  - `SnapshotDataStoreDaoImpl` conflicted on `main` because Apache adds a new state-in search builder in the same initialization block where this branch already introduced `idStateNeqSearch`
- Resolution notes:
  - Preserved the local `idStateNeqSearch` initialization and added the Apache `idEqRoleEqStateInSearch` builder without changing the branch-local non-destroyed lookup behavior

### Record 041 - pass snapshot CPG on Primera online copy

- Local branch: `main`
- Local commit: `e5e4e63261`
- Source Apache commits:
  - `8f3c6fad7a` set snapcpg config on copy (#12955)
- Summary:
  - Set `snapCpg` on Primera online copy parameters alongside the destination CPG
  - Leave the existing online copy behavior unchanged apart from propagating the configured snapshot CPG
- Functional impact:
  - Ensures Primera online copy operations inherit the configured snapshot CPG instead of relying only on the destination CPG
  - Reduces the risk of copy-time snapshot placement drifting from the storage policy expected by the Primera backend
- Validation:
  - Apache cherry-pick applied cleanly on `main` with no manual conflict resolution
  - Cached diff is limited to a single `parms.setSnapCPG(snapCpg)` line in `PrimeraAdapter`
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `Applied cleanly on ablestack-europa; local commit pending creation`
- Conflict notes:
  - `None observed on main`
- Resolution notes:
  - `N/A`

### Record 042 - scope persistent network lookup by zone

- Local branch: `main`
- Local commit: `2b5d1dc0d4`
- Source Apache commits:
  - `b805766f4b` Fix Host setup when persistent networks exist (#12751)
- Summary:
  - Add the data center filter to `PersistentNetworkSearch` in `NetworkDaoImpl`
  - Align the search builder with the existing `getAllPersistentNetworksFromZone(...)` parameter binding
- Functional impact:
  - Prevents persistent network discovery for host setup from matching networks that share attributes across different zones
  - Reduces the risk of reusing or counting persistent networks outside the requested data center when broadcast URI values overlap
- Validation:
  - Apache cherry-pick applied cleanly on `main` with no manual conflict resolution
  - Cached diff is limited to a single `PersistentNetworkSearch.and(\"dc\", ...)` addition in `NetworkDaoImpl`
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `Applied cleanly on ablestack-europa; local commit pending creation`
- Conflict notes:
  - `None observed on main`
- Resolution notes:
  - `N/A`

### Record 043 - compare NSX NAT delete service by name

- Local branch: `main`
- Local commit: `b078081e41`
- Source Apache commits:
  - `30dd234b00` fix: NsxResource.executeRequest DeleteNsxNatRuleCommand comparison bug (#12833)
- Summary:
  - Add `getNetworkServiceName()` to `DeleteNsxNatRuleCommand`
  - Compare NSX NAT delete service selection by service name instead of object identity
  - Add a focused test that verifies the Port Forwarding delete path reaches the expected NSX API call
- Functional impact:
  - Prevents NSX NAT rule deletion from missing the correct branch when the command carries an equivalent service object rather than the same enum instance
  - Improves reliability of Port Forwarding and Static NAT rule cleanup in NSX-backed networks
- Validation:
  - Apache cherry-pick applied cleanly on `main` with no manual conflict resolution
  - Cached diff is limited to the NAT delete command, `NsxResource`, and one focused `NsxResourceTest`
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `Applied on ablestack-europa as 7ad9fbc1f0`
- Conflict notes:
  - `None observed on main`
- Resolution notes:
  - `N/A`

### Record 044 - allow established and related traffic in routed VR forward chain

- Local branch: `main`
- Local commit: `166bb4304f`
- Source Apache commits:
  - `1fc4cb90bf` Routed VR: accept packets from related and established connections (#12986)
- Summary:
  - Add an nftables `ct state established,related accept` rule when creating `forward` chains in `CsNetfilter`
  - Leave the existing input/output ICMP allowance behavior unchanged
- Functional impact:
  - Prevents routed VR forward chains from dropping reply traffic that belongs to already established or related connections
  - Improves flow continuity for routed guest traffic without widening new-connection exposure
- Validation:
  - Apache cherry-pick applied cleanly on `main` with no manual conflict resolution
  - Cached diff is limited to a 2-line `CsNetfilter.py` change in the `forward` hook path
  - Runtime/systemvm test execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `Applied on ablestack-europa as 5681e66c42`
- Conflict notes:
  - `None observed on main`
- Resolution notes:
  - `N/A`

### Record 045 - validate zone local-storage enablement before creating file-based pools

- Local branch: `main`
- Local commit: `3c639d46fd`
- Source Apache commits:
  - `d38c1f8d12` Fix error message while creating local storage pool (#12767)
- Summary:
  - Reuse `isLocalStorageEnabledForZone(...)` when creating local storage
  - Reject file-scheme storage pool creation early when local storage is disabled for the zone
  - Replace duplicated zone-level local-storage checks with the shared helper
- Functional impact:
  - Returns a clearer validation failure when a local/file-backed primary storage pool is requested in a zone where local storage is disabled
  - Prevents the create-pool flow from reaching a later, less accurate error path
- Validation:
  - Apache cherry-pick required manual conflict resolution on `main` only in `StorageManagerImpl` because this branch already imports `ArrayUtils` in the same block where Apache adds `BooleanUtils`
  - The resolved file keeps the branch-local imports and preserves Apache's `isLocalStorageEnabledForZone(...)` guard in the create-pool path
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `Applied on ablestack-europa as 6fadfd9913`
- Conflict notes:
  - `StorageManagerImpl` conflicted on `main` only in the import section because the branch already had nearby local import additions
- Resolution notes:
  - Kept the existing `ArrayUtils` import and added Apache's `BooleanUtils` import so the shared helper and the local code both compile cleanly

### Record 046 - enable default SystemVM template registration on 4.20.2 -> 4.20.3 upgrade

- Local branch: `main`
- Local commit: `3422f7d5da`
- Source Apache commits:
  - `e2497cfc4d` backport: default system vm template update implementation (#12935)
- Summary:
  - Remove the empty `updateSystemVmTemplates(...)` override from `Upgrade42020to42030`
  - Let the upgrade path inherit the shared default implementation from `DbUpgradeSystemVmTemplate`
- Functional impact:
  - Restores automatic SystemVM template lookup/registration during the 4.20.2.0 -> 4.20.3.0 upgrade path
  - Prevents the upgrade from silently skipping the default SystemVM template update logic for that release jump
- Validation:
  - Apache cherry-pick applied cleanly on `main` with no manual conflict resolution
  - The branch already carried the interface-side default implementation, so the net change is the removal of the stale no-op override in `Upgrade42020to42030`
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `Applied on ablestack-europa as a51d5eb639`
- Conflict notes:
  - `None observed on main`
- Resolution notes:
  - `N/A`

### Record 047 - register new SystemVM templates for non-KVM hypervisors with amd64 arch

- Local branch: `main`
- Local commit: `76f1194d8c`
- Source Apache commits:
  - `6f1aa96b4c` engine/schema: fix new systemvm template is not registered during upgrade if hypervisor is not KVM (#12952)
- Summary:
  - Assign `CPU.CPUArch.amd64` to non-KVM hypervisors in `SystemVmTemplateRegistration.hypervisorList`
  - Update the registration test so VMware template metadata is expected with `amd64` instead of a null/default arch assumption
- Functional impact:
  - Prevents upgrade-time SystemVM template registration from skipping VMware, XenServer, Hyper-V, LXC, and OVM3 entries because their architecture was previously unspecified
  - Makes the registration map deterministic for non-KVM hypervisors
- Validation:
  - Apache cherry-pick required manual conflict resolution on `main` only in `SystemVmTemplateRegistrationTest` because this branch already refactored the test to call `getMetadataTemplateDetails(...)` directly
  - The resolved test keeps the branch-local helper call and adopts Apache's explicit `amd64` expectation
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `Applied on ablestack-europa as f0af890272 before backfilling local/main`
- Conflict notes:
  - `SystemVmTemplateRegistrationTest` conflicted on `main` because the local branch had already changed the VMware metadata lookup helper call while Apache updates the expected arch in the same assertion block
- Resolution notes:
  - Preserved the branch-local helper-based test structure and updated the asserted VMware arch to `CPU.CPUArch.amd64`

### Record 048 - add CloudStack user-agent headers to template download requests

- Local branch: `main`
- Local commit: `f051bdc876`
- Source Apache commits:
  - `4ebe3349b7` add user-agent header to template downloader request (#12791)
- Summary:
  - Introduce `HttpClientCloudStackUserAgent` as a shared CloudStack-branded user-agent string provider
  - Set the shared user-agent on HTTP and HEAD requests issued by template downloaders, direct downloads, URL validation helpers, and QCOW2 size probing
  - Reuse a common `UriUtils.USER_AGENT` constant for `HttpURLConnection`-based helper paths
- Functional impact:
  - Makes outbound template-download and remote-image probe traffic identify itself consistently to upstream HTTP servers
  - Reduces the chance of providers applying different behavior to anonymous Java HTTP clients during template validation, download, or virtual-size inspection flows
- Validation:
  - Apache cherry-pick applied cleanly on `main` with no manual conflict resolution
  - Cached diff is limited to downloader/helper call sites plus the new shared user-agent utility class
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `Applied on ablestack-europa as 1f8ff5cbad after history-doc conflict resolution`
- Conflict notes:
  - `None observed on main`
- Resolution notes:
  - `N/A`

### Record 049 - apply nexthop static routes to PBR tables and interface ACL chains

- Local branch: `main`
- Local commit: `994506d3ec`
- Source Apache commits:
  - `83f705ddc5` Static Routes with nexthop non-functional for private gateways (#12859)
- Summary:
  - Add `CsHelper.find_device_for_gateway(...)` to map a gateway IP to the matching router interface subnet
  - Update `CsStaticRoutes` so route add/delete operations touch both the main routing table and the matching interface-specific PBR table when a nexthop belongs to a private gateway subnet
  - Extend `CsAddress` firewall generation to emit the same inbound/outbound ACL chains for nexthop-based static routes as for legacy `ip_address`-based routes
- Functional impact:
  - Fixes VPC router traffic drops where static routes configured with a gateway/nexthop were installed only in the main routing table while policy-based routing uses interface-specific tables
  - Restores ACL and PREROUTING/FORWARD rule generation for nexthop-based static routes, so traffic can traverse private gateway paths consistently
- Validation:
  - Apache cherry-pick applied cleanly on `main` with no manual conflict resolution
  - Cached diff is limited to `CsAddress.py`, `CsHelper.py`, and `CsStaticRoutes.py`
  - Runtime/systemvm test execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `Applied on ablestack-europa as eb48668e0a after history-doc conflict resolution`
- Conflict notes:
  - `None observed on main`
- Resolution notes:
  - `N/A`

### Record 050 - skip redundant NSX LB patch operations that trigger 404s

- Local branch: `main`
- Local commit: `aaa8d95329`
- Source Apache commits:
  - `05c59630e0` fix: LB Creation avoid 404 API errors due to non-needed patches (#12835)
- Summary:
  - Check for existing NSX LB pools before patching and skip the update when the pool members are unchanged
  - Resolve monitor profile paths by direct lookup and create the monitor profile only when it is actually missing
  - Avoid patching an NSX virtual server when it already exists, and add focused regression tests for the skip/patch decision points
- Functional impact:
  - Prevents LB create/update flows from sending redundant NSX patch requests that can fail with `404 Not Found` when the target object is already in the desired state
  - Makes NSX LB provisioning more idempotent by distinguishing between genuinely missing objects and objects that already exist with the expected membership or monitor profile
- Validation:
  - Apache cherry-pick applied cleanly on `main` with no manual conflict resolution
  - Cached diff is limited to `NsxApiClient.java` and `NsxApiClientTest.java`
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `Applied on ablestack-europa as af6a324516 after history-doc conflict resolution`
- Conflict notes:
  - `None observed on main`
- Resolution notes:
  - `N/A`

### Record 051 - fetch all NSX paged list results instead of truncating at the first page

- Local branch: `main`
- Local commit: `10751b2efb`
- Source Apache commits:
  - `e0fe953791` fix: NSX SDK list operations are pageable: the API returns a non-null and non-empty (#12834)
- Summary:
  - Introduce a reusable `PagedFetcher` helper that follows NSX cursor-based pagination and merges items across pages
  - Update `NsxApiClient` list retrieval paths to use complete paged results for sites, enforcement points, locale services, and policy-group members
  - Add focused unit tests that cover single-page, empty-cursor, multi-page, and null-first-page-item flows
- Functional impact:
  - Prevents NSX-backed operations from acting on incomplete inventories when the NSX API returns more than one page of results
  - Makes NSX group, locale-service, and enforcement-point lookups deterministic in environments with larger object counts
- Validation:
  - Apache cherry-pick applied cleanly on `main` with no manual conflict resolution
  - Cached diff is limited to `NsxApiClient`, the new `PagedFetcher`, and its dedicated test class
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `Applied on ablestack-europa as 3604e72e77 after history-doc conflict resolution`
- Conflict notes:
  - `None observed on main`
- Resolution notes:
  - `N/A`

### Record 052 - add only missing PowerFlex MDMs when preparing the KVM SDC client

- Local branch: `main`
- Local commit: `c3d54c8797`
- Source Apache commits:
  - `71bd26ff7c` PowerFlex/ScaleIO storage - the MDMs validation improvements (#12893)
- Summary:
  - Filter the storage-pool MDM list down to only the addresses that are not already present in the SDC configuration
  - Return a success path when all requested MDMs are already configured, instead of forcing a redundant add flow
  - Report the exact missing MDM addresses when registration still fails after an add attempt
- Functional impact:
  - Prevents KVM ScaleIO/PowerFlex pool preparation from misclassifying a partially preconfigured MDM set as fully ready or fully failed based on the first address alone
  - Makes repeated SDC preparation idempotent and easier to troubleshoot when only a subset of MDM endpoints is missing
- Validation:
  - Apache cherry-pick applied cleanly on `main` with no manual conflict resolution
  - Cached diff is limited to `ScaleIOStorageAdaptor.java`
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `Applied on ablestack-europa as a0e88938be after history-doc conflict resolution`
- Conflict notes:
  - `None observed on main`
- Resolution notes:
  - `N/A`

### Record 053 - skip already-covered upgrade hops when the source version is not explicit in the graph

- Local branch: `main`
- Local commit: `2ce5188d0c`
- Source Apache commits:
  - `4b7370a601` upgrade: skip the upgrade paths which are not needed (#12881)
- Summary:
  - Filter `DatabaseVersionHierarchy.getPath(...)` results so only upgrade steps with a version strictly newer than the source database version are returned
  - Make the `Usage Server` configuration group insert idempotent in `schema-42000to42010.sql`
  - Add a regression test that verifies `4.20.1.0 -> 4.20.3.0` resolves directly to the `4.20.2.0 -> 4.20.3.0` upgrader
- Functional impact:
  - Prevents upgrade planning from replaying obsolete path segments when the exact source version is not a direct node in the version hierarchy
  - Makes the early 4.20 schema path safer on reruns by avoiding duplicate configuration-group insert failures
- Validation:
  - Apache cherry-pick applied cleanly on `main` with no manual conflict resolution
  - Cached diff is limited to the upgrade path resolver, one schema SQL line, and one targeted regression test
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `Applied on ablestack-europa as 8e73f1f762 after history-doc conflict resolution`
- Conflict notes:
  - `None observed on main`
- Resolution notes:
  - `N/A`

### Record 054 - restore management server id from cookies after SAML login

- Local branch: `main`
- Local commit: `affd5335ca`
- Source Apache commits:
  - `d6c39772b2` Set management server id from cookies after saml login (#12858)
- Summary:
  - Add the `managementserverid` cookie to the SAML login response when the login payload carries a management server id
  - Restore that cookie into the UI store during the SAML re-entry path in `permission.js`
- Functional impact:
  - Prevents post-SAML UI/API flows from losing the management server affinity that normal login paths already preserve
  - Reduces the chance of follow-up authenticated requests missing the server-id hint immediately after SAML authentication
- Validation:
  - Apache cherry-pick applied cleanly on `main` with no manual conflict resolution
  - Cached diff is limited to `SAMLUtils.java` and `ui/src/permission.js`
  - Maven/UI build execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `Applied on ablestack-europa as 1aabbb1777 after history-doc conflict resolution`
- Conflict notes:
  - `None observed on main`
- Resolution notes:
  - `N/A`

### Record 055 - avoid NAS backup provider crashes when no running KVM host is available

- Local branch: `main`
- Local commit: `e2bbf6a31f`
- Source Apache commits:
  - `6ca6aa1c3f` Fix NPE in NASBackupProvider when no running KVM host is available (#12805)
- Summary:
  - Guard `deleteBackup(...)` so it fails with a descriptive runtime exception when no running KVM host can be found in the target zone
  - Short-circuit `syncBackupStorageStats(...)` when there are no repositories or no eligible running KVM host
  - Keep the branch-local `commons-collections4` import while adopting Apache's host-null protections
- Functional impact:
  - Prevents the NAS backup sync background task from crashing with a null dereference during host outages or agent reconnect windows
  - Makes forced backup deletion failures explicit when there is no execution host available instead of failing later with an opaque NPE
- Validation:
  - Apache cherry-pick required manual conflict resolution on `main` only in the `CollectionUtils` import because this branch already uses `org.apache.commons.collections4.CollectionUtils`
  - The resolved file preserves Apache's null-host handling and repository-empty early return while keeping the local collections4 import
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `Applied on ablestack-europa as e85e854cd1 after history-doc conflict resolution`
- Conflict notes:
  - `NASBackupProvider` conflicted on `main` only in the import block due to the branch's existing `commons-collections4` migration
- Resolution notes:
  - Kept the local `org.apache.commons.collections4.CollectionUtils` import and applied the Apache runtime guards unchanged

### Record 056 - keep Public networks out of multi-CIDR cleanup side effects

- Local branch: `main`
- Local commit: `e8362ab92d`
- Source Apache commits:
  - `ae455ee193` VPC restart cleanup for Public networks with multi-CIDR data (#12622)
- Summary:
  - Skip `addCidrAndGatewayForIpv4/Ipv6(...)` and matching remove flows for `TrafficType.Public` networks in `ConfigurationManagerImpl`
  - Sanitize legacy Public-network addressing fields in `schema-42200to42210.sql` by nulling network-level CIDR/gateway columns
- Functional impact:
  - Prevents Public networks from accumulating comma-separated CIDR and gateway state that later breaks VPC restart cleanup with malformed CIDR parsing
  - Leaves Public network addressing sourced from VLAN/IP-range state rather than duplicating it into network-level fields
- Validation:
  - Apache cherry-pick required manual conflict resolution on `main` only in `schema-42200to42210.sql` because this branch already drops `backup_interval_type` in the same migration tail
  - The resolved migration keeps both the local backup cleanup and the Apache Public-network sanitization SQL
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `Applied on ablestack-europa as f93d0dde9b after history-doc conflict resolution`
- Conflict notes:
  - `schema-42200to42210.sql` conflicted on `main` because prior local migration work already rewrote the same tail section
- Resolution notes:
  - Preserved the existing `backup_interval_type` drop and inserted only the Apache Public-network cleanup statements beside it

### Record 057 - propagate forced delete flags across management servers

- Local branch: `main`
- Local commit: `a4baa35318`
- Source Apache commits:
  - `160876c6d7` Fix: API Thread held forever during force deleting across MS (#12968)
- Summary:
  - Extend `PropagateResourceEventCommand` with `forced` and `forceDeleteStorage` flags
  - Pass those flags through `ResourceManagerImpl.deleteHost(...)`, cross-MS propagation, and peer-side `executeUserRequest(...)`
  - Return peer-side runtime failure details as an explicit failed answer instead of letting the caller wait indefinitely
  - Add focused tests that verify delete-host overloads preserve the force flags
- Functional impact:
  - Prevents force-delete host operations routed through another management server from silently losing the force semantics
  - Turns peer-side propagated failures into deterministic API errors instead of threads hanging while waiting for a result that never arrives
- Validation:
  - Apache cherry-pick applied cleanly on `main` with no manual conflict resolution
  - Cached diff is limited to resource event propagation classes plus focused resource-manager tests
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `Applied on ablestack-europa as d1abaedd51 after history-doc conflict resolution`
- Conflict notes:
  - `None observed on main`
- Resolution notes:
  - `N/A`

### Record 058 - retry KVM incremental snapshot rebase after transient image locks

- Local branch: `main`
- Local commit: `eac20a6180`
- Source Apache commits:
  - `7c7b2ae75d` Fix KVM incremental volume snapshot creation (#12666)
- Summary:
  - Add `incremental.snapshot.retry.rebase.wait` to agent properties with a default 60-second backoff
  - Retry the QCOW2 rebase once when the initial rebase fails specifically because another process still holds the image lock
  - Preserve immediate failure behavior for non-lock-related rebase errors
- Functional impact:
  - Reduces transient KVM incremental snapshot failures caused by libvirt/qemu still holding the image lock immediately after snapshot operations
  - Keeps other rebase failures explicit instead of masking them behind generic retry behavior
- Validation:
  - Apache cherry-pick applied cleanly on `main` with no manual conflict resolution
  - Cached diff is limited to agent property definitions and `KVMStorageProcessor`
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `Applied on ablestack-europa as ec5184fc2d after history-doc conflict resolution`
- Conflict notes:
  - `None observed on main`
- Resolution notes:
  - `N/A`

### Record 059 - expose richer VM start failure details under an explicit config gate

- Local branch: `main`
- Local commit: `d5c5ff9455`
- Source Apache commits:
  - `68030df10b` VM start error handling improvements and config to expose error to users (#12894)
- Summary:
  - Add global config `expose.errors.to.user` and use it when deciding whether non-admin users may see detailed VM start errors
  - Track the last known start failure reason through deployment retries so final errors can surface a meaningful cause
  - Preserve the Europa-specific VirtualRouter network-unavailable message, but route its detail exposure through the same config gate
- Functional impact:
  - Gives operators and optionally end users clearer VM start failure messages instead of a generic “see management server log” response
  - Makes repeated deployment retries easier to diagnose by surfacing the last concrete failure reason when capacity or resource allocation ultimately fails
- Validation:
  - Apache cherry-pick required manual conflict resolution on `main` only in `VirtualMachineManagerImpl.start(...)` because this branch already had a custom VirtualRouter-specific error message
  - The resolved code keeps the local VirtualRouter wording but gates detailed exposure through Apache's new `canExposeError(...)` logic and config key
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `Applied on ablestack-europa as bcc7a07225 after history-doc conflict resolution`
- Conflict notes:
  - `VirtualMachineManagerImpl` conflicted on `main` where the branch already customized the VirtualRouter resource-unavailable error path
- Resolution notes:
  - Kept the local `The Network for VM ... is unavailable` message and switched its detail exposure to use Apache's `canExposeError(...)` policy

### Record 060 - expose and validate HAProxy idle timeout through load balancer orchestration

- Local branch: `main`
- Local commit: `32d8f85186`
- Source Apache commits:
  - `6e810989b6` HAProxy Configuration: network.loadbalancer.haproxy.idle.timeout (#12586)
- Summary:
  - Add `network.loadbalancer.haproxy.idle.timeout` as a configurable orchestration setting and propagate it through load balancer command construction
  - Teach `LoadBalancerConfigCommand`/`HAProxyConfigurator` to render `timeout client` and `timeout server` when the idle timeout is positive, and to blank them when explicitly set to `0`
  - Extend HAProxy health checks and tests so the new idle-timeout behavior is validated end to end
- Functional impact:
  - Gives operators a supported knob for HAProxy idle timeout without patching systemvm templates or generated configs by hand
  - Keeps runtime and health-check expectations aligned so VR load balancer checks do not falsely fail when the timeout is customized
- Validation:
  - Apache cherry-pick applied cleanly on `main` with no manual conflict resolution
  - Europa cherry-pick required manual conflict resolution in `LoadBalancerConfigCommand` and `HAProxyConfigurator` because this branch already carries `lbConnectTimeout`, `lbClientTimeout`, and `lbServerTimeout` command fields
  - Cached diff spans HAProxy command/config generation, orchestration config plumbing, tests, and the systemvm health check script
  - Maven/UI/systemvm test execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `Applied on ablestack-europa as a8c29e8606 after history-doc conflict resolution`
- Conflict notes:
  - `LoadBalancerConfigCommand` and `HAProxyConfigurator` conflicted on `ablestack-europa` where local HAProxy timeout customization already occupied the same command/config surfaces
- Resolution notes:
  - Preserved the local `lbConnectTimeout` / `lbClientTimeout` / `lbServerTimeout` fields and layered Apache's `idleTimeout` as a client/server override when present

### Record 061 - honor backup command timeout for NAS create and restore flows

- Local branch: `main`
- Local commit: `5803119a11`
- Source Apache commits:
  - `68bd056306` Support timeout configuration for Create and Restore NAS backup (#12964)
- Summary:
  - Use `command.wait` when provided, otherwise fall back to `commands.timeout`, for NAS backup create/restore KVM wrappers
  - Apply the resolved timeout to `rsync`, `qemu-img`, and piped backup commands instead of relying on mixed default process timeouts
  - Preserve the branch-local RBD restore helper flow while aligning its timeout handling with Apache's millisecond-based execution model
- Functional impact:
  - Prevents long-running NAS backup create/restore operations from timing out too early or ignoring operator-supplied wait values
  - Makes backup command execution more predictable across create and restore paths by using the same timeout source consistently
- Validation:
  - Apache cherry-pick required manual conflict resolution on `main` in `LibvirtRestoreBackupCommandWrapper` because this branch already refactored the RBD restore helper structure
  - The resolved code keeps the local RBD helper layout and applies Apache's timeout fallback logic to both restore and take-backup paths
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `Applied on ablestack-europa as c37ef5e0f1 after history-doc conflict resolution`
- Conflict notes:
  - `LibvirtRestoreBackupCommandWrapper` conflicted on `main` where the branch already carried a different restore-helper shape around the same timeout-sensitive logic
- Resolution notes:
  - Preserved the local RBD restore helper path and merged Apache's `command.wait -> commands.timeout` fallback plus timeout-aware script execution

### Record 062 - avoid forcing custom service offering changes on VM snapshot revert

- Local branch: `main`
- Local commit: `c778a082d4`
- Source Apache commits:
  - `b22dbbe2d7` Fix Revert Instance to Snapshot with custom service offering (#12885)
- Summary:
  - Split VM snapshot revert service-offering handling into a boolean "needs change" decision and only perform an upgrade when the snapshot actually differs from the current VM configuration
  - Compare dynamic compute offering CPU, memory, and speed against values stored in VM snapshot details so revert paths do not trigger unnecessary custom offering changes
  - Use snapshot detail values, not live VM detail values, when a service offering change is required during revert
- Functional impact:
  - Prevents revert-to-snapshot from forcing an unnecessary service offering change when the current VM already matches the snapshot's custom offering
  - Keeps dynamic custom offering reverts aligned with the snapshot's captured CPU and memory settings instead of whatever the VM happens to expose at revert time
- Validation:
  - Apache cherry-pick required manual conflict resolution on `main` in `VMSnapshotManagerImpl` and `VMSnapshotManagerTest` because this branch still carried the older inline upgrade flow
  - The resolved code keeps the Apache boolean gate, adds snapshot-detail map extraction, and only upgrades the VM offering inside the revert transaction when the snapshot truly requires it
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `Applied on ablestack-europa as bf875bec57 after history-doc conflict resolution`
- Conflict notes:
  - `VMSnapshotManagerImpl` and `VMSnapshotManagerTest` conflicted on `main` where the pre-existing logic upgraded the VM offering directly instead of deciding first whether a change was needed
- Resolution notes:
  - Replaced the older direct-upgrade path with Apache's conditional change flow and preserved the branch-local DAO and test wiring already present in these classes

### Record 063 - support SharedMountPoint volume checks during importVm preflight

- Local branch: `main`
- Local commit: `dae6777b00`
- Source Apache commits:
  - `b0b3dc91f5` fix: support SharedMountPoint volume checks for importVm (#12946)
- Summary:
  - Extend the KVM `CheckVolumeCommand` wrapper so SharedMountPoint pools are treated as supported when validating volumes for import flows
  - Keep the existing filesystem and NFS handling unchanged while broadening the accepted pool-type list
- Functional impact:
  - Prevents import-VM preflight checks from rejecting SharedMountPoint-backed volumes even though the hypervisor path can handle them
  - Reduces false negatives when validating KVM import candidates stored on shared mount primary storage
- Validation:
  - Apache cherry-pick applied cleanly on `main` with no manual conflict resolution
  - Staged diff only updates the supported pool-type list in `LibvirtCheckVolumeCommandWrapper`
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `Applied on ablestack-europa as 0f43c06318 after code and history-doc conflict resolution`
- Conflict notes:
  - `LibvirtCheckVolumeCommandWrapper` conflicted on `ablestack-europa` because the branch already carried `RBD` in the same supported pool-type list
- Resolution notes:
  - Preserved Europa's existing `RBD` support and added Apache's `SharedMountPoint` support alongside it

### Record 064 - support SharedMountPoint storage discovery for KVM import and unmanage

- Local branch: `main`
- Local commit: `920d6aa0ff`
- Source Apache commits:
  - `b1bc5380a2` fix: support SharedMountPoint for KVM volume import and unmanage (#12956)
- Summary:
  - Add SharedMountPoint to the supported KVM storage-pool types exposed by the import/unmanage API contract
  - Align the KVM volume listing wrapper's qemu-img-compatible storage-pool list with the same SharedMountPoint support
- Functional impact:
  - Lets KVM import and unmanage flows enumerate volumes on SharedMountPoint pools instead of excluding them as unsupported
  - Keeps API-level validation and hypervisor-side storage discovery consistent for the same storage backend
- Validation:
  - Apache cherry-pick applied cleanly on `main` with no manual conflict resolution
  - Staged diff updates one API interface constant and one KVM wrapper constant
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `Applied on ablestack-europa as fee04fc506 after code and history-doc conflict resolution`
- Conflict notes:
  - `LibvirtGetVolumesOnStorageCommandWrapper` conflicted on `ablestack-europa` because the branch already carried the same storage-pool types in a different local list layout
- Resolution notes:
  - Kept Europa's existing `RBD` and `SharedMountPoint` support while accepting the Apache alignment for the API and hypervisor-side constants

### Record 065 - replace GROUP_CONCAT backup volume serialization with JSON aggregation

- Local branch: `main`
- Local commit: `4d2401d7e7`
- Source Apache commits:
  - `4ba4bd33c3` replace GROUP_CONCAT with JSON_ARRAYAGG to avoid errors like Row 19 was cut by GROUP_CONCAT (#12777)
- Summary:
  - Rewrite upgrade SQL that backfills backup volume metadata so it uses `JSON_ARRAYAGG(JSON_OBJECT(...))` instead of string-built `GROUP_CONCAT(...)`
  - Keep the same backup volume payload fields while avoiding truncation-prone string concatenation in large-volume cases
- Functional impact:
  - Prevents upgrade-time backup metadata generation from silently truncating long volume lists under MySQL `GROUP_CONCAT` limits
  - Produces structurally valid JSON arrays for `backups.backed_volumes` and `vm_instance.backup_volumes` even when many disks are present
- Validation:
  - Apache cherry-pick applied cleanly on `main` with no manual conflict resolution
  - Staged diff only updates the SQL migration logic in `schema-42010to42100.sql`
  - Database migration execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `Applied on ablestack-europa as 6f1bd5d2f8 after history-doc auto-merge`
- Conflict notes:
  - `None observed on main`
- Resolution notes:
  - `N/A`

### Record 066 - improve KVM GPU domain parsing and support Display controller class

- Local branch: `main`
- Local commit: `a2baf8a85b`
- Source Apache commits:
  - `416679fae1` Fix domain parsing for GPU & add Display controller in the supported PCI class (#12981)
- Summary:
  - Tighten KVM GPU domain parsing in `LibvirtGpuDef` so discovery handles vendor output more reliably
  - Extend `gpudiscovery.sh` to treat Display controller PCI class entries as GPU-capable devices in addition to the previously supported classes
  - Add focused unit coverage for the updated GPU definition parsing behavior
- Functional impact:
  - Improves GPU discovery accuracy on hosts where PCI domain values or `lspci` output formatting previously caused parser failures
  - Enables detection of accelerator cards exposed through the Display controller class, including newer AMD Instinct-style hardware
- Validation:
  - Apache cherry-pick applied cleanly on `main` with no manual conflict resolution
  - Staged diff spans the GPU parser, the KVM discovery script, and new `LibvirtGpuDefTest` coverage
  - Maven-based Java test execution and script-level runtime validation have not been run yet in this environment by request
- Europa cherry-pick status:
  - `Applied on ablestack-europa as f77bea5384 after history-doc auto-merge`
- Conflict notes:
  - `None observed on main`
- Resolution notes:
  - `N/A`

### Record 067 - avoid custom offering NPEs during unmanaged and external VM import

- Local branch: `main`
- Local commit: `Pending commit creation`
- Source Apache commits:
  - `2416db2a44` Fix NPE on external/unmanaged instance import using custom offerings (#12884)
- Summary:
  - Move unmanaged/external KVM import CPU and memory reservation checks into dedicated helper methods that can read values either from the offering or from runtime/import details when the offering is dynamic
  - Add volume reservation helper coverage for external KVM import and wire the reservation lifecycle so conversions/imports close all temporary reservations safely
  - Extend `UnmanagedVMsManagerImplTest` with focused checks for dynamic offering detail parsing and invalid integer detail handling
- Functional impact:
  - Prevents unmanaged or external VM import from dereferencing null CPU/memory values when custom offerings rely on runtime/detail-provided sizing
  - Fails earlier and more cleanly when required import detail values are missing or malformed, instead of surfacing a later null-pointer failure
- Validation:
  - Apache cherry-pick required manual conflict resolution on `main` in `UnmanagedVMsManagerImpl` and `UnmanagedVMsManagerImplTest` because this branch already carries VMware import task tracking, extra-param validation, and adjacent import test coverage
  - The resolved code keeps the branch-local VMware import task flow and extra-param tests while layering Apache's reservation helpers and new custom-offering regression tests
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `Pending`
- Conflict notes:
  - `UnmanagedVMsManagerImpl` and `UnmanagedVMsManagerImplTest` conflicted on `main` where local import extensions and nearby tests occupied the same import-resource management sections
- Resolution notes:
  - Preserved local VMware import task bookkeeping and merged Apache's null-safe reservation helpers plus detail-parsing tests into the existing import flow

### Observed Already Satisfied

- `273699cf56` `kvm: fix wrong CheckVirtualMachineAnswer when vm does not exist (#12928)`
  - Current branch state already avoids `domainLookupByName(...)` for non-running VMs and already carries focused wrapper tests for the fixed behavior
  - Treat as already satisfied instead of creating a duplicate local commit
- `7ba5240b31` `Block backup deletion while create-VM-from-backup or restore jobs are in progress (#12792)`
  - Current branch state already blocks backup deletion when pending create-from-backup or restore jobs exist, and the matching regression test coverage is already present in `BackupManagerTest`
  - Treat as already satisfied instead of creating a duplicate local commit
- `abdf926219` `Revert "Use lateral join (introduced in MySQL 8.0.14) with subquery on user_statistics table in account_view for netstats (#12631)" (#12965)`
  - Current branch state already splits network statistics into `cloud.account_netstats_view` and joins that view from `cloud.account_view`
  - Treat as already satisfied instead of creating a duplicate local commit
- `8608b4edd0` `Fix snapshot copy resource limit concurrency`
  - Current branch state already wraps snapshot-chain copy reservation in `CheckedReservation` and routes per-snapshot copy through `copySnapshotToZone(..., shouldCheckResourceLimits)`
  - `SnapshotManagerImplTest` no longer stubs the removed direct `checkResourceLimit(...)` call in the covered copy flow
  - Treat as already satisfied instead of creating a duplicate local commit
- `470812100e` `server: set template type to ROUTING or USER if template type is not specified when upload a template (#12768)`
  - Current branch state already handles `GetUploadParamsForTemplateCmd` in `TemplateManagerImpl.validateTemplateType(...)`
  - The `schema-42200to42210.sql` backfill for `vm_template.type IS NULL -> USER` is also already present
  - Treat as already satisfied instead of creating a duplicate local commit
- `e10c066cc1` `Fix NPE during VM setup for pvlan (#12781)`
  - Current branch state already guards `setupVmForPvlan(...)` against null NICs, null broadcast URIs, non-PVLAN schemes, and missing hosts
  - Treat as already satisfied instead of creating a duplicate local commit
- `2359061f66` `api: remove required flag of gatewayid in CreateStaticRouteCmd (#12786)`
  - Current branch state already has `gatewayId` without `required = true`
  - Treat as already satisfied instead of creating a duplicate local commit
  - Re-check on `ablestack-europa` before final range reconciliation
- `59b6c32b60` `[UI] Fix create backup notification (#12903)`
  - Current branch state already uses `label.create.backup` in `StartBackup.vue`
  - Treat as already satisfied instead of creating a duplicate local commit
  - Re-check on `ablestack-europa` before final range reconciliation
- `c6936889f5` `server: prevent adding vm compute details when not applicable (#12637)`
  - Current branch state already contains the Apache validation changes in `validateCustomParameters(...)` and `verifyVmLimits(...)`
  - Matching regression tests are already present in `UserVmManagerImplTest`
  - Treat as already satisfied instead of creating a duplicate local commit

## Refined Resource-Limit Batches

### R01 - Backup / Bucket Reservation Core

- Apache commits:
  - `19b4ef1069`, `13842a626d`, `2511fdffaa`
- Scope:
  - Reservation-aware limit checks for backup create/delete and bucket alloc/delete/update flows
- Why grouped:
  - These three commits evolve the same operational path from initial reservation support to review fixes and `updateBucket` completion

### R02 - Secondary Storage Transfer Limits

- Apache commits:
  - `03dfe4d1f3`, `81a8ac8e1f`
- Scope:
  - Download/upload resource counting and reservation behavior on secondary storage transfer paths
- Why grouped:
  - Both commits govern transient secondary storage consumption during template/image movement and are likely to share supporting context

### R03 - Snapshot Copy Reservation Concurrency

- Apache commits:
  - `8608b4edd0`
- Scope:
  - Snapshot copy concurrency handling for resource limit reservations
- Why separate:
  - Touches snapshot management only and can be validated independently from backup/object storage flows

### R04 - VM Start Reservation Validation

- Apache commits:
  - `4bcd509193`
- Scope:
  - Reservation and limit validation during `StartVirtualMachine`
- Why separate:
  - High runtime sensitivity and likely overlap with Europa VM lifecycle customizations

### R05 - Reserved Resource Details Exposure

- Apache commits:
  - `95816b44e9`
- Scope:
  - API/UI exposure of reserved resource details for extensions and VM views
- Why separate:
  - This is an API/UI visibility change rather than a backend reservation enforcement change

## Initial Candidate Notes

### B00 - Metadata / CI / docs housekeeping

- Candidate Apache commits:
  - `608345d165` Update collaborators list in `.asf.yaml`
  - `9cc6c09b9e` Remove broken ViserJS attribution link from UI README
  - `9bbd32a8ef` Add contributor metadata
  - `d8f748ad0e` Update `.asf.yaml`
  - `b744824f65` Add code owners for NSX plugin
  - `6bcbb008b4` Bump `actions/checkout` to `v6`
  - `cf9bda2050` Add github-actions ecosystem to Dependabot
  - `5d95bdd0eb` pre-commit trailing whitespace auto clean up
  - `5d61ba3538` codespell and hook update
- Notes:
  - Safe starter batch for local/main commit workflow
  - Not all commits may need Europa cherry-pick if they do not affect runtime behavior

### B01 - Resource limits / quota / reservation

- Candidate Apache commits:
  - `37e3657770`, `003c840817`, `8d269cf5be`, `831ef82ff9`, `1f849caa0b`
  - `3d678e726a`, `d11d182c71`, `4855d40e6e`, `d722415105`, `07c3dc86b2`
  - `89df318164`, `4dd91feb27`, `1593944553`, `7faa1b650b`, `b025e85fc5`
  - `e0ef3a6947`, `06ee2fea76`, `4bcd509193`, `03dfe4d1f3`, `81a8ac8e1f`
  - `360b64ce1e`, `0a4b4c6af0`, `dc7068a135`, `9c0c8da706`, `e8d57d1b0d`
  - `4f93ba888c`, `19b4ef1069`, `2511fdffaa`
- Notes:
  - Highest functional risk area
  - Expect conflicts in `api`, `server`, `engine/schema`, `plugins/database/quota`
  - Duplicate release-line backports must be collapsed into one local change set

### B02 - Backup / volume / snapshot / import flows

- Candidate Apache commits:
  - `5d5ee7b689`, `f7f0e75122`, `88a12a801f`, `8ce1c9876e`, `24fd440ee7`
  - `86c9f7bd94`, `8608b4edd0`, `c19630f0a4`, `84676afd5c`, `b22dbbe2d7`
  - `2416db2a44`, `131ea9f7ac`, `6ca6aa1c3f`, `4ebe3349b7`, `e2497cfc4d`
  - `b0b3dc91f5`, `b1bc5380a2`, `03de62bf38`, `7ba5240b31`, `1ff9eec997`
  - `68bd056306`, `7b467496cb`, `2a60305792`, `8f3c6fad7a`, `df7ff97271`
  - `d75acb6efc`, `0c86899cc1`
- Notes:
  - Strong overlap with Europa customizations is likely
  - Storage provider-specific behavior must be reviewed before direct cherry-pick

### B03 - Network / VPC / LB / NSX / VR

- Candidate Apache commits:
  - `7ad68aafa5`, `2359061f66`, `27bce46a8e`, `09ee0927e9`, `93239e09f1`
  - `30dd234b00`, `abdf926219`, `ae455ee193`, `1fc4cb90bf`, `05c59630e0`
  - `e0fe953791`, `6e810989b6`, `83f705ddc5`
- Notes:
  - High probability of semantic conflicts on Europa networking behavior
  - Resolve based on current Europa service assumptions and API compatibility

### B04 - Hypervisor / KVM / VMware / CKS

- Candidate Apache commits:
  - `6419e1c825`, `9e386a3128`, `8c579538f9`, `7048944883`, `b497f58022`
  - `7107d28db8`, `7c3637a2f5`, `7cdcf571fa`, `c1af36f8fc`, `71bd26ff7c`
  - `18075ae4a9`, `7eea9ed448`, `e297644ce1`, `273699cf56`
- Notes:
  - Medium conflict risk, but runtime validation on KVM and CKS paths is required

### B05 - UI / UX / config defaults

- Candidate Apache commits:
  - `120a43648b`, `db83622956`, `7aa0558c5b`, `71daf84c9e`, `59b6c32b60`
  - `9f57a4dd19`, `ed575cc0a1`
- Notes:
  - Good early cherry-pick candidates after metadata batch
  - UI build and localization regression checks are required

### B06 - Async jobs / account / user / API ergonomics

- Candidate Apache commits:
  - `74af9b9875`, `470812100e`, `b5858029bb`, `416679fae1`, `b196e97cc3`
  - `47c5bb8ee7`, `38abe2df0b`, `5013cf2af6`, `160876c6d7`, `13842a626d`
- Notes:
  - Review API response compatibility before Europa propagation
