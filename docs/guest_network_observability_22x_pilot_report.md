# Guest Network Observability 22.x 실제 DB Clone 및 최소 배포 파일럿

## 1. 결과 요약

- 검증 일자: 2026-07-25
- 작업 브랜치: `codex/guest-network-observability`
- 파일럿 시작 소스: `2ff59a5ae76dc28ac1034f9d3eacec6f03559c78`
- 대상: 22.x 관리 서버 1대, KVM 호스트 3대
- 최초 배포 범위: 관리 서버 Backend/API/DB DAO/UI, KVM 호스트 1대의 Agent 최소 class patch
- 2026-07-26 추가 배포: 관리 서버 collector class 1개, KVM 호스트 3의 OS family 관련 class 19개
- OS family 추가 배포 미적용 호스트: KVM 호스트 1, 2
- 현재 기능 설정: `vm.guest.network.details.enabled=true`
- 현재 exec fallback 설정: `vm.guest.network.details.exec.fallback.enabled=true`

실제 22.x DB를 네트워크 비공개 clone으로 복제한 upgrade migration과
최소 배포 파일럿을 완료했다. VM의 모든 관측 IPv4/IPv6와 Cloud NIC 연결,
section별 상태가 API에서 확인됐다. 이후 Debian OS family 판별과
Backend 재시도 스케줄 결함을 수정해 실제 VM에서 route 10개와 DNS 서버
2개까지 `OK`로 저장되는 것을 확인했다. 기존 VM, 볼륨, NIC 작업은 기능
활성 상태에서 각 20회 모두 성공했고 p95 증가는 허용 기준 5% 이하였다.

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
route가 `UNSUPPORTED`였다. 당시 한 Debian VM의 결과를 command
allowlist 미지원으로 해석했으나 2026-07-26 사후 preflight에서 원인이
OS family 판별 결함임을 확인했다. 나머지 두 VM은 QGA
guest-exec/OS information capability가 없었다. API는 빈 값을 성공으로
오인하지 않고 각 사유를 `UNSUPPORTED`로 반환했다.

### 6.1 OS family 판별 사후 preflight

실제 `10.10.22.3`의 `i-2-608-VM`에서 읽기 전용 QGA preflight를
수행했다.

- QGA version: `7.2.22`
- `guest-exec`, `guest-exec-status`, `guest-get-osinfo`: enabled
- OS 정보: `id=debian`, `kernel-name` 미제공
- 표준 `guest-network-get-route`: unsupported
- 고정 IPv4 route 명령: exit 0, JSON route 10건
- 고정 IPv6 route 명령: exit 0, JSON route 0건
- `resolvectl`, `nmcli`: 실행 파일 없음
- 고정 `/usr/bin/cat /etc/resolv.conf`: exit 0, 74 byte

세 호스트의 실행 중 domain 37개를 VM당 `guest-get-osinfo` 한 번으로
탐색한 결과 `debian`, `rocky`, `centos`, `mswindows` ID를 확인했고
모든 응답에서 `kernel-name`은 제공되지 않았다. Ubuntu 실행 표본은
확인되지 않았다.

현재 코드는 OS ID에 `linux` 문자열이 포함된 경우만 Linux adapter를
선택한다. 따라서 `debian`, `rocky`, `centos`는 고정 allowlist 명령이
실제로 동작해도 실행 전에 거부된다. 개선 시 OS 정보를 독립 필드로
보존하고 명시적 fail-closed OS family resolver를 사용해야 한다.

코드 수준 설계, Debian/Ubuntu 회귀 테스트와 구현 후 22.x gate는
`docs/guest_network_observability_os_family_design.md`에 기록했다.

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

### 9.5 section 병합에 의한 실패 backoff 연장

OS family Agent patch 후에도 대상 VM의 route/DNS가 재수집되지 않았다.
관리 서버를 추적한 결과 persistence merge가 `NOT_DUE`를 이전
`UNSUPPORTED` 상태로 변경한 뒤, collector가 이 변경된 상태를 실패로
다시 기록해 다음 시각을 계속 뒤로 미루고 있었다.

`VmGuestNetworkCollector`가 persistence 전 section 상태 복사본을 만들고
그 값으로 실행 스케줄을 기록하도록 수정했다. 회귀 테스트는 persistence가
입력 상태를 변경해도 route/DNS 다음 실행 시각이 연장되지 않음을 검증한다.

### 9.6 UI webapp 배포 경계

관리 서버 collector class patch를 검증하기 위해 서비스를 재시작하면서
8080 UI는 열리지만 API가 404를 반환하는 문제를 발견했다. 현재 UI
webapp에서 `WEB-INF`가 누락되어 있었고, 이전 UI 정적 artifact 배포가
webapp 전체 디렉터리를 교체한 것이 원인이었다. 기존 JVM이 이미 backend를
적재한 상태에서는 드러나지 않았지만 재시작 시 backend servlet이 로드되지
않았다.

