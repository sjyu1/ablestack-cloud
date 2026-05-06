<template>
  <div class="p-2">
    <div class="toolbar-container">

      <div class="toolbar-left">
        <a-space wrap>
          <a-button type="primary" @click="saveRackData" :loading="saving" :disabled="!isDirty">
            <SaveOutlined /> 저장
          </a-button>

          <a-divider type="vertical" class="toolbar-divider" />

          <a-input-search
            v-model:value="searchQuery"
            placeholder="장비명, IP, 메모 검색..."
            style="width: 200px;"
            allow-clear
          />

          <a-button type="dashed" @click="openRackModal('add')">
            <PlusOutlined /> 새 랙 추가
          </a-button>

          <a-divider type="vertical" class="toolbar-divider" />

          <a-button @click="exportToJson" title="JSON 파일로 백업">
            <FileTextOutlined /> 백업
          </a-button>
          <a-button @click="triggerImport" title="JSON 파일 불러오기">
            <UploadOutlined /> 복원
          </a-button>
          <a-button @click="exportToImage" title="PNG 이미지 캡처">
            <CameraOutlined /> 캡처
          </a-button>
        </a-space>

        <input type="file" ref="fileInput" accept=".json" style="display: none" @change="handleImport" />
      </div>

      <div class="toolbar-right" style="display: flex; align-items: center;">
        <span class="zoom-label" style="margin-right: 8px;">Zoom:</span>

        <a-input-number
          v-model:value="zoomPercent"
          :min="40"
          :max="150"
          :formatter="value => `${value}%`"
          :parser="value => value.replace('%', '')"
          size="small"
          style="width: 70px; text-align: center; margin-right: 12px; flex-shrink: 0;"
        />

        <div style="width: 120px; flex-shrink: 0; padding-top: 4px;">
          <a-slider
            v-model:value="zoomPercent"
            :min="40"
            :max="150"
            :tip-formatter="null"
          />
        </div>
      </div>
    </div>
    <a-spin :spinning="loading || saving">
      <div class="rack-canvas" style="overflow: auto; min-height: 600px; padding: 20px; background: #eef0f4;">
        <div class="rack-zoom-wrapper" :style="{ zoom: zoomLevel }">

          <a-empty v-if="!parsedRacks.length" :description="'등록된 랙이 없습니다. 상단의 [새 랙 추가] 버튼을 눌러주세요.'" style="margin-top: 50px;" />

          <div class="rack-container" v-else>
            <div class="rack-wrapper" v-for="(rack, rIndex) in parsedRacks" :key="rIndex">

              <div class="rack-header">
                <div class="rack-header-inner">
                  <a-tooltip placement="top">
                    <template #title>
                      {{ rack.name }} ({{ rack.totalHeight }}U)
                    </template>
                    <span class="rack-name-text">
                      {{ rack.name }} ({{ rack.totalHeight }}U)
                    </span>
                  </a-tooltip>

                  <div class="rack-header-actions">
                    <a-button size="small" type="text" @click="moveRack(rIndex, -1)" :disabled="rIndex === 0" title="왼쪽 이동">
                      <LeftOutlined />
                    </a-button>
                    <a-button size="small" type="text" @click="moveRack(rIndex, 1)" :disabled="rIndex === parsedRacks.length - 1" title="오른쪽 이동">
                      <RightOutlined />
                    </a-button>
                    <a-button size="small" type="text" @click="cloneRack(rIndex)" title="랙 복제">
                      <CopyOutlined />
                    </a-button>
                    <a-button size="small" type="text" @click="openRackModal('edit', rIndex)" title="수정">
                      <SettingOutlined />
                    </a-button>
                    <a-popconfirm title="삭제하시겠습니까?" @confirm="deleteRack(rIndex)">
                      <a-button size="small" type="text" danger>
                        <DeleteOutlined />
                      </a-button>
                    </a-popconfirm>
                  </div>
                </div>
              </div>

              <div class="rack-body" style="display: flex; margin: 0 auto; width: 372px;">

                <div class="rack-ruler">
                  <div
                    v-for="u in rack.totalHeight"
                    :key="u"
                    class="ruler-number"
                  >
                    {{ rack.totalHeight - u + 1 }}
                  </div>
                </div>

                <div class="rack-frame" style="flex: 1; margin: 0;">
                  <div v-for="(item, iIndex) in rack.items" :key="iIndex" class="rack-item" :style="{ height: (item.height * 32) + 'px' }">
                  </div>
                </div>

                <div class="rack-frame" @dragover.prevent @drop.stop="onDropRackFrame(rIndex, $event)">
                  <div
                    v-for="(item, iIndex) in rack.items"
                    :key="iIndex"
                    class="rack-item"
                    @dragover.prevent
                    :style="{ height: (item.height * 32) + 'px' }"
                  >
                    <div v-if="item.type === 'gap'" class="gap-content" @click="openDeviceModal(rIndex, iIndex)">
                      <span>+ {{ item.height }}U 여백 (클릭하여 장비 추가)</span>
                    </div>

                    <div
                      v-else
                      class="device-content"
                      draggable="true"
                      @dragstart="onDragStart(rIndex, iIndex)"
                      :style="{ opacity: isMatched(item) ? 1 : 0.2, filter: isMatched(item) ? 'none' : 'grayscale(100%)' }"
                    >

                      <div class="device-top-line" :style="{ backgroundColor: item.type === 'blank' ? 'transparent' : getIconColor(item.type) }"></div>

                      <div class="device-pattern" :class="'pattern-' + item.type"></div>

                      <svg v-if="item.type === 'ups'" class="ups-watermark" viewBox="0 0 24 24" preserveAspectRatio="xMidYMid meet">
                        <path d="M13 2.05v9.45h4.5l-8.5 10.45v-9.45H4.5L13 2.05z" fill="rgba(255,255,255,0.03)" />
                      </svg>

                      <div
                        class="device-icon-overlay"
                        :style="{ color: getIconColor(item.type) }"
                        v-html="getDeviceIcon(item.type)"
                      ></div>

                      <div class="device-name-tag" v-if="item.type !== 'blank'">
                        <span v-if="item.type !== 'custom'" class="tag-text">{{ item.label }}</span>
                        <span v-else class="tag-text">{{ item.customType }}</span>
                        <span class="tag-badge">{{ item.height }}U</span>
                      </div>

                      <div class="device-actions">

                        <a-tooltip v-if="item.memo" placement="top" color="#108ee9" :mouseEnterDelay="0.1">
                          <template #title>
                            <div style="white-space: pre-wrap; font-size: 13px;">{{ item.memo }}</div>
                          </template>
                          <a-button size="small" type="text" @click.stop>
                            <InfoCircleOutlined style="color: #40a9ff !important;" />
                          </a-button>
                        </a-tooltip>

                        <a-tooltip title="장비 복제" :mouseEnterDelay="0.1">
                          <a-button size="small" type="text" @click.stop="cloneItem(rIndex, iIndex)">
                            <CopyOutlined />
                          </a-button>
                        </a-tooltip>

                        <a-tooltip title="다른 랙으로 이동" :mouseEnterDelay="0.1">
                          <a-button size="small" type="text" @click.stop="openMoveDeviceModal(rIndex, iIndex)">
                            <ExportOutlined />
                          </a-button>
                        </a-tooltip>

                        <a-tooltip title="설정" :mouseEnterDelay="0.1">
                          <a-button size="small" type="text" @click.stop="openDeviceModal(rIndex, iIndex)">
                            <SettingOutlined />
                          </a-button>
                        </a-tooltip>

                        <a-dropdown v-if="hasActionMenu(item)" :trigger="['click']">
                          <a-tooltip title="장치 작업" :mouseEnterDelay="0.1">
                            <a-button size="small" type="text" class="device-more-btn" @click.stop>
                              <MoreOutlined />
                            </a-button>
                          </a-tooltip>
                          <template #overlay>
                            <a-menu @click="({ key }) => handleHostActionMenu(key, item)">
                              <a-menu-item v-if="isHostLinked(item)" key="host-detail"><LinkOutlined /> 호스트 상세</a-menu-item>
                              <a-menu-item v-if="isHostLinked(item)" key="host-vms"><UnorderedListOutlined /> 호스트 VM 목록</a-menu-item>
                              <a-menu-item v-if="isHostLinked(item)" key="host-oobm"><LaptopOutlined /> OOBM 포털 접속</a-menu-item>
                              <a-menu-item v-if="isHostLinked(item)" key="host-cube"><AppstoreOutlined /> Cube 포털 접속</a-menu-item>
                              <a-menu-divider v-if="isHostLinked(item) && hasQuickLinks(item)" />
                              <a-menu-item v-for="(link, lIdx) in getQuickLinks(item)" :key="`quick-${lIdx}`">
                                <LinkOutlined /> {{ link.label || link.url }}
                              </a-menu-item>
                            </a-menu>
                          </template>
                        </a-dropdown>

                        <a-popconfirm title="삭제하시겠습니까?" @confirm="deleteItem(rIndex, iIndex)" placement="topRight">
                          <a-tooltip title="삭제" :mouseEnterDelay="0.1">
                            <a-button size="small" type="text" danger @click.stop>
                              <DeleteOutlined />
                            </a-button>
                          </a-tooltip>
                        </a-popconfirm>

                      </div>

                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </a-spin>

    <a-modal
      v-model:visible="rackModalVisible"
      :title="rackModalMode === 'add' ? '새 랙 추가' : '랙 설정 수정'"
      @ok="submitRackModal"
      @cancel="closeRackModal"
      destroyOnClose
    >
      <a-form layout="vertical">
        <a-form-item label="랙 이름 (Name)">
          <a-input v-model:value="rackForm.name" placeholder="예: Server Rack 1" />
        </a-form-item>
        <a-form-item label="총 높이 (U)">
          <a-input-number
            v-model:value="rackForm.totalHeight"
            :min="10"
            :max="50"
            :step="1"
            :precision="0"
            style="width: 100%"
          />
        </a-form-item>
      </a-form>
    </a-modal>
    <a-modal
      v-model:visible="moveModalVisible"
      title="장비 이동 (다른 랙으로 보내기)"
      @ok="submitMoveDevice"
      destroyOnClose
    >
      <div style="margin-bottom: 16px;">
        <InfoCircleOutlined style="color: #1890ff; margin-right: 8px;" />
        <span>이동할 대상 랙을 선택하면, <strong>빈 공간(Gap)을 찾아 자동으로 배치</strong>됩니다.</span>
      </div>

      <a-form layout="vertical">
        <a-form-item label="대상 랙 선택">
          <a-select v-model:value="moveTargetRackIndex" placeholder="랙을 선택하세요" style="width: 100%">
            <a-select-option
              v-for="(rack, idx) in parsedRacks"
              :key="idx"
              :value="idx"
              :disabled="idx === moveSourceInfo.rIndex"
            >
              {{ rack.name }} (잔여 여백 확인 필요)
            </a-select-option>
          </a-select>
        </a-form-item>
      </a-form>
    </a-modal>
    <a-modal
      v-model:visible="deviceModalVisible"
      :title="'장비 설정'"
      @ok="submitDeviceModal"
      @cancel="closeDeviceModal"
      destroyOnClose
    >
      <a-form layout="vertical">
        <a-form-item label="장비 종류">
          <a-select v-model:value="deviceForm.type" style="width: 100%" @change="handleTypeChange">
            <a-select-option value="server">서버 (Server)</a-select-option>
            <a-select-option value="switch">스위치 (Switch)</a-select-option>
            <a-select-option value="storage">스토리지 (Storage)</a-select-option>
            <a-select-option value="firewall">방화벽 (Firewall)</a-select-option>
            <a-select-option value="ups">UPS</a-select-option>
            <a-select-option value="patch">패치패널 (Patch Panel)</a-select-option>
            <a-select-option value="pdu">PDU</a-select-option>
            <a-select-option value="blank">블랭크 패널 (Blank)</a-select-option>
            <a-select-option value="custom">커스텀 (Custom)</a-select-option>
          </a-select>
        </a-form-item>

        <a-form-item label="장비명 (Label)" v-if="deviceForm.type !== 'blank'">
          <a-input
            v-model:value="deviceForm.label"
            placeholder="장비명을 입력하세요."
            allow-clear
          />
        </a-form-item>

        <a-form-item label="인프라 자산 선택" v-if="deviceForm.type !== 'blank'">
          <a-select
            v-model:value="deviceForm.sourceRef"
            :loading="inventoryLoading"
            :options="inventoryOptions"
            :show-search="true"
            option-filter-prop="label"
            placeholder="Host를 선택하세요."
            allow-clear
            style="width: 100%"
            @change="handleSourceChange"
          />
        </a-form-item>

        <a-form-item label="커스텀 타입명" v-if="deviceForm.type === 'custom'">
          <a-input v-model:value="deviceForm.customType" placeholder="예: Router" />
        </a-form-item>

        <a-form-item label="높이 (U)">
        <a-input-number
          v-model:value="deviceForm.height"
          :min="1"
          :max="maxAllowedHeight"
          :precision="0"
          style="width: 100%"
        />
        <div style="font-size: 12px; color: #888; margin-top: 4px;">
          <InfoCircleOutlined style="margin-right: 4px;" />
          현재 이 위치의 최대 허용 높이는 <strong>{{ maxAllowedHeight }}U</strong> 입니다.
        </div>
      </a-form-item>

        <a-form-item label="장비 메모 / 상세 설명">
          <a-textarea
            v-model:value="deviceForm.memo"
            :rows="3"
            placeholder="IP 주소, 용도, 담당자 등을 입력하세요"
          />
        </a-form-item>
        <a-form-item
          label="커스텀 바로가기 링크 (한 줄에 하나, 형식: 이름|URL)"
          :validateStatus="quickLinksError ? 'error' : ''"
          :help="quickLinksError"
        >
          <a-textarea
            v-model:value="deviceForm.quickLinksText"
            :rows="3"
            placeholder="예: iDRAC|https://10.0.0.10&#10;NAS|https://nas.local"
            @change="quickLinksError = ''"
          />
        </a-form-item>
      </a-form>
    </a-modal>
    <a-modal
      v-model:visible="hostVmModalVisible"
      :title="hostVmModalTitle"
      :footer="null"
      width="760px"
      destroyOnClose
    >
      <a-spin :spinning="hostVmLoading">
        <div class="host-vm-scroll-area">
        <div v-if="!hostVmLoading && !filteredHostVmList.length" class="host-vm-empty-wrap">
          <div class="ant-empty ant-empty-normal">
            <div class="ant-empty-image">
              <svg class="ant-empty-img-simple" width="64" height="41" viewBox="0 0 64 41">
                <g transform="translate(0 1)" fill="none" fill-rule="evenodd">
                  <ellipse class="ant-empty-img-simple-ellipse" fill="#F5F5F5" cx="32" cy="33" rx="32" ry="7"></ellipse>
                  <g class="ant-empty-img-simple-g" fill-rule="nonzero" stroke="#D9D9D9">
                    <path d="M55 12.76L44.854 1.258C44.367.474 43.656 0 42.907 0H21.093c-.749 0-1.46.474-1.947 1.257L9 12.761V22h46v-9.24z"></path>
                    <path d="M41.613 15.931c0-1.605.994-2.93 2.227-2.931H55v18.137C55 33.26 53.68 35 52.05 35h-40.1C10.32 35 9 33.259 9 31.137V13h11.16c1.233 0 2.227 1.323 2.227 2.928v.022c0 1.605 1.005 2.901 2.237 2.901h14.752c1.232 0 2.237-1.308 2.237-2.913v-.007z" fill="#FAFAFA" class="ant-empty-img-simple-path"></path>
                  </g>
                </g>
              </svg>
            </div>
            <p class="ant-empty-description">{{ $t('label.no.data') || 'No Data' }}</p>
          </div>
        </div>
        <div v-else-if="!hostVmLoading" class="host-vm-grid">
          <div
            v-for="vm in filteredHostVmList"
            :key="vm.id"
            class="host-vm-card"
            :class="{ 'host-vm-card-inactive': !isRunningVm(vm) }"
          >
            <div class="host-vm-icon">
              <font-awesome-icon :icon="['fab', getVmOsLogo(vm)]" size="lg" />
            </div>
            <a-tooltip :title="vm.displayname || vm.name || vm.id">
              <div class="host-vm-name">{{ vm.displayname || vm.name || vm.id }}</div>
            </a-tooltip>
            <div class="host-vm-meta">{{ vm.state || '-' }} / {{ vm.ostypename || vm.hypervisor || '-' }}</div>
          </div>
        </div>
        </div>
      </a-spin>
    </a-modal>

  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed, watch, onBeforeUnmount, nextTick } from 'vue'
