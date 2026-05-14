<template>
  <div class="p-2 rack-diagram-root" :class="{ 'is-dark': isDarkMode }">
    <div class="toolbar-container" :class="{ 'toolbar-detail': !showRackList }">

      <div class="toolbar-left">
        <a-space :wrap="false" size="small">
          <a-input-search
            v-model:value="searchQuery"
            :placeholder="t('rackDiagram.searchPlaceholder')"
            :style="{ width: showRackList ? '320px' : '240px' }"
            allow-clear
          />
        </a-space>
        <input type="file" ref="fileInput" accept=".json" style="display: none" @change="handleImport" />
      </div>

      <div class="toolbar-right" style="display: flex; align-items: center;">
        <a-space :wrap="false" size="small">
          <a-button type="primary" class="add-rack-btn" @click="openRackModal('add')">
            <PlusOutlined /> {{ t('rackDiagram.addRack') }}
          </a-button>
          <a-button type="primary" @click="saveRackData" :loading="saving" :disabled="!isDirty">
            <SaveOutlined /> {{ t('rackDiagram.save') }}
          </a-button>
          <a-button @click="exportToJson" :title="t('rackDiagram.backupJson')">
            <FileTextOutlined /> {{ t('rackDiagram.backup') }}
          </a-button>
          <a-button @click="triggerImport" :title="t('rackDiagram.restoreJson')">
            <UploadOutlined /> {{ t('rackDiagram.restore') }}
          </a-button>
          <a-button @click="exportToImage" :title="t('rackDiagram.capturePng')">
            <CameraOutlined /> {{ t('rackDiagram.capture') }}
          </a-button>
        </a-space>
      </div>

      <div v-if="!showRackList" class="toolbar-right" style="display: flex; align-items: center; margin-left: 12px;">
        <span class="zoom-label" style="margin-right: 8px;">{{ t('rackDiagram.zoom') }}:</span>

        <a-input-number
          :value="zoomPercentUi"
          @change="onZoomInputChange"
          @pressEnter="commitZoomUi"
          @blur="commitZoomUi"
          :min="40"
          :max="150"
          :formatter="value => `${value}%`"
          :parser="value => value.replace('%', '')"
          size="small"
          style="width: 70px; text-align: center; margin-right: 12px; flex-shrink: 0;"
        />

        <div style="width: 120px; flex-shrink: 0; padding-top: 4px;">
          <a-slider
            :value="zoomPercentUi"
            @change="onZoomSliderChange"
            @afterChange="commitZoomUi"
            :min="40"
            :max="150"
            :tip-formatter="null"
          />
        </div>
      </div>
    </div>
    <a-spin :spinning="loading || saving">
      <div v-if="showRackList" class="rack-list-view">
        <a-empty v-if="!parsedRacks.length" :description="t('rackDiagram.emptyRack')" style="margin-top: 50px;" />
        <div v-else class="rack-list-grid">
          <a-card
            v-for="(rack, idx) in parsedRacks"
            :key="`rack-list-${idx}`"
            hoverable
            :class="[
              'rack-list-card',
              { 'rack-list-card--matched': hasSearchQuery && isRackMatched(rack), 'rack-list-card--dimmed': hasSearchQuery && !isRackMatched(rack) }
            ]"
            @click="openRackDetail(idx)"
          >
            <div v-if="hasSearchQuery && getRackMatchCount(rack) > 0" class="rack-list-card-match">
              <a-tag color="blue">{{ t('rackDiagram.searchMatched', { count: getRackMatchCount(rack) }) }}</a-tag>
            </div>
            <div class="rack-list-card-title">{{ rack.name }}</div>
            <div class="rack-list-card-sub">{{ rack.totalHeight }}U {{ t('rackDiagram.rackUnit') }}</div>
            <div class="rack-list-card-usage">{{ t('rackDiagram.usage') }} {{ getRackUsagePercent(rack) }}%</div>
            <a-progress
              class="rack-list-progress"
              :percent="getRackUsagePercent(rack)"
              :show-info="false"
              size="small"
              :stroke-width="6"
            />
            <div class="rack-list-card-usage-detail">
              {{ getRackUsedU(rack) }}U {{ t('rackDiagram.used') }} / {{ rack.totalHeight }}U {{ t('rackDiagram.total') }}
            </div>
            <div class="rack-list-card-extra">
              {{ t('rackDiagram.free') }} {{ getRackFreeU(rack) }}U · {{ t('rackDiagram.deviceCount') }} {{ getRackDeviceCount(rack) }}{{ t('rackDiagram.countSuffix') }}
            </div>
          </a-card>
        </div>
      </div>
      <div v-else class="rack-canvas">
        <div class="rack-detail-layout" ref="rackDetailLayoutRef">
        <div class="rack-main-pane" ref="rackMainPaneRef">
        <div class="rack-zoom-wrapper" :style="zoomWrapperStyle">

          <a-empty v-if="!parsedRacks.length" :description="t('rackDiagram.emptyRack')" style="margin-top: 50px;" />

          <div class="rack-container" v-else>
            <div class="rack-wrapper" v-for="{ rack, index: rIndex } in visibleRackEntries" :key="`rack-${rIndex}`">

              <div class="rack-header">
                <div class="rack-header-inner">
                  <div class="rack-header-left">
                    <a-tooltip title="목록으로">
                      <a-button size="small" shape="circle" class="rack-back-inline-btn" @click="backToRackList">
                        <template #icon><LeftOutlined /></template>
                      </a-button>
                    </a-tooltip>
                    <a-tooltip placement="top">
                      <template #title>
                        {{ rack.name }} ({{ rack.totalHeight }}U)
                      </template>
                      <span class="rack-name-text">
                        {{ rack.name }} ({{ rack.totalHeight }}U)
                      </span>
                    </a-tooltip>
                  </div>

                  <div class="rack-header-actions">
                    <a-button size="small" type="text" @click="cloneRack(rIndex)" :title="t('rackDiagram.cloneRack')">
                      <CopyOutlined />
                    </a-button>
                    <a-button size="small" type="text" @click="openRackModal('edit', rIndex)" :title="t('rackDiagram.edit')">
                      <SettingOutlined />
                    </a-button>
                    <a-popconfirm :title="t('rackDiagram.deleteConfirm')" @confirm="deleteRack(rIndex)">
                      <a-button size="small" type="text" danger>
                        <DeleteOutlined />
                      </a-button>
                    </a-popconfirm>
                  </div>
                </div>
              </div>

              <div class="rack-body">

                <div class="rack-ruler">
                  <div
                    v-for="u in rack.totalHeight"
                    :key="u"
                    class="ruler-number"
                  >
                    {{ rack.totalHeight - u + 1 }}
                  </div>
                </div>

                <div class="rack-frame" @dragover.prevent @drop.stop="onDropRackFrame(rIndex, $event)">
                  <div
                    v-for="(item, iIndex) in rack.items"
                    :key="iIndex"
                    class="rack-item"
                    @dragover.prevent
                    :data-item-type="item.type"
                    :style="{
                      height: (item.height * RACK_UNIT_HEIGHT) + 'px',
                      opacity: isMatched(item) ? 1 : 0.2,
                      filter: isMatched(item) ? 'none' : 'grayscale(100%)'
                    }"
                  >
                    <div v-if="item.type === 'gap'" class="gap-content" @click="openDeviceModal(rIndex, iIndex)">
                      <span>+ {{ item.height }}U {{ t('rackDiagram.gapAddDevice') }}</span>
                    </div>

                    <div
                      v-else
                      class="device-content"
                      :class="`device-${item.type}`"
                      draggable="true"
                      @dragstart="onDragStart(rIndex, iIndex)"
                      @click="selectDevice(rIndex, iIndex)"
                    >

                      <div class="device-top-line" :style="{ backgroundColor: item.type === 'blank' ? 'transparent' : getIconColor(getDevicePanelType(item)) }"></div>

                      <div
                        class="device-pattern"
                        :class="`panel-${getDevicePanelType(item)}`"
                        v-html="renderDevicePanel(item)"
                      ></div>

                      <svg v-if="item.type === 'ups'" class="ups-watermark" viewBox="0 0 24 24" preserveAspectRatio="xMidYMid meet">
                        <path d="M13 2.05v9.45h4.5l-8.5 10.45v-9.45H4.5L13 2.05z" fill="rgba(255,255,255,0.03)" />
                      </svg>

                      <div
                        class="device-icon-overlay"
                        :style="{ color: getIconColor(getDevicePanelType(item)) }"
                        v-html="renderBadgeIcon(getDevicePanelType(item))"
                      ></div>

                      <div class="device-name-tag" v-if="item.type !== 'blank'">
                        <div class="tag-content">
                          <span v-if="item.type !== 'custom'" class="tag-text">{{ item.label }}</span>
                          <span v-else class="tag-text">{{ item.customType }}</span>
                          <span class="tag-badge">{{ item.height }}U</span>
                        </div>
                      </div>

                      <div class="device-actions">
                        <a-tooltip :title="t('rackDiagram.cloneDevice')" :mouseEnterDelay="0.1">
                          <a-button size="small" type="text" @click.stop="cloneItem(rIndex, iIndex)">
                            <CopyOutlined />
                          </a-button>
                        </a-tooltip>

                        <a-tooltip :title="t('rackDiagram.moveToOtherRack')" :mouseEnterDelay="0.1">
                          <a-button size="small" type="text" @click.stop="openMoveDeviceModal(rIndex, iIndex)">
                            <ExportOutlined />
                          </a-button>
                        </a-tooltip>

                        <a-tooltip :title="t('rackDiagram.settings')" :mouseEnterDelay="0.1">
                          <a-button size="small" type="text" @click.stop="openDeviceModal(rIndex, iIndex)">
                            <SettingOutlined />
                          </a-button>
                        </a-tooltip>

                        <a-dropdown v-if="hasActionMenu(item)" :trigger="['click']">
                          <a-tooltip :title="t('rackDiagram.deviceActions')" :mouseEnterDelay="0.1">
                            <a-button size="small" type="text" class="device-more-btn" @click.stop>
                              <MoreOutlined />
                            </a-button>
                          </a-tooltip>
                          <template #overlay>
                            <a-menu @click="({ key }) => handleHostActionMenu(key, item)">
                              <a-menu-item v-if="isHostLinked(item)" key="host-detail"><LinkOutlined /> {{ t('rackDiagram.hostDetail') }}</a-menu-item>
                              <a-menu-item v-if="isHostLinked(item)" key="host-vms"><UnorderedListOutlined /> {{ t('rackDiagram.hostVmList') }}</a-menu-item>
                              <a-menu-item v-if="isHostLinked(item)" key="host-oobm"><LaptopOutlined /> {{ t('rackDiagram.hostOobmPortal') }}</a-menu-item>
                              <a-menu-item v-if="isHostLinked(item)" key="host-cube"><AppstoreOutlined /> {{ t('rackDiagram.hostCubePortal') }}</a-menu-item>
                              <a-menu-divider v-if="isHostLinked(item) && hasQuickLinks(item)" />
                              <a-menu-item v-for="(link, lIdx) in getQuickLinks(item)" :key="`quick-${lIdx}`">
                                <LinkOutlined /> {{ link.label || link.url }}
                              </a-menu-item>
                            </a-menu>
                          </template>
                        </a-dropdown>

                        <a-popconfirm :title="t('rackDiagram.deleteConfirm')" @confirm="deleteItem(rIndex, iIndex)" placement="topRight">
                          <a-tooltip :title="t('rackDiagram.delete')" :mouseEnterDelay="0.1">
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
        <div
          v-if="selectedDevice"
          ref="rackSidePaneSlotRef"
          class="rack-side-pane-slot"
        >
          <div
            ref="rackSidePaneRef"
            class="rack-side-pane"
            :class="{
              'is-fixed': sidePaneMode === 'fixed',
              'is-bottom': sidePaneMode === 'bottom'
            }"
            :style="sidePaneInlineStyle"
          >
          <a-card size="small" class="rack-side-pane-card">
            <template #title>{{ t('rackDiagram.deviceInfo') }}</template>
            <template #extra>
              <a-button size="small" type="text" class="device-info-close-btn" @click="clearSelectedDevice">
                <CloseOutlined />
              </a-button>
            </template>
            <a-descriptions :column="1" size="small" bordered class="device-info-desc">
              <a-descriptions-item :label="t('rackDiagram.deviceName')">{{ selectedDevice.label || '-' }}</a-descriptions-item>
              <a-descriptions-item :label="t('rackDiagram.deviceType')">{{ selectedDevice.type || '-' }}</a-descriptions-item>
              <a-descriptions-item :label="t('rackDiagram.height')">{{ selectedDevice.height }}U</a-descriptions-item>
              <a-descriptions-item :label="t('rackDiagram.deviceMemo')">
                <div v-if="selectedDeviceMemoPlain.length || selectedDeviceMemoLinked.length" class="device-memo-wrap">
                  <div v-if="selectedDeviceMemoPlain.length" class="device-memo-plain">{{ selectedDeviceMemoPlain.join('\n') }}</div>
                  <div v-if="selectedDeviceMemoLinked.length" class="device-memo-linked">
                    <div class="device-memo-linked-list">
                      <div v-for="(line, idx) in selectedDeviceMemoLinked" :key="`memo-linked-${idx}`">{{ line }}</div>
                    </div>
                  </div>
                </div>
                <span v-else>-</span>
              </a-descriptions-item>
              <a-descriptions-item :label="t('rackDiagram.sourceRef')">{{ selectedDeviceSourceRefLabel }}</a-descriptions-item>
              <a-descriptions-item v-if="selectedDeviceHostInfo.length" :label="t('label.host')">
                <div class="device-host-info-list">
                  <div v-for="(row, idx) in selectedDeviceHostInfo" :key="`host-info-${idx}`">
                    <strong>{{ row.label }}</strong>: {{ row.value }}
                  </div>
                </div>
              </a-descriptions-item>
            </a-descriptions>
            <div class="device-info-actions">
              <a-button size="small" @click="openDeviceModal(selectedDeviceRackIndex, selectedDeviceItemIndex)">{{ t('rackDiagram.settings') }}</a-button>
              <a-popconfirm :title="t('rackDiagram.deleteConfirm')" @confirm="deleteItem(selectedDeviceRackIndex, selectedDeviceItemIndex)">
                <a-button size="small" danger>{{ t('rackDiagram.delete') }}</a-button>
              </a-popconfirm>
            </div>
          </a-card>
          </div>
        </div>
        </div>
      </div>
    </a-spin>

    <a-modal
      v-model:visible="rackModalVisible"
      :title="rackModalMode === 'add' ? t('rackDiagram.addRack') : t('rackDiagram.editRack')"
      @ok="submitRackModal"
      @cancel="closeRackModal"
      destroyOnClose
    >
      <a-form layout="vertical">
        <a-form-item :label="t('rackDiagram.rackName')">
          <a-input v-model:value="rackForm.name" :placeholder="t('rackDiagram.rackNamePlaceholder')" />
        </a-form-item>
        <a-form-item :label="t('rackDiagram.totalHeight')">
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
      :title="t('rackDiagram.moveDeviceTitle')"
      @ok="submitMoveDevice"
      destroyOnClose
    >
      <div style="margin-bottom: 16px;">
        <InfoCircleOutlined style="color: #1890ff; margin-right: 8px;" />
        <span>{{ t('rackDiagram.moveDeviceDesc1') }} <strong>{{ t('rackDiagram.moveDeviceDesc2') }}</strong>{{ t('rackDiagram.moveDeviceDesc3') }}</span>
      </div>

      <a-form layout="vertical">
        <a-form-item :label="t('rackDiagram.targetRack')">
          <a-select v-model:value="moveTargetRackIndex" :placeholder="t('rackDiagram.selectRack')" style="width: 100%">
            <a-select-option
              v-for="(rack, idx) in parsedRacks"
              :key="idx"
              :value="idx"
              :disabled="idx === moveSourceInfo.rIndex"
            >
              {{ rack.name }} ({{ t('rackDiagram.needGapCheck') }})
            </a-select-option>
          </a-select>
        </a-form-item>
      </a-form>
    </a-modal>
    <a-modal
      v-model:visible="deviceModalVisible"
      :title="t('rackDiagram.deviceConfig')"
      @ok="submitDeviceModal"
      @cancel="closeDeviceModal"
      destroyOnClose
    >
      <a-form layout="vertical">
        <a-form-item :label="t('rackDiagram.deviceType')">
          <a-select v-model:value="deviceForm.type" style="width: 100%" @change="handleTypeChange">
            <a-select-option value="server">{{ t('rackDiagram.deviceTypeServer') }}</a-select-option>
            <a-select-option value="blade">{{ t('rackDiagram.deviceTypeBlade') }}</a-select-option>
            <a-select-option value="switch">{{ t('rackDiagram.deviceTypeSwitch') }}</a-select-option>
            <a-select-option value="router">{{ t('rackDiagram.deviceTypeRouter') }}</a-select-option>
            <a-select-option value="loadbalancer">{{ t('rackDiagram.deviceTypeLoadBalancer') }}</a-select-option>
            <a-select-option value="storage">{{ t('rackDiagram.deviceTypeStorage') }}</a-select-option>
            <a-select-option value="nas">{{ t('rackDiagram.deviceTypeNas') }}</a-select-option>
            <a-select-option value="firewall">{{ t('rackDiagram.deviceTypeFirewall') }}</a-select-option>
            <a-select-option value="monitoring">{{ t('rackDiagram.deviceTypeMonitoring') }}</a-select-option>
            <a-select-option value="kvm">{{ t('rackDiagram.deviceTypeKvm') }}</a-select-option>
            <a-select-option value="cooling">{{ t('rackDiagram.deviceTypeCooling') }}</a-select-option>
            <a-select-option value="ups">UPS</a-select-option>
            <a-select-option value="patch">{{ t('rackDiagram.deviceTypePatch') }}</a-select-option>
            <a-select-option value="pdu">PDU</a-select-option>
            <a-select-option value="blank">{{ t('rackDiagram.deviceTypeBlank') }}</a-select-option>
            <a-select-option value="custom">{{ t('rackDiagram.deviceTypeCustom') }}</a-select-option>
          </a-select>
        </a-form-item>

        <a-form-item :label="t('rackDiagram.deviceName')" v-if="deviceForm.type !== 'blank'">
          <a-input
            v-model:value="deviceForm.label"
            :placeholder="t('rackDiagram.deviceNamePlaceholder')"
            allow-clear
          />
        </a-form-item>

        <a-form-item :label="t('rackDiagram.selectInfraAsset')" v-if="deviceForm.type === 'server'">
          <a-select
            v-model:value="deviceForm.sourceRef"
            :loading="inventoryLoading"
            :options="inventoryOptions"
            :show-search="true"
            option-filter-prop="label"
            :placeholder="t('rackDiagram.selectHost')"
            allow-clear
            style="width: 100%"
            @change="handleSourceChange"
          />
        </a-form-item>

        <a-form-item :label="t('rackDiagram.customTypeName')" v-if="deviceForm.type === 'custom'">
          <a-input v-model:value="deviceForm.customType" :placeholder="t('rackDiagram.customTypePlaceholder')" />
        </a-form-item>

        <a-form-item :label="t('rackDiagram.height')">
        <a-input-number
          v-model:value="deviceForm.height"
          :min="1"
          :max="maxAllowedHeight"
          :precision="0"
          style="width: 100%"
        />
        <div style="font-size: 12px; color: #888; margin-top: 4px;">
          <InfoCircleOutlined style="margin-right: 4px;" />
          {{ t('rackDiagram.maxAllowedHeightPrefix') }} <strong>{{ maxAllowedHeight }}U</strong> {{ t('rackDiagram.maxAllowedHeightSuffix') }}
        </div>
      </a-form-item>

        <a-form-item :label="t('rackDiagram.deviceMemo')">
          <a-textarea
            v-model:value="deviceForm.memo"
            :rows="3"
            :placeholder="t('rackDiagram.deviceMemoPlaceholder')"
          />
        </a-form-item>
        <a-form-item
          :label="t('rackDiagram.quickLinks')"
          :validateStatus="quickLinksError ? 'error' : ''"
          :help="quickLinksError"
        >
          <a-textarea
            v-model:value="deviceForm.quickLinksText"
            :rows="3"
            :placeholder="t('rackDiagram.quickLinksPlaceholder')"
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
            <p class="ant-empty-description">{{ t('label.no.data') }}</p>
          </div>
        </div>
        <div v-else-if="!hostVmLoading" class="host-vm-grid">
          <div
            v-for="vm in filteredHostVmList"
            :key="vm.id"
            class="host-vm-card"
            :class="{ 'host-vm-card-inactive': !isRunningVm(vm) }"
            @click="goToVmDetail(vm)"
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
import { ref, reactive, onMounted, computed, watch, onBeforeUnmount, nextTick, getCurrentInstance } from 'vue'
import { message } from 'ant-design-vue'
import html2canvas from 'html2canvas'
import { api } from '@/api'
import { useRouter } from 'vue-router'
import { useStore } from 'vuex'
import {
  CopyOutlined,
  SettingOutlined,
  DeleteOutlined,
  InfoCircleOutlined,
  LeftOutlined,
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
  UnorderedListOutlined,
  CloseOutlined
} from '@ant-design/icons-vue'