정적 UI 파일은 유지하고 다음 기존 backup에서 `WEB-INF`만 복원했다.

```text
/var/backups/guest-network-ui-webapp-20260726-002629-f5bae2/webapp/WEB-INF
```

복구 후 API 비인증 요청의 정상 `401` JSON, 8080/8250 listener, KVM 호스트
1/2/3 연결을 확인했다. 이후 UI 배포는 webapp을 삭제하거나 통째로
교체하지 않고 정적 파일만 동기화하며 `WEB-INF`를 보존해야 한다.

## 10. 2026-07-26 OS family 최소 배포 결과

### 10.1 Host 3 Agent

| 항목 | 값 |
|---|---|
| 대상 | `10.10.22.3` |
| JAR | `/usr/share/cloudstack-agent/lib/cloud-plugin-hypervisor-kvm-4.22.0.0-SNAPSHOT.jar` |
| 원본 SHA-256 | `87db9c48a4cafbcc084a2c18c45fca5adbe2410b4c500092864d7336be677c58` |
| patch class | 19개 |
| 배포 SHA-256 | `fb8df3ea620f6cbd9f3e6a7e46d6562f7aa1c8ccf8456169e0d0f49c64088f56` |
| backup | `/var/backups/guest-os-family-host3-20260726-005647` |

`mold-agent.service` 재시작 후 ReadyAnswer, 대상 VM Running, stats 수신을
확인했다. `NoClassDefFoundError`, `ClassNotFoundException`,
`VerifyError`, `UnsupportedClassVersionError`는 발생하지 않았다.

### 10.2 Management collector

| 항목 | 값 |
|---|---|
| 대상 | `10.10.22.10` |
| JAR | `/usr/share/cloudstack-management/lib/cloudstack-4.22.0.0-SNAPSHOT.jar` |
| 원본 SHA-256 | `b9c1697f43e87ba1b04c6be9bd8ff034cc039620ccdbae543fa680666f77e676` |
| patch class | `VmGuestNetworkCollector.class` 1개 |
| 배포 SHA-256 | `97258e0bf24a8221adb74d8053494de9ca5ec418b176b8656d55d88205fa7457` |
| backup | `/var/backups/guest-network-schedule-20260726-012200` |

JAR 전체를 다시 만들지 않고 `zip -u`로 단일 class entry만 교체했다.
관리 서버 재시작 후 API, UI listener, Agent 연결을 모두 확인했다.

### 10.3 실제 대상 VM 결과

대상은 Host 3의 `i-2-608-VM`
(`665980f9-423f-4914-9c90-bd4c1ae2de22`)이다.

| 상태 | 최종 값 |
|---|---|
| 전체 | `OK` |
| interface | `OK`, 2개 |
| route | `OK`, 10개 |
| DNS | `OK`, 서버 2개 |
| DNS source | `resolv.conf` |
| upstream 확인 | `true` |
| DB 갱신 시각 | `2026-07-25 16:24:12` UTC |

Debian에서 route/DNS fallback이 실제 동작했으며, 기존
`Unsupported guest OS for ... fallback: debian` 상태가 해소됐다.

### 10.4 다크모드 UI 개선 및 최소 배포

게스트 네트워크 화면에만 범위를 한정해 다크모드 색상을 보완했다.

| 항목 | 값 |
|---|---|
| 변경 파일 | `GuestNetworkSummary.vue`, `GuestNetworkTab.vue`, `dark-mode.less` |
| lint | `npm run lint -- --no-fix` 성공 |
| production build | Node.js 16.20.2, `NODE_OPTIONS=--openssl-legacy-provider`, 성공 |
| `index.html` SHA-256 | `77fcf27fd1ba4a4d7b95a718de23f20ed9578ce6b34e9552fe6227576576c762` |
| CSS SHA-256 | `8fcb8868df68b5b0e606773b9fb479c7fcbabfc938d98224f62f7ae7d12ab0e4` |
| JS SHA-256 | `c23f188e0eb9b387eb1b71544aff52ed9877ac52d11fbc1af8dd7ded6b430fb5` |
| 최초 배포 backup | `/var/backups/guest-network-dark-ui-20260726-170417` |
| 최종 보완 배포 backup | `/var/backups/guest-network-dark-ui-20260726-171315` |

UI 정적 파일만 content-only 방식으로 동기화했고 `WEB-INF`는 명시적으로
제외했다. 관리 서버나 Agent는 재시작하지 않았다. 실제 인증 세션에서
목록 20개 요약과 대상 VM 상세 탭을 확인했으며, 상세 탭은 interface 2개,
route 10개, DNS 2개를 표시했다.

다크모드에서는 설명 표 label/content, interface card, IP/상태 tag,
info alert, divider와 보조 label의 전경·배경·경계 색상이 각각 구분된다.
실제 계산 스타일 기준 설명 label은 `rgb(51, 52, 62)` 배경과
`rgba(255, 255, 255, 0.85)` 전경, content는 `rgb(34, 40, 47)` 배경과
`rgba(255, 255, 255, 0.65)` 전경으로 표시됐다. 목록의 `PARTIAL`과
`NOT_COLLECTED` tag도 서로 다른 amber/neutral 색상으로 표시됐다.