import { message } from 'ant-design-vue'
import html2canvas from 'html2canvas'
import { api } from '@/api'
import { useRouter } from 'vue-router'
import {
  CopyOutlined,
  SettingOutlined,
  DeleteOutlined,
  InfoCircleOutlined,
  LeftOutlined,
  RightOutlined,
  PlusOutlined,
  FileTextOutlined,
  UploadOutlined,
  CameraOutlined,
  SaveOutlined,
  ExportOutlined,
  LinkOutlined,
  LaptopOutlined,
  AppstoreOutlined,
  MoreOutlined,
  UnorderedListOutlined
} from '@ant-design/icons-vue'

// 전역 상태
const loading = ref(false)
const saving = ref(false)
const zoomLevel = ref(1)
const router = useRouter()

// 슬라이더 및 입력창과 연동할 퍼센트 단위 변수
const zoomPercent = computed({
  get: () => Math.round(zoomLevel.value * 100),
  set: (val) => {
    // 최소 40% ~ 최대 150% 범위 제한
    if (val < 40) val = 40
    if (val > 150) val = 150
    zoomLevel.value = val / 100
  }
})

// 데이터 모델
const parsedRacks = ref([])

// 데이터 변경 여부 추적
const isDirty = ref(false)

// parsedRacks 객체가 깊은 곳까지 변경되는지 감시
watch(parsedRacks, () => {
  isDirty.value = true
}, { deep: true })

// 페이지 이탈 방지 이벤트 핸들러
const handleBeforeUnload = (e) => {
  if (isDirty.value) {
    e.preventDefault()
    e.returnValue = '' // 브라우저 표준 경고창을 띄우기 위한 필수 값
  }
}

const currentZoneId = ref('')
const zoneLoading = ref(false)

const fetchZonesAndRackData = () => {
  zoneLoading.value = true // 필요시 템플릿에 스피너(Loading) 연동 가능

  api('listZones', {}).then(json => {
    const listZones = json.listzonesresponse.zone
    if (listZones && listZones.length > 0) {
      // 'Edge' 타입을 제외한 Zone 목록 필터링
      const filteredZones = listZones.filter(zone => zone.type !== 'Edge')

      if (filteredZones.length > 0) {
        // 첫 번째 Zone의 ID를 현재 Zone으로 설정
        currentZoneId.value = filteredZones[0].id

        // Zone ID를 성공적으로 가져왔으므로 DB에서 랙 구성 조회 시작
        fetchRackData()
      } else {
        message.warning('사용 가능한 Zone이 없습니다.')
      }
    }
  }).catch(error => {
    console.error('Zone 목록 조회 실패:', error)
    message.error('Zone 정보를 불러오는데 실패했습니다.')
  }).finally(() => {
    zoneLoading.value = false
  })
}

