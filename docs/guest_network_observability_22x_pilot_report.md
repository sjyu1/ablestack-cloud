# Guest Network Observability 22.x 실제 DB Clone 및 최소 배포 파일럿

## 1. 결과 요약

- 검증 일자: 2026-07-25
- 작업 브랜치: `codex/guest-network-observability`
- 파일럿 시작 소스: `2ff59a5ae76dc28ac1034f9d3eacec6f03559c78`
- 대상: 22.x 관리 서버 1대, KVM 호스트 3대
- 최종 배포 범위: 관리 서버 Backend/API/DB DAO/UI, KVM 호스트 1대의 Agent 최소 class patch
- 미배포 호스트: KVM 호스트 2대
- 최종 기능 설정: `vm.guest.network.details.enabled=false`
- 최종 exec fallback 설정: `vm.guest.network.details.exec.fallback.enabled=false`

실제 22.x DB를 네트워크 비공개 clone으로 복제한 upgrade migration과
최소 배포 파일럿을 완료했다. VM의 모든 관측 IPv4/IPv6와 Cloud NIC 연결,
section별 상태가 API에서 확인됐다. 기존 VM, 볼륨, NIC 작업은 기능 활성
상태에서 각 20회 모두 성공했고 p95 증가는 허용 기준 5% 이하였다.

## 2. SSH 인증 및 안전 경계

운영자가 제공한 SSH private key를 WSL의 임시 파일로만 복사하고 권한을
`0600`으로 제한했다. 다음 네 대상에서 root 공개키 인증을 확인했다.

| 대상 | SSH 포트 | 인증 결과 |
|---|---:|---|
| 관리 서버 | 22 | 성공 |
| KVM 호스트 1 | 22 | 성공 |
| KVM 호스트 2 | 22 | 성공 |
| KVM 호스트 3 | 22 | 성공 |

private key, Cloud API secret 및 DB password는 repository, 보고서, dump
파일에 기록하지 않았다. Cloud API helper는 관리 서버 root 전용 backup
디렉터리에서 파일럿 동안만 사용하고 검증 종료 시 제거한다.

## 3. 실제 22.x DB clone 검증

### 3.1 Clone 및 비식별화

운영 DB는 raw dump 파일을 남기지 않고 `mysqldump` stream을 Unix socket
전용 MariaDB 임시 인스턴스로 직접 주입했다.

- 원본 DB 엔진: MySQL `8.0.41`
- 원본 `cloud` table: 389개
- 원본 논리 크기: 약 254.5 MiB
- 원본 VM: 596개
- clone 네트워크: `skip_networking=1`
- clone 경로: `/tmp`의 작업별 임시 디렉터리

복제 직후 다음 항목을 비식별화하거나 제거했다.

| 항목 | 처리 결과 |
|---|---:|
| 사용자 식별정보 | 6/6 비식별화 |
| API key/secret | 3/3 제거 |
| 호스트 식별정보 | 12/12 비식별화 |
| VM 식별정보 | 596/596 비식별화 |
| host detail | 53/53 비식별화 |
| storage detail | 1/1 비식별화 |
| import credential | 전부 삭제 |
| 민감 configuration 잔존 | 0 |

### 3.2 실제 upgrade migration

비식별화 clone에 `schema-Europa-After.sql`을 적용하고 재실행했다.

- 최초 적용: 성공, 0.36초
- 재실행: 성공, 0.28초
- 기존 VM 596개 보존
- `vm_guest_network_state` 생성
- IPv4, 다중 IPv4, IPv6, DNS, IPv4/IPv6 route JSON 보존
- `JSON_VALID(payload)=1`
- VM별 unique 제약 및 VM 삭제 cascade 정상

동일 DML 후 fresh schema와 실제 upgrade clone의 정규화된
`SHOW CREATE TABLE` SHA-256은 모두 다음 값으로 일치했다.

```text
083356e939c7f475fb4a0512f9b8d04576edb2f30d010809856a032d04ef7e6a
```

## 4. 실제 관리 DB migration

파일럿 전 schema-only backup을 생성한 뒤 실제 관리 DB에 migration을
적용했다.

