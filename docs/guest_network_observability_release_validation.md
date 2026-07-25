# Guest Network Observability 배포 Artifact 및 DB Migration 검증

## 1. 검증 기준

- 검증 일자: 2026-07-25
- 작업 브랜치: `codex/guest-network-observability`
- 소스 커밋: `90f0fc825671e9f06fd9e649e76ba69e4be83301`
- 기준 브랜치: `ablestack-europa`
- DB 엔진: MariaDB `10.5.29`
- DB 실행 방식: `/tmp` 임시 datadir, Unix socket 전용, `skip_networking=1`
- shared 22.x 관리 서버와 운영 DB에는 접속하거나 변경하지 않았다.

## 2. 배포 Artifact

Artifact bundle:

```text
/root/work/ablestack-cloud/dist/guest-network-observability/90f0fc825671e9f06fd9e649e76ba69e4be83301
```

Backend/Agent/API/KVM artifact는 다음 명령의 clean build 결과에서 추출했다.

```bash
mvn -pl agent,api,engine/schema,server,plugins/hypervisors/kvm -am -DskipTests clean package
```

- Maven reactor: 37개 모듈 모두 성공
- 결과: `BUILD SUCCESS`
- 소요 시간: 311.26초

UI artifact는 다음 production build 결과를 재현 가능한 tarball로 묶었다.

```bash
NODE_OPTIONS=--openssl-legacy-provider npm run build
```

- 결과: `DONE Build complete`
- 소요 시간: 348.52초
- tar 조건: 파일명 정렬, numeric owner `0`, 소스 커밋 시각 고정, gzip timestamp 제거

### 2.1 파일 및 SHA-256

| 파일 | 크기(byte) | SHA-256 |
|---|---:|---|
| `cloud-agent-4.22.0.0-SNAPSHOT.jar` | 96,642 | `5cf604f0e2bc75797a7f1dbd6bcab01c2331d35e09110ba9cf1d39e00b12189b` |
| `cloud-api-4.22.0.0-SNAPSHOT.jar` | 3,082,380 | `527025a3dc3af1237442b13113d509a2b4155f1596528b892450c43a6b7eedc1` |
| `cloud-core-4.22.0.0-SNAPSHOT.jar` | 849,212 | `98d301b81e6ccd80636bf1fe9cc35160ce15ae5f6a551a0e9f560a274b049e7a` |
| `cloud-engine-schema-4.22.0.0-SNAPSHOT.jar` | 2,424,695 | `a69930bfae4f32ff17631fdbaab07b17d5896b5b63642a593147b20a98782d73` |
| `cloud-plugin-hypervisor-kvm-4.22.0.0-SNAPSHOT.jar` | 1,166,556 | `47d2ce18cc67dbe7a04fd10d8cf50bca4f602386d5a4677d829892e6c7cd3254` |
| `cloud-server-4.22.0.0-SNAPSHOT.jar` | 4,691,197 | `ae854abdb48cb41f6c63841ef7bf0d456ec0c1b0a906cceab9901b49505e02f2` |
| `cloudstack-ui-dist-4.22.0.0-SNAPSHOT.tar.gz` | 13,063,918 | `70a208e91f7f39894bf82284312d08e0cd9dfa22f9955b703327af1162c5c24c` |
| `create-schema.sql` | 142,669 | `59b372d2a4f05957aae6f2501798f9291ba6d9f95d0eb837b220e13545567db2` |
| `schema-Europa-After.sql` | 11,006 | `5af1438dd8ff8729c3ffddae690306998701698f1c98a0e1ef2dcd6183991365` |

Bundle의 `SHA256SUMS`에 위 값이 기록되어 있으며 `sha256sum -c SHA256SUMS` 결과는 9개 파일 모두 `OK`였다.

### 2.2 Artifact 내용 확인

다음 신규 class가 대상 JAR에 포함된 것을 확인했다.

