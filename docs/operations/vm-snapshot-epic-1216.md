<!--
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements. See the NOTICE file
distributed with this work for additional information
regarding copyright ownership. The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License. You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied. See the License for the
specific language governing permissions and limitations
under the License.
-->

# VM 스냅샷 Epic #1216: 구현 및 검증 기록

최종 통합 PR은 #1217~#1223의 검증을 완료한 뒤 생성한다. 이 문서는 배포 전 기록이며, 미실행 항목을 완료로 취급하지 않는다.

## 구현 계약

| 하위 이슈 | 구현 |
| --- | --- |
| #1217 대상 안전성 | 우클릭한 행과 확인창의 UUID를 고정한다. 체크한 다른 행이 행 메뉴의 대상이 되지 않는다. 선택 삭제는 별도의 상단 버튼으로 실행한다. |
| #1218 검색·페이지 | VM 표시 이름·내부 이름·호스트 이름과 스냅샷 이름·UUID·설명을 서버에서 검색한다. 유형·현재 기준점·상태 필터 및 허용된 서버 정렬에 ID 보조 정렬을 적용한다. 목록과 count에 같은 ACL 조건을 적용한다. |
| #1220 작업 상태 | 제출 직전에 스냅샷·VM·진행 중 작업·백업 및 접근 권한을 재조회한다. 같은 VM의 삭제는 직렬, 다른 VM은 최대 두 개를 병렬 처리한다. 실패·결과 불명 시 해당 VM의 후속 요청을 중단한다. |
| #1221 공통 UI | 기존 AutogenView/ListView, Vue 3, Ant Design Vue, ResourceContextMenu를 사용한다. 목록 배치를 유지한다. 공통 대화상자 스타일에서 헤더·푸터를 고정하고 본문만 스크롤한다. |
| #1222 관계 | VM 범위의 모든 페이지를 독립적으로 읽는다. 숨겨진 노드는 VM ID를 지정한 요청에서만 포함하고 기존 ACL을 적용한다. UUID로 연결하며 누락 부모·순환·부분 결과를 표시한다. 최대 2,000개이다. |
| #1219 스토리지 | 관리되지 않는 SharedMountPoint/KVM/QCOW2의 내부 COW 스냅샷에만 논리 할당량 정책을 적용한다. 다른 제공자와 managed storage의 카운터·드라이버 경로를 유지한다. |

`current`는 복원 기준점이다. 최신 생성 시각, 백업 완료 여부, 물리 사용량을 뜻하지 않는다. 숨겨진 관계 노드는 Ready 작업 대상이 아니며 작업을 활성화하지 않는다.

## 스토리지 용량 계약

논리 할당량에는 각 볼륨의 가상 크기를 한 번 반영한다. 내부 qcow2 스냅샷마다 같은 볼륨의 가상 크기를 더하지 않는다. 기존 `vm_snapshot_chain_size` 값이 남아 있어도 적용 대상 풀의 집계에서는 제외한다. 이것은 과거 모든 스냅샷의 실제 사용량을 0으로 정의하는 변경이 아니다.

물리 사용량은 풀의 실제 통계로 유지한다. RAM 저장과 이후 COW 쓰기는 물리 공간을 사용한다. 메모리 스냅샷 생성 시 영향을 받는 풀의 잠금을 ID 순서로 잡고 Agent의 최신 통계를 읽는다. 루트 풀은 RAM + 10% + 1 GiB, 추가 데이터 풀은 1 GiB의 여유 공간을 검사한다. 실제 임계치와 잔여 공간을 적용하며 논리 over-provisioning은 사용하지 않는다. 통계가 없거나 공간이 부족하면 요청을 거부한다. 이 검사 뒤 장시간의 게스트 쓰기로 생기는 물리 소모는 일반적인 풀 모니터링·경보의 대상이다.

정책 버전과 최초 카운터는 표시되지 않는 volume details에 보존한다. 중간에 중단된 전환의 원본 출처를 다시 쓰지 않는다. 생성·삭제 재시도, resize, 새로운 작업에서 원래 볼륨 크기를 반복 누적하지 않는다.

### 기존 카운터 감사 도구

`tools/vmsnapshot-accounting-transition.py`의 기본 `dry-run`은 DB를 수정하지 않는다. MySQL 인증은 관리 서버의 별도 파일에 두고 `chmod 600`을 적용한다. 인증 파일과 세션/API 키는 Git, 이슈, 검증 기록에 포함하지 않는다.

```sh
python3 tools/vmsnapshot-accounting-transition.py dry-run \
  --mysql-defaults-file /root/epic1216/mysql.cnf \
  --audit /root/epic1216/legacy-audit.json
```

저장된 감사에는 볼륨 UUID, VM·풀 ID, 가상 크기, 원본 카운터, 스냅샷 메타데이터 지문과 고유 감사 ID를 포함한다. 기존 감사 파일을 덮어쓰지 않는다. 미버전 출처가 이미 있으면 자동 전환하지 않는다.

`apply`와 `rollback`은 Mold가 inactive이고 대상 VM에 미완료 스냅샷 작업이 없을 때만 실행한다. 적용 전 UUID·풀·크기·지문을 대조하고 행 잠금·원본 카운터·자기 감사 ID 조건으로 변경한다. 재실행은 같은 상태에서만 no-op이다. 적용 이후 다른 작업·resize·migration이 발생하면 rollback을 거부한다. 현재 배포안은 기존 카운터를 보존하며 이 도구의 apply/rollback을 자동으로 실행하지 않는다.

## 빌드와 배포 절차