- 적용 전 신규 table: 0
- 적용 후 신규 table: 1
- 적용 시간: 628 ms
- 기존 VM: 596개 유지
- 신규 snapshot row: 0
- 기능 기본값: `false`

신규 table은 다른 기존 table을 변경하지 않으며, rollback 시 drop하지
않고 기능을 비활성화한 채 보존한다.

## 5. 최소 배포

관리 서버의 실제 배포 기준 revision은
`0726eeb18c0e216b26aa19579ecd3b6f1b8c2faa`였다. 작업 브랜치 전체 JAR을
그대로 배포하지 않고 이 revision에 기능 변경만 이식해 37개 Maven
module과 UI를 다시 빌드했다.

관리 서버에는 Backend/API/DAO class, Spring bean 등록 resource와 UI
정적 artifact만 반영했다. KVM 호스트 1에는 신규 Agent/Core/KVM class만
기존 JAR에 patch했으며, 배포 runtime에 없는 외부 script를 요구하는
`LibvirtComputingResource` 변경은 제외했다. 호스트 2와 3은 변경하지
않았다.

최종 Agent 최소 patch:

| Artifact | patch entry | SHA-256 |
|---|---:|---|
| `cloud-agent` | 11 | `a463d8d19fd2cd8aa31f883b0cf9ba8465c25c0f99b5f6d4defff797e77837d7` |
| `cloud-core` | 11 | `8dd1a3086edf715e4301d430a1c65e255889a031b925886406651771258a4cdb` |
| `cloud-plugin-hypervisor-kvm` | 18 | `87db9c48a4cafbcc084a2c18c45fca5adbe2410b4c500092864d7336be677c58` |

최종 관리 서버 artifact:

| Artifact | SHA-256 |
|---|---|
| Management aggregate JAR | `b9c1697f43e87ba1b04c6be9bd8ff034cc039620ccdbae543fa680666f77e676` |
| Schema JAR | `55ab867bee48330ba24761529fbf2c417f8490032e02fe3b499be699eca82681` |
| UI `index.html` | `a12c170ef16e9a3cf52f8b95bc6498d6c12b4d2771903d64cc42391b1dd3a5b7` |

배포 후 관리 UI는 HTTP 200, 비인증 API는 HTTP 401, Host 1은 `Up`,
Agent는 `Ready` 상태를 유지했다.

## 6. 기능 파일럿

파일럿 scope와 부하 제한은 다음과 같다.

```text
zone.ids=1
host.ids=1
interface.interval=30
dns.interval=60
route.interval=60
jitter.percent=0
max.concurrent.hosts=1
max.concurrent.vms.per.host=1
max.vms.per.host.cycle=3
max.hosts.per.cycle=1
command.timeout=3
cycle.timeout=30
```

Host 1의 실행 VM 3대만 직렬 수집했고 Host 2와 3에는 신규 명령이
전달되지 않았다. Agent 전용 `GuestNetwork-Worker`는 worker 1,
active 0, queue 0, pending 0 상태로 반복 완료됐다.

파일럿 VM에서 확인한 관측 데이터:

- QGA version 확인
- Cloud NIC UUID와 guest interface 연결
- 일반 interface의 IPv4 보존
- 동일 interface의 global IPv6 및 link-local IPv6 동시 보존
- loopback IPv4/IPv6 보존
- section별 `OK`/`UNSUPPORTED` 및 상세 사유 표시

exec fallback을 켠 별도 기능 확인에서는 Host 1의 VM 3대 모두 DNS와
route prerequisite를 충족하지 못했다. 한 VM은 현재 allowlist에 없는
Debian이었고 두 VM은 QGA guest-exec/OS information capability가
없었다. API는 빈 값을 성공으로 오인하지 않고 각 사유를
`UNSUPPORTED`로 반환했다. 따라서 실제 DNS/route 값은 이번 환경에서
확인하지 못했으며 지원 VM fixture 또는 별도 파일럿이 필요하다.

## 7. 핵심 작업 회귀

기능 비활성/활성 상태에서 동일 VM과 disposable volume/network를 사용해
각 작업을 20회 반복했다.

