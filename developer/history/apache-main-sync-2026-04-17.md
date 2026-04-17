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