설정 화면에서 라이트모드로 전환해 기존 밝은 배경과 검은색 계열 전경이
유지되는 것을 확인한 뒤, 사용자 원래 설정인 다크모드로 복원했다.
최종 UI 응답은 HTTP 200, 비인증 API 응답은 정상 HTTP 401 JSON이며,
`WEB-INF/web.xml` 보존도 확인했다.

### 10.5 VM 목록 IP 압축 요약 UI

기존 `Cloud`/`게스트 IP 주소` 두 줄과 여러 주소 tag를 한 줄 압축 요약으로
교체했다. `C`는 Cloud 관리 IP, `G`는 게스트 관측 대표 IP이며, 나머지
주소는 `+N` popover에서 전체 IPv4/IPv6와 prefix를 확인하고 복사한다.
IPv6가 있으면 `v6` marker를 표시하고 수집 상태는 짧은 현지화 tag로
표시한다.

목록 summary API에는 route/interface 연결 정보가 없으므로 UI-only 대표
주소 선택은 non-loopback/non-link-local IPv4, 동일 조건 IPv6, 첫
non-loopback 주소 순서다. API, Backend/DB와 Agent 계약은 변경하지 않았다.

| 항목 | 값 |
|---|---|
| 변경 파일 | `GuestNetworkSummary.vue`, `dark-mode.less`, `en.json`, `ko_KR.json` |
| lint | `npm run lint -- --no-fix` 성공 |
| production build | Node.js 16.20.2, `NODE_OPTIONS=--openssl-legacy-provider`, 성공 |
| `index.html` SHA-256 | `2f545f7c4b7d1d3b3fd944e3f30d43d810eee87d44d97f4be11411402d252b9c` |
| CSS SHA-256 | `9458f3b68f3de085c58105efd7b49f5f4c493a2b8c774e3a3eed82feee717f22` |
| JS SHA-256 | `5a0c133bf37117172c07cc984470f932eab71ba30113aaf101cb1f0de7164f60` |
| 한국어 locale SHA-256 | `b7733d043ee171f359f2e50bba300e04e54c9e869c80b3534e96e540b87980dc` |
| 배포 backup | `/var/backups/guest-network-compact-ui-20260726-174733` |

`WEB-INF`를 제외한 UI 정적 파일만 checksum 기준으로 동기화했으며 관리
서버와 Agent를 재시작하지 않았다. 최종 상태는 `mold.service=active`,
UI HTTP 200, 비인증 API HTTP 401, `WEB-INF/web.xml` 보존이다.

실제 22.x 인증 화면에서 20개 VM summary, 각 summary의 `C`/`G` marker
40개, 상태 tag 20개를 확인했다. 목록 행 높이는 49px, IP summary
높이는 24px로 주소 수에 관계없이 한 줄을 유지했다. Host 1의
`foms-control-19ce0ede800`에서 `+2`를 선택하면 대표 주소를 포함한
IPv4 주소 3개와 전체 복사 동작이 popover에 표시됐다. 일반/다크 테마를
각각 확인했고 브라우저 console error는 없었다. 검증 후 사용자 설정은
다크 테마로 복원했다.

## 11. 검증 및 현재 상태

- KVM parser/resolver/route/DNS/wrapper 테스트 38개: `BUILD SUCCESS`
- Server collector 테스트 12개: `BUILD SUCCESS`
- 실제 DB clone fresh/upgrade DDL 일치
- 관리 서버/UI/API 정상
- 배포 UI bundle에 게스트 네트워크 화면 코드 포함
- 인증된 실제 화면에서 다크/라이트 목록 및 상세 탭 육안·계산 스타일 확인 완료
- VM 목록 IP 압축 요약, `+N` 전체 주소 popover와 일반/다크 테마 확인 완료
- Host 1/2 연결 정상, Host 3 신규 Agent patch 및 Ready 정상
- 기능: `true`
- exec fallback: `true`
- 대상 Debian VM interface/route/DNS: 모두 `OK`
- Ubuntu 실제 실행 표본: 미확보, fixture 및 단위 테스트만 완료
- 비활성화 후 in-flight 1개 완료 다음 전체 주기에서 추가 갱신: 0
- disposable NIC: 제거
- disposable volume: 분리 후 삭제
- 인증 helper/private key/DB clone: 검증 종료 시 제거

rollback backup ID:

```text
guest-network-20260725-211518-2ff59a5
```

관리 서버와 Host 1의 최초 원본 JAR/UI backup은 위 ID 아래에 보존했다.
추가 OS family/collector patch의 원본은 10.1과 10.2의 별도 backup에
보존했다. 기능 비활성화가 1차 rollback이며, artifact rollback이 필요하면
서비스를 중지하고 해당 backup artifact를 복원한 뒤 다시 시작한다.
