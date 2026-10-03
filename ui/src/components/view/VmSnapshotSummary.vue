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
<template>
        <a-descriptions class="mold-dialog-section" :column="1" bordered size="small">
          <a-descriptions-item :label="$t('label.virtualmachinename')">{{ snapshot.virtualmachinename }} · {{ snapshot.virtualmachineinstancename || '—' }}</a-descriptions-item>
          <a-descriptions-item :label="$t('label.virtualmachineid')"><CopyLabel :label="snapshot.virtualmachineid" /></a-descriptions-item>
          <a-descriptions-item v-if="vmOnly" :label="$t('label.state')"><Status :text="snapshot.virtualmachinestate" display-text /></a-descriptions-item>
          <template v-if="!vmOnly">
          <a-descriptions-item :label="$t('label.displayname')">{{ snapshot.displayname || snapshot.name }}</a-descriptions-item>
          <a-descriptions-item :label="$t('label.id')"><CopyLabel :label="snapshot.id" /></a-descriptions-item>
          <a-descriptions-item :label="$t('label.name')">{{ snapshot.name }}</a-descriptions-item>
          <a-descriptions-item :label="$t('label.description')">{{ snapshot.description || '—' }}</a-descriptions-item>
          <a-descriptions-item :label="$t('label.created')">{{ $toLocaleDate(snapshot.created) }} ({{ timezone }})</a-descriptions-item>
          <a-descriptions-item :label="$t('label.type')">{{ $t(snapshot.type === 'DiskAndMemory' ? 'label.vmsnapshot.disk.memory' : 'label.vmsnapshot.disk') }}</a-descriptions-item>
          <a-descriptions-item :label="$t('label.state')"><Status :text="snapshot.state" display-text /> · {{ $t('label.vm') }}: <Status v-if="snapshot.virtualmachinestate" :text="snapshot.virtualmachinestate" display-text :show-tooltip="false" /><span v-else>—</span></a-descriptions-item>
          <a-descriptions-item :label="$t('label.current')">{{ $t(snapshot.current ? 'label.vmsnapshot.current.yes' : 'label.vmsnapshot.current.no') }} — {{ $t('message.vmsnapshot.current.reference') }}</a-descriptions-item>
          <a-descriptions-item :label="$t('label.parentname')">{{ snapshot.parentName || '—' }}<CopyLabel v-if="snapshot.parent" :label="snapshot.parent" /></a-descriptions-item>
          </template>
        </a-descriptions>
</template>
<script>
import CopyLabel from '@/components/widgets/CopyLabel'
import Status from '@/components/widgets/Status'
export default {
  name: 'VmSnapshotSummary',
  components: { CopyLabel, Status },
  props: { snapshot: { type: Object, required: true }, vmOnly: { type: Boolean, default: false } },
  computed: { timezone () { return Intl.DateTimeFormat().resolvedOptions().timeZone } }
}
</script>