// 전역 상태
const loading = ref(false)
const saving = ref(false)
const zoomLevel = ref(0.65)
const AUTO_ZOOM_MIN = 0.65
const AUTO_ZOOM_MAX = 1.0
const isAutoZoomEnabled = ref(true)
const rackMainPaneRef = ref(null)
const rackDetailLayoutRef = ref(null)
const rackSidePaneSlotRef = ref(null)
const rackSidePaneRef = ref(null)
const sidePaneMode = ref('static') // static | fixed | bottom
const sidePaneLeft = ref(0)
const sidePaneWidth = ref(0)
const SIDE_PANE_TOP = 64
let resizeDebounceTimer = null
let zoomRafId = 0
let zoomApplyRafId = 0
const router = useRouter()
const store = useStore()
const { proxy } = getCurrentInstance()
const t = (key, args) => proxy?.$t ? proxy.$t(key, args) : key
const isDarkMode = computed(() => !!store.getters.darkMode)
const RACK_UNIT_HEIGHT = 52

// 슬라이더 및 입력창과 연동할 퍼센트 단위 변수
const zoomPercent = computed({
  get: () => Math.round(zoomLevel.value * 100),
  set: (val) => {
    isAutoZoomEnabled.value = false
    // 최소 40% ~ 최대 150% 범위 제한
    if (val < 40) val = 40
    if (val > 150) val = 150
    zoomLevel.value = val / 100
  }
})
const zoomPercentUi = ref(zoomPercent.value)

const clampZoomPercent = (val) => {
  let next = Number(val || 0)
  if (!Number.isFinite(next)) next = zoomPercent.value
  if (next < 40) next = 40
  if (next > 150) next = 150
  return Math.round(next)
}

const scheduleZoomApply = (percent) => {
  if (zoomApplyRafId) cancelAnimationFrame(zoomApplyRafId)
  zoomApplyRafId = requestAnimationFrame(() => {
    zoomPercent.value = percent
  })
}

const onZoomSliderChange = (val) => {
  zoomPercentUi.value = clampZoomPercent(val)
  scheduleZoomApply(zoomPercentUi.value)
}

const onZoomInputChange = (val) => {
  zoomPercentUi.value = clampZoomPercent(val)
}

const commitZoomUi = () => {
  zoomPercentUi.value = clampZoomPercent(zoomPercentUi.value)
  zoomPercent.value = zoomPercentUi.value
}

const zoomWrapperStyle = computed(() => {
  const z = Number(zoomLevel.value || 1)
  return {
    zoom: z
  }
})

const sidePaneInlineStyle = computed(() => {
  if (sidePaneMode.value === 'fixed') {
    return {
      left: `${Math.round(sidePaneLeft.value)}px`,
      top: `${SIDE_PANE_TOP}px`,
      width: `${Math.round(sidePaneWidth.value)}px`
    }
  }
  if (sidePaneMode.value === 'bottom') {
    return {
      width: `${Math.round(sidePaneWidth.value)}px`
    }
  }
  return {}
})