// ----------------------------------------------------------------
// 3. DB에서 랙 구성 불러오기 (GET)
// ----------------------------------------------------------------
const fetchRackData = () => {
  if (!currentZoneId.value) return

  loading.value = true

  api('listRackLayouts', {
    zoneid: currentZoneId.value,
    name: 'default'
  }).then(json => {
    const layouts = json.listracklayoutsresponse.racklayout

    // DB에 저장된 데이터가 있고, 내용(content)이 존재하는 경우
    if (layouts && layouts.length > 0 && layouts[0].content) {
      parsedRacks.value = JSON.parse(layouts[0].content)
      message.success('랙 구성을 불러왔습니다.')
    } else {
      // DB에 데이터가 전혀 없는 경우 (최초 접속) -> 기본 빈 랙 생성
      parsedRacks.value = [{
        name: 'Main Rack',
        totalHeight: 42,
        items: [{ type: 'gap', height: 42 }]
      }]
    }
    // 데이터를 방금 불러왔으므로 미저장 상태(isDirty) 초기화 (빈 줄 삭제함)
    nextTick(() => { isDirty.value = false })
  }).catch(error => {
    console.error('랙 데이터 불러오기 실패:', error)
    message.error('랙 구성을 불러오는데 실패했습니다.')
  }).finally(() => {
    loading.value = false
  })
}

const saveRackData = () => {
  if (!currentZoneId.value) {
    message.error('저장할 Zone 정보가 없습니다.')
    return
  }

  saving.value = true
  const jsonContent = JSON.stringify(parsedRacks.value)

  api('updateRackLayout', {
    zoneid: currentZoneId.value,
    name: 'default',
    content: jsonContent
  }).then(json => {
    message.success('랙 구성이 DB에 안전하게 저장되었습니다.')
    isDirty.value = false // 저장 성공 시 변경 상태 뱃지 숨김
  }).catch(error => {
    console.error('랙 데이터 저장 실패:', error)
    message.error('랙 구성 저장에 실패했습니다.')
  }).finally(() => {
    saving.value = false
  })
}

// ---------------- 랙 모달 로직 ----------------
const rackModalVisible = ref(false)
const rackModalMode = ref('add') // 'add' or 'edit'
const targetRackIndex = ref(-1)

const rackForm = reactive({ name: '', totalHeight: 42 })

const openRackModal = (mode, index = -1) => {
  rackModalMode.value = mode
  targetRackIndex.value = index
  if (mode === 'edit' && index > -1) {
    rackForm.name = parsedRacks.value[index].name
    rackForm.totalHeight = parsedRacks.value[index].totalHeight
  } else {
    rackForm.name = ''
    rackForm.totalHeight = 42
  }
  rackModalVisible.value = true
}

const closeRackModal = () => {
  rackModalVisible.value = false
}

// 랙 전체를 이미지로 저장하는 함수
const exportToImage = async () => {
  const element = document.querySelector('.rack-container')
  if (!element) {
    message.warning('저장할 랙 구성이 없습니다.')
    return
  }

  try {
    message.loading({ content: '이미지 변환 중...', key: 'exporting' })
    const fullWidth = element.scrollWidth
    const fullHeight = element.scrollHeight

    // 가로 스크롤 영역 전체를 포함해 캡처
    const canvas = await html2canvas(element, {
      backgroundColor: '#eef0f4',
      scale: 2,
      width: fullWidth,
      height: fullHeight,
      windowWidth: fullWidth,
      windowHeight: fullHeight,
      scrollX: 0,
      scrollY: 0,
      onclone: (doc) => {
        const cloned = doc.querySelector('.rack-container')
        if (cloned) {
          cloned.style.overflow = 'visible'
          cloned.style.width = `${fullWidth}px`
          cloned.style.height = `${fullHeight}px`
        }
      }
    })

    const link = document.createElement('a')
    link.download = 'rack-diagram-export.png'
    link.href = canvas.toDataURL('image/png')
    link.click()

    message.success({ content: '이미지 저장이 완료되었습니다.', key: 'exporting' })
  } catch (error) {
    console.error(error)
    message.error({ content: '이미지 저장에 실패했습니다.', key: 'exporting' })
  }
}

// 파일 Input 요소를 참조하기 위한 ref
const fileInput = ref(null)

// JSON으로 내보내기 (Export)
const exportToJson = () => {
  if (!parsedRacks.value || parsedRacks.value.length === 0) {
    message.warning('내보낼 랙 데이터가 없습니다.')
    return
  }

  // 1. 데이터를 JSON 문자열로 변환 (들여쓰기 2칸으로 보기 좋게)
  const dataStr = JSON.stringify(parsedRacks.value, null, 2)

  // 2. Blob 객체를 생성하여 파일화
  const blob = new Blob([dataStr], { type: 'application/json' })
  const url = URL.createObjectURL(blob)

  // 3. 가상의 a 태그를 만들어 클릭 이벤트 발생 (다운로드 실행)
  const link = document.createElement('a')
  link.href = url
  // 오늘 날짜를 파일명에 포함
  const dateStr = new Date().toISOString().slice(0, 10)
  link.download = `rack-data-${dateStr}.json`
  link.click()

  // 4. 메모리 정리
  URL.revokeObjectURL(url)
  message.success('JSON 파일이 다운로드되었습니다.')
}

// JSON 불러오기 버튼 클릭 시 숨겨진 input 실행
const triggerImport = () => {
  if (fileInput.value) {
    fileInput.value.click()
  }
}

// 선택된 JSON 파일 읽고 화면에 렌더링 (Import)
const handleImport = (event) => {
  const file = event.target.files[0]
  if (!file) return

  const reader = new FileReader()
  reader.onload = (e) => {
    try {
      // 1. 파일 내용 파싱
      const importedData = JSON.parse(e.target.result)

      // 2. 간단한 유효성 검사 (배열 형태인지 확인)
      if (Array.isArray(importedData)) {
        parsedRacks.value = importedData // 화면 갱신
        message.success('성공적으로 데이터를 불러왔습니다.')
      } else {
        message.error('유효하지 않은 파일 형식입니다. (배열이 아님)')
      }
    } catch (error) {
      console.error(error)
      message.error('JSON 파일 파싱에 실패했습니다. 파일이 손상되었을 수 있습니다.')
    } finally {
      // 3. 같은 파일을 다시 선택할 수 있도록 input 초기화
      event.target.value = ''
    }
  }

  // 텍스트 형태로 파일 읽기 시작
  reader.readAsText(file)
}

const cloneRack = (rIndex) => {
  const targetRack = parsedRacks.value[rIndex]
  const newRack = {
    ...targetRack,
    name: `${targetRack.name} (Copy)`,
    items: targetRack.items.map(item => ({ ...item }))
  }
  parsedRacks.value.push(newRack)
  message.success('랙이 복제되었습니다.')
}

// 랙 순서 좌우 이동 함수
const moveRack = (rIndex, direction) => {
  const targetIndex = rIndex + direction
  if (targetIndex < 0 || targetIndex >= parsedRacks.value.length) return

  const temp = parsedRacks.value[rIndex]
  parsedRacks.value[rIndex] = parsedRacks.value[targetIndex]
  parsedRacks.value[targetIndex] = temp
}

// 랙 모달 저장 로직
const submitRackModal = () => {
  const rackName = String(rackForm.name || '').trim()
  if (!rackName) {
    message.warning('랙 이름을 입력해주세요.')
    return
  }
  if (rackName.length > 60) {
    message.warning('랙 이름은 60자 이하로 입력해주세요.')
    return
  }

  if (!Number.isInteger(rackForm.totalHeight) || rackForm.totalHeight < 10 || rackForm.totalHeight > 50) {
    message.warning('총 높이(U)는 10~50 사이의 정수만 입력할 수 있습니다.')
    return
  }

  const duplicateName = parsedRacks.value.some((rack, idx) => {
    if (rackModalMode.value === 'edit' && idx === targetRackIndex.value) return false
    return String(rack.name || '').trim().toLowerCase() === rackName.toLowerCase()
  })
  if (duplicateName) {
    message.warning('동일한 랙 이름이 이미 존재합니다.')
    return
  }

  if (rackModalMode.value === 'add') {
    parsedRacks.value.push({
      name: rackName,
      totalHeight: rackForm.totalHeight,
      items: [{ type: 'gap', height: rackForm.totalHeight }]
    })
  } else {
    const targetRack = parsedRacks.value[targetRackIndex.value]
    const currentItemsHeight = targetRack.items.reduce((sum, item) => sum + item.height, 0)
    const diff = rackForm.totalHeight - currentItemsHeight

    if (diff > 0) {
      // 랙 크기가 커졌으면 맨 아래에 그만큼 여백(Gap) 추가
      targetRack.items.push({ type: 'gap', height: diff })
      targetRack.name = rackName
      targetRack.totalHeight = rackForm.totalHeight
    } else if (diff < 0) {
      // 랙 크기가 줄어들었을 때의 스마트 처리 로직
      let absDiff = Math.abs(diff)
      const tempItems = [...targetRack.items]

      // 1. 랙 안에 있는 '실제 장비(gap 제외)'들의 총 높이만 먼저 계산
      const equipmentHeight = tempItems
        .filter(item => item.type !== 'gap')
        .reduce((sum, item) => sum + item.height, 0)

      // 2. 실제 장비들의 총합보다 랙을 더 작게 만들 수는 없으므로 사전 차단
      if (rackForm.totalHeight < equipmentHeight) {
        message.error(`실제 장비가 차지하는 공간(${equipmentHeight}U) 이하로 랙 크기를 줄일 수 없습니다.`)
        return
      }

      // 3. 맨 아래부터 순회하며 빈 공간(gap)만 골라서 깎아냄
      for (let i = tempItems.length - 1; i >= 0; i--) {
        if (tempItems[i].type === 'gap') {
          if (tempItems[i].height > absDiff) {
            tempItems[i].height -= absDiff
            absDiff = 0
            break // 필요한 만큼 다 깎았으면 종료
          } else {
            absDiff -= tempItems[i].height
            tempItems.splice(i, 1) // 여백 크기가 작으면 완전 삭제하고 계속 진행
          }
        }
        // 장비(server, switch 등)를 만나면 아무 짓도 안 하고 그냥 건너뜀!

        // 목표치를 다 줄였으면 반복문 탈출
        if (absDiff === 0) break
      }

      targetRack.items = tempItems
      targetRack.name = rackName
      targetRack.totalHeight = rackForm.totalHeight
    } else {
      // 높이는 그대로고 이름만 변경된 경우
      targetRack.name = rackName
    }
  }
  closeRackModal()
}

