# Guest Network Observability Phase 5 완료 보고

작성일: 2026-07-24

작업 브랜치: `codex/guest-network-observability`
작업 범위: Linux/Windows DNS 수집, 정규화, snapshot/API/UI 표시

## 1. 완료 결과

Phase 5의 DNS 수집과 UI 구현을 완료했다.

- Linux `resolvectl`, `nmcli`, `/etc/resolv.conf` parser
- Windows DNS Client 및 global setting parser
- 고정 guest-exec allowlist, timeout polling, output 크기 제한, base64/UTF-8 검증
- IPv4/IPv6, global/per-interface, search/routing domain 정규화
- local stub와 확인된 upstream DNS 구분
- DNS 독립 cadence와 failure backoff
- DB snapshot section 병합과 stale 보존
- 상세 API DNS 응답
- Ant Design DNS summary 및 scope table

Phase 5에는 DB schema 변경이 없다. Phase 2에서 추가한
`vm_guest_network_state.payload`의 DNS section에 함께 저장된다.

## 2. 수집 우선순위

QGA에는 DNS 전용 표준 조회 명령이 없기 때문에 `guest-info`에서
`guest-exec`, `guest-get-osinfo` capability를 확인한 뒤 고정 adapter를 사용한다.

Linux:

```text
resolvectl status --no-pager
  └─ usable DNS 없음/실패
       nmcli -t -f GENERAL.DEVICE,IP4.DNS,IP6.DNS,IP4.DOMAIN,IP6.DOMAIN device show
         └─ usable DNS 없음/실패
              cat /etc/resolv.conf
```

Windows:

```text
Get-DnsClientServerAddress
Get-DnsClient
Get-DnsClientGlobalSetting
  └─ 필요한 속성만 Select-Object
  └─ 단일 JSON으로 반환
```

Linux 기본 실행 경로가 없을 때만 `/bin` 대체 경로를 시도한다. 정상 실행 후
빈 결과가 반환되면 같은 도구를 다른 경로로 다시 실행하지 않고 다음 source로 이동한다.

## 3. 안전한 guest-exec

허용된 실행 파일과 인수는 코드에 고정되어 있다.

- `/usr/bin/resolvectl`, `/bin/resolvectl`
- `/usr/bin/nmcli`, `/bin/nmcli`
- `/usr/bin/cat`, `/bin/cat`와 고정 `/etc/resolv.conf`
- Windows PowerShell 절대 경로와 고정 DNS projection

다음 동작은 허용하지 않는다.

- 사용자나 API 입력으로 실행 파일, 인수, 경로 또는 script 생성
- `/bin/sh -c`, `cmd.exe /c`
- 임의 파일 읽기
- 범용 guest command API

fallback은 `vm.guest.network.details.exec.fallback.enabled=false`가 기본값이다.
기능 전체가 비활성화되면 scheduler, Agent command, QGA, DB write가 발생하지 않는다.

## 4. DNS 모델

DNS snapshot은 다음 정보를 보존한다.

- 전체 source
- unique DNS server 및 search domain summary
- global/per-interface configuration
- 각 DNS server의 IPv4/IPv6 family
- local stub 여부
- search domain과 routing-only domain 구분
- upstream DNS 확인 여부

`127.0.0.0/8`, `::1`과 같은 loopback DNS는 local stub로 기록한다.
`/etc/resolv.conf`에서 local stub만 확인된 경우
`upstreamServersKnown=false`로 저장하며 실제 upstream 주소를 추정하지 않는다.

## 5. 부하와 cadence

interface, route, DNS의 due schedule과 실패 backoff를 각각 독립적으로 관리한다.

| 상황 | 실행 내용 |
|---|---|
| DNS만 due | DNS capability/adapter만 실행, NIC DAO 조회 없음 |
| route와 DNS due | 한 전용 Agent command에서 section별 순차 실행, OS 정보는 한 번만 조회 |
| DNS 성공 | 기본 600초 interval과 deterministic jitter 적용 |
| DNS 실패/미지원 | DNS 전용 exponential backoff |
| fallback disabled | DNS QGA/guest-exec 호출 0, `UNSUPPORTED` |
| 기능 전체 disabled | scheduler/worker/QGA/DB 작업 0 |

DNS server와 domain은 각각 최대 64개, decoded stdout/stderr는 각각 기본 1 MiB,
전체 canonical payload는 기존 정책대로 최대 2 MiB로 제한한다.

## 6. snapshot 병합과 상태

- `NOT_DUE`: 기존 DNS 데이터와 section 상태 유지
- `OK`, `EMPTY`: 새 결과로 교체
- `PARTIAL`: 제한된 결과와 truncation metadata 저장
- `UNAVAILABLE`, `UNSUPPORTED` + 기존 성공 데이터: 마지막 성공 DNS 유지 및 `STALE`
- 성공 데이터 없음: 실제 실패 상태 기록

DNS 실패는 interface와 route 결과를 제거하지 않으며, 전체 상태는 성공/실패 section
조합에 따라 `OK`, `PARTIAL`, `STALE`, `UNAVAILABLE`, `UNSUPPORTED`로 계산한다.

## 7. API와 UI

기존 `getVirtualMachineGuestNetworkState` 상세 응답에 `dns` 객체를 추가했다.

- API는 DB snapshot만 읽으며 Agent/host command를 생성하지 않는다.
- VM 목록 summary에는 DNS를 추가하지 않는다.
- 기존 VM RBAC와 `ListEntry` ACL을 그대로 적용한다.
- UI에는 source, DNS server 수, upstream 확인 상태를 요약 표시한다.
- global/per-interface table에 IPv4/IPv6 server와 search/routing domain을 표시한다.
- local stub는 별도 색상과 label로 표시한다.
- upstream이 확인되지 않으면 추정하지 않았다는 안내를 표시한다.
- DNS section의 `STALE`, `PARTIAL`, `UNSUPPORTED`, `UNAVAILABLE` 상태를 표시한다.

## 8. 검증

검증 범위:

- systemd-resolved per-link DNS 및 routing domain fixture
- NetworkManager IPv4/IPv6와 escaped IPv6 fixture
- `/etc/resolv.conf` local stub only fixture
- Windows interface DNS, suffix, global suffix fixture
- local stub/upstream 구분
- DNS server 64개 상한과 truncation
- Linux source 우선순위와 실행 경로 fallback
- allowlist 외 shell 거부
- DNS-only cycle의 NIC DAO/interface/route 요청 부재
- section별 독립 cadence/backoff
- DNS 실패 시 마지막 성공값 stale 보존
- API DNS serialization/mapping
- UI lint, locale JSON, production build

검증 결과:

- Core/KVM 대상 테스트: 37개 통과
- API/Backend 대상 테스트: 29개 통과
- 합계: 66개 통과, failure/error 0
- UI ESLint: 통과
- 영문/한글 locale JSON: 통과
- UI production build: 통과
- `git diff --check`: 통과

기존 KVM plugin POM의 중복 `org.json:json` 경고와 Browserslist DB 갱신 안내는
이번 변경과 무관한 기존 경고다.

## 9. 배포 상태와 다음 단계

- local WSL 작업 트리에만 구현했다.
- commit, push, PR은 수행하지 않았다.
- 공용 22.x management/UI/host에 배포하지 않았다.
- service restart와 DB migration은 수행하지 않았다.

다음 단계는 Phase 6의 통합 검증 및 좁은 범위 배포 준비다.