// 데이터 모델
const parsedRacks = ref([])
const showRackList = ref(true)
const selectedRackIndex = ref(-1)
const selectedDeviceRackIndex = ref(-1)
const selectedDeviceItemIndex = ref(-1)
const visibleRackEntries = computed(() => {
  if (selectedRackIndex.value > -1 && parsedRacks.value[selectedRackIndex.value]) {
    return [{ rack: parsedRacks.value[selectedRackIndex.value], index: selectedRackIndex.value }]
  }
  return parsedRacks.value.map((rack, index) => ({ rack, index }))
})
const selectedDevice = computed(() => {
  if (selectedDeviceRackIndex.value < 0 || selectedDeviceItemIndex.value < 0) return null
  const rack = parsedRacks.value[selectedDeviceRackIndex.value]
  const item = rack?.items?.[selectedDeviceItemIndex.value]
  if (!item || item.type === 'gap') return null
  return item
})
const selectedDeviceHost = ref(null)
const selectedDeviceMemoPlain = computed(() => {
  const memo = String(selectedDevice.value?.memo || '')
  const marker = '[LinkedAsset]'
  return memo
    .split('\n')
    .map(line => line.trim())
    .filter(line => line && !line.startsWith(marker))
})
const selectedDeviceMemoLinked = computed(() => {
  const memo = String(selectedDevice.value?.memo || '')
  const marker = '[LinkedAsset]'
  return memo
    .split('\n')
    .map(line => line.trim())
    .filter(line => line.startsWith(marker))
    .map(line => line.replace(marker, '').trim())
})
const selectedDeviceSourceRefLabel = computed(() => {
  const sourceRef = String(selectedDevice.value?.sourceRef || '')
  if (!sourceRef) return '-'
  if (!sourceRef.startsWith('host:')) return sourceRef
  const hostId = sourceRef.split(':')[1] || ''
  const host = selectedDeviceHost.value
  if (host && String(host.id) === hostId) {
    return host.name || host.hostname || hostId
  }
  return hostId || sourceRef
})
const selectedDeviceHostInfo = computed(() => {
  const host = selectedDeviceHost.value
  if (!host) return []
  const toStateLabel = (state) => {
    const s = String(state || '').toLowerCase()
    if (s === 'up' || s === 'running') return t('label.running')
    if (s === 'enabled' || s === 'normal') return t('label.enabled')
    if (s === 'disabled') return t('label.disabled')
    return state || '-'
  }
  const toPowerLabel = (state) => {
    const s = String(state || '').toLowerCase()
    if (!s) return '-'
    if (s === 'disabled' || s === 'off' || s === 'inactive') return t('label.disabled')
    if (s === 'enabled' || s === 'on' || s === 'active') return t('label.enabled')
    return state
  }
  const toHaLabel = (v) => {
    const s = String(v ?? '').toLowerCase()
    if (s === 'true' || s === 'enabled' || s === 'yes' || s === '1') return t('label.enabled')
    if (s === 'false' || s === 'disabled' || s === 'no' || s === '0') return t('label.disabled')
    return '-'
  }

  const powerRaw = host?.powerstate || host?.outofbandmanagement?.powerstate || host?.details?.powerstate
  const haRaw = host?.haenable ?? host?.haenabled ?? host?.hahost ?? host?.details?.haenable ?? host?.details?.haenabled ?? host?.details?.hahost
  return [
    { label: t('label.ip'), value: host.ipaddress || '-' },
    { label: t('label.state'), value: toStateLabel(host.state) },
    { label: t('label.resourcestate'), value: toStateLabel(host.resourcestate) },
    { label: t('label.powerstate'), value: toPowerLabel(powerRaw) },
    { label: t('label.ha'), value: toHaLabel(haRaw) },
    { label: t('label.hypervisor'), value: host.hypervisor || '-' }
  ]
})

const updateSidePanePosition = () => {
  const layout = rackDetailLayoutRef.value
  const slot = rackSidePaneSlotRef.value
  const pane = rackSidePaneRef.value
  if (!layout || !slot || !pane || showRackList.value || !selectedDevice.value) {
    sidePaneMode.value = 'static'
    return
  }

  const layoutRect = layout.getBoundingClientRect()
  const slotRect = slot.getBoundingClientRect()
  const paneHeight = pane.offsetHeight
  const limitBottom = layoutRect.bottom - SIDE_PANE_TOP

  sidePaneWidth.value = slotRect.width

  if (layoutRect.top > SIDE_PANE_TOP) {
    sidePaneMode.value = 'static'
    return
  }

  if (limitBottom <= paneHeight) {
    sidePaneMode.value = 'bottom'
    return
  }

  sidePaneLeft.value = slotRect.left
  sidePaneMode.value = 'fixed'
}
// 데이터 변경 여부 추적
const isDirty = ref(false)

// parsedRacks 객체가 깊은 곳까지 변경되는지 감시
watch(parsedRacks, () => {
  isDirty.value = true
}, { deep: true })

watch(zoomPercent, (val) => {
  zoomPercentUi.value = clampZoomPercent(val)
})

watch(selectedDevice, async (device) => {
  nextTick(() => updateSidePanePosition())
  selectedDeviceHost.value = null
  const sourceRef = String(device?.sourceRef || '')
  if (!sourceRef.startsWith('host:')) return
  const hostId = sourceRef.split(':')[1] || ''
  if (!hostId) return
  try {
    const host = await fetchHostById(hostId)
    selectedDeviceHost.value = host || null
  } catch (e) {
    selectedDeviceHost.value = null
  }
})

watch(showRackList, () => nextTick(() => updateSidePanePosition()))

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
        message.warning(t('rackDiagram.msg.noAvailableZone'))
      }
    }
  }).catch(error => {
    console.error('Zone 목록 조회 실패:', error)
    message.error(t('rackDiagram.msg.zoneLoadFailed'))
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
      message.success(t('rackDiagram.msg.rackLoaded'))
    } else {
      // DB에 데이터가 전혀 없는 경우 (최초 접속) -> 기본 빈 랙 생성
      parsedRacks.value = [{
        name: t('rackDiagram.defaultRackName'),
        totalHeight: 42,
        items: [{ type: 'gap', height: 42 }]
      }]
    }
    // 데이터를 방금 불러왔으므로 미저장 상태(isDirty) 초기화 (빈 줄 삭제함)
    nextTick(() => { isDirty.value = false })
  }).catch(error => {
    console.error('랙 데이터 불러오기 실패:', error)
    message.error(t('rackDiagram.msg.rackLoadFailed'))
  }).finally(() => {
    loading.value = false
  })
}

const saveRackData = () => {
  if (!currentZoneId.value) {
    message.error(t('rackDiagram.msg.noZoneToSave'))
    return
  }

  saving.value = true
  const jsonContent = JSON.stringify(parsedRacks.value)

  api('updateRackLayout', {
    zoneid: currentZoneId.value,
    name: 'default',
    content: jsonContent
  }).then(json => {
    message.success(t('rackDiagram.msg.rackSaved'))
    isDirty.value = false // 저장 성공 시 변경 상태 뱃지 숨김
  }).catch(error => {
    console.error('랙 데이터 저장 실패:', error)
    message.error(t('rackDiagram.msg.rackSaveFailed'))
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
    message.warning(t('rackDiagram.msg.noRackToSaveImage'))
    return
  }

  try {
    message.loading({ content: t('rackDiagram.msg.exportingImage'), key: 'exporting' })
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

    message.success({ content: t('rackDiagram.msg.imageSaved'), key: 'exporting' })
  } catch (error) {
    console.error(error)
    message.error({ content: t('rackDiagram.msg.imageSaveFailed'), key: 'exporting' })
  }
}

// 파일 Input 요소를 참조하기 위한 ref
const fileInput = ref(null)

// JSON으로 내보내기 (Export)
const exportToJson = () => {
  if (!parsedRacks.value || parsedRacks.value.length === 0) {
    message.warning(t('rackDiagram.msg.noRackToExport'))
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
  message.success(t('rackDiagram.msg.jsonDownloaded'))
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
        message.success(t('rackDiagram.msg.importSuccess'))
      } else {
        message.error(t('rackDiagram.msg.invalidFileFormat'))
      }
    } catch (error) {
      console.error(error)
      message.error(t('rackDiagram.msg.jsonParseFailed'))
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
    name: `${targetRack.name} (${t('rackDiagram.copySuffix')})`,
    items: targetRack.items.map(item => ({ ...item }))
  }
  parsedRacks.value.push(newRack)
  message.success(t('rackDiagram.msg.rackCloned'))
}

// 랙 모달 저장 로직
const submitRackModal = () => {
  const rackName = String(rackForm.name || '').trim()
  if (!rackName) {
    message.warning(t('rackDiagram.msg.enterRackName'))
    return
  }
  if (rackName.length > 60) {
    message.warning(t('rackDiagram.msg.rackNameMax'))
    return
  }

  if (!Number.isInteger(rackForm.totalHeight) || rackForm.totalHeight < 10 || rackForm.totalHeight > 50) {
    message.warning(t('rackDiagram.msg.rackHeightRange'))
    return
  }

  const duplicateName = parsedRacks.value.some((rack, idx) => {
    if (rackModalMode.value === 'edit' && idx === targetRackIndex.value) return false
    return String(rack.name || '').trim().toLowerCase() === rackName.toLowerCase()
  })
  if (duplicateName) {
    message.warning(t('rackDiagram.msg.duplicateRackName'))
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
  if (selectedRackIndex.value === index) {
    selectedRackIndex.value = -1
    showRackList.value = true
  } else if (selectedRackIndex.value > index) {
    selectedRackIndex.value -= 1
  }
  if (!parsedRacks.value.length) showRackList.value = true
}

const openRackDetail = (index) => {
  if (index < 0 || index >= parsedRacks.value.length) return
  selectedRackIndex.value = index
  selectedDeviceRackIndex.value = -1
  selectedDeviceItemIndex.value = -1
  showRackList.value = false
  isAutoZoomEnabled.value = true
  nextTick(() => applyResponsiveZoom(true))
}

const backToRackList = () => {
  selectedRackIndex.value = -1
  selectedDeviceRackIndex.value = -1
  selectedDeviceItemIndex.value = -1
  showRackList.value = true
}

const applyResponsiveZoom = (force = false) => {
  if (showRackList.value) return
  if (!force && !isAutoZoomEnabled.value) return

  const mainPaneWidth = rackMainPaneRef.value?.clientWidth || 0
  if (!mainPaneWidth) return

  // 랙 1개 상세 기준의 대략적 기준폭(헤더/스크롤 여유 포함)
  const baseWidth = 1280
  const ratio = mainPaneWidth / baseWidth
  const nextZoom = Math.max(AUTO_ZOOM_MIN, Math.min(AUTO_ZOOM_MAX, Number(ratio.toFixed(2))))
  zoomLevel.value = nextZoom
}

const handleResponsiveResize = () => {
  if (resizeDebounceTimer) clearTimeout(resizeDebounceTimer)
  resizeDebounceTimer = setTimeout(() => {
    if (zoomRafId) cancelAnimationFrame(zoomRafId)
    zoomRafId = requestAnimationFrame(() => {
      applyResponsiveZoom(false)
      updateSidePanePosition()
    })
  }, 120)
}

const selectDevice = (rIndex, iIndex) => {
  selectedDeviceRackIndex.value = rIndex
  selectedDeviceItemIndex.value = iIndex
}

const clearSelectedDevice = () => {
  selectedDeviceRackIndex.value = -1
  selectedDeviceItemIndex.value = -1
}

const getRackUsedU = (rack) => {
  if (!rack?.items?.length) return 0
  return rack.items
    .filter(item => item.type !== 'gap')
    .reduce((sum, item) => sum + Number(item.height || 0), 0)
}
const getRackFreeU = (rack) => Math.max(0, Number(rack?.totalHeight || 0) - getRackUsedU(rack))
const getRackDeviceCount = (rack) => (rack?.items || []).filter(item => item.type !== 'gap').length
const getRackUsagePercent = (rack) => {
  const total = Number(rack?.totalHeight || 0)
  if (!total) return 0
  return Math.round((getRackUsedU(rack) / total) * 100)
}
const searchQuery = ref('')
const hasSearchQuery = computed(() => !!searchQuery.value?.trim())

const getRackMatchCount = (rack) => {
  const query = searchQuery.value?.trim().toLowerCase()
  if (!query) return 0

  let count = 0
  if ((rack?.name || '').toLowerCase().includes(query)) count += 1

  for (const item of (rack?.items || [])) {
    if (item?.type === 'gap') continue
    const target = `${item?.label || ''} ${item?.memo || ''} ${item?.sourceRef || ''}`.toLowerCase()
    if (target.includes(query)) count += 1
  }
  return count
}

const isRackMatched = (rack) => {
  const query = searchQuery.value?.trim().toLowerCase()
  if (!query) return true
  if ((rack?.name || '').toLowerCase().includes(query)) return true

  return (rack?.items || []).some((item) => {
    if (item?.type === 'gap') return false
    const target = `${item?.label || ''} ${item?.memo || ''} ${item?.sourceRef || ''}`.toLowerCase()
    return target.includes(query)
  })
}

const isMatched = (item) => {
  const query = searchQuery.value?.trim().toLowerCase()
  if (!query) return true
  if (item?.type === 'gap') return true
  const target = `${item?.label || ''} ${item?.memo || ''} ${item?.sourceRef || ''}`.toLowerCase()
  return target.includes(query)
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
const hostVmModalTitle = ref(t('rackDiagram.hostVmList'))
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

  if (!deviceForm.label || Object.values(defaultLabelKeys).map(key => t(key)).includes(deviceForm.label)) {
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
      errors.push(t('rackDiagram.msg.quickLinkUrlEmpty', { line: idx + 1 }))
      return
    }
    if (!/^https?:\/\//i.test(url)) {
      errors.push(t('rackDiagram.msg.quickLinkUrlInvalid', { line: idx + 1 }))
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
  const rackHeight = rack?.totalHeight || rack.items.reduce((s, i) => s + i.height, 0)
  const unitHeight = rect.height / rackHeight
  const raw = Math.floor(y / unitHeight)
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
    message.warning(t('rackDiagram.msg.notEnoughGapOnTarget', { height: moving.height }))
    // 복구
    const restore = findBestStartUInRack(sourceRack, moving.height, 0)
    if (restore) placeDeviceAtStartU(sourceRack, restore.index, restore.startU, moving)
    return
  }

  const placed = placeDeviceAtStartU(targetRack, best.index, best.startU, moving)
  if (!placed) {
    message.warning(t('rackDiagram.msg.dropCalcFailed'))
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
      message.warning(t('rackDiagram.msg.oobmNotFound'))
      return
    }
    window.open(`${protocol}://${address}:${port}`, '_blank')
  } catch (e) {
    message.error(t('rackDiagram.msg.oobmLoadFailed'))
  }
}