| 작업 | 비활성 p95(초) | 활성 p95(초) | 변화 |
|---|---:|---:|---:|
| VM stop | 9.065 | 9.156 | +1.00% |
| VM start | 9.040 | 9.116 | +0.84% |
| Volume attach | 9.237 | 9.201 | -0.39% |
| Volume detach | 8.987 | 9.122 | +1.50% |
| NIC add | 9.132 | 8.991 | -1.54% |
| NIC remove | 9.122 | 9.081 | -0.45% |

- 총 120개 비동기 작업: 모두 성공
- 각 활성 p95 변화: 허용 기준 `+5%` 이내
- 기존 VM/볼륨/NIC 명령과 신규 QGA 명령: 별도 command와 전용 executor
- Agent 신규 queue overflow/error: 0

## 8. CPU 부하

기능 활성 상태의 동일 10분 표본 창을 관리 서버와 Agent 3대에서
측정하고 비활성 기준과 비교한다.

| 프로세스 | 비활성 평균 CPU | 활성 평균 CPU | 변화 |
|---|---:|---:|---:|
| Management | 3.0525% | 3.1612% | +0.1087 pp |
| Host 1 Agent | 0.2532% | 0.4297% | +0.1765 pp |
| Host 2 Agent | 0.2648% | 0.3064% | +0.0416 pp |
| Host 3 Agent | 0.6362% | 0.6412% | +0.0050 pp |

각 파일은 60초 간격 10개 표본이며 오류는 0이었다. 모든 프로세스가
management/agent 허용 기준 `+2.0 percentage point`를 충족했다. Host
2와 3의 소폭 변화는 신규 Agent artifact나 명령 없이 같은 시간대의
control group 변동이다.

## 9. 파일럿 중 발견 및 보완

### 9.1 Spring bean 누락

API service, state service, collector bean이 Spring context에 명시적으로
등록되지 않아 관리 서버 기동 실패로 드러났다. 배포 artifact와 source
XML 모두 등록을 추가했다.

### 9.2 배포 runtime 불일치

작업 브랜치 전체 class를 운영 JAR에 patch하면 배포 revision에 없는
다른 API class를 참조해 기동하지 못했다. 배포 revision 기반 worktree에
기능만 이식해 artifact를 다시 만들었다.

### 9.3 동적 활성화

관리 서버가 기능 비활성 상태로 시작하면 scheduler를 만들지 않아
`dynamic=true` 설정을 런타임에 켜도 수집이 시작되지 않았다. 비활성
상태에서는 30초마다 flag만 확인하는 단일 scheduler가 유휴 상태로
존재하고, Agent 명령과 DB write는 만들지 않도록 source를 보완했다.

### 9.4 작은 cycle limit의 VM 공정성

`max.vms.per.host.cycle=1`에서 정렬상 첫 VM이 매번 다시 due가 되어 다른
VM이 선택되지 않는 현상을 확인했다. 호스트별 마지막 선택 VM 이후부터
순환하도록 보완하고 3대 순환 unit test를 추가했다. 최종 관리 JAR을
기능 `false` 상태로 재기동한 뒤 재시작 없이 활성화했고, 30초 간격 세
주기에서 서로 다른 VM 3대가 차례로 갱신되는 것을 실제 환경에서도
확인했다.

## 10. 검증 및 최종 상태

- Server guest-network 관련 5개 test suite: `BUILD SUCCESS`
- 실제 DB clone fresh/upgrade DDL 일치
- 관리 서버/UI/API 정상
- Host 1 Agent Ready/Up
- Host 2/3 artifact 미변경
- 기능: `false`
- exec fallback: `false`
- 비활성화 후 in-flight 1개 완료 다음 전체 주기에서 추가 갱신: 0
- disposable NIC: 제거
- disposable volume: 분리 후 삭제
- 인증 helper/private key/DB clone: 검증 종료 시 제거

rollback backup ID:

```text
guest-network-20260725-211518-2ff59a5
```

관리 서버와 Host 1의 원본 JAR/UI backup은 위 ID 아래에 보존했다. 기능
비활성화가 1차 rollback이며, artifact rollback이 필요하면 서비스를
중지하고 backup artifact를 복원한 뒤 다시 시작한다.