const deleteRack = (index) => {
  parsedRacks.value.splice(index, 1)
}

// ---------------- 장비 모달/관리 로직 ----------------
const deviceModalVisible = ref(false)
const targetItemIndex = ref(-1)

const deviceForm = reactive({
  type: 'server',
  label: '',
  height: 1,
  customType: '',
  memo: '',
  sourceRef: undefined,
  quickLinksText: ''
})

const inventoryLoading = ref(false)
const inventoryOptions = ref([])
const hostCache = ref({})
const hostVmModalVisible = ref(false)
const hostVmModalTitle = ref('호스트 VM 목록')
const hostVmLoading = ref(false)
const hostVmList = ref([])
const hostVmFallbackList = ref([])
const dragSource = ref({ rIndex: -1, iIndex: -1 })
const quickLinksError = ref('')
const ENABLE_VM_FALLBACK_MOCK = true
const HOST_ACTIVE_VM_STATES = new Set(['running', 'starting', 'stopping', 'migrating'])

const buildVmMockList = (hostId, hostName = 'sample') => {
  const osPool = [
    'CentOS Linux (Sample)',
    'Rocky Linux (Sample)',
    'Ubuntu Linux (Sample)',
    'Windows Server (Sample)',
    'Windows 11 Pro (Sample)'
  ]
  const list = []
  for (let i = 1; i <= 20; i++) {
    const idx = i - 1
    const no = String(i).padStart(2, '0')
    list.push({
      id: `sample-${hostId}-${no}`,
      name: `${hostName}-vm-${no}`,
      displayname: `${hostName}-vm-${no}`,
      state: 'Running',
      ostypename: osPool[idx % osPool.length]
    })
  }
  return list
}

const filteredHostVmList = computed(() => {
  const merged = hostVmList.value.length ? hostVmList.value : hostVmFallbackList.value
  return merged.filter(vm => HOST_ACTIVE_VM_STATES.has(String(vm?.state || '').toLowerCase()))
})

const mergeAssetMemo = (existingMemo, linesToAdd) => {
  const memo = existingMemo || ''
  const marker = '[LinkedAsset]'
  const cleaned = memo
    .split('\n')
    .filter(line => !line.startsWith(marker))
    .join('\n')
    .trim()
  const linked = linesToAdd.map(line => `${marker} ${line}`).join('\n')
  return [cleaned, linked].filter(Boolean).join('\n')
}

const buildInventoryOptions = async () => {
  if (!currentZoneId.value) return
  inventoryLoading.value = true
  const options = []

  try {
    const hostJson = await api('listHosts', { zoneid: currentZoneId.value, listall: true })
    const hosts = hostJson?.listhostsresponse?.host || []
    hosts.forEach(h => {
      const name = h.name || h.hostname || h.id
      const ip = h.ipaddress ? ` / ${h.ipaddress}` : ''
      options.push({
        label: `[Host] ${name}${ip}`,
        value: `host:${h.id}`,
        meta: { kind: 'host', id: h.id, name, ip: h.ipaddress || '' }
      })
      hostCache.value[h.id] = h
    })
  } catch (e) {
    console.warn('listHosts failed:', e)
  }

  inventoryOptions.value = options
  inventoryLoading.value = false
}

const handleSourceChange = (value) => {
  if (!value) return
  const selected = inventoryOptions.value.find(o => o.value === value)
  if (!selected) return

  if (!deviceForm.label || Object.values(defaultLabels).includes(deviceForm.label)) {
    deviceForm.label = selected.meta.name
  }

  const lines = [
    `Type=${selected.meta.kind}`,
    `Name=${selected.meta.name}`,
    `Id=${selected.meta.id}`
  ]
  if (selected.meta.ip) lines.push(`IP=${selected.meta.ip}`)
  deviceForm.memo = mergeAssetMemo(deviceForm.memo, lines)
}

const getLinkedHostId = (item) => {
  if (!item?.sourceRef || typeof item.sourceRef !== 'string') return null
  if (!item.sourceRef.startsWith('host:')) return null
  return item.sourceRef.split(':')[1] || null
}

const isHostLinked = (item) => {
  return !!getLinkedHostId(item)
}

const parseQuickLinksTextWithValidation = (text) => {
  if (!text) return { links: [], errors: [] }
  const lines = text.split('\n')
  const links = []
  const errors = []

  lines.forEach((raw, idx) => {
    const line = raw.trim()
    if (!line) return

    let label = ''
    let url = ''
    if (line.includes('|')) {
      const parts = line.split('|')
      label = (parts[0] || '').trim()
      url = (parts.slice(1).join('|') || '').trim()
    } else {
      url = line
    }

    if (!url) {
      errors.push(`${idx + 1}행: URL이 비어 있습니다. (형식: 이름|URL)`)
      return
    }
    if (!/^https?:\/\//i.test(url)) {
      errors.push(`${idx + 1}행: URL은 http:// 또는 https:// 로 시작해야 합니다.`)
      return
    }

    links.push({ label, url })
  })

  return { links, errors }
}

const getQuickLinks = (item) => {
  if (!item?.quickLinks || !Array.isArray(item.quickLinks)) return []
  return item.quickLinks.filter(link => link?.url)
}

const hasQuickLinks = (item) => getQuickLinks(item).length > 0
const hasActionMenu = (item) => isHostLinked(item) || hasQuickLinks(item)

const compactGaps = (rack) => {
  for (let i = rack.items.length - 1; i > 0; i--) {
    if (rack.items[i].type === 'gap' && rack.items[i - 1].type === 'gap') {
      rack.items[i - 1].height += rack.items[i].height
      rack.items.splice(i, 1)
    }
  }
}

const onDragStart = (rIndex, iIndex) => {
  dragSource.value = { rIndex, iIndex }
}

const getDesiredStartUFromRackEvent = (event, rack) => {
  if (!event?.currentTarget) return 0
  const rect = event.currentTarget.getBoundingClientRect()
  const y = Math.max(0, Math.min(rect.height - 1, event.clientY - rect.top))
  const raw = Math.floor(y / 32)
  const rackHeight = rack?.totalHeight || rack.items.reduce((s, i) => s + i.height, 0)
  return Math.max(0, Math.min(rackHeight - 1, raw))
}

const findBestStartUInRack = (rack, deviceHeight, desiredStartU) => {
  compactGaps(rack)
  let cursor = 0
  let best = null
  for (let i = 0; i < rack.items.length; i++) {
    const item = rack.items[i]
    if (item.type === 'gap') {
      const minStart = cursor
      const maxStart = cursor + item.height - deviceHeight
      if (maxStart >= minStart) {
        const candidate = Math.max(minStart, Math.min(desiredStartU, maxStart))
        const dist = Math.abs(candidate - desiredStartU)
        if (!best || dist < best.dist) {
          best = { index: i, startU: candidate, dist }
        }
      }
    }
    cursor += item.height
  }
  return best
}

const placeDeviceAtStartU = (rack, gapIndex, startU, device) => {
  let cursor = 0
  for (let i = 0; i < gapIndex; i++) cursor += rack.items[i].height
  const gap = rack.items[gapIndex]
  if (!gap || gap.type !== 'gap') return false

  const localStart = startU - cursor
  const localEnd = localStart + device.height
  if (localStart < 0 || localEnd > gap.height) return false

  const before = localStart
  const after = gap.height - localEnd
  const insert = []
  if (before > 0) insert.push({ type: 'gap', height: before })
  insert.push({ ...device })
  if (after > 0) insert.push({ type: 'gap', height: after })
  rack.items.splice(gapIndex, 1, ...insert)
  compactGaps(rack)
  return true
}