const openLinkedHostCube = async (item) => {
  const hostId = getLinkedHostId(item)
  if (!hostId) return
  try {
    const host = await fetchHostById(hostId)
    const ip = host?.ipaddress
    if (!ip) {
      message.warning(t('rackDiagram.msg.hostIpNotFound'))
      return
    }
    window.open(`https://${ip}:9090`, '_blank')
  } catch (e) {
    message.error(t('rackDiagram.msg.cubeLoadFailed'))
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
    hostVmModalTitle.value = `${t('rackDiagram.hostVmList')} - ${host?.name || host?.hostname || hostId}`
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

const goToVmDetail = (vm) => {
  const vmId = vm?.id
  if (!vmId) return
  hostVmModalVisible.value = false
  router.push({ path: `/vm/${vmId}` })
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
const defaultLabelKeys = {
  server: 'rackDiagram.defaultLabelServer',
  blade: 'rackDiagram.defaultLabelBlade',
  switch: 'rackDiagram.defaultLabelSwitch',
  router: 'rackDiagram.defaultLabelRouter',
  loadbalancer: 'rackDiagram.defaultLabelLoadBalancer',
  storage: 'rackDiagram.defaultLabelStorage',
  nas: 'rackDiagram.defaultLabelNas',
  firewall: 'rackDiagram.defaultLabelFirewall',
  monitoring: 'rackDiagram.defaultLabelMonitoring',
  kvm: 'rackDiagram.defaultLabelKvm',
  cooling: 'rackDiagram.defaultLabelCooling',
  patch: 'rackDiagram.defaultLabelPatch',
  pdu: 'rackDiagram.defaultLabelPdu',
  ups: 'rackDiagram.defaultLabelUps',
  blank: 'rackDiagram.defaultLabelBlank',
  custom: 'rackDiagram.defaultLabelCustom'
}
const getDefaultLabel = (type) => t(defaultLabelKeys[type] || 'rackDiagram.defaultLabelCustom')

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
  const isLabelDefault = Object.values(defaultLabelKeys).map(key => t(key)).includes(deviceForm.label)

  if (isLabelEmpty || isLabelDefault) {
    deviceForm.label = getDefaultLabel(newType) || ''
  }
  if (newType !== 'server') {
    deviceForm.sourceRef = undefined
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
  const finalLabel = rawLabel || getDefaultLabel(deviceForm.type) || t('rackDiagram.newDevice')
  const finalCustomType = rawCustomType || (deviceForm.type === 'custom' ? t('rackDiagram.customUnit') : '')

  if (deviceForm.type !== 'blank' && !finalLabel) {
    message.warning(t('rackDiagram.msg.enterDeviceName'))
    return
  }
  if (finalLabel.length > 60) {
    message.warning(t('rackDiagram.msg.deviceNameMax'))
    return
  }
  if (deviceForm.type === 'custom' && !rawCustomType) {
    message.warning(t('rackDiagram.msg.enterCustomType'))
    return
  }
  if (deviceForm.type === 'custom' && rawCustomType.length > 60) {
    message.warning(t('rackDiagram.msg.customTypeMax'))
    return
  }
  if (!Number.isInteger(deviceForm.height) || deviceForm.height <= 0) {
    message.warning(t('rackDiagram.msg.deviceHeightInteger'))
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
      message.error(t('rackDiagram.msg.deviceLargerThanGap'))
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
        message.error(t('rackDiagram.msg.notEnoughGapBelow'))
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

  message.error(t('rackDiagram.msg.notEnoughSpaceInRack', { height: neededHeight }))
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

  if (selectedDeviceRackIndex.value === rIndex && selectedDeviceItemIndex.value === iIndex) {
    selectedDeviceRackIndex.value = -1
    selectedDeviceItemIndex.value = -1
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
    message.warning(t('rackDiagram.msg.noOtherRackToMove'))
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
    message.warning(t('rackDiagram.msg.selectTargetRack'))
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
    message.error(t('rackDiagram.msg.targetRackNoSpace', { rack: targetRack.name, height: deviceToMove.height }))
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

  message.success(t('rackDiagram.msg.deviceMoved', { label: deviceToMove.label, rack: targetRack.name }))
  moveModalVisible.value = false
}

// 종류별 아이콘 정의
const getDeviceIcon = (type) => {
  const icons = {
    // 내부가 더 꽉 차 보이는 Solid/Bold 스타일 SVG
    server: `<svg viewBox="0 0 24 24" fill="currentColor" stroke="none"><path d="M20 10H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2v4a2 2 0 0 1-2 2zm-10-5h2v2h-2V5zm6 0h2v2h-2V5zM20 22H4a2 2 0 0 1-2-2v-4a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2v4a2 2 0 0 1-2 2zm-10-5h2v2h-2v-2zm6 0h2v2h-2v-2z"/></svg>`,
    switch: `<svg viewBox="0 0 24 24" fill="currentColor" stroke="none"><rect x="2" y="5" width="20" height="14" rx="2"/><circle cx="6" cy="12" r="1.5" fill="black"/><circle cx="12" cy="12" r="1.5" fill="black"/><circle cx="18" cy="12" r="1.5" fill="black"/></svg>`,
    storage: `<svg viewBox="0 0 24 24" fill="currentColor" stroke="none"><path d="M12 2C6.48 2 2 3.8 2 6v12c0 2.2 4.48 4 10 4s10-1.8 10-4V6c0-2.2-4.48-4-10-4zm0 18c-4.41 0-8-1.34-8-3s3.59-3 8-3 8 1.34 8 3-3.59 3-8 3zm0-10c-4.41 0-8-1.34-8-3s3.59-3 8-3 8 1.34 8 3-3.59 3-8 3z"/></svg>`,
    blade: `<svg viewBox="0 0 24 24" fill="currentColor"><rect x="3" y="4" width="4" height="16" rx="1"/><rect x="9" y="4" width="4" height="16" rx="1"/><rect x="15" y="4" width="4" height="16" rx="1"/></svg>`,
    firewall: `<svg viewBox="0 0 24 24" fill="currentColor" stroke="none"><path d="M12 1L3 5v6c0 5.55 3.84 10.74 9 12 5.16-1.26 9-6.45 9-12V5l-9-4z"/></svg>`,
    router: `<svg viewBox="0 0 24 24" fill="currentColor"><path d="M12 3l4 4h-3v4h-2V7H8l4-4zM5 13h14a2 2 0 012 2v3a2 2 0 01-2 2H5a2 2 0 01-2-2v-3a2 2 0 012-2zm2 3a1 1 0 100 2 1 1 0 000-2z"/></svg>`,
    loadbalancer: `<svg viewBox="0 0 24 24" fill="currentColor"><path d="M5 5h4v4H5V5zm10 0h4v4h-4V5zM5 15h4v4H5v-4zm7-7l3 3h-2v2h2l-3 3-3-3h2v-2H9l3-3z"/></svg>`,
    nas: `<svg viewBox="0 0 24 24" fill="currentColor"><rect x="4" y="3" width="16" height="18" rx="2"/><rect x="7" y="6" width="10" height="3" fill="white" opacity=".45"/><circle cx="9" cy="16" r="1.3" fill="white" opacity=".55"/><circle cx="15" cy="16" r="1.3" fill="white" opacity=".55"/></svg>`,
    monitoring: `<svg viewBox="0 0 24 24" fill="currentColor"><path d="M3 5h18v12H3V5zm3 7h3l2-4 3 7 2-3h2v-2h-3l-1 1.5L11 5 8 10H6v2zm3 7h6v2H9v-2z"/></svg>`,
    kvm: `<svg viewBox="0 0 24 24" fill="currentColor"><rect x="3" y="4" width="18" height="11" rx="1"/><rect x="6" y="17" width="12" height="2" rx="1"/><rect x="8" y="20" width="8" height="1"/></svg>`,
    ups: `<svg viewBox="0 0 24 24" fill="currentColor" stroke="none"><path d="M13 2.05v9.45h4.5l-8.5 10.45v-9.45H4.5L13 2.05z"/></svg>`,
    patch: `<svg viewBox="0 0 24 24" fill="currentColor" stroke="none"><path d="M22 7H2v10h20V7zm-14 6H4v-2h4v2zm6 0h-4v-2h4v2zm6 0h-4v-2h4v2z"/></svg>`,
    pdu: `<svg viewBox="0 0 24 24" fill="currentColor" stroke="none"><rect x="7" y="2" width="10" height="20" rx="2"/><circle cx="12" cy="6" r="1.5" fill="black"/><circle cx="12" cy="12" r="1.5" fill="black"/><circle cx="12" cy="18" r="1.5" fill="black"/></svg>`,
    cooling: `<svg viewBox="0 0 24 24" fill="currentColor"><circle cx="12" cy="12" r="3"/><path d="M12 2a5 5 0 014 8c-2 0-4-1-5-3-.6-1.4-.2-3.2 1-5zM22 12a5 5 0 01-8 4c0-2 1-4 3-5 1.4-.6 3.2-.2 5 1zM12 22a5 5 0 01-4-8c2 0 4 1 5 3 .6 1.4.2 3.2-1 5zM2 12a5 5 0 018-4c0 2-1 4-3 5-1.4.6-3.2.2-5-1z"/></svg>`,
    blank: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><rect x="4" y="7" width="16" height="10" rx="2"/><line x1="7" y1="12" x2="17" y2="12"/></svg>`,
    custom: `<svg viewBox="0 0 24 24" fill="currentColor" stroke="none"><path d="M12 2L1 21h22L12 2zm0 4.19L19.53 19H4.47L12 6.19z"/></svg>`
  }
  return icons[type] || icons.custom
}

const renderBadgeIcon = (type) => getDeviceIcon(type)

const getDevicePanelType = (item) => {
  const text = `${item?.type || ''} ${item?.customType || ''} ${item?.label || ''}`.toLowerCase()
  if (text.includes('blade') || text.includes('블레이드')) return 'blade'
  if (text.includes('router') || text.includes('라우터')) return 'router'
  if (text.includes('load') || text.includes('balance') || text.includes('로드')) return 'loadbalancer'
  if (text.includes('nas')) return 'nas'
  if (text.includes('monitor') || text.includes('모니터')) return 'monitoring'
  if (text.includes('kvm') || text.includes('console') || text.includes('콘솔')) return 'kvm'
  if (text.includes('cool') || text.includes('fan') || text.includes('쿨링')) return 'cooling'
  if (text.includes('patch') || text.includes('패치')) return 'patch'
  if (text.includes('firewall') || text.includes('방화벽')) return 'firewall'
  if (text.includes('switch') || text.includes('스위치')) return 'switch'
  if (text.includes('storage') || text.includes('스토리지')) return 'storage'
  if (text.includes('pdu')) return 'pdu'
  if (text.includes('ups')) return 'ups'
  if (text.includes('server') || text.includes('서버') || text.includes('host') || text.includes('agent')) return 'server'
  return item?.type || 'custom'
}

const PANEL_VIEW_WIDTH = 760
const PANEL_UNIT_HEIGHT = RACK_UNIT_HEIGHT
const PANEL_GAP = 8
const PANEL_PAD_X = 12
const PANEL_PAD_Y = 6
const DETAIL = {
  fill: 'detail-fill',
  mid: 'detail-mid',
  dark: 'detail-dark',
  line: 'detail-line'
}

const n = (value) => Number(value.toFixed(2))
const svgRect = (x, y, w, h, cls = DETAIL.mid, rx = 2) => `<rect x="${n(x)}" y="${n(y)}" width="${n(w)}" height="${n(h)}" rx="${rx}" class="${cls}" />`
const svgCircle = (cx, cy, r, cls = DETAIL.dark) => `<circle cx="${n(cx)}" cy="${n(cy)}" r="${n(r)}" class="${cls}" />`
const svgLine = (x1, y1, x2, y2, cls = DETAIL.line) => `<line x1="${n(x1)}" y1="${n(y1)}" x2="${n(x2)}" y2="${n(y2)}" class="${cls}" />`
const svgPolyline = (points, cls = DETAIL.line) => `<polyline points="${points.map(([x, y]) => `${n(x)},${n(y)}`).join(' ')}" class="${cls}" />`
const repeat = (count, renderer) => Array.from({ length: count }, (_, index) => renderer(index)).join('')

const svgPort = (x, y, w = 16, h = 12) => `${svgRect(x, y, w, h, 'port-shell', 1.5)}${svgRect(x + 3, y + 3, w - 6, h - 5, 'port-hole', 0.5)}`
const svgOutlet = (x, y, w = 28, h = 22) => `<g>${svgRect(x, y, w, h, 'outlet-shell', 4)}${svgRect(x + w * 0.32, y + h * 0.28, 3, h * 0.44, 'port-hole', 1)}${svgRect(x + w * 0.58, y + h * 0.28, 3, h * 0.44, 'port-hole', 1)}</g>`
const svgPlug = (x, y, w = 34, h = 28) => {
  const cut = Math.min(4.5, w * 0.12)
  const points = [
    [x + cut, y],
    [x + w - cut, y],
    [x + w, y + cut],
    [x + w, y + h - cut],
    [x + w - cut, y + h],
    [x + cut, y + h],
    [x, y + h - cut],
    [x, y + cut]
  ].map(([px, py]) => `${n(px)},${n(py)}`).join(' ')
  const slotW = Math.max(1.8, w * 0.08)
  const slotH = Math.max(7, h * 0.3)
  const slotY = y + h * 0.3
  const slot1X = x + w * 0.36
  const slot2X = x + w * 0.56
  const groundDotX = x + w / 2
  const groundDotY = y + h * 0.76
  return `<g><polygon points="${points}" class="plug-shell" />${svgRect(slot1X, slotY, slotW, slotH, 'plug-slot', 0.8)}${svgRect(slot2X, slotY, slotW, slotH, 'plug-slot', 0.8)}${svgCircle(groundDotX, groundDotY, 1.2, 'plug-slot')}</g>`
}

const rowMetric = (item) => {
  const rawRowSpan = Number(item?.height || item?.rowSpan || 1)
  const rowSpan = Number.isFinite(rawRowSpan) ? Math.max(1, rawRowSpan) : 1
  const viewHeight = rowSpan * PANEL_UNIT_HEIGHT
  return {
    rowSpan,
    viewHeight,
    panelX: PANEL_PAD_X,
    panelY: PANEL_PAD_Y,
    panelW: PANEL_VIEW_WIDTH - PANEL_PAD_X * 2,
    panelH: viewHeight - PANEL_GAP
  }
}
const sizeTier = (rowSpan) => (rowSpan >= 3 ? 'large' : (rowSpan >= 2 ? 'medium' : 'compact'))
const centerY = (panelY, panelH, h) => Math.max(panelY + 2, panelY + (panelH - h) / 2)
const centerX = (panelX, panelW, w) => panelX + (panelW - w) / 2
const densityByHeight = (panelH, base, step = 32) => Math.max(base, base + Math.floor((panelH - 44) / step))
const fitRange = (v, min, max) => Math.max(min, Math.min(max, v))

const slots = (x, y, count, slotW, slotH, gap, led = true) => repeat(count, i => {
  const sx = x + i * (slotW + gap)
  return `${svgRect(sx, y, slotW, slotH, DETAIL.fill, 1.5)}${svgRect(sx + 2, y + 2, slotW - 4, Math.max(4, slotH * 0.2), 'detail-soft', 1)}${led ? svgCircle(sx + slotW / 2, y + slotH - 6, 2, DETAIL.dark) : ''}`
})

const vents = (x, y, count, h, gap = 9, w = 4) => repeat(count, i => svgRect(x + i * gap, y, w, h, DETAIL.mid, 1))
const fan = (cx, cy, rX, rY) => `<g>${`<ellipse cx="${n(cx)}" cy="${n(cy)}" rx="${n(rX)}" ry="${n(rY)}" class="fan-ring" />`}${`<ellipse cx="${n(cx)}" cy="${n(cy)}" rx="${n(rX * 0.18)}" ry="${n(rY * 0.18)}" class="${DETAIL.mid}" />`}${svgLine(cx - rX * 0.82, cy, cx + rX * 0.82, cy, 'fan-line')}${svgLine(cx, cy - rY * 0.82, cx, cy + rY * 0.82, 'fan-line')}${svgLine(cx - rX * 0.58, cy - rY * 0.58, cx + rX * 0.58, cy + rY * 0.58, 'fan-line')}${svgLine(cx - rX * 0.58, cy + rY * 0.58, cx + rX * 0.58, cy - rY * 0.58, 'fan-line')}</g>`

const renderStorageDetails = ({ panelX, panelY, panelW, panelH, rowSpan }) => {
  const tier = sizeTier(rowSpan)
  const count = fitRange(densityByHeight(panelH, 14, 34), 12, 22)
  const slotH = tier === 'compact' ? 28 : (tier === 'medium' ? 44 : 62)
  const slotW = tier === 'large' ? 18 : 20
  const gap = fitRange((panelW - count * slotW) / Math.max(1, (count - 1)), 8, 14)
  const totalW = count * slotW + (count - 1) * gap
  const x = panelX + (panelW - totalW) / 2
  const y = centerY(panelY, panelH, slotH)
  return slots(x, y, count, slotW, slotH, gap, true)
}

const renderServerDetails = ({ panelX, panelY, panelW, panelH, rowSpan }) => {
  const tier = sizeTier(rowSpan)
  const bandH = tier === 'compact' ? 28 : (tier === 'medium' ? 40 : 56)
  const y = centerY(panelY, panelH, bandH)
  const cy = panelY + panelH / 2
  const driveY = y + 2
  const portY = y + bandH - 13
  const ventCount = fitRange(densityByHeight(panelH, 8, 40), 8, 14)
  const driveCount = tier === 'large' ? 4 : 3
  return `${vents(panelX + 32, y + 4, ventCount, 32, 10, 5)}${svgCircle(panelX + 172, cy, 4, 'fan-ring')}${svgCircle(panelX + 198, cy, 2.3, DETAIL.dark)}${repeat(driveCount, i => svgRect(panelX + panelW - 205 + i * 52, driveY, 42, 14, 'drive'))}${repeat(driveCount, i => svgPort(panelX + panelW - 205 + i * 52, portY, 38, 11))}`
}

const renderBladeDetails = ({ panelX, panelY, panelW, panelH, rowSpan }) => {
  const tier = sizeTier(rowSpan)
  const count = fitRange(densityByHeight(panelH, 20, 26), 20, 34)
  const slotW = 13
  const gap = fitRange((panelW - count * slotW) / Math.max(1, (count - 1)), 5, 10)
  const slotH = tier === 'compact' ? 28 : (tier === 'medium' ? 42 : 60)
  const totalW = count * slotW + (count - 1) * gap
  const x = panelX + (panelW - totalW) / 2
  const y = centerY(panelY, panelH, slotH)
  return repeat(count, i => {
    const sx = x + i * (slotW + gap)
    return `${svgRect(sx, y, slotW, slotH, 'blade-slot', 1)}${svgRect(sx + 2, y + 4, slotW - 4, 5, 'detail-soft', 0.5)}${svgCircle(sx + slotW / 2, y + slotH - 6, 1.8, DETAIL.dark)}`
  })
}

const renderSwitchDetails = ({ panelX, panelY, panelW, panelH, rowSpan }) => {
  const tier = sizeTier(rowSpan)
  const portW = 16
  const portH = tier === 'compact' ? 9 : 10
  const rowGap = tier === 'compact' ? 5 : 6
  const startY = centerY(panelY, panelH, portH * 2 + rowGap)
  const cols = fitRange(densityByHeight(panelH, 10, 34), 10, 16)
  const gapX = fitRange((panelW - 260 - cols * portW) / Math.max(1, cols - 1), 7, 13)
  const row2cols = Math.max(8, cols - 2)
  return `${repeat(cols, i => svgPort(panelX + 38 + i * (portW + gapX), startY, portW, portH))}${repeat(row2cols, i => svgPort(panelX + 38 + i * (portW + gapX), startY + portH + rowGap, portW, portH))}${repeat(tier === 'large' ? 4 : 3, i => svgPort(panelX + panelW - 122 + i * 28, panelY + panelH / 2 - 7, 20, 14))}`
}

const renderPatchPanelDetails = ({ panelX, panelY, panelW, panelH, rowSpan }) => {
  const count = fitRange(densityByHeight(panelH, 18, 32), 18, 30)
  const portW = 15
  const gap = fitRange((panelW - count * portW) / Math.max(1, count - 1), 6, 11)
  const totalW = count * portW + (count - 1) * gap
  const x = panelX + (panelW - totalW) / 2
  const y = panelY + panelH / 2 - 7
  return repeat(count, i => svgPort(x + i * (portW + gap), y, portW, 14))
}

const renderRouterDetails = ({ panelX, panelY, panelW, panelH, rowSpan }) => {
  const tier = sizeTier(rowSpan)
  const portCount = tier === 'large' ? 7 : 6
  return `${svgCircle(panelX + 38, panelY + panelH / 2, 3, DETAIL.dark)}${svgCircle(panelX + 60, panelY + panelH / 2, 3, DETAIL.dark)}${svgCircle(panelX + 82, panelY + panelH / 2, 3, DETAIL.dark)}${vents(panelX + 140, panelY + panelH / 2 - 16, tier === 'large' ? 8 : 7, 32, 14, 5)}${svgPolyline([[panelX + 255, panelY + panelH / 2], [panelX + 300, panelY + panelH / 2], [panelX + 322, panelY + panelH / 2 - 12]], 'detail-line')}${repeat(portCount, i => svgPort(panelX + panelW - 205 + i * 28, panelY + panelH / 2 - 7, 20, 14))}`
}

const renderFirewallDetails = ({ panelX, panelY, panelW, panelH, rowSpan }) => {
  const tier = sizeTier(rowSpan)
  const baseX = panelX + 165
  const baseY = panelY + panelH / 2 - 20
  const portCount = tier === 'large' ? 6 : 5
  return `${svgCircle(panelX + 42, panelY + panelH / 2, 4, DETAIL.dark)}${repeat(6, i => svgRect(baseX + i * 18, baseY + (5 - i) * 3, 14, 26 - Math.abs(i - 3) * 3, DETAIL.mid, 1))}${repeat(5, i => svgRect(baseX + 130 + i * 18, baseY + i * 3, 14, 22 - i * 2, DETAIL.fill, 1))}${repeat(portCount, i => svgPort(panelX + panelW - 200 + i * 30, panelY + panelH / 2 - 7, 22, 14))}`
}

const renderLoadBalancerDetails = ({ panelX, panelY, panelW, panelH, rowSpan }) => {
  const tier = sizeTier(rowSpan)
  const cy = panelY + panelH / 2
  const outputs = tier === 'large' ? 8 : 6
  return `${repeat(5, i => svgCircle(panelX + 38 + i * 28, cy, 3, DETAIL.dark))}${svgPolyline([[panelX + 190, cy], [panelX + 255, cy], [panelX + 305, cy - 18]], 'detail-line')}${svgPolyline([[panelX + 255, cy], [panelX + 305, cy]], 'detail-line')}${svgPolyline([[panelX + 255, cy], [panelX + 305, cy + 18]], 'detail-line')}${repeat(outputs, i => svgCircle(panelX + panelW - 175 + i * 20, cy, 2.5, DETAIL.dark))}${svgCircle(panelX + panelW - 32, cy, 7, 'fan-ring')}`
}

const renderNasDetails = ({ panelX, panelY, panelW, panelH, rowSpan }) => {
  const tier = sizeTier(rowSpan)
  const driveH = tier === 'compact' ? 28 : (tier === 'medium' ? 36 : 52)
  const y = centerY(panelY, panelH, driveH)
  const bays = tier === 'large' ? 6 : 5
  return `${repeat(bays, i => `${svgRect(panelX + 32 + i * 48, y, 34, driveH, 'drive')}${svgRect(panelX + 39 + i * 48, y + 8, 20, 6, 'detail-soft', 1)}${svgCircle(panelX + 49 + i * 48, y + driveH - 8, 2.2, DETAIL.dark)}`)}${vents(panelX + panelW - 205, panelY + panelH / 2 - 20, tier === 'large' ? 15 : 13, 40, 10, 4)}${svgCircle(panelX + panelW - 60, panelY + panelH / 2, 3, DETAIL.dark)}${svgCircle(panelX + panelW - 42, panelY + panelH / 2, 3, DETAIL.dark)}${svgPort(panelX + panelW - 22, panelY + panelH / 2 - 8, 16, 16)}`
}

const renderMonitoringDetails = ({ panelX, panelY, panelW, panelH, rowSpan }) => {
  const tier = sizeTier(rowSpan)
  const cy = panelY + panelH / 2
  const screenX = panelX + 92
  const indicators = tier === 'large' ? 6 : 5
  return `${svgCircle(panelX + 38, cy, 3, DETAIL.dark)}${svgRect(screenX, cy - 16, 150, 32, 'screen')}${svgPolyline([[screenX + 14, cy + 2], [screenX + 32, cy + 2], [screenX + 42, cy - 8], [screenX + 54, cy + 12], [screenX + 70, cy - 13], [screenX + 86, cy + 8], [screenX + 100, cy - 4], [screenX + 118, cy + 2], [screenX + 138, cy + 2]], 'detail-line')}${repeat(indicators, i => svgCircle(panelX + panelW - 168 + i * 24, cy, 3, DETAIL.dark))}${svgPort(panelX + panelW - 30, cy - 9, 18, 18)}`
}

const renderKvmDetails = ({ panelX, panelY, panelW, panelH, rowSpan }) => {
  const tier = sizeTier(rowSpan)
  const keyCols = tier === 'large' ? 4 : 3
  const keyRows = tier === 'large' ? 3 : 3
  const keyW = 13
  const keyH = 12
  const keyGapX = 8
  const keyGapY = 7
  const keyBlockH = keyRows * keyH + (keyRows - 1) * keyGapY
  const keyStartX = panelX + 26
  const keyStartY = centerY(panelY, panelH, keyBlockH)

  const displayW = tier === 'large' ? 132 : 118
  const displayH = tier === 'large' ? 32 : 28
  const displayX = panelX + 172
  const displayY = centerY(panelY, panelH, displayH)

  const rightX = panelX + panelW - 188
  const rightW = 158
  const rightY = panelY + 8
  const rightH = panelH - 16
  const controlBtnCount = tier === 'large' ? 3 : 2
  const portsCount = tier === 'large' ? 4 : 3
  const portW = 20
  const portH = 12
  const portsTotalW = portsCount * portW + (portsCount - 1) * 10
  const portsStartX = centerX(rightX, rightW, portsTotalW)
  const portsY = rightY + rightH - 18

  return `${repeat(keyCols * keyRows, i => svgRect(keyStartX + (i % keyCols) * (keyW + keyGapX), keyStartY + Math.floor(i / keyCols) * (keyH + keyGapY), keyW, keyH, 'key', 1))}${svgRect(displayX, displayY, displayW, displayH, 'screen')}${svgRect(displayX + 12, displayY + 8, displayW - 24, 5, 'detail-soft', 1)}${svgRect(rightX, rightY, rightW, rightH, 'detail-soft', 3)}${repeat(controlBtnCount, i => svgCircle(rightX + 26 + i * 24, rightY + 14, 3.5, DETAIL.dark))}${repeat(controlBtnCount, i => svgRect(rightX + 96 + i * 16, rightY + 10, 11, 8, 'key', 1))}${repeat(portsCount, i => svgPort(portsStartX + i * (portW + 10), portsY, portW, portH))}`
}

const renderPduDetails = ({ panelX, panelY, panelW, panelH, rowSpan }) => {
  const tier = sizeTier(rowSpan)
  const outletW = 30
  const outletH = tier === 'compact' ? 18 : (tier === 'medium' ? 24 : 28)
  const y = centerY(panelY, panelH, outletH)
  const outletCount = tier === 'large' ? 10 : 9
  return `${repeat(outletCount, i => svgOutlet(panelX + 22 + i * 40, y, outletW, outletH))}${svgPort(panelX + panelW - 120, panelY + panelH / 2 - 8, 24, 16)}${svgCircle(panelX + panelW - 72, panelY + panelH / 2, 3, DETAIL.dark)}${svgCircle(panelX + panelW - 48, panelY + panelH / 2, 3, DETAIL.dark)}`
}

const renderUpsStatus = (x, y, w, h) => {
  const batteryW = 60
  const batteryH = 20
  const batteryX = x + 18
  const batteryY = y + h * 0.5 - batteryH / 2
  const waveX = x + w - 72
  const waveY = y + h * 0.5
  return `${svgRect(x, y, w, h, 'screen', 2)}${svgRect(batteryX, batteryY, batteryW, batteryH, 'battery', 2)}${svgRect(batteryX + batteryW, batteryY + 6, 8, 8, 'battery', 1)}${svgRect(x + 20, y + 14, w - 40, 10, 'detail-soft', 1)}${repeat(4, i => svgRect(x + 24 + i * 22, y + h - 16, 14, 7, i < 2 ? DETAIL.mid : DETAIL.dark, 1))}${svgPolyline([[waveX - 28, waveY + 2], [waveX - 16, waveY + 2], [waveX - 10, waveY - 8], [waveX - 2, waveY + 8], [waveX + 10, waveY - 2]], 'detail-line')}`
}

const renderUpsPlugs = (x, y, cols, rows, plugW, plugH, gapX, gapY) => {
  return repeat(cols * rows, i => svgPlug(x + (i % cols) * (plugW + gapX), y + Math.floor(i / cols) * (plugH + gapY), plugW, plugH))
}

const renderUpsDetailsCompact = ({ panelX, panelY, panelW, panelH }) => {
  const cy = panelY + panelH / 2
  const plugW = 28
  const plugH = 22
  const leftX = panelX + 18
  const leftY = cy - plugH / 2
  const statusW = 150
  const statusH = 34
  const statusX = panelX + 170
  const statusY = cy - statusH / 2
  const rightX = panelX + panelW - 188
  return `${renderUpsPlugs(leftX, leftY, 3, 1, plugW, plugH, 10, 10)}${renderUpsStatus(statusX, statusY, statusW, statusH)}${svgRect(rightX, cy - 16, 68, 32, 'drive')}${repeat(8, i => svgRect(rightX + 84 + i * 8, cy - 16, 4, 32, DETAIL.mid, 1))}`
}

const renderUpsDetailsMedium = ({ panelX, panelY, panelW, panelH }) => {
  const leftBoxX = panelX + 18
  const leftBoxY = panelY + 10
  const leftBoxW = 195
  const leftBoxH = panelH - 20
  const plugW = 45
  const plugH = 24
  const gapX = 10
  const gapY = 12
  const plugGroupW = plugW * 3 + gapX * 2
  const plugStartX = centerX(leftBoxX, leftBoxW, plugGroupW)
  const plugStartY = centerY(leftBoxY, leftBoxH, plugH * 2 + gapY)
  const statusW = 184
  const statusH = 66
  const statusX = panelX + 238
  const statusY = centerY(panelY, panelH, statusH)
  const rightBoxX = panelX + panelW - 214
  const rightBoxY = panelY + 14
  const rightBoxH = panelH - 28
  return `${svgRect(leftBoxX, leftBoxY, leftBoxW, leftBoxH, 'detail-soft', 4)}${renderUpsPlugs(plugStartX, plugStartY, 3, 2, plugW, plugH, gapX, gapY)}${renderUpsStatus(statusX, statusY, statusW, statusH)}${svgRect(rightBoxX, rightBoxY, 78, rightBoxH, 'drive')}${svgRect(rightBoxX + 12, rightBoxY + 12, 54, 10, 'detail-soft', 1)}${repeat(11, i => svgRect(rightBoxX + 92 + i * 9, rightBoxY, 4, rightBoxH, DETAIL.mid, 1))}`
}

const renderUpsDetailsLarge = ({ panelX, panelY, panelW, panelH }) => {
  const leftBoxX = panelX + 18
  const leftBoxH = 118
  const leftBoxY = centerY(panelY, panelH, leftBoxH)
  const leftBoxW = 210
  const plugW = 60
  const plugH = 26
  const gapX = 10
  const gapY = 10
  const plugGroupW = plugW * 3 + gapX * 2
  const plugStartX = centerX(leftBoxX, leftBoxW, plugGroupW)
  const plugStartY = centerY(leftBoxY, leftBoxH, plugH * 2 + gapY)
  const statusW = 198
  const statusH = 88
  const statusX = panelX + 240
  const statusY = centerY(panelY, panelH, statusH)
  const rightBoxX = panelX + panelW - 222
  const rightBoxY = panelY + 16
  const rightBoxH = panelH - 32
  const ventsX = rightBoxX + 92
  return `${svgRect(leftBoxX, leftBoxY, leftBoxW, leftBoxH, 'detail-soft', 4)}${renderUpsPlugs(plugStartX, plugStartY, 3, 2, plugW, plugH, gapX, gapY)}${repeat(3, i => svgCircle(plugStartX + 10 + i * 46, leftBoxY + leftBoxH - 10, 2, DETAIL.dark))}${renderUpsStatus(statusX, statusY, statusW, statusH)}${repeat(4, i => svgRect(statusX + 20 + i * 24, statusY + statusH - 14, 14, 7, DETAIL.dark, 1))}${svgRect(rightBoxX, rightBoxY, 82, rightBoxH, 'drive')}${svgRect(rightBoxX + 12, rightBoxY + 14, 58, 11, 'detail-soft', 1)}${svgRect(rightBoxX + 16, rightBoxY + rightBoxH - 34, 22, 10, DETAIL.dark, 1)}${svgRect(rightBoxX + 44, rightBoxY + rightBoxH - 34, 22, 10, DETAIL.dark, 1)}${repeat(12, i => svgRect(ventsX + i * 9, rightBoxY, 4, rightBoxH, DETAIL.mid, 1))}${repeat(3, i => svgCircle(ventsX + 8 + i * 36, rightBoxY + rightBoxH - 6, 2.2, DETAIL.dark))}`
}

const renderUpsDetails = (metric) => {
  if (metric.rowSpan >= 3) return renderUpsDetailsLarge(metric)
  if (metric.rowSpan >= 2) return renderUpsDetailsMedium(metric)
  return renderUpsDetailsCompact(metric)
}

const renderCoolingDetails = ({ panelX, panelY, panelW, panelH, rowSpan }) => {
  const tier = sizeTier(rowSpan)
  const r = tier === 'compact' ? 12 : (tier === 'medium' ? 18 : 24)
  const cy = panelY + panelH / 2
  // compensate non-uniform x/y scaling from preserveAspectRatio="none"
  const frameWpx = 560
  const panelLeftPx = 74
  const panelRightPx = 12
  const panelWpx = frameWpx - panelLeftPx - panelRightPx
  const panelHpx = rowSpan * RACK_UNIT_HEIGHT - 14
  const scaleX = panelWpx / PANEL_VIEW_WIDTH
  const scaleY = panelHpx / (rowSpan * PANEL_UNIT_HEIGHT)
  const rx = scaleX > 0 ? r * (scaleY / scaleX) : r
  const fanCount = tier === 'large' ? 4 : 3
  return `${repeat(fanCount, i => fan(panelX + 90 + i * 130, cy, rx, r))}${vents(panelX + panelW - 150, cy - 28, tier === 'large' ? 14 : 12, 56, 11, 4)}`
}

const renderCustomDetails = ({ panelX, panelY, panelW, panelH }) => {
  const slotH = 34
  const y = centerY(panelY, panelH, slotH)
  return `${slots(panelX + 34, y, 12, 18, slotH, 12, false)}${svgRect(panelX + panelW - 180, panelY + panelH / 2 - 16, 110, 32, 'screen')}${svgCircle(panelX + panelW - 36, panelY + panelH / 2, 7, 'fan-ring')}`
}

const renderBlankDetails = ({ panelX, panelY, panelW, panelH }) => {
  const insetX = panelX + 18
  const insetY = panelY + 10
  const insetW = panelW - 36
  const insetH = panelH - 20
  const seamY = panelY + panelH / 2
  return `${svgRect(insetX, insetY, insetW, insetH, 'detail-soft', 5)}${svgRect(insetX + 24, seamY - 1, insetW - 48, 2, 'detail-line', 1)}`
}

const renderDevicePanel = (item) => {
  const type = getDevicePanelType(item)
  const metric = rowMetric(item)
  const renderers = {
    storage: renderStorageDetails,
    server: renderServerDetails,
    blade: renderBladeDetails,
    switch: renderSwitchDetails,
    patch: renderPatchPanelDetails,
    router: renderRouterDetails,
    firewall: renderFirewallDetails,
    loadbalancer: renderLoadBalancerDetails,
    nas: renderNasDetails,
    monitoring: renderMonitoringDetails,
    kvm: renderKvmDetails,
    pdu: renderPduDetails,
    ups: renderUpsDetails,
    cooling: renderCoolingDetails,
    blank: renderBlankDetails,
    custom: renderCustomDetails
  }
  const body = (renderers[type] || renderCustomDetails)(metric)
  return `<svg class="device-panel-svg" viewBox="0 0 ${PANEL_VIEW_WIDTH} ${metric.viewHeight}" preserveAspectRatio="none" aria-hidden="true">
    <g id="device-${type}">
      <rect x="${metric.panelX}" y="${metric.panelY}" width="${metric.panelW}" height="${metric.panelH}" rx="6" class="panel-shell" />
      ${body}
    </g>
  </svg>`
}

// 각 장비의 상단 라인 컬러와 맞춘 아이콘 컬러 정의
const getIconColor = (type) => {
  const colors = {
    server: '#4a90e2',
    switch: '#27ae60',
    storage: '#8e44ad',
    blade: '#35b7df',
    firewall: '#c0392b',
    router: '#7c4dff',
    loadbalancer: '#d94b9a',
    nas: '#4a90e2',
    monitoring: '#f39c32',
    kvm: '#a44cc5',
    ups: '#00b894', // 민트색
    patch: '#f39c12', // 주황색
    pdu: '#d35400', // 진한 주황색
    cooling: '#40c4e8',
    blank: '#94a3b8',
    custom: '#95a5a6' // 회색
  }
  return colors[type] || '#ffffff'
}

onMounted(() => {
  // 1. Zone 목록을 먼저 가져오고, 성공하면 내부에서 fetchRackData()를 자동으로 실행합니다.
  fetchZonesAndRackData()

  // 2. 창 닫기/새로고침 방지 이벤트 리스너 등록
  window.addEventListener('beforeunload', handleBeforeUnload)
  window.addEventListener('scroll', updateSidePanePosition, { passive: true })
  window.addEventListener('resize', handleResponsiveResize)
  nextTick(() => {
    applyResponsiveZoom(true)
    updateSidePanePosition()
  })
})

onBeforeUnmount(() => {
  window.removeEventListener('beforeunload', handleBeforeUnload)
  window.removeEventListener('scroll', updateSidePanePosition)
  window.removeEventListener('resize', handleResponsiveResize)
  if (resizeDebounceTimer) clearTimeout(resizeDebounceTimer)
  if (zoomRafId) cancelAnimationFrame(zoomRafId)
  if (zoomApplyRafId) cancelAnimationFrame(zoomApplyRafId)
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

.toolbar-detail {
  flex-wrap: nowrap !important;
}

.toolbar-detail .toolbar-left {
  min-width: 0;
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
  overflow: visible;
  min-height: 600px;
  padding: 20px;
  scrollbar-width: thin;
  scrollbar-color: #888 #eef0f4;
  background: radial-gradient(circle, #f0f2f5 0%, #e6e9ed 100%);
}

.rack-list-view {
  min-height: 600px;
  padding: 8px 4px 12px;
}

.rack-list-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 12px;
}

.rack-list-card {
  border-radius: 10px;
  position: relative;
  transition: opacity 0.2s ease, border-color 0.2s ease, box-shadow 0.2s ease;
}

.rack-list-card-match {
  position: absolute;
  top: 10px;
  right: 10px;
}

.rack-list-card--matched {
  border-color: #91caff;
  box-shadow: 0 0 0 1px rgba(22, 119, 255, 0.18) inset;
}

.rack-list-card--dimmed {
  opacity: 0.52;
}

.add-rack-btn {
  border-radius: 8px !important;
  font-weight: 600;
  box-shadow: 0 2px 8px rgba(22, 119, 255, 0.22);
}

.rack-list-card-title {
  font-size: 16px;
  font-weight: 700;
  color: #1f2937;
}

.rack-list-card-sub {
  margin-top: 2px;
  font-size: 13px;
  color: #6b7280;
}

.rack-list-card-usage {
  margin-top: 12px;
  font-size: 15px;
  font-weight: 500;
  color: #1f2937;
}

.rack-list-progress {
  margin-top: 10px;
}

.rack-list-card-usage-detail {
  margin-top: 8px;
  font-size: 13px;
  color: #4b5563;
}

.rack-list-card-extra {
  margin-top: 12px;
  font-size: 12px;
  color: #64748b;
}

.rack-detail-layout {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  position: relative;
  min-width: 0;
}

.rack-main-pane {
  flex: 1 1 auto;
  min-width: 0;
  overflow-x: auto;
  overflow-y: visible;
  padding-bottom: 8px;
}

.rack-side-pane-slot {
  width: clamp(300px, 24vw, 420px);
  flex: 0 0 clamp(300px, 24vw, 420px);
  position: relative;
  align-self: flex-start;
}

.rack-side-pane {
  width: 100%;
  position: -webkit-sticky;
  position: sticky;
  top: 64px;
  align-self: flex-start;
  height: fit-content;
  max-height: none;
  overflow: visible;
  max-width: 100%;
  z-index: 20;
}

.rack-side-pane.is-fixed {
  position: fixed !important;
  z-index: 40;
}

.rack-side-pane.is-bottom {
  position: absolute !important;
  top: auto !important;
  left: 0;
  right: 0;
  bottom: 0;
}

.rack-side-pane-card :deep(.ant-card-head) {
  position: sticky;
  top: 0;
  z-index: 2;
  background: #fff;
}

.device-info-row {
  margin-bottom: 8px;
  font-size: 12px;
  color: #374151;
  word-break: break-word;
}

.device-info-desc :deep(.ant-descriptions-item-label),
.device-info-desc :deep(.ant-descriptions-item-content) {
  font-size: 14px;
  line-height: 1.55;
}

.device-info-desc :deep(.ant-descriptions-item-label) {
  width: 100px;
  color: #64748b;
}

.device-info-actions {
  display: flex;
  gap: 8px;
  margin-top: 12px;
}

.device-info-close-btn {
  color: #64748b !important;
}

.device-info-close-btn:hover {
  color: #334155 !important;
  background: #f1f5f9 !important;
}

.device-memo-wrap {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.device-memo-plain {
  white-space: pre-wrap;
  word-break: break-word;
  color: #334155;
}

.device-memo-linked {
  background: transparent;
  border-radius: 6px;
  padding: 0;
}

.device-memo-linked-title {
  display: none;
}

.device-memo-linked-list,
.device-host-info-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
  white-space: pre-wrap;
  word-break: break-word;
  color: #334155;
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
  background: #ffffff;
  border: 1px solid #d9e2ec;
  border-radius: 10px;
  padding: 15px;
  box-shadow: 0 2px 8px rgba(15, 23, 42, 0.06);
  flex-shrink: 0;
  width: 668px;
  flex: 0 0 668px
}

/* 랙 헤더 */
.rack-header {
  color: #1f2937;
  font-weight: bold;
  margin-bottom: 15px;
  padding-bottom: 15px;
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

.rack-header-left {
  display: flex;
  align-items: center;
  min-width: 0;
  gap: 8px;
  flex: 1;
}

.rack-back-inline-btn {
  color: #64748b !important;
  height: 28px !important;
  width: 28px !important;
  min-width: 28px !important;
  padding: 0 !important;
  border-radius: 999px !important;
  border: 1px solid #dbe3ee !important;
  background: #fff !important;
  display: inline-flex !important;
  align-items: center;
  justify-content: center;
}

.rack-back-inline-btn:hover {
  color: #1677ff !important;
  border-color: #dbeafe !important;
  background: #f0f7ff !important;
}

/* 랙 본체 가로 정렬용 */
.rack-body {
  display: flex;
  width: 604px;
  margin: 0 auto;
  background: #f1f5f9;
  border-radius: 8px;
  border: 2px solid #cfd8e3;
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
  width: 44px;
  background: #eaf0f7;
  border-right: 1px solid #c5cfdb;
  display: flex;
  flex-direction: column;
}

/*  개별 눈금 텍스트 (정확히 1U = RACK_UNIT_HEIGHT에 맞춤) */
.ruler-number {
  height: 52px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #6b7280;
  font-size: 14px;
  font-family: 'Courier New', monospace;
  font-weight: bold;
  box-sizing: border-box;
  /* 눈금선 느낌을 위해 위아래에 아주 연한 점선 추가 */
  border-bottom: 1px dotted rgba(15, 23, 42, 0.12);
}

/* 맨 위 눈금 상단 선 보정 */
.ruler-number:first-child {
  border-top: 1px dotted rgba(15, 23, 42, 0.12);
}

/* 랙 내부 프레임 */
.rack-frame {
  width: 560px;
  background: #f8fbff;
  border: 2px solid #cfd8e3;
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
  border-bottom: 1px solid #c4cfdd;
  border-radius: 0;
  overflow: hidden; /* 자식 요소의 각진 부분 잘라내기 */
  margin-bottom: 0;
}

.rack-item::after {
  content: "";
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 1px;
  background: #c4cfdd;
  pointer-events: none;
}

/* 랙 이름 텍스트: 길어지면 말줄임표 처리 */
.rack-name-text {
  flex: 1;
  min-width: 0; /* flex 환경에서 ellipsis 작동 필수 조건 */
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 19px; /* 가독성과 공간 확보를 위한 최적 크기 */
  font-weight: bold;
  margin-right: 10px;
  /* 마우스 오버 시 클릭 가능한 느낌 전달 */
  //cursor: pointer;
  transition: color 0.2s ease;
}

/* 마우스 호버 시 텍스트 색상을 밝게 변경 */
.rack-name-text:hover {
  color: #1677ff !important;
  text-decoration: none; /* 밑줄 추가로 가독성 확보 */
}

.rack-item:last-child {
  border-bottom: none;
  margin-bottom: 0;
}

.rack-item:last-child::after {
  content: none;
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
  color: #334155 !important;
  padding: 0 8px !important;
  height: 34px !important;
  min-width: 34px;
  display: inline-flex !important; /* flex 대신 inline-flex 적용 */
  align-items: center;
  justify-content: center;
  background: transparent;
  border: 1px solid #d8e1ec;
  border-radius: 6px;
  box-shadow: none;
}

/* 3. 내부 Ant Design 아이콘 색상 강제 (검은색 변함 방지) */
.rack-header-actions .ant-btn :deep(.anticon) {
  color: #334155 !important;
  font-size: 18px !important;
}

/* 4. 버튼 비활성화 상태 색상 처리 */
.rack-header-actions .ant-btn:disabled,
.rack-header-actions .ant-btn:disabled :deep(.anticon) {
  color: rgba(51, 65, 85, 0.35) !important;
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
    #eef2f7,
    #eef2f7 10px,
    #e2e8f0 10px,
    #e2e8f0 20px
  );
  color: #475569;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
  /* 장비와 확실히 구분되도록 안쪽 그림자 제거 혹은 변경 */
  box-shadow: inset 0 0 8px rgba(15, 23, 42, 0.08);
}

/* 여백 호버 시 피드백 강화 */
.gap-content:hover {
  background: #dbe5f2;
  color: #1677ff;
}

/* 장비 내용 래퍼 */
.device-content {
  position: relative;
  height: 100%;
  overflow: hidden;
  cursor: pointer;
  background: #f8fbff !important;
  border: 0;
  box-shadow: none;
}

.device-content::before {
  content: none;
}

/* 1. 상단 컬러 라인 (기존 SVG 상단 라인 대체) */
.device-top-line {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 2px;
  opacity: 0.92;
  z-index: 2;
}

/* 2. 질감 패턴 기본 틀 */
.device-pattern {
  position: absolute;
  top: 7px;
  bottom: 7px;
  right: 12px;
  left: 74px;
  z-index: 1;
  opacity: 1;
  border-radius: 6px;
}

.device-pattern :deep(.device-panel-svg) {
  width: 100%;
  height: 100%;
  display: block;
}

.device-pattern :deep(.panel-shell) {
  fill: #f1f5f9;
  stroke: #cfdae6;
  stroke-width: 1.2;
}

.device-pattern :deep(.detail-fill),
.device-pattern :deep(.bay),
.device-pattern :deep(.drive),
.device-pattern :deep(.screen),
.device-pattern :deep(.blade-slot) {
  fill: #bfccd9;
  stroke: #9aa8ba;
  stroke-width: 0.8;
}

.device-pattern :deep(.detail-soft) {
  fill: #d8e1eb;
}

.device-pattern :deep(.thin),
.device-pattern :deep(.vent),
.device-pattern :deep(.line),
.device-pattern :deep(.block),
.device-pattern :deep(.light),
.device-pattern :deep(.detail-mid) {
  fill: #aebccc;
}

.device-pattern :deep(.port),
.device-pattern :deep(.jack),
.device-pattern :deep(.sfp),
.device-pattern :deep(.key),
.device-pattern :deep(.port-shell),
.device-pattern :deep(.outlet-shell),
.device-pattern :deep(.plug-shell) {
  fill: #f8fafc;
  stroke: #64748b;
  stroke-width: 1;
}

.device-pattern :deep(.plug-shell) {
  fill: #f4f7fb;
  stroke: #7f8da0;
  stroke-width: 1.5;
}

.device-pattern :deep(.plug-slot) {
  fill: #66758a;
  stroke: none;
}

.device-pattern :deep(.hole),
.device-pattern :deep(.port-hole),
.device-pattern :deep(.detail-dark) {
  fill: #475569;
  stroke: none;
}

.device-pattern :deep(.dot) {
  fill: #64748b;
}

.device-pattern :deep(.ring),
.device-pattern :deep(.fan),
.device-pattern :deep(.fan-ring) {
  fill: none;
  stroke: #64748b;
  stroke-width: 1.6;
}

.device-pattern :deep(.stroke),
.device-pattern :deep(.fan-line),
.device-pattern :deep(.detail-line) {
  fill: none;
  stroke: #94a3b8;
  stroke-width: 2;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.device-pattern :deep(.outlet) {
  fill: #f8fafc;
  stroke: #64748b;
  stroke-width: 1.1;
}

.device-pattern :deep(.battery) {
  fill: #64748b;
}

/* 🔲 블랭크 패널: 전체를 덮는 연한 사선 (투명도 1) */
.pattern-blank {
  top: 0; bottom: 0; left: 0; right: 0;
  background-image: repeating-linear-gradient(-45deg, #e2e8f0 0, #e2e8f0 6px, #edf2f7 6px, #edf2f7 12px);
  opacity: 1;
}

/* 3. UPS 전용 정비율 거대 워터마크 */
.ups-watermark {
  display: none;
}

.device-icon-overlay {
  position: absolute;
  left: 23px;
  top: 50%;
  transform: translateY(-50%);
  width: 26px;
  height: 26px;
  opacity: 0.96;
  filter: none;
  pointer-events: none;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 4;
}

.device-icon-overlay::before {
  content: none;
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
  left: 160px;
  right: 160px;
  transform: translateY(-50%);
  color: #1f2937;
  font-size: 15px;
  font-weight: bold;
  text-shadow: none;
  pointer-events: none;
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 5;
  letter-spacing: -0.2px;
}

.tag-content {
  display: flex;
  align-items: center;
  justify-content: center;
  max-width: 100%;
  min-width: 110px;
  padding: 3px 9px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: none;
  -webkit-backdrop-filter: none;
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.1);
  gap: 8px;
}

.tag-text {
  display: block;
  flex: 1 1 auto;
  min-width: 0;
  max-width: none;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.tag-badge {
  background: #1f2937;
  color: #ffffff;
  padding: 2px 6px;
  border-radius: 10px;
  flex: 0 0 auto;
  font-size: 11px;
  font-weight: 700;
  border: 1px solid rgba(255, 255, 255, 0.18);
}

/* 액션 버튼 (호버 시에만 표시) */
.device-actions {
  /* 버튼을 장비 둥둥 띄우는 핵심 코드 (이게 빠져서 안 보였던 겁니다!) */
  position: absolute !important;
  top: 50%;
  bottom: auto;
  left: 50%;
  transform: translate(-50%, -50%);
  z-index: 5; /* 장비나 텍스트 위로 확실히 올림 */

  /* 디자인 요소 (아까 적용한 예쁜 간격과 배경) */
  display: flex !important;
  justify-content: center;
  align-items: center;
  gap: 4px !important;
  background: rgba(0, 0, 0, 0.85) !important;
  border-radius: 8px;
  padding: 6px 10px;
  height: 36px;
  box-shadow: 0 3px 10px rgba(0, 0, 0, 0.42);

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
  padding: 0 6px !important;
  height: 30px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.device-actions .ant-btn :deep(.anticon) {
  font-size: 18px !important;
  color: #ffffff !important;
  text-shadow: 0 0 1px rgba(255, 255, 255, 0.6);
  transition: all 0.2s ease;
}

.device-actions .ant-btn:hover :deep(.anticon) {
  color: #40a9ff !important;
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
  cursor: pointer;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.host-vm-card:hover {
  border-color: #91caff;
  box-shadow: 0 2px 8px rgba(24, 144, 255, 0.15);
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

/* dark mode in this project is attached on body */
.rack-diagram-root.is-dark .toolbar-container {
  background: #22282f !important;
  border-color: #3e444c !important;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.35) !important;
}

.rack-diagram-root.is-dark .toolbar-divider {
  background-color: #4a515a !important;
}

.rack-diagram-root.is-dark .zoom-label {
  color: rgba(255, 255, 255, 0.85) !important;
}

.rack-diagram-root.is-dark .rack-canvas {
  scrollbar-color: #6b7280 #1b2129;
  background: radial-gradient(circle, #232a33 0%, #1b2129 100%);
}

.rack-diagram-root.is-dark .rack-list-card {
  background: #1f2732;
  border-color: #3a4654;
}

.rack-diagram-root.is-dark .rack-list-card--matched {
  border-color: #4c93ff;
  box-shadow: 0 0 0 1px rgba(76, 147, 255, 0.32) inset;
}

.rack-diagram-root.is-dark .rack-list-card-title {
  color: rgba(255, 255, 255, 0.9);
}

.rack-diagram-root.is-dark .rack-list-card-sub,
.rack-diagram-root.is-dark .rack-list-card-usage-detail,
.rack-diagram-root.is-dark .rack-list-card-extra {
  color: rgba(255, 255, 255, 0.65);
}

.rack-diagram-root.is-dark .rack-list-card-usage {
  color: rgba(255, 255, 255, 0.9);
}

.rack-diagram-root.is-dark .rack-back-inline-btn {
  color: rgba(255, 255, 255, 0.72) !important;
  border-color: #3a4654 !important;
  background: #1f2732 !important;
}

.rack-diagram-root.is-dark .rack-back-inline-btn:hover {
  color: #91caff !important;
  border-color: #31445f !important;
  background: #273244 !important;
}

.rack-diagram-root.is-dark .device-info-row {
  color: rgba(255, 255, 255, 0.78);
}

.rack-diagram-root.is-dark .device-info-desc :deep(.ant-descriptions-item-label) {
  color: rgba(255, 255, 255, 0.68);
}

.rack-diagram-root.is-dark .rack-side-pane-card :deep(.ant-card-head) {
  background: #1f2732;
}

.rack-diagram-root.is-dark .device-info-close-btn {
  color: rgba(255, 255, 255, 0.7) !important;
}

.rack-diagram-root.is-dark .device-info-close-btn:hover {
  color: rgba(255, 255, 255, 0.92) !important;
  background: #273244 !important;
}

.rack-diagram-root.is-dark .tag-content {
  background: rgba(15, 23, 42, 0.52);
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.35);
}

@media (max-width: 980px) {
  .rack-side-pane-slot {
    width: 280px;
    flex: 0 0 280px;
  }
}

.rack-diagram-root.is-dark .host-vm-card {
  background: #1f2732;
  border-color: #3a4654;
}

.rack-diagram-root.is-dark .host-vm-name {
  color: rgba(255, 255, 255, 0.9);
}

.rack-diagram-root.is-dark .host-vm-meta {
  color: rgba(255, 255, 255, 0.65);
}

.rack-diagram-root.is-dark .toolbar-container :deep(.ant-input),
.rack-diagram-root.is-dark .toolbar-container :deep(.ant-input-number),
.rack-diagram-root.is-dark .toolbar-container :deep(.ant-input-number-input),
.rack-diagram-root.is-dark .toolbar-container :deep(.ant-input-search .ant-input),
.rack-diagram-root.is-dark .toolbar-container :deep(.ant-input-group-addon),
.rack-diagram-root.is-dark .toolbar-container :deep(.ant-slider-rail) {
  background: #1a212b !important;
  border-color: #4a515a !important;
  color: rgba(255, 255, 255, 0.85) !important;
}

.rack-diagram-root.is-dark .toolbar-container :deep(.ant-input::placeholder),
.rack-diagram-root.is-dark .toolbar-container :deep(.ant-input-number-input::placeholder) {
  color: rgba(255, 255, 255, 0.45) !important;
}

.rack-diagram-root.is-dark .toolbar-container :deep(.ant-input-search-button),
.rack-diagram-root.is-dark .toolbar-container :deep(.ant-slider-track) {
  background: #2a8be7 !important;
  border-color: #2a8be7 !important;
}

.rack-diagram-root.is-dark .toolbar-container :deep(.ant-slider-handle) {
  border-color: #69c0ff !important;
}

/* ant-space-item wrappers can override inherited color */
.rack-diagram-root.is-dark .toolbar-container :deep(.ant-space-item) {
  color: rgba(255, 255, 255, 0.88) !important;
}

.rack-diagram-root.is-dark .toolbar-container :deep(.ant-space-item .ant-btn) {
  background: #1a212b !important;
  border-color: #4a515a !important;
  color: rgba(255, 255, 255, 0.88) !important;
}

.rack-diagram-root.is-dark .toolbar-container :deep(.ant-space-item .ant-btn > span),
.rack-diagram-root.is-dark .toolbar-container :deep(.ant-space-item .ant-btn .anticon),
.rack-diagram-root.is-dark .toolbar-container :deep(.ant-space-item .ant-btn .anticon svg) {
  color: rgba(255, 255, 255, 0.88) !important;
  fill: currentColor !important;
  -webkit-text-fill-color: rgba(255, 255, 255, 0.88) !important;
}

.rack-diagram-root.is-dark .toolbar-container :deep(.ant-space-item .ant-btn-primary) {
  background: #1677ff !important;
  border-color: #1677ff !important;
}

.rack-diagram-root.is-dark .toolbar-container :deep(.ant-space-item .ant-btn-primary > span),
.rack-diagram-root.is-dark .toolbar-container :deep(.ant-space-item .ant-btn-primary .anticon),
.rack-diagram-root.is-dark .toolbar-container :deep(.ant-space-item .ant-btn-primary .anticon svg) {
  color: #ffffff !important;
  fill: currentColor !important;
  -webkit-text-fill-color: #ffffff !important;
}
</style>