- Core: `GetVmGuestNetworkStateCommand`
- Agent: `Agent`
- API: `GetVirtualMachineGuestNetworkStateCmd`
- Schema: `VmGuestNetworkStateDaoImpl`
- Server: `VmGuestNetworkCollector`
- KVM: `LibvirtGetVmGuestNetworkStateCommandWrapper`

## 3. Fresh schema 적용 검증

빈 `cloud` database를 생성한 뒤 current `setup/db/create-schema.sql` 전체를 MariaDB client로 적용했다.

- 적용 결과: 성공
- 소요 시간: 3.45초
- `vm_guest_network_state` 생성: 성공
- Engine/charset: `InnoDB`, `utf8mb4`
- Primary key: `id`
- VM별 unique key: `uc_vm_guest_network_state__vm_id`
- 상태 조회 index: `i_vm_guest_network_state__status_observed_at(status, observed_at)`
- FK: `vm_id -> vm_instance.id ON DELETE CASCADE`

실제 DML 검증 결과:

- snapshot INSERT/UPDATE/SELECT: 성공
- payload 내 IPv4, 보조 IPv4, IPv6, DNS, IPv4/IPv6 route 보존: 성공
- `JSON_VALID(payload)`: `1`
- 동일 VM 두 번째 INSERT: 예상대로 `ERROR 1062`, unique key 동작
- 부모 `vm_instance` 삭제 후 snapshot row: `0`, cascade 동작

## 4. Upgrade migration 적용 검증

upgrade clone은 다음 순서로 구성했다.

1. 직전 커밋(`HEAD^`)의 `setup/db/create-schema.sql` 적용
2. `schema-42100to42200.sql`의 import VM predecessor table 구간 적용
3. 직전 커밋의 `schema-Europa-After.sql` 전체 적용
4. 신규 테이블이 없는 상태와 기존 VM sentinel row 존재 확인
5. current `schema-Europa-After.sql` 전체 적용

적용 전:

- 전체 table 수: 139
- `vm_guest_network_state`: 0개
- 기존 VM sentinel row: 1개

적용 후:

- current Europa migration 적용 결과: 성공
- 소요 시간: 0.29초
- 전체 table 수: 140
- `vm_guest_network_state`: 1개
- 기존 VM sentinel row: 1개
- migration 재실행: 성공, 0.28초

실제 DML과 제약조건 검증은 fresh 경로와 동일하게 모두 통과했다.

- IPv4 및 보조 IPv4 보존: 성공
- IPv6 보존: 성공
- DNS 보존: 성공
- IPv4/IPv6 route 보존: 성공
- valid JSON: 성공
- VM별 unique 제약: 성공
- parent delete cascade: 성공

동일하게 한 row를 INSERT/DELETE한 상태에서 `SHOW CREATE TABLE` 결과를 SHA-256으로 비교했다.

```text
fresh:   dde3246a9059941fa4acb34013e2d16593f93c307b40d9ff1f5e850cba6837d9
upgrade: dde3246a9059941fa4acb34013e2d16593f93c307b40d9ff1f5e850cba6837d9
```

따라서 fresh와 upgrade 경로의 최종 table DDL은 일치한다.

## 5. 범위와 후속 검증

- 이번 검증은 직전 Git 커밋으로 재구성한 격리 schema clone을 사용했다.
- 실제 shared 22.x 운영 DB dump나 운영 credential은 사용하지 않았다.
- 전체 `schema-42100to42200.sql`은 최소 fresh baseline에 과거 `router_health_check` table이 없어 단독 재생할 수 없으므로, 이번 기능의 predecessor인 import VM table 구간과 직전 Europa migration 전체를 적용해 이전 상태를 구성했다.
- 다음 단계는 shared 22.x의 비식별화 DB dump clone에 동일 upgrade를 적용하고, 최소 artifact 배포·성능 예산·rollback을 파일럿으로 확인하는 것이다.
