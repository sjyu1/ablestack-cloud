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

### Observed Already Satisfied

- `2359061f66` `api: remove required flag of gatewayid in CreateStaticRouteCmd (#12786)`
  - Current branch state already has `gatewayId` without `required = true`
  - Treat as already satisfied instead of creating a duplicate local commit
  - Re-check on `ablestack-europa` before final range reconciliation
- `59b6c32b60` `[UI] Fix create backup notification (#12903)`
  - Current branch state already uses `label.create.backup` in `StartBackup.vue`
  - Treat as already satisfied instead of creating a duplicate local commit
  - Re-check on `ablestack-europa` before final range reconciliation

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
