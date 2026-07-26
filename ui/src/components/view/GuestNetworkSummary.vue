<!-- Licensed to the Apache Software Foundation (ASF) under one -->
<!-- or more contributor license agreements.  See the NOTICE file -->
<!-- distributed with this work for additional information -->
<!-- regarding copyright ownership.  The ASF licenses this file -->
<!-- to you under the Apache License, Version 2.0 (the -->
<!-- "License"); you may not use this file except in compliance -->
<!-- with the License.  You may obtain a copy of the License at -->
<!-- -->
<!--   http://www.apache.org/licenses/LICENSE-2.0 -->
<!-- -->
<!-- Unless required by applicable law or agreed to in writing, -->
<!-- software distributed under the License is distributed on an -->
<!-- "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY -->
<!-- KIND, either express or implied.  See the License for the -->
<!-- specific language governing permissions and limitations -->
<!-- under the License. -->

<template>
  <div class="guest-network-summary">
    <div class="summary-line summary-line-compact">
      <a-tooltip :title="$t('label.cloud.ip')">
        <span class="summary-source">C</span>
      </a-tooltip>
      <copy-label v-if="cloudAddress" :label="cloudAddress" />
      <span v-else>-</span>
      <span class="summary-separator"></span>
      <a-tooltip :title="$t('label.guest.ip')">
        <span class="summary-source summary-source-guest">G</span>
      </a-tooltip>
      <copy-label
        v-if="primaryAddress"
        class="primary-address"
        :label="primaryAddress.address" />
      <span v-else>-</span>
      <a-popover
        v-if="remainingAddressCount > 0"
        overlayClassName="guest-network-summary-popover"
        placement="bottomLeft"
        trigger="click">
        <template #content>
          <div class="address-popover">
            <div class="address-popover-header">
              <strong>{{ $t('label.guest.ip') }} {{ allAddresses.length }}</strong>
              <copy-label
                :label="$t('label.copy.all')"
                :copyValue="allAddressCopyValue" />
            </div>
            <div
              v-for="item in allAddresses"
              :key="item.family + item.address"
              class="address-popover-row">
              <a-tag :color="item.family === 'IPv6' ? 'purple' : 'green'">
                {{ item.family }}
              </a-tag>
              <copy-label :label="item.address" />
            </div>
          </div>
        </template>
        <a-button
          class="summary-more"
          size="small"
          :aria-label="'+' + remainingAddressCount + ' ' + $t('label.more')">
          +{{ remainingAddressCount }}
        </a-button>
      </a-popover>
      <a-tag
        v-if="hasIpv6"
        class="address-family-indicator"
        color="purple">
        v6
      </a-tag>
      <a-tooltip v-if="showStatus" :title="statusTooltip">
        <a-tag class="summary-status" :color="statusColor">
          {{ statusLabel }}
        </a-tag>
      </a-tooltip>
    </div>
  </div>
</template>

<script>
import CopyLabel from '@/components/widgets/CopyLabel'

export default {
  name: 'GuestNetworkSummary',
  components: {
    CopyLabel
  },
  props: {
    cloudAddress: {
      type: String,
      default: ''
    },
    summary: {
      type: Object,
      default: () => ({})
    }
  },
  computed: {
    allAddresses () {
      return [
        ...(this.summary.ipv4addresses || []).map(address => ({ family: 'IPv4', address })),
        ...(this.summary.ipv6addresses || []).map(address => ({ family: 'IPv6', address }))
      ]
    },
    primaryAddress () {
      return this.allAddresses.find(item => item.family === 'IPv4' && this.isPreferredAddress(item.address)) ||
        this.allAddresses.find(item => item.family === 'IPv6' && this.isPreferredAddress(item.address)) ||
        this.allAddresses.find(item => this.isNonLoopbackAddress(item.address)) ||
        this.allAddresses[0] ||
        null
    },
    remainingAddressCount () {
      return Math.max(0, this.allAddresses.length - (this.primaryAddress ? 1 : 0))
    },
    hasIpv6 () {
      return (this.summary.ipv6addresses || []).length > 0
    },
    allAddressCopyValue () {
      return this.allAddresses.map(item => item.address).join('\n')
    },
    showStatus () {
      return Boolean(this.summary.status)
    },
    statusColor () {
      const colors = {
        OK: 'green',
        PARTIAL: 'orange',
        STALE: 'gold',
        STOPPED: 'default',
        UNSUPPORTED: 'red',
        UNAVAILABLE: 'red',
        NOT_COLLECTED: 'default'
      }
      return colors[this.summary.status] || 'default'
    },
    statusLabel () {
      const key = 'label.guest.network.status.' + String(this.summary.status || '').toLowerCase()
      return this.$t(key)
    },
    statusTooltip () {
      const key = 'message.guest.network.status.' + String(this.summary.status || '').toLowerCase()
      const message = this.$t(key)
      if (!this.summary.observed) {
        return message
      }
      return message + ' · ' + this.$t('label.guest.network.observed') + ': ' +
        this.$toLocaleDate(this.summary.observed)
    }
  },
  methods: {
    normalizedAddress (address) {
      return String(address || '').split('/')[0].toLowerCase()
    },
    isNonLoopbackAddress (address) {
      const normalized = this.normalizedAddress(address)
      return normalized !== '::1' && !normalized.startsWith('127.')
    },
    isPreferredAddress (address) {
      const normalized = this.normalizedAddress(address)
      return this.isNonLoopbackAddress(address) &&
        normalized !== '0.0.0.0' &&
        !normalized.startsWith('169.254.') &&
        !normalized.startsWith('fe80:')
    }
  }
}
</script>

<style scoped lang="less">
.guest-network-summary {
  min-width: 330px;
}

.summary-line {
  display: flex;
  align-items: center;
  min-height: 24px;
}

.summary-line-compact {
  gap: 6px;
  white-space: nowrap;
}

.summary-source {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  flex: 0 0 18px;
  border: 1px solid #d9d9d9;
  border-radius: 50%;
  color: rgba(0, 0, 0, 0.45);
  font-size: 10px;
  font-weight: 600;
  line-height: 16px;
}

.summary-source-guest {
  color: #096dd9;
  border-color: #91d5ff;
}

.summary-separator {
  width: 1px;
  height: 18px;
  margin: 0 2px;
  background: #e8e8e8;
}

.primary-address {
  color: rgba(0, 0, 0, 0.85);
}

.summary-more {
  height: 20px;
  padding: 0 7px;
  color: #096dd9;
  font-size: 11px;
  line-height: 18px;
  border-radius: 10px;
}

.address-family-indicator,
.summary-status {
  margin: 0;
  padding: 0 6px;
  font-size: 10px;
  line-height: 18px;
  border-radius: 10px;
}

.address-popover {
  min-width: 310px;
  max-width: 520px;
  max-height: 280px;
  overflow-y: auto;
}

.address-popover-header,
.address-popover-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.address-popover-header {
  justify-content: space-between;
  margin-bottom: 8px;
}

.address-popover-row {
  margin: 5px 0;
}

.address-popover-row .ant-tag {
  min-width: 42px;
  margin: 0;
  font-size: 10px;
  text-align: center;
}
</style>