const onDropRackFrame = (targetRIndex, event) => {
  const { rIndex: sourceRIndex, iIndex: sourceIIndex } = dragSource.value
  dragSource.value = { rIndex: -1, iIndex: -1 }

  if (sourceRIndex < 0 || sourceIIndex < 0) return
  const sourceRack = parsedRacks.value[sourceRIndex]
  const targetRack = parsedRacks.value[targetRIndex]
  const sourceItem = sourceRack?.items?.[sourceIIndex]
  if (!sourceRack || !targetRack || !sourceItem || sourceItem.type === 'gap') return

  const moving = { ...sourceItem }
  const sameRack = sourceRIndex === targetRIndex

  // 원본 위치 비우기
  sourceRack.items.splice(sourceIIndex, 1, { type: 'gap', height: moving.height })
  compactGaps(sourceRack)

  const desiredStartU = getDesiredStartUFromRackEvent(event, targetRack)
  const best = findBestStartUInRack(targetRack, moving.height, desiredStartU)
  if (!best) {
    message.warning(`대상 랙의 연속 여백이 부족합니다. (필요 ${moving.height}U)`)
    // 복구
    const restore = findBestStartUInRack(sourceRack, moving.height, 0)
    if (restore) placeDeviceAtStartU(sourceRack, restore.index, restore.startU, moving)
    return
  }

  const placed = placeDeviceAtStartU(targetRack, best.index, best.startU, moving)
  if (!placed) {
    message.warning('드롭 위치 계산에 실패했습니다. 다시 시도해주세요.')
    const restore = findBestStartUInRack(sourceRack, moving.height, 0)
    if (restore) placeDeviceAtStartU(sourceRack, restore.index, restore.startU, moving)
    return
  }

  if (sameRack) compactGaps(targetRack)
}

const fetchHostById = async (hostId) => {
  if (!hostId) return null
  if (hostCache.value[hostId]) return hostCache.value[hostId]
  const json = await api('listHosts', { id: hostId })
  const host = json?.listhostsresponse?.host?.[0] || null
  if (host) hostCache.value[hostId] = host
  return host
}

const goToLinkedHost = (item) => {
  const hostId = getLinkedHostId(item)
  if (!hostId) return
  router.push({ path: `/host/${hostId}` })
}

const openLinkedHostOobm = async (item) => {
  const hostId = getLinkedHostId(item)
  if (!hostId) return
  try {
    const host = await fetchHostById(hostId)
    const protocol = host?.details?.manageconsoleprotocol || 'http'
    const address = host?.outofbandmanagement?.address || ''
    const port = host?.details?.manageconsoleport
    if (!address || !port) {
      message.warning('선택한 호스트의 OOBM 포털 정보를 찾을 수 없습니다.')
      return
    }
    window.open(`${protocol}://${address}:${port}`, '_blank')
  } catch (e) {
    message.error('OOBM 포털 정보를 불러오지 못했습니다.')
  }
}

const openLinkedHostCube = async (item) => {
  const hostId = getLinkedHostId(item)
  if (!hostId) return
  try {
    const host = await fetchHostById(hostId)
    const ip = host?.ipaddress
    if (!ip) {
      message.warning('선택한 호스트의 IP 정보를 찾을 수 없습니다.')
      return
    }
    window.open(`https://${ip}:9090`, '_blank')
  } catch (e) {
    message.error('Cube 포털 정보를 불러오지 못했습니다.')
  }
}

const openLinkedHostVmModal = async (item) => {
  const hostId = getLinkedHostId(item)
  if (!hostId) return
  hostVmLoading.value = true
  hostVmModalVisible.value = true
  hostVmList.value = []
  hostVmFallbackList.value = []

  try {
    const host = await fetchHostById(hostId)
    hostVmModalTitle.value = `호스트 VM 목록 - ${host?.name || host?.hostname || hostId}`
    const hostIdStr = String(hostId)
    const isVmAssignedToHost = (vm) => {
      const vmHostId = String(vm?.hostid || '')
      const vmState = String(vm?.state || '').toLowerCase()
      // 정책: 현재 host_id가 해당 호스트 + 활성 상태 VM만 표시
      return vmHostId === hostIdStr && HOST_ACTIVE_VM_STATES.has(vmState)
    }

    // 1) hostid 직접 조회
    const directJson = await api('listVirtualMachines', {
      hostid: hostId,
      listall: true,
      projectid: '-1',
      details: 'min',
      pagesize: 500
    })
    let vms = (directJson?.listvirtualmachinesresponse?.virtualmachine || []).filter(isVmAssignedToHost)

    // 2) hostid 조회가 비면 전체에서 host 매핑 기준으로 재탐색
    if (!vms.length) {
      const allJson = await api('listVirtualMachines', {
        listall: true,
        projectid: '-1',
        details: 'min',
        pagesize: 500
      })
      const allVms = allJson?.listvirtualmachinesresponse?.virtualmachine || []
      vms = allVms.filter(isVmAssignedToHost)
    }

    hostVmList.value = vms

    // 개발환경 fallback: host 매핑 샘플 제공
    if (ENABLE_VM_FALLBACK_MOCK && !hostVmList.value.length) {
      const hostName = host?.name || host?.hostname || hostId
      hostVmFallbackList.value = buildVmMockList(hostId, hostName)
    }
  } catch (e) {
    // 통일된 UI 정책: 실패 시 토스트 없이 No Data 표시
    hostVmList.value = []
    hostVmFallbackList.value = ENABLE_VM_FALLBACK_MOCK
      ? buildVmMockList(hostId, 'sample')
      : []
  } finally {
    hostVmLoading.value = false
  }
}

const getVmOsLogo = (vm) => {
  const osname = String(vm?.ostypename || vm?.name || '').toLowerCase()
  if (osname.includes('centos')) return 'centos'
  if (osname.includes('debian')) return 'debian'
  if (osname.includes('ubuntu')) return 'ubuntu'
  if (osname.includes('suse')) return 'suse'
  if (osname.includes('redhat')) return 'redhat'
  if (osname.includes('fedora')) return 'fedora'
  if (osname.includes('windows') || osname.includes('dos')) return 'windows'
  // Rocky는 전용 브랜드 아이콘이 없어서 Linux 계열로 표현
  if (osname.includes('rocky') || osname.includes('linux')) return 'linux'
  if (osname.includes('bsd')) return 'freebsd'
  if (osname.includes('apple') || osname.includes('mac')) return 'apple'
  return 'linux'
}

const isRunningVm = (vm) => {
  return String(vm?.state || '').toLowerCase() === 'running'
}

const handleHostActionMenu = (key, item) => {
  if (typeof key === 'string' && key.startsWith('quick-')) {
    const idx = Number(key.replace('quick-', ''))
    const link = getQuickLinks(item)[idx]
    if (link?.url) window.open(link.url, '_blank')
    return
  }
  if (key === 'host-detail') goToLinkedHost(item)
  if (key === 'host-vms') openLinkedHostVmModal(item)
  if (key === 'host-oobm') openLinkedHostOobm(item)
  if (key === 'host-cube') openLinkedHostCube(item)
}

// 입력 가능한 최대 높이 사전 계산 (물리적 한계)
const maxAllowedHeight = computed(() => {
  if (targetRackIndex.value === -1 || targetItemIndex.value === -1) return 42
  const rack = parsedRacks.value[targetRackIndex.value]
  const item = rack.items[targetItemIndex.value]

  let physicalMax = item.height // 기본적으로 내 크기 보장

  if (item.type === 'gap') {
    physicalMax = item.height // 빈 공간이면 그 크기가 한계
  } else {
    // 기존 장비 수정 시: 바로 아래가 여백이면 그 공간까지 끌어다 쓸 수 있음
    if (targetItemIndex.value < rack.items.length - 1) {
      const nextItem = rack.items[targetItemIndex.value + 1]
      if (nextItem.type === 'gap') physicalMax += nextItem.height
    }
  }
  return physicalMax
})

// 각 장비별 기본 라벨명 매핑
const defaultLabels = {
  server: 'Server',
  switch: 'Switch',
  storage: 'Storage',
  firewall: 'Firewall',
  patch: 'Patch Panel',
  pdu: 'PDU',
  ups: 'UPS',
  blank: 'Blank Panel',
  custom: 'Custom Device'
}

// 모달 열기 로직 수정
const openDeviceModal = (rIndex, iIndex) => {
  targetRackIndex.value = rIndex
  targetItemIndex.value = iIndex
  const item = parsedRacks.value[rIndex].items[iIndex]

  if (item.type === 'gap') {
    deviceForm.type = 'server'
    deviceForm.label = ''
    deviceForm.height = 1
    deviceForm.customType = ''
    deviceForm.memo = ''
    deviceForm.sourceRef = undefined
    deviceForm.quickLinksText = ''
    quickLinksError.value = ''
  } else {
    // 수정 모드일 때는 기존 데이터 로드
    deviceForm.type = item.type
    deviceForm.label = item.label || ''
    deviceForm.height = item.height
    deviceForm.customType = item.customType || ''
    deviceForm.memo = item.memo || ''
    deviceForm.sourceRef = item.sourceRef || undefined
    deviceForm.quickLinksText = getQuickLinks(item).map(link => `${link.label || ''}|${link.url || ''}`.replace(/^\|/, '')).join('\n')
    quickLinksError.value = ''
  }
  if (!inventoryOptions.value.length) {
    buildInventoryOptions()
  }
  deviceModalVisible.value = true
}

// 장비 종류 Select Box 변경 시 라벨 자동 업데이트
const handleTypeChange = (newType) => {
  // 사용자가 직접 입력한 라벨이 없거나, 기존 기본 라벨명과 똑같을 때만 새 기본명으로 교체
  const isLabelEmpty = !deviceForm.label
  const isLabelDefault = Object.values(defaultLabels).includes(deviceForm.label)

  if (isLabelEmpty || isLabelDefault) {
    deviceForm.label = defaultLabels[newType] || ''
  }
}

const closeDeviceModal = () => {
  deviceModalVisible.value = false
}

