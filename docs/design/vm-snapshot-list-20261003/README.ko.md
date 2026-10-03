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

# 컴퓨트 VM 스냅샷 목록 UI 목업

31번 클러스터에서 확인한 전체 VM 스냅샷 목록 문제를 개선하는 설계 자료다.
기준 소스는 upstream Europa `b31026f919856a1b61c1f86dca450e16ac0673e1`이며 [개선 계획](implementation-plan.ko.md)에 관찰 근거, 우선순위, API 계획, 수용 기준을 기록했다.
추적 Epic: [ablecloud-team/ablestack-cloud#1216](https://github.com/ablecloud-team/ablestack-cloud/issues/1216). [1차 스토리지 할당량 분석](storage-accounting.ko.md)도 개선 범위에 포함한다.

## 보기

[mockup.html](mockup.html)을 브라우저에서 연다. 외부 라이브러리나 네트워크 API 없이 예시 데이터만으로 동작한다.
한 번에 받으려면 [mockup.zip](mockup.zip)을 다운로드하고 압축을 풀어 mockup.html을 연다.
상단의 UI 개선안 표시와 상태 선택기는 목업 시연 도구이며 제품 화면에 추가할 요소가 아니다.

- 라이트/다크 테마를 전환한다.
- VM/스냅샷 이름 및 설명으로 검색하고 VM·상태·유형·현재 기준점 필터를 조합한다.
- 열 머리글로 전체 예시 데이터를 정렬한 후 페이지를 이동한다.
- 행 작업 메뉴 및 우클릭은 그 행을 대상으로 한다. 다른 행의 체크 여부와 무관하다.
- 여러 항목을 선택하면 선택 삭제 도구 모음이 나타난다. 실행 불가 항목이 있으면 사유를 표시한다.
- 복원/삭제 확인창의 대상 정보·영향·조건과 확인 체크를 살펴본다. 최종 동작은 시연 안내만 표시한다.
- VM별 관계 보기에서 W2025-GFS2-Sparse의 3개 예시 복원 지점과 현재 기준점을 확인한다.
- 최초 로딩, 빈 목록, 검색 결과 없음, 조회 실패, 부분 관계 조회 상태를 전환한다.
- 소유자/Zone 선택 열을 켜고 상세 창에서 UUID/내부 이름/설명을 확인한다.

## 예시 데이터와 실제 구현의 차이

목업의 이름·상태·VM 상태·설명은 설계 시나리오를 위해 가공한 예시다. 실제 31번 클러스터의 현재 상태/작업 결과를 뜻하지 않는다.
목업은 전체 예시 배열을 대상으로 검색·정렬·페이징한다. 제품 구현은 서버에서 ACL을 적용한 전체 검색·정렬·페이징을 수행해야 한다.
관계 보기 역시 완전한 예시 데이터를 사용한다. 제품에서는 선택 VM 전체 관계 데이터의 완전성을 확인해야 한다.
저장 공간 사용량·회수 가능 공간·복원 소요 시간은 현재 API 근거가 없어 표시하지 않는다.
목업은 API, SSH, libvirt, qemu, Cloud DB를 호출하지 않는다. 실제 삭제/복원이나 제품 배포가 수행되지 않는다.

## 화면

이미지는 images/에 저장한다. 목록 양 테마, 행 작업 메뉴, 복원 확인창, 선택 삭제 확인창, VM 관계 보기를 포함한다.
[목업 검증 기록](verification.ko.md)에 실제로 확인한 상호작용 및 범위 한계를 남긴다.