Windows의 별도 `codex/vm-snapshot-epic-1216` 작업 트리와 WSL ext4의 전용 작업 트리를 사용한다. 기존 FTCTL 작업 트리를 변경하지 않는다. 전체 Cloud 빌드를 실행하지 않는다.

```sh
source /home/ablecloud/work/dhslove/europa-build-env.sh
cd /home/ablecloud/work/dhslove/ablestack-cloud-epic-1216
mvn -B -pl api,engine/schema,server,engine/storage/snapshot install
cd ui
npm ci --ignore-scripts --no-audit --no-fund
npm run lint
npm run test:unit -- --runInBand \
  'VmSnapshot|vmSnapshot|ResourceContextMenu|ListViewAccounts|ListViewDiskOffering|ListViewSnapshotContext|AutogenView|AutogenSnapshotActions'
npm run build
```

31번 관리 서버는 `cloudstack-4.23.0.0-Mold.Europa-202609222315.jar`에 클래스를 합친 형태이다. 변경 모듈 빌드 결과에서 변경 Java 클래스와 내부 클래스만 추출하고 SHA-256 목록을 저장한다. 실제 JAR을 먼저 백업한 뒤 Mold를 중지하고 이 클래스들을 반영한 후 재시작한다. 원본 manifest와 관련 없는 JAR 항목을 유지한다. 관리 서버의 실제 클래스 바이트를 빌드 산출물의 SHA-256과 대조한다.

UI는 `/usr/share/cloudstack-management/webapp`의 정적 파일만 업데이트한다. 기존 `WEB-INF`, `META-INF`, `config.json` 및 서버 디렉터리를 유지한다. 웹앱 루트 교체나 `rsync --delete`를 사용하지 않는다. 배포 전후 WEB-INF 존재, `/client/` HTTP 200, Mold active, 번들 마커·해시, 런타임 로그·API 응답을 확인한다.

복구 시 백업한 JAR을 원래 경로에 되돌리고 서비스를 재시작한다. 정적 파일은 개별 백업에서 복구한다. DB를 보존하는 배포이므로 배포 복구에 DB 카운터 복원이 필요하지 않다.

## 31번 검증 상태

- 관리 서버 `10.10.31.10`, SSH 22, Mold active, `/client/` HTTP 200, WEB-INF 존재를 배포 전 확인했다.
- 활성 스냅샷은 16개이며 모두 Ready/DiskAndMemory이다. 관리되지 않는 SharedMountPoint 풀과 CLVM/CLVM_NG 풀이 있다.
- 기존 양수 카운터 14개, 합계 1,717,986,918,400 bytes를 read-only 감사에서 확인했다. 실제 물리 사용량이나 전체 할당량 차이를 이 수치만으로 설명하지 않는다.
- 생성한 전용 시험 VM: `epic1216-a` (`47fbae47-18cb-43d7-b00b-f14080bf9a2f`), `epic1216-b` (`5e6a5131-d75e-48af-be7e-70caa6460c03`), `epic1216-c` (`3b6b4c34-1d7d-4765-87ef-769420088670`). 세 VM 모두 Stopped로 생성했다. 기존 사용자 VM에 복원·삭제를 실행하지 않았다.
- API·스키마·서버·스냅샷 네 모듈의 선택 테스트 빌드가 통과했다. 추가 물리 공간 부족·통계 불명·다중 풀 잠금 테스트를 포함한 스냅샷 테스트 9개가 통과했다. 더 넓은 모듈 테스트와 최종 UI 검증을 진행 중이다.
- 배포 실행은 자동 승인 검토에 두 번 차단됐다. 첫 명령은 카운터 전환을 포함했고 두 번째 명령은 DB를 보존했으나 동일한 `blocked by policy`가 반환됐다. 실제 배포가 성공한 것으로 취급하지 않는다.

## 배포 후 남은 통합 검증

1. 새 API 필터·VM 키워드·정렬·count·25개 이상/두 페이지·URL 복원·마지막 페이지를 실제 응답과 대조한다. Admin/DomainAdmin/User·프로젝트에서 서로의 데이터가 섞이지 않는지 확인한다.
2. 전용 VM에서 쓰기 전후 스냅샷 생성·분기 복원·첫/중간/마지막 삭제를 실행한다. Job, DB 상태/부모/current, provider 목록과 실제 파일 상태가 일치하는지 확인한다.
3. A를 체크하고 B를 우클릭해 확인창 및 실제 API UUID가 B인지 확인한다. 정렬·새로고침·페이지 이동 후에도 대상이 바뀌지 않는지 확인한다. 선택 삭제 직렬화와 실패·결과 불명·미실행 결과를 확인한다.
4. 1차 스토리지 목록·상세·API·capacity·경보/할당 경로를 대조한다. 내부 스냅샷이 논리 할당량을 반복 증가시키지 않고 실제 RAM/COW 쓰기는 물리 통계에 남는지 확인한다. 다른 제공자·볼륨·백업·스토리지 선택 경로의 회귀를 점검한다.
5. 실제 배포 UI에서 기존 메뉴 폭·그룹·아이콘·삭제/비활성 이유를 확인한다. Shift+F10/Menu, Escape, 외부 클릭·스크롤·resize, 포커스 복귀를 검증한다. 밝은/어두운 테마, 1920×1080·1366×768·작은 화면에서 대화상자 제목/푸터 고정과 본문 스크롤을 확인한다.
6. 실패가 해결되고 모든 하위 이슈의 검증 증거가 갖춰진 뒤 Epic 수준의 한국어 PR 하나를 생성한다. merge 전 이슈를 수동으로 완료 처리하지 않는다.