const submitDeviceModal = () => {
  const rIndex = targetRackIndex.value
  const iIndex = targetItemIndex.value
  const rack = parsedRacks.value[rIndex]
  const oldItem = rack.items[iIndex]

  const rawLabel = String(deviceForm.label || '').trim()
  const rawCustomType = String(deviceForm.customType || '').trim()
  const finalLabel = rawLabel || defaultLabels[deviceForm.type] || 'New Device'
  const finalCustomType = rawCustomType || (deviceForm.type === 'custom' ? 'Custom Unit' : '')

  if (deviceForm.type !== 'blank' && !finalLabel) {
    message.warning('장비명을 입력해주세요.')
    return
  }
  if (finalLabel.length > 60) {
    message.warning('장비명은 60자 이하로 입력해주세요.')
    return
  }
  if (deviceForm.type === 'custom' && !rawCustomType) {
    message.warning('커스텀 타입명을 입력해주세요.')
    return
  }
  if (deviceForm.type === 'custom' && rawCustomType.length > 60) {
    message.warning('커스텀 타입명은 60자 이하로 입력해주세요.')
    return
  }
  if (!Number.isInteger(deviceForm.height) || deviceForm.height <= 0) {
    message.warning('장비 높이(U)는 1 이상의 정수만 입력할 수 있습니다.')
    return
  }
  const quickLinkParsed = parseQuickLinksTextWithValidation(deviceForm.quickLinksText)
  if (quickLinkParsed.errors.length > 0) {
    quickLinksError.value = quickLinkParsed.errors[0]
    return
  }
  quickLinksError.value = ''

  const newItem = {
    type: deviceForm.type,
    label: finalLabel,
    height: deviceForm.height,
    customType: finalCustomType,
    memo: deviceForm.memo,
    sourceRef: deviceForm.sourceRef || null,
    quickLinks: quickLinkParsed.links
  }

  // 여백(Gap)에 새 장비 추가 시
  if (oldItem.type === 'gap') {
    if (oldItem.height < newItem.height) {
      message.error('선택한 여백보다 큰 장비는 추가할 수 없습니다.')
      return
    }
    const remainingHeight = oldItem.height - newItem.height
    rack.items.splice(iIndex, 1, newItem)
    if (remainingHeight > 0) {
      rack.items.splice(iIndex + 1, 0, { type: 'gap', height: remainingHeight })
    }
  } else {
    // 기존 장비 수정 시 (버그 픽스된 부분)
    const heightDiff = newItem.height - oldItem.height // 높이 변화량

    if (heightDiff === 0) {
      // 높이 변화가 없으면 그냥 교체
      rack.items.splice(iIndex, 1, newItem)
    } else if (heightDiff > 0) {
      // 장비 크기를 늘렸을 때: 아래쪽 여백(Gap)을 그만큼 깎아냄
      const nextItem = rack.items[iIndex + 1]
      if (nextItem && nextItem.type === 'gap' && nextItem.height >= heightDiff) {
        rack.items.splice(iIndex, 1, newItem)
        nextItem.height -= heightDiff
        if (nextItem.height === 0) rack.items.splice(iIndex + 1, 1) // 여백이 0이 되면 삭제
      } else {
        message.error('아래쪽 여백이 부족하여 크기를 늘릴 수 없습니다.')
        return
      }
    } else {
      // 장비 크기를 줄였을 때: 아래쪽에 남는 만큼 여백(Gap) 추가
      const absDiff = Math.abs(heightDiff)
      rack.items.splice(iIndex, 1, newItem)
      const nextItem = rack.items[iIndex + 1]

      if (nextItem && nextItem.type === 'gap') {
        nextItem.height += absDiff // 이미 여백이 있으면 합치기
      } else {
        rack.items.splice(iIndex + 1, 0, { type: 'gap', height: absDiff }) // 없으면 새로 여백 생성
      }
    }
  }
  closeDeviceModal()
}

const cloneItem = (rIndex, iIndex) => {
  const rack = parsedRacks.value[rIndex]
  const itemToClone = rack.items[iIndex]
  const neededHeight = itemToClone.height
  const newItem = { ...itemToClone }

  const placeIntoGap = (gapIndex, placeAtBottom = false) => {
    const gap = rack.items[gapIndex]
    if (!gap || gap.type !== 'gap' || gap.height < neededHeight) return false
    const remain = gap.height - neededHeight
    if (placeAtBottom) {
      const insert = []
      if (remain > 0) insert.push({ type: 'gap', height: remain })
      insert.push(newItem)
      rack.items.splice(gapIndex, 1, ...insert)
    } else {
      rack.items.splice(gapIndex, 1, newItem)
      if (remain > 0) rack.items.splice(gapIndex + 1, 0, { type: 'gap', height: remain })
    }
    return true
  }

  // 1) 아래쪽 어디든 충분한 gap 우선
  for (let i = iIndex + 1; i < rack.items.length; i++) {
    if (rack.items[i].type === 'gap' && placeIntoGap(i, false)) return
  }

  // 2) 위쪽 어디든 충분한 gap (원본 근처에 붙도록 아래쪽 정렬)
  for (let i = iIndex - 1; i >= 0; i--) {
    if (rack.items[i].type === 'gap' && placeIntoGap(i, true)) return
  }

  // 3) 그래도 없으면 랙 전체 검사(방어)
  for (let i = 0; i < rack.items.length; i++) {
    if (rack.items[i].type === 'gap' && placeIntoGap(i, false)) return
  }

  message.error(`랙 내에 여유 공간이 부족합니다. (필요 공간: ${neededHeight}U)`)
}

const deleteItem = (rIndex, iIndex) => {
  const rack = parsedRacks.value[rIndex]
  const itemHeight = rack.items[iIndex].height

  // 삭제하는 장비를 Gap으로 변경
  rack.items.splice(iIndex, 1, { type: 'gap', height: itemHeight })

  // 연속된 Gap 병합 (위아래 빈 공간 합치기)
  for (let i = rack.items.length - 1; i > 0; i--) {
    if (rack.items[i].type === 'gap' && rack.items[i - 1].type === 'gap') {
      rack.items[i - 1].height += rack.items[i].height
      rack.items.splice(i, 1)
    }
  }
}

// ---------------- 장비 이동(이사) 로직 ----------------
const moveModalVisible = ref(false)
const moveTargetRackIndex = ref(null) // 사용자가 선택할 도착지 랙
const moveSourceInfo = reactive({ rIndex: -1, iIndex: -1 }) // 이사 갈 장비의 원래 위치

// 이사 모달 열기
const openMoveDeviceModal = (rIndex, iIndex) => {
  // 랙이 하나밖에 없으면 이사 불가
  if (parsedRacks.value.length <= 1) {
    message.warning('이동할 다른 랙이 없습니다. [새 랙 추가]를 먼저 해주세요.')
    return
  }

  moveSourceInfo.rIndex = rIndex
  moveSourceInfo.iIndex = iIndex
  moveTargetRackIndex.value = null // 초기화
  moveModalVisible.value = true
}

// 이사 실행 (확인 버튼 클릭 시)
const submitMoveDevice = () => {
  if (moveTargetRackIndex.value === null) {
    message.warning('이동할 대상 랙을 선택해주세요.')
    return
  }

  const sourceRIndex = moveSourceInfo.rIndex
  const sourceIIndex = moveSourceInfo.iIndex
  const targetRIndex = moveTargetRackIndex.value

  const sourceRack = parsedRacks.value[sourceRIndex]
  const targetRack = parsedRacks.value[targetRIndex]
  const deviceToMove = { ...sourceRack.items[sourceIIndex] } // 장비 복사

  // 1. 대상 랙에 들어갈 공간(Gap)이 있는지 탐색
  // (장비 높이보다 크거나 같은 첫 번째 여백을 찾음)
  const targetGapIndex = targetRack.items.findIndex(
    item => item.type === 'gap' && item.height >= deviceToMove.height
  )

  if (targetGapIndex === -1) {
    message.error(`'${targetRack.name}'에 ${deviceToMove.height}U 공간이 부족하여 이동할 수 없습니다.`)
    return
  }

  // 2. [대상 랙] 처리: 찾은 여백을 쪼개서 장비 넣기
  const targetGap = targetRack.items[targetGapIndex]
  const remainingHeight = targetGap.height - deviceToMove.height

  // 여백 위치에 장비 덮어쓰기
  targetRack.items.splice(targetGapIndex, 1, deviceToMove)

  // 남은 공간이 있다면 그 아래에 다시 여백 추가
  if (remainingHeight > 0) {
    targetRack.items.splice(targetGapIndex + 1, 0, { type: 'gap', height: remainingHeight })
  }

  // 3. [출발 랙] 처리: 장비가 떠난 자리를 여백으로 메꾸고 병합
  // 기존 장비 삭제 및 Gap으로 변환
  sourceRack.items.splice(sourceIIndex, 1, { type: 'gap', height: deviceToMove.height })

  // 연속된 Gap 병합 (위아래 싹 훑어서 합침)
  // (Tip: 역순으로 돌면서 합쳐야 인덱스가 꼬이지 않음)
  for (let i = sourceRack.items.length - 1; i > 0; i--) {
    const current = sourceRack.items[i]
    const above = sourceRack.items[i - 1]

    if (current.type === 'gap' && above.type === 'gap') {
      above.height += current.height
      sourceRack.items.splice(i, 1) // 현재 Gap 제거 (위쪽 Gap에 흡수됨)
    }
  }

  message.success(`'${deviceToMove.label}' 장비가 '${targetRack.name}'(으)로 이동되었습니다.`)
  moveModalVisible.value = false
}

