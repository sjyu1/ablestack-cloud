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
    <div class="summary-line">
      <span class="summary-label">{{ $t('label.cloud.ip') }}</span>
      <copy-label v-if="cloudAddress" :label="cloudAddress" />
      <span v-else>-</span>
    </div>
    <div class="summary-line">
      <span class="summary-label">{{ $t('label.guest.ip') }}</span>
      <span v-if="visibleAddresses.length">
        <a-tag
          v-for="item in visibleAddresses"
          :key="item.family + item.address"
          :color="item.family === 'IPv6' ? 'purple' : 'green'"
          class="address-tag">
          {{ item.address }}
        </a-tag>
        <a-tooltip v-if="hiddenAddressCount > 0" :title="hiddenAddresses.join(', ')">
          <a-tag>+{{ hiddenAddressCount }} {{ $t('label.more') }}</a-tag>
        </a-tooltip>
      </span>
      <span v-else>-</span>
      <a-tooltip v-if="showStatus" :title="statusTooltip">
        <a-tag :color="statusColor">{{ summary.status }}</a-tag>
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
    visibleAddresses () {
      return this.allAddresses.slice(0, 3)
    },
    hiddenAddresses () {
      return this.allAddresses.slice(3).map(item => item.address)
    },
    hiddenAddressCount () {
      return this.hiddenAddresses.length
    },
    showStatus () {
      return this.summary.status && this.summary.status !== 'OK'
    },
    statusColor () {
      const colors = {
        PARTIAL: 'orange',
        STALE: 'gold',
        STOPPED: 'default',
        UNSUPPORTED: 'red',
        UNAVAILABLE: 'red',
        NOT_COLLECTED: 'default'
      }
      return colors[this.summary.status] || 'default'
    },
    statusTooltip () {
      const key = 'message.guest.network.status.' + String(this.summary.status || '').toLowerCase()
      return this.$t(key)
    }
  }
}
</script>

<style scoped lang="less">
.guest-network-summary {
  min-width: 260px;
}

.summary-line {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  min-height: 28px;
}

.summary-label {
  width: 54px;
  margin-right: 8px;
  color: rgba(0, 0, 0, 0.45);
  font-size: 12px;
}

.address-tag {
  margin-bottom: 2px;
}
</style>
