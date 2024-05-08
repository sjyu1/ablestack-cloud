// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

<template>
  <div class="form-layout">
    <a-spin :spinning="loading">
      <a-form
        :ref="formRef"
        :model="form"
        :rules="rules"
        @finish="handleSubmit"
        v-ctrl-enter="handleSubmit"
        class="form"
        layout="vertical"
      >
        <a-form-item name="vlan" ref="vlan">
          <template #label>
            <tooltip-label :title="$t('label.vlan')" />
          </template>
          <a-input-number v-model:value="vlan" :min="1" :max="2000" />
        </a-form-item>
        <div :span="24" class="action-button">
          <a-button @click="$emit('close-action')">{{ $t('label.cancel') }}</a-button>
          <a-button type="primary" @click="handleSubmit" ref="submit">{{ $t('label.ok') }}</a-button>
        </div>
      </a-form>
    </a-spin>
  </div>
</template>

<script>
import { ref, reactive } from 'vue'
import { api } from '@/api'
import TooltipLabel from '@/components/widgets/TooltipLabel'

export default {
  name: 'UpdateRouterGuestVlan',
  components: {
    TooltipLabel
  },
  props: {
    resource: {
      type: Object,
      required: true
    }
  },
  data () {
    return {
      data: [],
      loading: false,
      vlan: 1
    }
  },
  created () {
    this.initForm()
    this.data = this.resource.nic.filter(val => val.traffictype === 'Guest')
    this.vlan = this.data[0].broadcasturi.replaceAll('vlan://', '')
  },
  beforeCreate () {
  },
  methods: {
    initForm () {
      this.formRef = ref()
      this.form = reactive({})
      this.rules = reactive({})
    },
    handleSubmit (e) {
      e.preventDefault()
      this.formRef.value.validate().then(() => {
        var params = {
          id: this.resource.id,
          vlan: this.vlan
        }
        this.loading = true
        api('updateRouterGuestVlan', params).then(json => {
          this.$emit('refresh-data')
          this.$notification.success({
            message: this.$t('label.action.update.guest.vlan'),
            description: this.$t('label.action.update.guest.vlan.desc')
          })
          this.$emit('close-action')
        }).catch(error => {
          this.$notifyError(error)
        }).finally(() => {
          this.loading = false
        })
      })
    }
  }
}

</script>

<style scoped>
.reason {
  padding-top: 20px
}

.form-layout {
    width: 30vw;

    @media (min-width: 500px) {
      width: 450px;
    }
  }
</style>