// 종류별 아이콘 정의
const getDeviceIcon = (type) => {
  const icons = {
    // 내부가 더 꽉 차 보이는 Solid/Bold 스타일 SVG
    server: `<svg viewBox="0 0 24 24" fill="currentColor" stroke="none"><path d="M20 10H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2v4a2 2 0 0 1-2 2zm-10-5h2v2h-2V5zm6 0h2v2h-2V5zM20 22H4a2 2 0 0 1-2-2v-4a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2v4a2 2 0 0 1-2 2zm-10-5h2v2h-2v-2zm6 0h2v2h-2v-2z"/></svg>`,
    switch: `<svg viewBox="0 0 24 24" fill="currentColor" stroke="none"><rect x="2" y="5" width="20" height="14" rx="2"/><circle cx="6" cy="12" r="1.5" fill="black"/><circle cx="12" cy="12" r="1.5" fill="black"/><circle cx="18" cy="12" r="1.5" fill="black"/></svg>`,
    storage: `<svg viewBox="0 0 24 24" fill="currentColor" stroke="none"><path d="M12 2C6.48 2 2 3.8 2 6v12c0 2.2 4.48 4 10 4s10-1.8 10-4V6c0-2.2-4.48-4-10-4zm0 18c-4.41 0-8-1.34-8-3s3.59-3 8-3 8 1.34 8 3-3.59 3-8 3zm0-10c-4.41 0-8-1.34-8-3s3.59-3 8-3 8 1.34 8 3-3.59 3-8 3z"/></svg>`,
    firewall: `<svg viewBox="0 0 24 24" fill="currentColor" stroke="none"><path d="M12 1L3 5v6c0 5.55 3.84 10.74 9 12 5.16-1.26 9-6.45 9-12V5l-9-4z"/></svg>`,
    ups: `<svg viewBox="0 0 24 24" fill="currentColor" stroke="none"><path d="M13 2.05v9.45h4.5l-8.5 10.45v-9.45H4.5L13 2.05z"/></svg>`,
    patch: `<svg viewBox="0 0 24 24" fill="currentColor" stroke="none"><path d="M22 7H2v10h20V7zm-14 6H4v-2h4v2zm6 0h-4v-2h4v2zm6 0h-4v-2h4v2z"/></svg>`,
    pdu: `<svg viewBox="0 0 24 24" fill="currentColor" stroke="none"><rect x="7" y="2" width="10" height="20" rx="2"/><circle cx="12" cy="6" r="1.5" fill="black"/><circle cx="12" cy="12" r="1.5" fill="black"/><circle cx="12" cy="18" r="1.5" fill="black"/></svg>`,
    custom: `<svg viewBox="0 0 24 24" fill="currentColor" stroke="none"><path d="M12 2L1 21h22L12 2zm0 4.19L19.53 19H4.47L12 6.19z"/></svg>`
  }
  return icons[type] || icons.custom
}

// 각 장비의 상단 라인 컬러와 맞춘 아이콘 컬러 정의
const getIconColor = (type) => {
  const colors = {
    server: '#4a90e2',
    switch: '#27ae60',
    storage: '#8e44ad',
    firewall: '#c0392b',
    ups: '#00b894', // 민트색
    patch: '#f39c12', // 주황색
    pdu: '#d35400', // 진한 주황색
    custom: '#95a5a6' // 회색
  }
  return colors[type] || '#ffffff'
}

// 검색어 상태
const searchQuery = ref('')

// 검색 일치 여부 확인 함수
const isMatched = (item) => {
  if (!searchQuery.value) return true // 검색어가 없으면 모두 정상 표시
  if (item.type === 'gap') return true // 여백은 검색 대상에서 제외

  const query = searchQuery.value.toLowerCase()
  const labelMatch = item.label && item.label.toLowerCase().includes(query)
  const memoMatch = item.memo && item.memo.toLowerCase().includes(query)

  return labelMatch || memoMatch
}

onMounted(() => {
  // 1. Zone 목록을 먼저 가져오고, 성공하면 내부에서 fetchRackData()를 자동으로 실행합니다.
  fetchZonesAndRackData()

  // 2. 창 닫기/새로고침 방지 이벤트 리스너 등록
  window.addEventListener('beforeunload', handleBeforeUnload)
})

onBeforeUnmount(() => {
  window.removeEventListener('beforeunload', handleBeforeUnload)
})

</script>

<style scoped>
/* 툴바 전체 컨테이너 (좌우 양끝 정렬) */
.toolbar-container {
  display: flex !important;
  justify-content: space-between !important;
  align-items: center !important;

  /* 좁아지면 자연스럽게 줄바꿈되도록 허용 */
  flex-wrap: wrap !important;
  gap: 12px; /* 줄바꿈 되었을 때 위아래 여백 확보 */

  background: #ffffff;
  padding: 12px 16px;
  border-radius: 8px;
  border: 1px solid #e8e8e8;
  box-shadow: 0 2px 4px rgba(0,0,0,0.02);
  margin-bottom: 16px;
}

.toolbar-left, .toolbar-right {
  display: flex;
  align-items: center;
}

/* 버튼 사이 구분선 스타일 */
.toolbar-divider {
  background-color: #d9d9d9;
  height: 20px;
  margin: 0 4px;
}

/* 화면 배율 텍스트 */
.zoom-label {
  font-weight: bold;
  color: #595959;
  margin-right: 8px;
  font-size: 13px;
}

