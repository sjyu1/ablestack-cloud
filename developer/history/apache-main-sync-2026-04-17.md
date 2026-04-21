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

### Observed Already Satisfied

- `2359061f66` `api: remove required flag of gatewayid in CreateStaticRouteCmd (#12786)`
  - Current branch state already has `gatewayId` without `required = true`
  - Treat as already satisfied instead of creating a duplicate local commit
  - Re-check on `ablestack-europa` before final range reconciliation
- `59b6c32b60` `[UI] Fix create backup notification (#12903)`
  - Current branch state already uses `label.create.backup` in `StartBackup.vue`
  - Treat as already satisfied instead of creating a duplicate local commit
  - Re-check on `ablestack-europa` before final range reconciliation

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
