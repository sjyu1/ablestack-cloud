<!-- Licensed to the Apache Software Foundation (ASF) under one or more
contributor license agreements. See the NOTICE file for copyright ownership.
Licensed under the Apache License, Version 2.0 (the "License"); you may obtain
a copy at http://www.apache.org/licenses/LICENSE-2.0 . Distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND. -->
<template>
        <a-descriptions class="mold-dialog-section" :column="1" bordered size="small">
          <a-descriptions-item :label="$t('label.virtualmachinename')">{{ snapshot.virtualmachinename }} · {{ snapshot.virtualmachineinstancename || '—' }}</a-descriptions-item>
          <a-descriptions-item :label="$t('label.virtualmachineid')"><CopyLabel :label="snapshot.virtualmachineid" /></a-descriptions-item>
          <a-descriptions-item :label="$t('label.displayname')">{{ snapshot.displayname || snapshot.name }}</a-descriptions-item>
          <a-descriptions-item :label="$t('label.id')"><CopyLabel :label="snapshot.id" /></a-descriptions-item>
          <a-descriptions-item :label="$t('label.name')">{{ snapshot.name }}</a-descriptions-item>
          <a-descriptions-item :label="$t('label.description')">{{ snapshot.description || '—' }}</a-descriptions-item>
          <a-descriptions-item :label="$t('label.created')">{{ $toLocaleDate(snapshot.created) }} ({{ timezone }})</a-descriptions-item>
          <a-descriptions-item :label="$t('label.type')">{{ $t(snapshot.type === 'DiskAndMemory' ? 'label.vmsnapshot.disk.memory' : 'label.vmsnapshot.disk') }}</a-descriptions-item>
          <a-descriptions-item :label="$t('label.state')"><Status :text="snapshot.state" display-text /> · {{ $t('label.vm') }}: {{ snapshot.virtualmachinestate || '—' }}</a-descriptions-item>
          <a-descriptions-item :label="$t('label.current')">{{ $t(snapshot.current ? 'label.vmsnapshot.current.yes' : 'label.vmsnapshot.current.no') }} — {{ $t('message.vmsnapshot.current.reference') }}</a-descriptions-item>
          <a-descriptions-item :label="$t('label.parentname')">{{ snapshot.parentName || '—' }}<CopyLabel v-if="snapshot.parent" :label="snapshot.parent" /></a-descriptions-item>
        </a-descriptions>
</template>
<script>
import CopyLabel from '@/components/widgets/CopyLabel'
import Status from '@/components/widgets/Status'
export default {
  name: 'VmSnapshotSummary',
  components: { CopyLabel, Status },
  props: { snapshot: { type: Object, required: true } },
  computed: { timezone () { return Intl.DateTimeFormat().resolvedOptions().timeZone } }
}
</script>