/* 랙 캔버스 배경 */
.rack-canvas {
  scrollbar-width: thin;
  scrollbar-color: #888 #eef0f4;
  background: radial-gradient(circle, #f0f2f5 0%, #e6e9ed 100%);
}

/* 랙 컨테이너 (가로 정렬) */
.rack-container {
  display: flex;
  gap: 30px;
  align-items: flex-start;
  width: max-content;
  padding-bottom: 20px; /* 가로 스크롤바와 랙 사이 여백 */
}

.rack-zoom-wrapper {
  display: inline-block;
  width: max-content;
}

/* 개별 랙 래퍼 */
.rack-wrapper {
  background: #36393f;
  border-radius: 6px;
  padding: 15px;
  box-shadow: 0 4px 10px rgba(0,0,0,0.3);
  flex-shrink: 0;
  width: 414px; /* 기존 폭 대비 20% 확장 */
  flex: 0 0 414px
}

/* 랙 헤더 */
.rack-header {
  color: #fff;
  font-weight: bold;
  margin-bottom: 10px;
  padding-bottom: 5px;
  border-bottom: 2px solid #1890ff;
}

.rack-header-inner {
  display: flex;
  flex-direction: row;
  flex-wrap: nowrap; /* 절대 줄바꿈 금지 */
  align-items: center;
  justify-content: space-between;
  width: 100%;
  height: 24px; /* 높이 고정으로 안정감 부여 */
}

/* 랙 본체 가로 정렬용 */
.rack-body {
  display: flex;
  background: #1a1b1e; /* 전체 프레임 배경과 맞춤 */
  border-radius: 8px;
  border: 4px solid #2b2d31;
  overflow: hidden;
}

/* 기존 rack-frame의 중복 테두리와 여백 제거 (rack-body로 옮겼으므로) */
.rack-frame {
  border: none !important;
  border-radius: 0 !important;
  margin: 0 !important;
  background: transparent !important;
}

/* 눈금자 기둥 영역 */
.rack-ruler {
  width: 29px; /* 기존 폭 대비 20% 확장 */
  background: #232428; /* 프레임보다 아주 살짝 어두운 색 */
  border-right: 2px solid #111; /* 장비 영역과의 경계선 */
  display: flex;
  flex-direction: column;
}

/*  개별 눈금 텍스트 (정확히 1U = 32px에 맞춤) */
.ruler-number {
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #555c65;
  font-size: 11px;
  font-family: 'Courier New', monospace;
  font-weight: bold;
  box-sizing: border-box;
  /* 눈금선 느낌을 위해 위아래에 아주 연한 점선 추가 */
  border-bottom: 1px dotted rgba(255, 255, 255, 0.05);
}

/* 맨 위 눈금 상단 선 보정 */
.ruler-number:first-child {
  border-top: 1px dotted rgba(255, 255, 255, 0.05);
}

/* 랙 내부 프레임 */
.rack-frame {
  width: 336px; /* 기존 폭 대비 20% 확장 */
  background: #2c2f35;
  border: 4px solid #3f434b;
  /* 프레임 자체 모서리를 둥글게 */
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  margin: 0 auto;
  /* 내부 장비들이 둥근 모서리를 벗어나지 않도록 잘라냄 */
  overflow: hidden;
  padding: 2px 0;
}

/* 개별 아이템 (장비 또는 여백) */
.rack-item {
  width: 100%;
  position: relative;
  box-sizing: border-box;
  /* 장비 사이 틈을 조금 더 부드럽게 처리 */
  border-bottom: 2px solid #0a0a0a;
  /* 개별 장비 모서리도 약간 둥글게 */
  border-radius: 4px;
  overflow: hidden; /* 자식 요소의 각진 부분 잘라내기 */
  margin-bottom: -1px; /* 테두리 겹침 보정 */
}

/* 랙 이름 텍스트: 길어지면 말줄임표 처리 */
.rack-name-text {
  flex: 1;
  min-width: 0; /* flex 환경에서 ellipsis 작동 필수 조건 */
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 14px; /* 가독성과 공간 확보를 위한 최적 크기 */
  font-weight: bold;
  margin-right: 10px;
  /* 마우스 오버 시 클릭 가능한 느낌 전달 */
  //cursor: pointer;
  transition: color 0.2s ease;
}

/* 마우스 호버 시 텍스트 색상을 밝게 변경 */
.rack-name-text:hover {
  color: #5865f2 !important; /* 포인트 컬러로 강조 */
  text-decoration: none; /* 밑줄 추가로 가독성 확보 */
}

.rack-item:last-child {
  border-bottom: none;
  margin-bottom: 0;
}

/* 1. 버튼들을 감싸는 컨테이너를 무조건 가로(Row)로 배치 */
.rack-header-actions {
  display: flex !important;
  flex-direction: row !important; /* 세로로 쌓이는 것 방지 */
  align-items: center;
  flex-shrink: 0;
}

/* 2. 개별 버튼 스타일 (inline-flex를 써야 가로 배열이 유지됨) */
.rack-header-actions .ant-btn {
  color: #ffffff !important;
  padding: 0 4px !important;
  display: inline-flex !important; /* flex 대신 inline-flex 적용 */
  align-items: center;
  justify-content: center;
  background: transparent;
  border: none;
  box-shadow: none;
}

/* 3. 내부 Ant Design 아이콘 색상 강제 (검은색 변함 방지) */
.rack-header-actions .ant-btn :deep(.anticon) {
  color: #ffffff !important;
}

/* 4. 버튼 비활성화 상태 색상 처리 */
.rack-header-actions .ant-btn:disabled,
.rack-header-actions .ant-btn:disabled :deep(.anticon) {
  color: rgba(255, 255, 255, 0.3) !important;
}

/* 여백 스타일 */
.gap-content {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  /* 배경을 더 밝은 회색 계열로 변경 */
  background: repeating-linear-gradient(
    45deg,
    #3a3d42,
    #3a3d42 10px,
    #42464d 10px,
    #42464d 20px
  );
  /* 텍스트 색상을 더 밝게 하여 가독성 확보 */
  color: #ffffff;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
  /* 장비와 확실히 구분되도록 안쪽 그림자 제거 혹은 변경 */
  box-shadow: inset 0 0 15px rgba(0,0,0,0.2);
}

/* 여백 호버 시 피드백 강화 */
.gap-content:hover {
  background: #4f545c;
  color: #5865f2;
}

/* 장비 내용 래퍼 */
.device-content {
  position: relative;
  height: 100%;
  overflow: hidden;
  cursor: pointer;
  background-color: #232529 !important;
  /* 양옆에 랙 마운트용 브라켓(금속 날개) 디자인 추가 */
  border-left: 8px solid #4f545c;
  border-right: 8px solid #4f545c;
}

/* 1. 상단 컬러 라인 (기존 SVG 상단 라인 대체) */
.device-top-line {
  position: absolute;
  top: 0; left: 0; right: 0;
  height: 3px;
  z-index: 2;
}

/* 2. 질감 패턴 기본 틀 */
.device-pattern {
  position: absolute;
  /* 이름표(text)와 겹치지 않게 오른쪽 구석으로 밀어둠 */
  top: 8px; bottom: 8px; right: 16px; left: 60px;
  z-index: 1;
  opacity: 0.6;
}

/* 💻 서버: 촘촘한 세로 디스크 베이 */
.pattern-server {
  background-image: repeating-linear-gradient(to right, #111 0, #111 6px, transparent 6px, transparent 10px);
}

/* 🗄️ 스토리지: 넓은 세로 디스크 베이 */
.pattern-storage {
  background-image: repeating-linear-gradient(to right, #111 0, #111 16px, transparent 16px, transparent 20px);
}

/* 🎛️ 스위치: 상하 여백을 줘서 포트 구멍처럼 보이게 */
.pattern-switch {
  top: 35%; bottom: 35%;
  background-image: repeating-linear-gradient(to right, #000 0, #000 10px, transparent 10px, transparent 14px);
}

/* 🧱 방화벽: 빗살무늬 해치 패턴 */
.pattern-firewall {
  background-image: repeating-linear-gradient(45deg, #111 0, #111 6px, transparent 6px, transparent 12px);
}

/* 🔌 패치패널: 얇은 점선 느낌 */
.pattern-patch {
  top: 45%; bottom: 45%;
  background-image: repeating-linear-gradient(to right, #000 0, #000 4px, transparent 4px, transparent 8px);
}

/* ⚡ UPS: 우측 끝 환풍구 (가운데는 워터마크가 들어감) */
.pattern-ups {
  left: auto; width: 60px; right: 20px;
  background-image: repeating-linear-gradient(to right, #111 0, #111 6px, transparent 6px, transparent 12px);
}

/* 🔋 PDU: 콘센트 구멍 느낌 */
.pattern-pdu {
  background-image: radial-gradient(circle, #000 3px, transparent 4px);
  background-size: 16px 16px;
  background-position: left center;
}

/* 🔲 블랭크 패널: 전체를 덮는 연한 사선 (투명도 1) */
.pattern-blank {
  top: 0; bottom: 0; left: 0; right: 0;
  background-image: repeating-linear-gradient(-45deg, #1c1e22 0, #1c1e22 6px, #232529 6px, #232529 12px);
  opacity: 1;
}

/* 3. UPS 전용 정비율 거대 워터마크 */
.ups-watermark {
  position: absolute;
  top: 50%; left: 50%;
  transform: translate(-50%, -50%);
  height: 80%; /* U 높이가 커져도 최대 80%까지만 예쁘게 커짐 */
  max-height: 200px;
  z-index: 1;
  pointer-events: none;
}

.device-icon-overlay {
  position: absolute;
  left: 14px;
  top: 50%;
  transform: translateY(-50%);
  width: 22px;
  height: 22px;
  /* 0.85 투명도로 존재감을 주고, 그림자로 텍스트와의 간섭 최소화 */
  opacity: 0.85;
  filter: drop-shadow(0 0 3px rgba(0,0,0,0.8));
  pointer-events: none;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 4;
}

/* SVG 내부 아이콘이 영역을 꽉 채우도록 설정 */
.device-icon-overlay :deep(svg) {
  width: 100%;
  height: 100%;
}

/* 장비명 태그 */
.device-name-tag {
  position: absolute;
  top: 50%;
  left: 56px;
  right: 56px;
  transform: translateY(-50%);
  color: #ffffff;
  font-size: 15px;
  font-weight: bold;
  /* 배경과 겹쳐도 글씨가 뚜렷하게 보이도록 진한 텍스트 그림자 추가 */
  text-shadow: 0 1px 3px rgba(0, 0, 0, 0.9), 0 0 2px rgba(0, 0, 0, 1);
  pointer-events: none;
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 5;
  letter-spacing: -0.2px;
}

.tag-text {
  display: inline-block;
  max-width: calc(100% - 44px);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  vertical-align: middle;
}

.tag-badge {
  background: rgba(0,0,0,0.5);
  padding: 2px 6px;
  border-radius: 10px;
  margin-left: 8px;
  font-size: 10px;
}

/* 액션 버튼 (호버 시에만 표시) */
.device-actions {
  /* 버튼을 장비 둥둥 띄우는 핵심 코드 (이게 빠져서 안 보였던 겁니다!) */
  position: absolute !important;
  top: 50%;
  bottom: 2px;
  left: 50%;
  transform: translate(-50%, -50%);
  z-index: 5; /* 장비나 텍스트 위로 확실히 올림 */

  /* 디자인 요소 (아까 적용한 예쁜 간격과 배경) */
  display: flex !important;
  justify-content: center;
  align-items: center;
  gap: 1px !important;
  background: rgba(0, 0, 0, 0.85) !important;
  border-radius: 6px;
  padding: 4px 8px;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.5);

  /* 기본적으로는 숨겨둠 (투명도 0, 클릭 방지) */
  opacity: 0;
  pointer-events: none;
  transition: opacity 0.2s ease;
}

/* 🟢 복구됨: 장비(부모 요소)에 마우스를 올리면 버튼 그룹이 나타남 */
/* (참고: 장비를 감싸는 클래스가 .rack-item이거나 .rack-slot일 수 있습니다.
   만약 호버가 안 먹히면 부모 클래스 이름에 맞게 수정해 주세요) */
.rack-item:hover .device-actions,
.rack-slot:hover .device-actions,
.device-container:hover .device-actions {
  opacity: 1 !important;
  pointer-events: auto !important;
}

/* 버튼 내부 스타일 및 아이콘 크기 (아까와 동일) */
.device-actions .ant-btn {
  padding: 0 4px !important;
  height: 26px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.device-actions .ant-btn :deep(.anticon) {
  font-size: 16px !important;
  color: #ffffff !important;
  text-shadow: 0 0 1px rgba(255, 255, 255, 0.6);
  transition: all 0.2s ease;
}

.device-actions .ant-btn:hover :deep(.anticon) {
  color: #40a9ff !important;
  transform: scale(1.15);
}

.device-actions .ant-btn:disabled :deep(.anticon) {
  color: rgba(255, 255, 255, 0.25) !important;
  text-shadow: none;
}

.device-actions .ant-btn {
  color: white;
}

.device-more-btn {
  width: 30px !important;
  height: 30px !important;
  border-radius: 8px !important;
  background: rgba(64, 169, 255, 0.2) !important;
}

.device-more-btn :deep(.anticon) {
  font-size: 18px !important;
}

.device-content:hover .device-actions {
  opacity: 1;
}

.host-vm-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 12px;
}

.host-vm-scroll-area {
  max-height: 52vh;
  overflow-y: auto;
  overflow-x: hidden;
  padding-right: 4px;
}

.host-vm-empty-wrap {
  min-height: 180px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.host-vm-card {
  border: 1px solid #d9e2ec;
  border-radius: 10px;
  background: #f9fbff;
  padding: 10px 12px;
}

.host-vm-card-inactive {
  background: #f3f4f6;
  border-color: #d1d5db;
}

.host-vm-card-inactive .host-vm-icon {
  color: #9ca3af;
}

.host-vm-card-inactive .host-vm-name {
  color: #6b7280;
}

.host-vm-card-inactive .host-vm-meta {
  color: #9ca3af;
}

.host-vm-icon {
  font-size: 22px;
  color: #3b82f6;
  margin-bottom: 6px;
}

.host-vm-name {
  font-weight: 700;
  color: #1f2937;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.host-vm-meta {
  margin-top: 4px;
  font-size: 12px;
  color: #6b7280;
}
</style>
