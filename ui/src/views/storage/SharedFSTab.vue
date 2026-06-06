<template>
  <a-spin :spinning="storageService.initialLoading">
    <a-tabs
      class="storage-service-tabs"
      :activeKey="currentTab"
      tabPosition="top"
      :animated="false"
      @change="handleChangeTab">
      <a-tab-pane :tab="$t('label.details')" key="details">
        <DetailsTab :resource="dataResource" :loading="loading" />
        <div v-if="hasStorageServiceApi" class="storage-service storage-service--overview">
          <h3 class="storage-service__section-title">{{ $t('label.storage.service.overview') }}</h3>
          <a-alert
            v-if="!storageService.instance && !storageService.loading"
            class="storage-service__alert"
            type="warning"
            show-icon
            :message="$t('message.storage.service.no.mirror')"
            :description="$t('message.storage.service.no.mirror.description')" />
          <template v-if="storageService.instance">
            <dl class="storage-detail-list">
              <div class="storage-detail-list__row">
                <dt>{{ $t('label.storage.service.active.services') }}</dt>
                <dd>
                  <template v-if="activeServiceTypes.length > 0">
                    <a-tag v-for="service in activeServiceTypes" :key="service" color="blue">{{ service }}</a-tag>
                  </template>
                  <template v-else>-</template>
                </dd>
              </div>
            </dl>
          </template>
        </div>
      </a-tab-pane>

      <a-tab-pane v-if="hasStorageServiceApi" tab="NFS" key="nfs">
        <div class="storage-service storage-service--protocol" :class="{ 'storage-service--wide': protocolWideLayout }">
          <div class="storage-protocol-topbar">
            <protocol-header protocol="NFS" />
            <a-space v-if="storageService.instance" class="protocol-toolbar protocol-toolbar--right" wrap>
              <a-button @click="toggleProtocolWideLayout">
                <template #icon><FullscreenExitOutlined v-if="protocolWideLayout" /><FullscreenOutlined v-else /></template>
                {{ protocolWideLayout ? $t('label.storage.service.default.view') : $t('label.storage.service.wide.view') }}
              </a-button>
              <a-button type="primary" @click="openActionModal('enableProtocol', { protocol: 'NFS' })">
                <template #icon><PoweroffOutlined /></template>
                {{ $t('label.storage.service.enable.protocol') }}
              </a-button>
              <a-button danger @click="openActionModal('deleteEndpoint', { protocol: 'NFS' })">
                <template #icon><DeleteOutlined /></template>
                {{ $t('label.storage.service.delete.endpoint') }}
              </a-button>
              <a-button @click="openActionModal('nfsExport')">
                <template #icon><PlusOutlined /></template>
                {{ $t('label.storage.service.create.nfs.export') }}
              </a-button>
              <a-button :loading="storageService.refreshing" @click="fetchStorageServiceData">
                <template #icon><ReloadOutlined /></template>
                {{ $t('label.refresh') }}
              </a-button>
            </a-space>
          </div>
          <template v-if="storageService.instance">
            <div class="storage-protocol-grid">
              <section class="storage-panel storage-panel--connection">
                <div class="storage-panel__title">{{ $t('label.storage.service.connection.info') }}</div>
                <p class="storage-panel__description">{{ $t('message.storage.service.nfs.connection.generic') }}</p>
                <div v-for="command in nfsConnectionCommands" :key="command" class="command-line command-line--copyable">
                  {{ command }}
                </div>
              </section>
              <section class="storage-panel storage-panel--status">
                <div class="storage-panel__title">{{ $t('label.storage.service.status.summary') }}</div>
                <dl class="storage-kv storage-kv--compact">
                  <dt>{{ $t('label.storage.service.endpoint') }}</dt>
                  <dd><ellipsis-text :value="serviceEndpointSummary || '-'" /></dd>
                  <dt>{{ $t('label.storage.service.monitor.cache') }}</dt>
                  <dd>
                    <a-tag :color="monitorCacheColor">{{ monitorCacheLabel }}</a-tag>
                  </dd>
                  <dt>{{ $t('label.storage.service.last.refresh') }}</dt>
                  <dd><ellipsis-text :value="monitorCacheTimestamp" /></dd>
                </dl>
              </section>
            </div>

            <a-alert
              v-if="nfsFailedExportMessages.length"
              class="storage-service__alert"
              type="error"
              show-icon
              :message="$t('message.storage.service.nfs.initial.setup.failed')">
              <template #description>
                <ul class="storage-service-error-list">
                  <li v-for="message in nfsFailedExportMessages" :key="message">{{ message }}</li>
                </ul>
              </template>
            </a-alert>

            <section class="storage-table-section">
              <div class="storage-table-section__header">
                <div>
                  <h4>{{ $t('label.storage.service.nfs.exports') }}</h4>
                  <p>{{ $t('message.storage.service.nfs.exports.table') }}</p>
                </div>
              </div>
              <a-table
                class="storage-data-table"
                size="small"
                rowKey="key"
                :columns="nfsExportColumns"
                :dataSource="nfsExportRows"
                :pagination="false"
                :scroll="{ x: 1580 }"
                :locale="storageTableLocale('message.storage.service.no.nfs.exports')">
                <template #bodyCell="{ column, record }">
                  <template v-if="column.key === 'state'">
                    <a-tag :color="runtimeColor(record)">{{ storageCellValue(record, column) }}</a-tag>
                  </template>
                  <template v-else-if="column.key === 'actions'">
                    <a-space>
                      <a-button size="small" @click="openActionModal('editNfsExport', record)">
                        <template #icon><EditOutlined /></template>
                        {{ $t('label.edit') }}
                      </a-button>
                      <a-button size="small" @click="openActionModal('resizeShare', { id: record.id })">
                        <template #icon><ExpandAltOutlined /></template>
                        {{ $t('label.storage.service.resize.file.share') }}
                      </a-button>
                      <a-button size="small" danger @click="openDeleteModal('nfsExport', record)">
                        <template #icon><DeleteOutlined /></template>
                        {{ $t('label.delete') }}
                      </a-button>
                    </a-space>
                  </template>
                  <template v-else>
                    <ellipsis-text :value="storageCellValue(record, column)" :code="column.code" />
                  </template>
                </template>
              </a-table>
            </section>

            <section class="storage-table-section">
              <div class="storage-table-section__header">
                <div>
                  <h4>{{ $t('label.storage.service.access.rules') }}</h4>
                  <p>{{ $t('message.storage.service.nfs.acls.table') }}</p>
                </div>
                <a-space class="storage-section-actions">
                  <a-button @click="openActionModal('nfsAcl')">
                    <template #icon><SafetyCertificateOutlined /></template>
                    {{ $t('label.storage.service.create.nfs.acl') }}
                  </a-button>
                </a-space>
              </div>
              <a-table
                class="storage-data-table"
                size="small"
                rowKey="key"
                :columns="nfsAclColumns"
                :dataSource="nfsAclRows"
                :pagination="false"
                :scroll="{ x: 1360 }"
                :locale="storageTableLocale('message.storage.service.no.access.rules')">
                <template #bodyCell="{ column, record }">
                  <template v-if="column.key === 'state'">
                    <a-tag :color="runtimeColor(record)">{{ storageCellValue(record, column) }}</a-tag>
                  </template>
                  <template v-else-if="column.key === 'actions'">
                    <a-space>
                      <a-button size="small" @click="openActionModal('editNfsAcl', record)">
                        <template #icon><EditOutlined /></template>
                        {{ $t('label.edit') }}
                      </a-button>
                      <a-button size="small" danger @click="openDeleteModal('nfsAcl', record)">
                        <template #icon><DeleteOutlined /></template>
                        {{ $t('label.delete') }}
                      </a-button>
                    </a-space>
                  </template>
                  <template v-else>
                    <ellipsis-text :value="storageCellValue(record, column)" :code="column.code" />
                  </template>
                </template>
              </a-table>
            </section>

            <section class="storage-table-section">
              <div class="storage-table-section__header">
                <div>
                  <h4>{{ $t('label.storage.service.backing.volumes') }}</h4>
                  <p>{{ $t('message.storage.service.nfs.volumes.table') }}</p>
                </div>
              </div>
              <a-table
                class="storage-data-table"
                size="small"
                rowKey="key"
                :columns="nfsVolumeColumns"
                :dataSource="nfsVolumeRows"
                :pagination="false"
                :scroll="{ x: 1490 }"
                :locale="storageTableLocale('message.storage.service.no.backing.volumes')">
                <template #bodyCell="{ column, record }">
                  <template v-if="column.key === 'state'">
                    <a-tag :color="runtimeColor(record)">{{ storageCellValue(record, column) }}</a-tag>
                  </template>
                  <template v-else-if="column.key === 'actions'">
                    <a-button size="small" :disabled="!record.exportId" @click="openActionModal('resizeVolume', { id: record.exportId, volumeid: record.id })">
                      <template #icon><ExpandAltOutlined /></template>
                      {{ $t('label.storage.service.resize.volume') }}
                    </a-button>
                    <a-button size="small" danger :disabled="!record.detachAllowed" @click="openActionModal('detachBackingVolume', record)">
                      <template #icon><DisconnectOutlined /></template>
                      {{ $t('label.storage.service.detach.backing.volume') }}
                    </a-button>
                  </template>
                  <template v-else>
                    <ellipsis-text :value="storageCellValue(record, column)" :code="column.code" />
                  </template>
                </template>
              </a-table>
            </section>

            <section class="storage-table-section">
              <div class="storage-table-section__header">
                <div>
                  <h4>{{ $t('label.storage.service.sessions') }}</h4>
                  <p>{{ $t('message.storage.service.nfs.sessions.table') }}</p>
                </div>
              </div>
              <a-table
                class="storage-data-table"
                size="small"
                rowKey="key"
                :columns="nfsSessionColumns"
                :dataSource="nfsSessionRows"
                :pagination="false"
                :scroll="{ x: 1160 }"
                :locale="storageTableLocale('message.storage.service.no.sessions')">
                <template #bodyCell="{ column, record }">
                  <template v-if="column.key === 'state'">
                    <a-tag :color="runtimeColor(record)">{{ storageCellValue(record, column) }}</a-tag>
                  </template>
                  <template v-else-if="column.key === 'actions'">
                    <a-button size="small" danger @click="openActionModal('disconnectSession', record)">
                      <template #icon><DisconnectOutlined /></template>
                      {{ $t('label.storage.service.disconnect.session') }}
                    </a-button>
                  </template>
                  <template v-else>
                    <ellipsis-text :value="storageCellValue(record, column)" :code="column.code" />
                  </template>
                </template>
              </a-table>
            </section>
          </template>
        </div>
      </a-tab-pane>

      <a-tab-pane v-if="hasStorageServiceApi" tab="SMB" key="smb">
        <div class="storage-service storage-service--protocol" :class="{ 'storage-service--wide': protocolWideLayout }">
          <div class="storage-protocol-topbar">
            <protocol-header protocol="SMB" />
            <a-space v-if="storageService.instance" class="protocol-toolbar protocol-toolbar--right" wrap>
              <a-button @click="toggleProtocolWideLayout">
                <template #icon><FullscreenExitOutlined v-if="protocolWideLayout" /><FullscreenOutlined v-else /></template>
                {{ protocolWideLayout ? $t('label.storage.service.default.view') : $t('label.storage.service.wide.view') }}
              </a-button>
              <a-button type="primary" @click="openActionModal('enableProtocol', { protocol: 'SMB' })">
                <template #icon><PoweroffOutlined /></template>
                {{ $t('label.storage.service.enable.protocol') }}
              </a-button>
              <a-button @click="openActionModal('smbShare')">
                <template #icon><PlusOutlined /></template>
                {{ $t('label.storage.service.create.smb.share') }}
              </a-button>
              <a-button @click="openActionModal('adJoin')">
                <template #icon><LinkOutlined /></template>
                {{ $t('label.storage.service.join.ad.domain') }}
              </a-button>
              <a-button :loading="storageService.refreshing" @click="fetchStorageServiceData">
                <template #icon><ReloadOutlined /></template>
                {{ $t('label.refresh') }}
              </a-button>
            </a-space>
          </div>
          <template v-if="storageService.instance">
            <a-alert
              v-if="smbSetupIncomplete"
              class="storage-service__alert"
              type="warning"
              show-icon
              :message="$t('message.storage.service.smb.setup.incomplete')" />
            <div class="storage-protocol-grid">
              <section class="storage-panel storage-panel--connection">
                <div class="storage-panel__title">{{ $t('label.storage.service.connection.info') }}</div>
                <p class="storage-panel__description">{{ $t('message.storage.service.smb.connection.generic') }}</p>
                <div v-for="command in smbConnectionCommands" :key="command" class="command-line command-line--copyable">
                  {{ command }}
                </div>
              </section>
              <section class="storage-panel storage-panel--status">
                <div class="storage-panel__title">{{ $t('label.storage.service.status.summary') }}</div>
                <dl class="storage-kv storage-kv--compact">
                  <dt>{{ $t('label.storage.service.endpoint') }}</dt>
                  <dd><ellipsis-text :value="smbEndpoint" code /></dd>
                  <dt>{{ $t('label.storage.service.smb.identity.mode') }}</dt>
                  <dd><ellipsis-text :value="smbIdentityMode" /></dd>
                  <dt>{{ $t('label.storage.service.domain.join.state') }}</dt>
                  <dd><ellipsis-text :value="smbDomainState" /></dd>
                  <dt>{{ $t('label.storage.service.daemon.state') }}</dt>
                  <dd><ellipsis-text :value="smbDaemonState" /></dd>
                  <dt>{{ $t('label.storage.service.monitor.cache') }}</dt>
                  <dd>
                    <a-tag :color="monitorCacheColor">{{ monitorCacheLabel }}</a-tag>
                  </dd>
                  <dt>{{ $t('label.storage.service.last.refresh') }}</dt>
                  <dd><ellipsis-text :value="monitorCacheTimestamp" /></dd>
                </dl>
              </section>
            </div>

            <section class="storage-table-section">
              <div class="storage-table-section__header">
                <div>
                  <h4>{{ $t('label.storage.service.smb.shares') }}</h4>
                  <p>{{ $t('message.storage.service.smb.shares.table') }}</p>
                </div>
              </div>
              <a-table
                class="storage-data-table"
                size="small"
                rowKey="key"
                :columns="smbShareColumns"
                :dataSource="smbShareRows"
                :pagination="false"
                :scroll="{ x: 1540 }"
                :locale="storageTableLocale('message.storage.service.no.smb.shares')">
                <template #bodyCell="{ column, record }">
                  <template v-if="column.key === 'state'">
                    <a-tag :color="runtimeColor(record)">{{ storageCellValue(record, column) }}</a-tag>
                  </template>
                  <template v-else-if="column.key === 'actions'">
                    <a-button size="small" @click="openActionModal('resizeShare', { id: record.id })">
                      <template #icon><ExpandAltOutlined /></template>
                      {{ $t('label.storage.service.resize.file.share') }}
                    </a-button>
                  </template>
                  <template v-else>
                    <ellipsis-text :value="storageCellValue(record, column)" :code="column.code" />
                  </template>
                </template>
              </a-table>
            </section>

            <section class="storage-table-section">
              <div class="storage-table-section__header">
                <div>
                  <h4>{{ $t('label.storage.service.smb.access.accounts') }}</h4>
                  <p>{{ $t('message.storage.service.smb.acls.table') }}</p>
                </div>
                <a-space class="storage-section-actions">
                  <a-button @click="openActionModal('smbAcl')">
                    <template #icon><SafetyCertificateOutlined /></template>
                    {{ $t('label.storage.service.create.smb.acl') }}
                  </a-button>
                </a-space>
              </div>
              <a-table
                class="storage-data-table"
                size="small"
                rowKey="key"
                :columns="smbAclColumns"
                :dataSource="smbAclRows"
                :pagination="false"
                :scroll="{ x: 1230 }"
                :locale="storageTableLocale('message.storage.service.no.access.rules')">
                <template #bodyCell="{ column, record }">
                  <template v-if="column.key === 'state'">
                    <a-tag :color="runtimeColor(record)">{{ storageCellValue(record, column) }}</a-tag>
                  </template>
                  <template v-else>
                    <ellipsis-text :value="storageCellValue(record, column)" :code="column.code" />
                  </template>
                </template>
              </a-table>
            </section>

            <section class="storage-table-section">
              <div class="storage-table-section__header">
                <div>
                  <h4>{{ $t('label.storage.service.smb.identity') }}</h4>
                  <p>{{ $t('message.storage.service.smb.identity.table') }}</p>
                </div>
              </div>
              <dl class="storage-detail-list">
                <div class="storage-detail-list__row">
                  <dt>{{ $t('label.storage.service.smb.identity.mode') }}</dt>
                  <dd><ellipsis-text :value="smbIdentityMode" /></dd>
                </div>
                <div class="storage-detail-list__row">
                  <dt>{{ $t('label.storage.service.ad.domain') }}</dt>
                  <dd><ellipsis-text :value="smbDomainName" /></dd>
                </div>
                <div class="storage-detail-list__row">
                  <dt>{{ $t('label.storage.service.workgroup') }}</dt>
                  <dd><ellipsis-text :value="smbWorkgroup" /></dd>
                </div>
                <div class="storage-detail-list__row">
                  <dt>{{ $t('label.storage.service.domain.join.state') }}</dt>
                  <dd><ellipsis-text :value="smbDomainState" /></dd>
                </div>
              </dl>
            </section>

            <section class="storage-table-section">
              <div class="storage-table-section__header">
                <div>
                  <h4>{{ $t('label.storage.service.backing.volumes') }}</h4>
                  <p>{{ $t('message.storage.service.smb.volumes.table') }}</p>
                </div>
              </div>
              <a-table
                class="storage-data-table"
                size="small"
                rowKey="key"
                :columns="smbVolumeColumns"
                :dataSource="smbVolumeRows"
                :pagination="false"
                :scroll="{ x: 1490 }"
                :locale="storageTableLocale('message.storage.service.no.backing.volumes')">
                <template #bodyCell="{ column, record }">
                  <template v-if="column.key === 'state'">
                    <a-tag :color="runtimeColor(record)">{{ storageCellValue(record, column) }}</a-tag>
                  </template>
                  <template v-else-if="column.key === 'actions'">
                    <a-button size="small" @click="openActionModal('resizeVolume', { id: record.shareId, volumeid: record.id })">
                      <template #icon><ExpandAltOutlined /></template>
                      {{ $t('label.storage.service.resize.volume') }}
                    </a-button>
                  </template>
                  <template v-else>
                    <ellipsis-text :value="storageCellValue(record, column)" :code="column.code" />
                  </template>
                </template>
              </a-table>
            </section>

            <section class="storage-table-section">
              <div class="storage-table-section__header">
                <div>
                  <h4>{{ $t('label.storage.service.sessions') }}</h4>
                  <p>{{ $t('message.storage.service.smb.sessions.table') }}</p>
                </div>
              </div>
              <a-table
                class="storage-data-table"
                size="small"
                rowKey="key"
                :columns="smbSessionColumns"
                :dataSource="smbSessionRows"
                :pagination="false"
                :scroll="{ x: 1320 }"
                :locale="storageTableLocale('message.storage.service.no.sessions')">
                <template #bodyCell="{ column, record }">
                  <template v-if="column.key === 'state'">
                    <a-tag :color="runtimeColor(record)">{{ storageCellValue(record, column) }}</a-tag>
                  </template>
                  <template v-else-if="column.key === 'actions'">
                    <a-button size="small" danger @click="openActionModal('disconnectSession', record)">
                      <template #icon><DisconnectOutlined /></template>
                      {{ $t('label.storage.service.disconnect.session') }}
                    </a-button>
                  </template>
                  <template v-else>
                    <ellipsis-text :value="storageCellValue(record, column)" :code="column.code" />
                  </template>
                </template>
              </a-table>
            </section>
          </template>
        </div>
      </a-tab-pane>

      <a-tab-pane v-if="hasStorageServiceApi" tab="iSCSI" key="iscsi">
        <div class="storage-service storage-service--protocol" :class="{ 'storage-service--wide': protocolWideLayout }">
          <div class="storage-protocol-topbar">
            <protocol-header protocol="ISCSI" />
            <a-space v-if="storageService.instance" class="protocol-toolbar protocol-toolbar--right" wrap>
              <a-button @click="toggleProtocolWideLayout">
                <template #icon><FullscreenExitOutlined v-if="protocolWideLayout" /><FullscreenOutlined v-else /></template>
                {{ protocolWideLayout ? $t('label.storage.service.default.view') : $t('label.storage.service.wide.view') }}
              </a-button>
              <a-button type="primary" @click="openActionModal('enableProtocol', { protocol: 'ISCSI' })">
                <template #icon><PoweroffOutlined /></template>
                {{ $t('label.storage.service.enable.protocol') }}
              </a-button>
              <a-button @click="openActionModal('iscsiTarget')">
                <template #icon><PlusOutlined /></template>
                {{ $t('label.storage.service.create.iscsi.target') }}
              </a-button>
              <a-button @click="openActionModal('iscsiAcl')">
                <template #icon><SafetyCertificateOutlined /></template>
                {{ $t('label.storage.service.create.iscsi.acl') }}
              </a-button>
              <a-button :loading="storageService.refreshing" @click="fetchStorageServiceData">
                <template #icon><ReloadOutlined /></template>
                {{ $t('label.refresh') }}
              </a-button>
            </a-space>
          </div>
          <template v-if="storageService.instance">
            <div class="storage-protocol-grid">
              <section class="storage-panel storage-panel--connection">
                <div class="storage-panel__title">{{ $t('label.storage.service.connection.info') }}</div>
                <p class="storage-panel__description">{{ $t('message.storage.service.iscsi.connection.generic') }}</p>
                <div v-for="command in iscsiConnectionCommands" :key="command" class="command-line command-line--copyable">
                  {{ command }}
                </div>
              </section>
              <section class="storage-panel storage-panel--status">
                <div class="storage-panel__title">{{ $t('label.storage.service.status.summary') }}</div>
                <dl class="storage-kv storage-kv--compact">
                  <dt>{{ $t('label.storage.service.endpoint') }}</dt>
                  <dd><ellipsis-text :value="`${serviceEndpoint || '<service-ip>'}:3260`" code /></dd>
                  <dt>{{ $t('label.storage.service.monitor.cache') }}</dt>
                  <dd><a-tag :color="monitorCacheColor">{{ monitorCacheLabel }}</a-tag></dd>
                  <dt>{{ $t('label.storage.service.last.refresh') }}</dt>
                  <dd><ellipsis-text :value="monitorCacheTimestamp" /></dd>
                </dl>
              </section>
            </div>
            <section class="storage-table-section">
              <div class="storage-table-section__header">
                <div>
                  <h4>{{ $t('label.storage.service.iscsi.targets') }}</h4>
                  <p>{{ $t('message.storage.service.iscsi.targets.table') }}</p>
                </div>
              </div>
              <a-table
                class="storage-data-table"
                size="small"
                rowKey="key"
                :columns="iscsiTargetColumns"
                :dataSource="iscsiTargetRows"
                :pagination="false"
                :scroll="{ x: 1500 }"
                :locale="storageTableLocale('message.storage.service.no.iscsi.targets')">
                <template #bodyCell="{ column, record }">
                  <template v-if="column.key === 'state'">
                    <a-tag :color="runtimeColor(record)">{{ storageCellValue(record, column) }}</a-tag>
                  </template>
                  <template v-else>
                    <ellipsis-text :value="storageCellValue(record, column)" :code="column.code" />
                  </template>
                </template>
              </a-table>
            </section>

            <section class="storage-table-section">
              <div class="storage-table-section__header">
                <div>
                  <h4>{{ $t('label.storage.service.access.rules') }}</h4>
                  <p>{{ $t('message.storage.service.iscsi.acls.table') }}</p>
                </div>
                <a-space class="storage-section-actions">
                  <a-button @click="openActionModal('iscsiAcl')">
                    <template #icon><SafetyCertificateOutlined /></template>
                    {{ $t('label.storage.service.create.iscsi.acl') }}
                  </a-button>
                </a-space>
              </div>
              <a-table
                class="storage-data-table"
                size="small"
                rowKey="key"
                :columns="iscsiAclColumns"
                :dataSource="iscsiAclRows"
                :pagination="false"
                :scroll="{ x: 1380 }"
                :locale="storageTableLocale('message.storage.service.no.access.rules')">
                <template #bodyCell="{ column, record }">
                  <template v-if="column.key === 'state'">
                    <a-tag :color="runtimeColor(record)">{{ storageCellValue(record, column) }}</a-tag>
                  </template>
                  <template v-else>
                    <ellipsis-text :value="storageCellValue(record, column)" :code="column.code" />
                  </template>
                </template>
              </a-table>
            </section>

            <section class="storage-table-section">
              <div class="storage-table-section__header">
                <div>
                  <h4>{{ $t('label.storage.service.backing.volumes') }}</h4>
                  <p>{{ $t('message.storage.service.iscsi.volumes.table') }}</p>
                </div>
              </div>
              <a-table
                class="storage-data-table"
                size="small"
                rowKey="key"
                :columns="iscsiVolumeColumns"
                :dataSource="iscsiVolumeRows"
                :pagination="false"
                :scroll="{ x: 1480 }"
                :locale="storageTableLocale('message.storage.service.no.backing.volumes')">
                <template #bodyCell="{ column, record }">
                  <template v-if="column.key === 'state'">
                    <a-tag :color="runtimeColor(record)">{{ storageCellValue(record, column) }}</a-tag>
                  </template>
                  <template v-else-if="column.key === 'actions'">
                    <a-button size="small" @click="openActionModal('resizeVolume', { id: record.targetId, volumeid: record.id })">
                      <template #icon><ExpandAltOutlined /></template>
                      {{ $t('label.storage.service.resize.volume') }}
                    </a-button>
                  </template>
                  <template v-else>
                    <ellipsis-text :value="storageCellValue(record, column)" :code="column.code" />
                  </template>
                </template>
              </a-table>
            </section>

            <section class="storage-table-section">
              <div class="storage-table-section__header">
                <div>
                  <h4>{{ $t('label.storage.service.sessions') }}</h4>
                  <p>{{ $t('message.storage.service.iscsi.sessions.table') }}</p>
                </div>
              </div>
              <a-table
                class="storage-data-table"
                size="small"
                rowKey="key"
                :columns="iscsiSessionColumns"
                :dataSource="iscsiSessionRows"
                :pagination="false"
                :scroll="{ x: 1160 }"
                :locale="storageTableLocale('message.storage.service.no.sessions')">
                <template #bodyCell="{ column, record }">
                  <template v-if="column.key === 'state'">
                    <a-tag :color="runtimeColor(record)">{{ storageCellValue(record, column) }}</a-tag>
                  </template>
                  <template v-else-if="column.key === 'actions'">
                    <a-button size="small" danger @click="openActionModal('disconnectSession', record)">
                      <template #icon><DisconnectOutlined /></template>
                      {{ $t('label.storage.service.disconnect.session') }}
                    </a-button>
                  </template>
                  <template v-else>
                    <ellipsis-text :value="storageCellValue(record, column)" :code="column.code" />
                  </template>
                </template>
              </a-table>
            </section>
          </template>
        </div>
      </a-tab-pane>

      <a-tab-pane v-if="hasStorageServiceApi" tab="NVMe-oF" key="nvmeof">
        <div class="storage-service storage-service--protocol" :class="{ 'storage-service--wide': protocolWideLayout }">
          <div class="storage-protocol-topbar">
            <protocol-header protocol="NVME_OF" />
            <a-space v-if="storageService.instance" class="protocol-toolbar protocol-toolbar--right" wrap>
              <a-button @click="toggleProtocolWideLayout">
                <template #icon><FullscreenExitOutlined v-if="protocolWideLayout" /><FullscreenOutlined v-else /></template>
                {{ protocolWideLayout ? $t('label.storage.service.default.view') : $t('label.storage.service.wide.view') }}
              </a-button>
              <a-button type="primary" @click="openActionModal('enableProtocol', { protocol: 'NVME_OF' })">
                <template #icon><PoweroffOutlined /></template>
                {{ $t('label.storage.service.enable.protocol') }}
              </a-button>
              <a-button @click="openActionModal('nvmePrepare')">
                <template #icon><ReloadOutlined /></template>
                {{ $t('label.storage.service.prepare.nvmeof') }}
              </a-button>
              <a-button @click="openActionModal('nvmeSubsystem')">
                <template #icon><PlusOutlined /></template>
                {{ $t('label.storage.service.create.nvme.subsystem') }}
              </a-button>
              <a-button @click="openActionModal('nvmeHostAcl')">
                <template #icon><SafetyCertificateOutlined /></template>
                {{ $t('label.storage.service.create.nvme.host.acl') }}
              </a-button>
              <a-button :loading="storageService.refreshing" @click="fetchStorageServiceData">
                <template #icon><ReloadOutlined /></template>
                {{ $t('label.refresh') }}
              </a-button>
            </a-space>
          </div>
          <template v-if="storageService.instance">
            <a-alert
              v-if="!nvmeDhChapSupported"
              class="storage-service__alert"
              type="warning"
              show-icon
              :message="nvmeDhChapUnsupportedMessage" />
            <div class="storage-protocol-grid">
              <section class="storage-panel storage-panel--connection">
                <div class="storage-panel__title">{{ $t('label.storage.service.connection.info') }}</div>
                <p class="storage-panel__description">{{ $t('message.storage.service.nvme.connection.generic') }}</p>
                <div v-for="command in nvmeConnectionCommands" :key="command" class="command-line command-line--copyable">
                  {{ command }}
                </div>
              </section>
              <section class="storage-panel storage-panel--status">
                <div class="storage-panel__title">{{ $t('label.storage.service.status.summary') }}</div>
                <dl class="storage-kv storage-kv--compact">
                  <dt>{{ $t('label.storage.service.endpoint') }}</dt>
                  <dd><ellipsis-text :value="`${serviceEndpoint || '<service-ip>'}:4420`" code /></dd>
                  <dt>{{ $t('label.storage.service.monitor.cache') }}</dt>
                  <dd><a-tag :color="monitorCacheColor">{{ monitorCacheLabel }}</a-tag></dd>
                  <dt>{{ $t('label.storage.service.last.refresh') }}</dt>
                  <dd><ellipsis-text :value="monitorCacheTimestamp" /></dd>
                  <dt>{{ $t('label.storage.service.dhchap.support') }}</dt>
                  <dd><a-tag :color="nvmeDhChapSupported ? 'green' : 'orange'">{{ nvmeDhChapSupported ? $t('label.supported') : $t('label.unsupported') }}</a-tag></dd>
                </dl>
              </section>
            </div>
            <section class="storage-table-section">
              <div class="storage-table-section__header">
                <div>
                  <h4>{{ $t('label.storage.service.nvme.subsystems') }}</h4>
                  <p>{{ $t('message.storage.service.nvme.subsystems.table') }}</p>
                </div>
              </div>
              <a-table
                class="storage-data-table"
                size="small"
                rowKey="key"
                :columns="nvmeSubsystemColumns"
                :dataSource="nvmeSubsystemRows"
                :pagination="false"
                :scroll="{ x: 1500 }"
                :locale="storageTableLocale('message.storage.service.no.nvme.subsystems')">
                <template #bodyCell="{ column, record }">
                  <template v-if="column.key === 'state'">
                    <a-tag :color="runtimeColor(record)">{{ storageCellValue(record, column) }}</a-tag>
                  </template>
                  <template v-else>
                    <ellipsis-text :value="storageCellValue(record, column)" :code="column.code" />
                  </template>
                </template>
              </a-table>
            </section>

            <section class="storage-table-section">
              <div class="storage-table-section__header">
                <div>
                  <h4>{{ $t('label.storage.service.access.rules') }}</h4>
                  <p>{{ $t('message.storage.service.nvme.acls.table') }}</p>
                </div>
                <a-space class="storage-section-actions">
                  <a-button @click="openActionModal('nvmeHostAcl')">
                    <template #icon><SafetyCertificateOutlined /></template>
                    {{ $t('label.storage.service.create.nvme.host.acl') }}
                  </a-button>
                </a-space>
              </div>
              <a-table
                class="storage-data-table"
                size="small"
                rowKey="key"
                :columns="nvmeAclColumns"
                :dataSource="nvmeAclRows"
                :pagination="false"
                :scroll="{ x: 1300 }"
                :locale="storageTableLocale('message.storage.service.no.access.rules')">
                <template #bodyCell="{ column, record }">
                  <template v-if="column.key === 'state'">
                    <a-tag :color="runtimeColor(record)">{{ storageCellValue(record, column) }}</a-tag>
                  </template>
                  <template v-else>
                    <ellipsis-text :value="storageCellValue(record, column)" :code="column.code" />
                  </template>
                </template>
              </a-table>
            </section>

            <section class="storage-table-section">
              <div class="storage-table-section__header">
                <div>
                  <h4>{{ $t('label.storage.service.sessions') }}</h4>
                  <p>{{ $t('message.storage.service.nvme.sessions.table') }}</p>
                </div>
              </div>
              <a-table
                class="storage-data-table"
                size="small"
                rowKey="key"
                :columns="nvmeSessionColumns"
                :dataSource="nvmeSessionRows"
                :pagination="false"
                :scroll="{ x: 1160 }"
                :locale="storageTableLocale('message.storage.service.no.sessions')">
                <template #bodyCell="{ column, record }">
                  <template v-if="column.key === 'state'">
                    <a-tag :color="runtimeColor(record)">{{ storageCellValue(record, column) }}</a-tag>
                  </template>
                  <template v-else-if="column.key === 'actions'">
                    <a-button size="small" danger @click="openActionModal('disconnectSession', record)">
                      <template #icon><DisconnectOutlined /></template>
                      {{ $t('label.storage.service.disconnect.session') }}
                    </a-button>
                  </template>
                  <template v-else>
                    <ellipsis-text :value="storageCellValue(record, column)" :code="column.code" />
                  </template>
                </template>
              </a-table>
            </section>
          </template>
        </div>
      </a-tab-pane>

      <a-tab-pane :tab="$t('label.networks')" key="nics" v-if="'listNics' in $store.getters.apis"><NicsTab :resource="vm"/></a-tab-pane>
      <a-tab-pane v-if="$store.getters.features.instancesdisksstatsretentionenabled" :tab="$t('label.volume.metrics')" key="volumestats"><StatsTab :resource="volume" :resourceType="'Volume'"/></a-tab-pane>
      <a-tab-pane :tab="$t('label.metrics')" key="vmstats"><StatsTab :resource="vm"/></a-tab-pane>
      <a-tab-pane :tab="$t('label.events')" key="events" v-if="'listEvents' in $store.getters.apis"><events-tab :resource="resource" resourceType="SharedFS" :loading="loading" /></a-tab-pane>
    </a-tabs>

    <a-modal
:visible="actionModal.visible"
:title="actionModalTitle"
:confirmLoading="actionModal.loading"
:maskClosable="false"
:width="860"
:centered="true"
:okText="actionModalOkText"
:cancelText="$t('label.cancel')"
:okButtonProps="actionModalOkButtonProps"
wrapClassName="storage-service-action-modal"
@ok="submitActionModal"
@cancel="closeActionModal">
      <div class="storage-modal-body"><a-form layout="vertical">
        <div v-if="actionModal.type === 'enableProtocol'" class="storage-action-form storage-action-form--vertical">
          <a-form-item required>
            <template #label>
              <tooltip-label :title="$t('label.protocol')" :tooltip="$t('message.storage.service.protocol.help')" />
            </template>
            <a-select v-model:value="forms.enableProtocol.protocol">
              <a-select-option value="NFS">NFS</a-select-option>
              <a-select-option value="SMB">SMB</a-select-option>
              <a-select-option value="ISCSI">iSCSI</a-select-option>
              <a-select-option value="NVME_OF">NVMe-oF</a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item required>
            <template #label>
              <tooltip-label :title="$t('label.storage.service.listen.ip.mode')" :tooltip="$t('message.storage.service.listen.ip.mode.help')" />
            </template>
            <a-radio-group v-model:value="forms.enableProtocol.listenipmode">
              <a-radio value="EXISTING">{{ $t('label.storage.service.listen.ip.existing') }}</a-radio>
              <a-radio value="NEW">{{ $t('label.storage.service.listen.ip.new') }}</a-radio>
            </a-radio-group>
          </a-form-item>
          <a-form-item v-if="forms.enableProtocol.listenipmode === 'EXISTING'" required>
            <template #label>
              <tooltip-label :title="$t('label.storage.service.listen.ip')" :tooltip="$t('message.storage.service.listen.ip.help')" />
            </template>
            <a-select v-model:value="forms.enableProtocol.listenip" show-search optionFilterProp="label">
              <a-select-option v-for="nic in serviceListenIps" :key="nic.key" :value="nic.ipaddress" :label="nic.label">
                {{ nic.label }}
              </a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item v-else required>
            <template #label>
              <tooltip-label :title="$t('label.storage.service.new.listen.ip')" :tooltip="$t('message.storage.service.listen.ip.same.cidr')" />
            </template>
            <a-input v-model:value="forms.enableProtocol.listenip" placeholder="10.10.254.20" />
          </a-form-item>
          <a-form-item required>
            <template #label>
              <tooltip-label :title="$t('label.port')" :tooltip="$t('message.storage.service.protocol.port.help')" />
            </template>
            <a-input-number v-model:value="forms.enableProtocol.port" class="storage-input-number" :disabled="forms.enableProtocol.protocol === 'NFS'" />
            <div v-if="forms.enableProtocol.protocol === 'NFS'" class="field-validation-hint">
              {{ $t('message.storage.service.nfs.port.fixed') }}
            </div>
          </a-form-item>
        </div>
        <div v-if="actionModal.type === 'nfsExport' || actionModal.type === 'editNfsExport'" class="storage-action-form storage-action-form--vertical">
          <a-form-item>
            <template #label>
              <tooltip-label :title="$t('label.name')" :tooltip="$t('message.storage.service.nfs.name.autogenerated')" />
            </template>
            <a-input v-model:value="forms.nfsExport.name" />
          </a-form-item>
          <a-form-item required>
            <template #label>
              <tooltip-label :title="$t('label.storage.service.internal.path')" :tooltip="$t('message.storage.service.nfs.internal.path.help')" />
            </template>
            <a-input v-model:value="forms.nfsExport.path" placeholder="/export/nfs01" />
          </a-form-item>
          <section class="storage-action-section">
            <div class="storage-action-section__title">{{ $t('label.storage.service.backing.volume') }}</div>
            <a-form-item required>
              <template #label>
                <tooltip-label :title="$t('label.storage.service.volume.mode')" :tooltip="$t('message.storage.service.volume.mode.help')" />
              </template>
            <a-radio-group v-model:value="forms.nfsExport.volumemode">
                <a-radio value="CURRENT">{{ $t('label.storage.service.current.volume') }}</a-radio>
                <a-radio value="EXISTING">{{ $t('label.storage.service.existing.volume.select') }}</a-radio>
                <a-radio value="NEW">{{ $t('label.storage.service.new.volume') }}</a-radio>
              </a-radio-group>
            </a-form-item>
            <a-form-item
              v-if="forms.nfsExport.volumemode === 'CURRENT'"
              required>
              <template #label>
                <tooltip-label :title="$t('label.storage.service.current.backing.volume')" :tooltip="$t('message.storage.service.current.volume.select.help')" />
              </template>
              <a-select
                v-model:value="forms.nfsExport.volumeid"
                :loading="volumeLoading"
                show-search
                optionFilterProp="label">
                <a-select-option v-for="volume in currentBackingVolumes" :key="volume.id" :value="volume.id" :label="formatCurrentBackingVolumeOption(volume)">
                  {{ formatCurrentBackingVolumeOption(volume) }}
                </a-select-option>
              </a-select>
              <div v-if="selectedCurrentBackingVolume" class="storage-action-summary-box storage-action-summary-box--compact">
                <div class="storage-action-summary-row">
                  <span>{{ $t('label.storage.service.mount.path') }}</span>
                  <code>{{ currentBackingVolumeMountPath(selectedCurrentBackingVolume) || '-' }}</code>
                </div>
                <div class="storage-action-summary-row">
                  <span>{{ $t('label.storage.service.attached.export') }}</span>
                  <code>{{ currentBackingVolumeExportSummary(selectedCurrentBackingVolume) }}</code>
                </div>
              </div>
            </a-form-item>
            <volume-select
              v-if="forms.nfsExport.volumemode === 'EXISTING'"
              v-model:value="forms.nfsExport.volumeid"
              :volumes="availableVolumes"
              :loading="volumeLoading"
              :formatter="formatVolumeOption"
              required
              :tooltip="$t('message.storage.service.existing.volume.select.help')" />
            <a-form-item>
              <a-checkbox v-model:checked="forms.nfsExport.createdirectory">
                {{ $t('label.storage.service.create.directory.if.missing') }}
              </a-checkbox>
            </a-form-item>
            <template v-if="forms.nfsExport.volumemode === 'NEW'">
              <a-form-item required>
                <template #label>
                  <tooltip-label :title="$t('label.storage.service.new.volume.name')" :tooltip="$t('message.storage.service.new.volume.name.help')" />
                </template>
                <a-input v-model:value="forms.nfsExport.newvolumename" />
              </a-form-item>
              <a-form-item required>
                <template #label>
                  <tooltip-label :title="$t('label.diskoffering')" :tooltip="$t('message.storage.service.new.volume.diskoffering.help')" />
                </template>
                <a-select v-model:value="forms.nfsExport.diskofferingid" :loading="diskOfferingLoading" show-search optionFilterProp="label" @change="reconcileNfsNewVolumeStorage">
                  <a-select-option v-for="offering in diskOfferings" :key="offering.id" :value="offering.id" :label="offering.displaytext || offering.name">
                    {{ offering.displaytext || offering.name }}
                  </a-select-option>
                </a-select>
              </a-form-item>
              <a-form-item required>
                <template #label>
                  <tooltip-label :title="$t('label.primary.storage')" :tooltip="$t('message.storage.service.new.volume.storage.help')" />
                </template>
                <a-select v-model:value="forms.nfsExport.storageid" :loading="storagePoolLoading" show-search optionFilterProp="label">
                  <a-select-option v-for="pool in filteredNfsNewVolumeStoragePools" :key="pool.id" :value="pool.id" :label="storagePoolLabel(pool)">
                    {{ storagePoolLabel(pool) }}
                  </a-select-option>
                </a-select>
                <div v-if="selectedNfsDiskOfferingTags.length" class="storage-field-hint">
                  {{ $t('message.storage.service.primary.storage.tag.filtered', { tags: selectedNfsDiskOfferingTags.join(', ') }) }}
                </div>
              </a-form-item>
              <a-form-item required>
                <template #label>
                  <tooltip-label :title="$t('label.storage.service.volume.size.gib')" :tooltip="$t('message.storage.service.volume.size.help')" />
                </template>
                <a-input-number v-model:value="forms.nfsExport.newvolumesize" class="storage-input-number" :min="1" />
              </a-form-item>
              <a-form-item required>
                <template #label>
                  <tooltip-label :title="$t('label.filesystem')" :tooltip="$t('message.storage.service.new.volume.filesystem.help')" />
                </template>
                <a-select v-model:value="forms.nfsExport.filesystem">
                  <a-select-option value="xfs">XFS</a-select-option>
                  <a-select-option value="ext4">EXT4</a-select-option>
                </a-select>
              </a-form-item>
            </template>
          </section>
          <capacity-input :label="$t('label.storage.service.nfs.export.capacity.limit')" :tooltip="$t('message.storage.service.nfs.quota.help')" v-model:amount="forms.nfsExport.quotaamount" v-model:unit="forms.nfsExport.quotaunit" :units="capacityUnits" />
          <section class="storage-action-section">
            <div class="storage-action-section__title">{{ $t('label.storage.service.nfs.export.endpoints') }}</div>
            <a-form-item required>
              <template #label>
                <tooltip-label :title="$t('label.storage.service.endpoint.selection')" :tooltip="$t('message.storage.service.nfs.endpoint.selection.help')" />
              </template>
              <a-radio-group v-model:value="forms.nfsExport.endpointmode">
                <a-radio value="ALL">{{ $t('label.storage.service.all.endpoints') }}</a-radio>
                <a-radio value="SELECTED">{{ $t('label.storage.service.selected.endpoints') }}</a-radio>
              </a-radio-group>
            </a-form-item>
            <a-form-item v-if="forms.nfsExport.endpointmode === 'SELECTED'" required>
              <template #label>
                <tooltip-label :title="$t('label.storage.service.listen.ip')" :tooltip="$t('message.storage.service.nfs.endpoint.listenips.help')" />
              </template>
              <a-select v-model:value="forms.nfsExport.listenips" mode="multiple" show-search optionFilterProp="label">
                <a-select-option v-for="endpoint in serviceListenIps" :key="endpoint.ipaddress" :value="endpoint.ipaddress" :label="endpoint.label">
                  {{ endpoint.label }}
                </a-select-option>
              </a-select>
            </a-form-item>
          </section>
          <section class="storage-action-section">
            <div class="storage-action-section__title">{{ $t('label.storage.service.nfs.export.options') }}</div>
            <div class="storage-action-checkbox-grid">
              <a-checkbox v-model:checked="forms.nfsExport.readonly">{{ $t('label.storage.service.permission.readonly') }}</a-checkbox>
              <a-checkbox v-model:checked="forms.nfsExport.rootsquash">{{ $t('label.storage.service.root.squash') }}</a-checkbox>
              <a-checkbox v-model:checked="forms.nfsExport.allsquash">{{ $t('label.storage.service.all.squash') }}</a-checkbox>
              <a-checkbox v-model:checked="forms.nfsExport.sync">{{ $t('label.storage.service.sync') }}</a-checkbox>
              <a-checkbox v-model:checked="forms.nfsExport.secure">
                <a-tooltip :title="$t('message.storage.service.secure.help')">
                  <span>{{ $t('label.storage.service.secure') }}</span>
                </a-tooltip>
              </a-checkbox>
            </div>
            <a-alert
              v-if="forms.nfsExport.rootsquash"
              class="storage-action-context-alert"
              type="info"
              show-icon
              :message="$t('message.storage.service.nfs.posix.permission.help')" />
          </section>
          <section class="storage-action-section">
            <div class="storage-action-section__title">{{ $t('label.storage.service.posix.permission') }}</div>
            <a-row :gutter="12">
              <a-col :xs="24" :md="12"><a-form-item><template #label><tooltip-label :title="$t('label.storage.service.owner.uid')" :tooltip="$t('message.storage.service.owner.uid.help')" /></template><a-input-number v-model:value="forms.nfsExport.owneruid" class="storage-input-number" :min="0" :max="65535" /></a-form-item></a-col>
              <a-col :xs="24" :md="12"><a-form-item><template #label><tooltip-label :title="$t('label.storage.service.owner.gid')" :tooltip="$t('message.storage.service.owner.gid.help')" /></template><a-input-number v-model:value="forms.nfsExport.ownergid" class="storage-input-number" :min="0" :max="65535" /></a-form-item></a-col>
              <a-col :xs="24" :md="12"><a-form-item><template #label><tooltip-label :title="$t('label.storage.service.anon.uid')" :tooltip="$t('message.storage.service.anon.uid.help')" /></template><a-input-number v-model:value="forms.nfsExport.anonuid" class="storage-input-number" :min="0" :max="65535" /></a-form-item></a-col>
              <a-col :xs="24" :md="12"><a-form-item><template #label><tooltip-label :title="$t('label.storage.service.anon.gid')" :tooltip="$t('message.storage.service.anon.gid.help')" /></template><a-input-number v-model:value="forms.nfsExport.anongid" class="storage-input-number" :min="0" :max="65535" /></a-form-item></a-col>
              <a-col :xs="24" :md="12"><a-form-item><template #label><tooltip-label :title="$t('label.storage.service.directory.mode')" :tooltip="$t('message.storage.service.directory.mode.help')" /></template><a-input v-model:value="forms.nfsExport.mode" placeholder="0775" /></a-form-item></a-col>
              <a-col :xs="24" :md="12"><a-form-item><template #label><tooltip-label :title="$t('label.storage.service.recursive.permission')" :tooltip="$t('message.storage.service.recursive.permission.help')" /></template><a-switch v-model:checked="forms.nfsExport.recursivepermission" /></a-form-item></a-col>
            </a-row>
          </section>
        </div>
        <div v-if="actionModal.type === 'nfsAcl' || actionModal.type === 'editNfsAcl'" class="storage-action-form storage-action-form--vertical">
          <a-form-item required>
            <template #label>
              <tooltip-label :title="$t('label.storage.service.export.name')" :tooltip="$t('message.storage.service.export.name.help')" />
            </template>
            <a-select v-model:value="forms.nfsAcl.exportid" show-search optionFilterProp="label" :disabled="actionModal.type === 'editNfsAcl'">
              <a-select-option v-for="share in storageService.nfsExports" :key="share.id" :value="share.id" :label="shareNameLabel(share)">
                {{ shareNameLabel(share) }}
              </a-select-option>
            </a-select>
          </a-form-item>
          <div v-if="selectedNfsAclExport" class="storage-action-summary-box">
            <div class="storage-action-summary-row">
              <span>{{ $t('label.storage.service.internal.path') }}</span>
              <code>{{ selectedNfsAclExport.path || '-' }}</code>
            </div>
            <div class="storage-action-summary-row">
              <span>{{ $t('label.storage.service.client.mount.root') }}</span>
              <code>/{{ shareNameLabel(selectedNfsAclExport) }}</code>
            </div>
          </div>
          <a-form-item required>
            <template #label>
              <tooltip-label :title="$t('label.storage.service.principal')" :tooltip="$t('message.storage.service.allowed.cidrs.help')" />
            </template>
            <a-select
              v-if="actionModal.type !== 'editNfsAcl'"
              v-model:value="forms.nfsAcl.principals"
              mode="tags"
              :tokenSeparators="[',']"
              :placeholder="$t('message.storage.service.allowed.cidrs.placeholder')" />
            <a-input
              v-else
              v-model:value="forms.nfsAcl.principal"
              :placeholder="$t('message.storage.service.allowed.cidrs.single.placeholder')" />
          </a-form-item>
          <a-form-item required>
            <template #label>
              <tooltip-label :title="$t('label.storage.service.permission')" :tooltip="$t('message.storage.service.permission.help')" />
            </template>
            <a-select v-model:value="forms.nfsAcl.permission">
              <a-select-option value="READ_ONLY">{{ $t('label.storage.service.permission.readonly') }}</a-select-option>
              <a-select-option value="READ_WRITE">{{ $t('label.storage.service.permission.readwrite') }}</a-select-option>
            </a-select>
          </a-form-item>
          <section class="storage-action-section">
            <div class="storage-action-section__title">{{ $t('label.storage.service.nfs.access.options') }}</div>
            <div class="storage-action-checkbox-grid">
              <a-checkbox v-model:checked="forms.nfsAcl.rootsquash">{{ $t('label.storage.service.root.squash') }}</a-checkbox>
              <a-checkbox v-model:checked="forms.nfsAcl.allsquash">{{ $t('label.storage.service.all.squash') }}</a-checkbox>
              <a-checkbox v-model:checked="forms.nfsAcl.sync">{{ $t('label.storage.service.sync') }}</a-checkbox>
              <a-checkbox v-model:checked="forms.nfsAcl.secure">
                <a-tooltip :title="$t('message.storage.service.secure.help')">
                  <span>{{ $t('label.storage.service.secure') }}</span>
                </a-tooltip>
              </a-checkbox>
            </div>
          </section>
          <section class="storage-action-section">
            <div class="storage-action-section__title">{{ $t('label.storage.service.squash.user.mapping') }}</div>
            <a-row :gutter="12">
              <a-col :xs="24" :md="12"><a-form-item><template #label><tooltip-label :title="$t('label.storage.service.anon.uid')" :tooltip="$t('message.storage.service.anon.uid.help')" /></template><a-input-number v-model:value="forms.nfsAcl.anonuid" class="storage-input-number" :min="0" :max="65535" /></a-form-item></a-col>
              <a-col :xs="24" :md="12"><a-form-item><template #label><tooltip-label :title="$t('label.storage.service.anon.gid')" :tooltip="$t('message.storage.service.anon.gid.help')" /></template><a-input-number v-model:value="forms.nfsAcl.anongid" class="storage-input-number" :min="0" :max="65535" /></a-form-item></a-col>
            </a-row>
          </section>
        </div>
        <div v-if="actionModal.type === 'deleteConfirm'" class="storage-action-form storage-action-form--vertical">
          <a-alert
            class="storage-action-delete-alert"
            type="warning"
            show-icon
            :message="$t('message.storage.service.delete.warning')" />
          <div class="storage-action-summary-box">
            <div class="storage-action-summary-row">
              <span>{{ $t('label.type') }}</span>
              <code>{{ deleteTargetTypeLabel }}</code>
            </div>
            <div class="storage-action-summary-row">
              <span>{{ $t('label.name') }}</span>
              <code>{{ actionModal.context?.name || '-' }}</code>
            </div>
          </div>
          <a-form-item required>
            <template #label>
              <tooltip-label :title="$t('label.confirmation')" :tooltip="$t('message.storage.service.delete.confirm.tooltip')" />
            </template>
            <a-input v-model:value="forms.deleteConfirm.confirmation" :placeholder="actionModal.context?.name || ''" />
            <div class="field-validation-hint">
              {{ $t('message.storage.service.delete.confirm.input') }}
            </div>
          </a-form-item>
        </div>
        <div v-if="actionModal.type === 'deleteEndpoint'" class="storage-action-form storage-action-form--vertical">
          <a-alert
            class="storage-action-context-alert storage-action-context-alert--danger"
            type="warning"
            show-icon
            :message="$t('message.storage.service.delete.endpoint.warning')" />
          <a-form-item required>
            <template #label>
              <tooltip-label :title="$t('label.storage.service.endpoint')" :tooltip="$t('message.storage.service.delete.endpoint.help')" />
            </template>
            <a-select v-model:value="forms.deleteEndpoint.listenip" show-search optionFilterProp="label">
              <a-select-option v-for="endpoint in removableServiceEndpoints" :key="endpoint.ipaddress" :value="endpoint.ipaddress" :label="endpoint.label">
                {{ endpoint.label }}
              </a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item required>
            <template #label>
              <tooltip-label :title="$t('label.storage.service.delete.confirm')" :tooltip="$t('message.storage.service.delete.endpoint.confirm.tooltip')" />
            </template>
            <a-input v-model:value="forms.deleteEndpoint.confirmation" :placeholder="forms.deleteEndpoint.listenip || ''" />
          </a-form-item>
        </div>
        <div v-if="actionModal.type === 'smbShare'" class="storage-action-form storage-action-form--vertical">
          <a-row :gutter="16">
            <a-col :xs="24" :md="12"><a-form-item><template #label><tooltip-label :title="$t('label.name')" :tooltip="$t('message.storage.service.smb.name.autogenerated')" /></template><a-input v-model:value="forms.smbShare.name" /></a-form-item></a-col>
            <a-col :xs="24" :md="12"><a-form-item required><template #label><tooltip-label :title="$t('label.storage.service.internal.path')" :tooltip="$t('message.storage.service.smb.internal.path.help')" /></template><a-input v-model:value="forms.smbShare.path" placeholder="/export/smb01" /><div class="field-hint">{{ $t('message.storage.service.smb.internal.path.help') }}</div></a-form-item></a-col>
            <a-col :xs="24" :md="12"><volume-select v-model:value="forms.smbShare.volumeid" :volumes="availableVolumes" :loading="volumeLoading" :formatter="formatVolumeOption" :tooltip="$t('message.storage.service.existing.volume.select.help')" /></a-col>
            <a-col :xs="24" :md="12"><capacity-input :label="$t('label.storage.service.smb.share.capacity.limit')" :tooltip="$t('message.storage.service.smb.quota.help')" v-model:amount="forms.smbShare.quotaamount" v-model:unit="forms.smbShare.quotaunit" :units="capacityUnits" /></a-col>
            <a-col :xs="24"><a-space wrap><a-checkbox v-model:checked="forms.smbShare.readonly">{{ $t('label.storage.service.permission.readonly') }}</a-checkbox><a-checkbox v-model:checked="forms.smbShare.browseable">{{ $t('label.storage.service.browseable') }}</a-checkbox><a-checkbox v-model:checked="forms.smbShare.guestok">{{ $t('label.storage.service.guest.access') }}</a-checkbox></a-space></a-col>
          </a-row>
        </div>
        <div v-if="actionModal.type === 'smbAcl'" class="storage-action-form storage-action-form--vertical">
          <a-row :gutter="16">
            <a-col :xs="24" :md="12"><a-form-item required><template #label><tooltip-label :title="$t('label.share')" :tooltip="$t('message.storage.service.share.help')" /></template><a-select v-model:value="forms.smbAcl.shareid" show-search optionFilterProp="label"><a-select-option v-for="share in storageService.smbShares" :key="share.id" :value="share.id" :label="shareLabel(share)">{{ shareLabel(share) }}</a-select-option></a-select></a-form-item></a-col>
            <a-col :xs="24" :md="12"><a-form-item required><template #label><tooltip-label :title="$t('label.storage.service.principal')" :tooltip="$t('message.storage.service.smb.principal.help')" /></template><a-input v-model:value="forms.smbAcl.principal" /></a-form-item></a-col>
            <a-col :xs="24" :md="12"><a-form-item required><template #label><tooltip-label :title="$t('label.storage.service.principal.type')" :tooltip="$t('message.storage.service.principal.type.help')" /></template><a-select v-model:value="forms.smbAcl.principaltype"><a-select-option value="LOCAL_USER">{{ $t('label.storage.service.local.user') }}</a-select-option><a-select-option value="LOCAL_GROUP">{{ $t('label.storage.service.local.group') }}</a-select-option><a-select-option value="AD_USER">{{ $t('label.storage.service.ad.user') }}</a-select-option><a-select-option value="AD_GROUP">{{ $t('label.storage.service.ad.group') }}</a-select-option></a-select></a-form-item></a-col>
            <a-col :xs="24" :md="12"><a-form-item required><template #label><tooltip-label :title="$t('label.storage.service.permission')" :tooltip="$t('message.storage.service.permission.help')" /></template><a-select v-model:value="forms.smbAcl.permission"><a-select-option value="READ_ONLY">{{ $t('label.storage.service.permission.readonly') }}</a-select-option><a-select-option value="READ_WRITE">{{ $t('label.storage.service.permission.readwrite') }}</a-select-option><a-select-option value="ADMIN">{{ $t('label.admin') }}</a-select-option></a-select></a-form-item></a-col>
            <a-col :xs="24" :md="12"><a-form-item><template #label><tooltip-label :title="$t('label.storage.service.local.user.password')" :tooltip="$t('message.storage.service.smb.local.password.help')" /></template><a-input-password v-model:value="forms.smbAcl.password" autocomplete="new-password" /></a-form-item></a-col>
          </a-row>
        </div>
        <div v-if="actionModal.type === 'adJoin'" class="storage-action-form storage-action-form--vertical">
          <a-alert class="storage-service__alert" type="info" show-icon :message="$t('message.storage.service.smb.password.sensitive')" />
          <a-row :gutter="16">
            <a-col :xs="24" :md="12"><a-form-item required><template #label><tooltip-label :title="$t('label.storage.service.ad.domain')" :tooltip="$t('message.storage.service.ad.domain.help')" /></template><a-input v-model:value="forms.adJoin.domainname" /></a-form-item></a-col>
            <a-col :xs="24" :md="12"><a-form-item required><template #label><tooltip-label :title="$t('label.username')" :tooltip="$t('message.storage.service.ad.username.help')" /></template><a-input v-model:value="forms.adJoin.username" /></a-form-item></a-col>
            <a-col :xs="24" :md="12"><a-form-item required><template #label><tooltip-label :title="$t('label.password')" :tooltip="$t('message.storage.service.ad.password.help')" /></template><a-input-password v-model:value="forms.adJoin.password" autocomplete="new-password" /></a-form-item></a-col>
            <a-col :xs="24" :md="12"><a-form-item><template #label><tooltip-label :title="$t('label.storage.service.dns.servers')" :tooltip="$t('message.storage.service.dns.servers.help')" /></template><a-input v-model:value="forms.adJoin.dnsservers" /></a-form-item></a-col>
          </a-row>
        </div>
        <div v-if="actionModal.type === 'iscsiTarget'" class="storage-action-form storage-action-form--vertical">
          <a-row :gutter="16">
            <a-col :xs="24" :md="12"><a-form-item required><template #label><tooltip-label :title="$t('label.storage.service.target.iqn')" :tooltip="$t('message.storage.service.target.iqn.help')" /></template><a-input v-model:value="forms.iscsiTarget.targetname" /></a-form-item></a-col>
            <a-col :xs="24" :md="6"><a-form-item required><template #label><tooltip-label title="LUN" :tooltip="$t('message.storage.service.lun.help')" /></template><a-input v-model:value="forms.iscsiTarget.lun" /></a-form-item></a-col>
            <a-col :xs="24" :md="6">
              <volume-select
                v-model:value="forms.iscsiTarget.volumeid"
                :volumes="availableVolumes"
                :loading="volumeLoading"
                :formatter="formatVolumeOption"
                required
                :tooltip="$t('message.storage.service.existing.volume.select.help')" />
            </a-col>
            <a-col :xs="24" :md="12"><capacity-input :label="$t('label.storage.service.lun.size')" :tooltip="$t('message.storage.service.lun.size.help')" v-model:amount="forms.iscsiTarget.lunsizeamount" v-model:unit="forms.iscsiTarget.lunsizeunit" :units="capacityUnits" /></a-col>
          </a-row>
        </div>
        <div v-if="actionModal.type === 'iscsiAcl'" class="storage-action-form storage-action-form--vertical">
          <a-row :gutter="16">
            <a-col :xs="24" :md="12"><a-form-item required><template #label><tooltip-label :title="$t('label.storage.service.iscsi.target')" :tooltip="$t('message.storage.service.iscsi.target.help')" /></template><a-select v-model:value="forms.iscsiAcl.targetid" show-search optionFilterProp="label"><a-select-option v-for="target in storageService.iscsiTargets" :key="target.id" :value="target.id" :label="targetLabel(target)">{{ targetLabel(target) }}</a-select-option></a-select></a-form-item></a-col>
            <a-col :xs="24" :md="12"><a-form-item required><template #label><tooltip-label :title="$t('label.storage.service.allowed.initiator.iqn')" :tooltip="$t('message.storage.service.allowed.initiator.iqn.help')" /></template><a-input v-model:value="forms.iscsiAcl.initiatoriqn" /></a-form-item></a-col>
            <a-col :xs="24" :md="12"><a-form-item required><template #label><tooltip-label :title="$t('label.storage.service.permission')" :tooltip="$t('message.storage.service.permission.help')" /></template><a-select v-model:value="forms.iscsiAcl.permission"><a-select-option value="READ_ONLY">{{ $t('label.storage.service.permission.readonly') }}</a-select-option><a-select-option value="READ_WRITE">{{ $t('label.storage.service.permission.readwrite') }}</a-select-option></a-select></a-form-item></a-col>
            <a-col :xs="24" :md="12"><a-form-item><template #label><tooltip-label :title="$t('label.storage.service.chap.enabled')" :tooltip="$t('message.storage.service.chap.enabled.help')" /></template><a-switch v-model:checked="forms.iscsiAcl.chapenabled" /></a-form-item></a-col>
            <a-col :xs="24" :md="12" v-if="forms.iscsiAcl.chapenabled"><a-form-item required><template #label><tooltip-label :title="$t('label.storage.service.chap.username')" :tooltip="$t('message.storage.service.chap.username.help')" /></template><a-input v-model:value="forms.iscsiAcl.chapusername" /></a-form-item></a-col>
            <a-col :xs="24" :md="12" v-if="forms.iscsiAcl.chapenabled"><a-form-item required><template #label><tooltip-label :title="$t('label.storage.service.chap.secret')" :tooltip="$t('message.storage.service.chap.secret.help')" /></template><a-input-password v-model:value="forms.iscsiAcl.chapsecret" /></a-form-item></a-col>
          </a-row>
        </div>
        <div v-if="actionModal.type === 'nvmePrepare'" class="storage-action-form storage-action-form--vertical">
          <a-row :gutter="16">
            <a-col :xs="24" :md="8"><a-form-item required><template #label><tooltip-label :title="$t('label.storage.service.engine')" :tooltip="$t('message.storage.service.nvme.engine.help')" /></template><a-select v-model:value="forms.nvmePrepare.engine"><a-select-option value="KERNEL_NVMET">KERNEL_NVMET</a-select-option><a-select-option value="SPDK">SPDK</a-select-option></a-select></a-form-item></a-col>
            <a-col :xs="24" :md="8"><a-form-item required><template #label><tooltip-label :title="$t('label.storage.service.transport')" :tooltip="$t('message.storage.service.nvme.transport.help')" /></template><a-select v-model:value="forms.nvmePrepare.transport"><a-select-option value="tcp">tcp</a-select-option></a-select></a-form-item></a-col>
            <a-col :xs="24" :md="8"><a-form-item><template #label><tooltip-label :title="$t('label.storage.service.validate.only')" :tooltip="$t('message.storage.service.validate.only.help')" /></template><a-switch v-model:checked="forms.nvmePrepare.validateonly" /></a-form-item></a-col>
          </a-row>
        </div>
        <div v-if="actionModal.type === 'nvmeSubsystem'" class="storage-action-form storage-action-form--vertical">
          <a-row :gutter="16">
            <a-col :xs="24" :md="12"><a-form-item required><template #label><tooltip-label :title="$t('label.storage.service.subsystem.nqn')" :tooltip="$t('message.storage.service.subsystem.nqn.help')" /></template><a-input v-model:value="forms.nvmeSubsystem.subsystemnqn" /></a-form-item></a-col>
            <a-col :xs="24" :md="6"><a-form-item required><template #label><tooltip-label :title="$t('label.storage.service.engine')" :tooltip="$t('message.storage.service.nvme.engine.help')" /></template><a-select v-model:value="forms.nvmeSubsystem.engine"><a-select-option value="KERNEL_NVMET">KERNEL_NVMET</a-select-option><a-select-option value="SPDK">SPDK</a-select-option></a-select></a-form-item></a-col>
            <a-col :xs="24" :md="6"><a-form-item><template #label><tooltip-label :title="$t('label.storage.service.allow.any.host')" :tooltip="$t('message.storage.service.allow.any.host.help')" /></template><a-switch v-model:checked="forms.nvmeSubsystem.allowanyhost" /></a-form-item></a-col>
          </a-row>
        </div>
        <a-row :gutter="16" v-if="actionModal.type === 'nvmeHostAcl'">
          <a-col :xs="24">
            <a-alert
              v-if="!nvmeDhChapSupported"
              class="storage-service__alert"
              type="warning"
              show-icon
              :message="nvmeDhChapUnsupportedMessage" />
          </a-col>
          <a-col :xs="24" :md="12"><a-form-item required><template #label><tooltip-label :title="$t('label.storage.service.nvme.subsystem')" :tooltip="$t('message.storage.service.nvme.subsystem.help')" /></template><a-select v-model:value="forms.nvmeHostAcl.subsystemid" show-search optionFilterProp="label"><a-select-option v-for="target in storageService.nvmeSubsystems" :key="target.id" :value="target.id" :label="targetLabel(target)">{{ targetLabel(target) }}</a-select-option></a-select></a-form-item></a-col>
          <a-col :xs="24" :md="12"><a-form-item required><template #label><tooltip-label :title="$t('label.storage.service.allowed.host.nqn')" :tooltip="$t('message.storage.service.allowed.host.nqn.help')" /></template><a-input v-model:value="forms.nvmeHostAcl.hostnqn" /></a-form-item></a-col>
          <a-col :xs="24" :md="12"><a-form-item><template #label><tooltip-label :title="$t('label.storage.service.dhchap.enabled')" :tooltip="$t('message.storage.service.dhchap.enabled.help')" /></template><a-switch v-model:checked="forms.nvmeHostAcl.dhchapenabled" :disabled="!nvmeDhChapSupported" /></a-form-item></a-col>
          <a-col :xs="24" :md="12"><a-form-item><template #label><tooltip-label :title="$t('label.storage.service.dhchap.controller.enabled')" :tooltip="$t('message.storage.service.dhchap.controller.enabled.help')" /></template><a-switch v-model:checked="forms.nvmeHostAcl.dhchapctrlenabled" :disabled="!nvmeDhChapSupported || !nvmeDhChapCtrlSupported || !forms.nvmeHostAcl.dhchapenabled" /></a-form-item></a-col>
          <a-col :xs="24" :md="12" v-if="forms.nvmeHostAcl.dhchapenabled"><a-form-item required><template #label><tooltip-label :title="$t('label.storage.service.dhchap.key')" :tooltip="$t('message.storage.service.dhchap.key.help')" /></template><a-input-password v-model:value="forms.nvmeHostAcl.dhchapkey" /></a-form-item></a-col>
          <a-col :xs="24" :md="12" v-if="forms.nvmeHostAcl.dhchapenabled && forms.nvmeHostAcl.dhchapctrlenabled"><a-form-item required><template #label><tooltip-label :title="$t('label.storage.service.dhchap.controller.key')" :tooltip="$t('message.storage.service.dhchap.controller.key.help')" /></template><a-input-password v-model:value="forms.nvmeHostAcl.dhchapctrlkey" /></a-form-item></a-col>
        </a-row>
        <div v-if="actionModal.type === 'attachVolume'" class="storage-action-form storage-action-form--vertical">
          <a-row :gutter="16">
            <a-col :xs="24" :md="12"><a-form-item required><template #label><tooltip-label :title="$t('label.storage.service.file.share')" :tooltip="$t('message.storage.service.select.share')" /></template><a-select v-model:value="forms.attachVolume.id" show-search optionFilterProp="label"><a-select-option v-for="share in fileShares" :key="share.id" :value="share.id" :label="shareLabel(share)">{{ shareLabel(share) }}</a-select-option></a-select></a-form-item></a-col>
            <a-col :xs="24" :md="12">
              <volume-select
                v-model:value="forms.attachVolume.volumeid"
                :volumes="availableVolumes"
                :loading="volumeLoading"
                :formatter="formatVolumeOption"
                required
                :tooltip="$t('message.storage.service.existing.volume.select.help')" />
            </a-col>
            <a-col :xs="24" :md="12"><a-form-item><template #label><tooltip-label :title="$t('label.filesystem')" :tooltip="$t('message.storage.service.attach.filesystem.help')" /></template><a-select v-model:value="forms.attachVolume.filesystem"><a-select-option value="auto">auto</a-select-option><a-select-option value="xfs">xfs</a-select-option><a-select-option value="ext4">ext4</a-select-option></a-select></a-form-item></a-col>
            <a-col :xs="24" :md="12"><a-form-item required><template #label><tooltip-label :title="$t('label.storage.service.mount.path')" :tooltip="$t('message.storage.service.mount.path.help')" /></template><a-input v-model:value="forms.attachVolume.path" /></a-form-item></a-col>
          </a-row>
        </div>
        <div v-if="actionModal.type === 'resizeShare'" class="storage-action-form storage-action-form--vertical">
          <a-form-item required><template #label><tooltip-label :title="$t('label.storage.service.file.share')" :tooltip="$t('message.storage.service.select.share')" /></template><a-select v-model:value="forms.resizeShare.id" show-search optionFilterProp="label"><a-select-option v-for="share in fileShares" :key="share.id" :value="share.id" :label="shareLabel(share)">{{ shareLabel(share) }}</a-select-option></a-select></a-form-item>
          <a-form-item><template #label><tooltip-label :title="$t('label.storage.service.volume.size.gib')" :tooltip="$t('message.storage.service.volume.size.help')" /></template><a-input-number v-model:value="forms.resizeShare.size" class="storage-input-number" /></a-form-item>
          <capacity-input :label="$t('label.storage.service.file.share.capacity.limit')" :tooltip="$t('message.storage.service.quota.bytes.help')" v-model:amount="forms.resizeShare.quotaamount" v-model:unit="forms.resizeShare.quotaunit" :units="capacityUnits" />
        </div>
        <div v-if="actionModal.type === 'resizeVolume'" class="storage-action-form storage-action-form--vertical">
          <a-form-item required><template #label><tooltip-label :title="$t('label.storage.service.file.share')" :tooltip="$t('message.storage.service.select.share')" /></template><a-select v-model:value="forms.resizeShare.id" show-search optionFilterProp="label"><a-select-option v-for="share in fileShares" :key="share.id" :value="share.id" :label="shareLabel(share)">{{ shareLabel(share) }}</a-select-option></a-select></a-form-item>
          <a-form-item required><template #label><tooltip-label :title="$t('label.storage.service.volume.size.gib')" :tooltip="$t('message.storage.service.volume.size.help')" /></template><a-input-number v-model:value="forms.resizeShare.size" class="storage-input-number" /></a-form-item>
        </div>
        <div v-if="actionModal.type === 'detachBackingVolume'" class="storage-action-form storage-action-form--vertical">
          <a-alert type="warning" show-icon :message="$t('message.storage.service.detach.volume.warning')" />
          <div class="storage-action-summary-box storage-action-summary-box--compact">
            <div class="storage-action-summary-row"><span>{{ $t('label.volumename') }}</span><code>{{ actionModal.context?.name || '-' }}</code></div>
            <div class="storage-action-summary-row"><span>{{ $t('label.volumeid') }}</span><code>{{ forms.detachBackingVolume.volumeid || '-' }}</code></div>
            <div class="storage-action-summary-row"><span>{{ $t('label.size') }}</span><code>{{ actionModal.context?.size || '-' }}</code></div>
            <div class="storage-action-summary-row"><span>{{ $t('label.storage.service.mount.path') }}</span><code>{{ actionModal.context?.mountPath || '-' }}</code></div>
          </div>
          <a-checkbox v-model:checked="forms.detachBackingVolume.confirmation">
            {{ $t('message.storage.service.detach.volume.confirm') }}
          </a-checkbox>
        </div>
        <div v-if="actionModal.type === 'disconnectSession'" class="storage-action-form storage-action-form--vertical">
          <a-row :gutter="16">
            <a-col :xs="24"><a-alert type="warning" show-icon :message="$t('message.storage.service.session.disconnect.warning')" /></a-col>
            <a-col :xs="24" :md="12"><a-form-item><template #label><tooltip-label :title="$t('label.protocol')" :tooltip="$t('message.storage.service.protocol.help')" /></template><a-input v-model:value="forms.disconnectSession.protocol" disabled /></a-form-item></a-col>
            <a-col :xs="24" :md="12"><a-form-item required><template #label><tooltip-label :title="$t('label.storage.service.peer')" :tooltip="$t('message.storage.service.session.peer.help')" /></template><a-input v-model:value="forms.disconnectSession.peer" /></a-form-item></a-col>
            <a-col :xs="24" :md="12"><a-form-item><template #label><tooltip-label :title="$t('label.storage.service.local')" :tooltip="$t('message.storage.service.session.local.help')" /></template><a-input v-model:value="forms.disconnectSession.local" /></a-form-item></a-col>
            <a-col :xs="24" :md="12"><a-form-item><template #label><tooltip-label :title="$t('label.storage.service.force')" :tooltip="$t('message.storage.service.session.force.help')" /></template><a-switch v-model:checked="forms.disconnectSession.force" /></a-form-item></a-col>
          </a-row>
        </div>
      </a-form></div>
    </a-modal>
  </a-spin>
</template>
<script>

import { h, resolveComponent } from 'vue'
import { getAPI, postAPI } from '@/api'
import { mixinDevice } from '@/utils/mixin.js'
import Status from '@/components/widgets/Status'
import DetailsTab from '@/components/view/DetailsTab'
import StatsTab from '@/components/view/StatsTab'
import EventsTab from '@/components/view/EventsTab'
import NicsTab from '@/views/network/NicsTab.vue'
import TooltipButton from '@/components/widgets/TooltipButton'
import TooltipLabel from '@/components/widgets/TooltipLabel'
import { Empty } from 'ant-design-vue'
import {
  DeleteOutlined,
  DisconnectOutlined,
  EditOutlined,
  ExpandAltOutlined,
  FullscreenExitOutlined,
  FullscreenOutlined,
  LinkOutlined,
  PlusOutlined,
  PoweroffOutlined,
  ReloadOutlined,
  SafetyCertificateOutlined
} from '@ant-design/icons-vue'

const ProtocolHeader = {
  props: {
    protocol: {
      type: String,
      required: true
    }
  },
  computed: {
    protocolLabel () {
      return this.protocol === 'NVME_OF' ? 'NVMe-oF' : this.protocol
    }
  },
  render () {
    return h('div', { class: 'protocol-header' }, [
      h('div', [
        h('h3', this.protocolLabel),
        h('p', this.$t('message.storage.service.protocol.tab.description'))
      ])
    ])
  }
}

const EllipsisText = {
  props: {
    value: {
      type: [String, Number, Boolean],
      default: '-'
    },
    code: {
      type: Boolean,
      default: false
    }
  },
  computed: {
    displayValue () {
      if (this.value === undefined || this.value === null || this.value === '') {
        return '-'
      }
      return String(this.value)
    }
  },
  render () {
    return h('span', {
      class: {
        'storage-ellipsis': true,
        'storage-ellipsis--code': this.code
      },
      title: this.displayValue
    }, this.displayValue)
  }
}

const VolumeSelect = {
  components: {
    TooltipLabel
  },
  props: {
    value: {
      type: String,
      default: ''
    },
    volumes: {
      type: Array,
      default: () => []
    },
    loading: {
      type: Boolean,
      default: false
    },
    formatter: {
      type: Function,
      required: true
    },
    required: {
      type: Boolean,
      default: false
    },
    tooltip: {
      type: String,
      default: ''
    }
  },
  emits: ['update:value'],
  render () {
    const AFormItem = resolveComponent('a-form-item')
    const ASelect = resolveComponent('a-select')
    const ASelectOption = resolveComponent('a-select-option')
    return h(AFormItem, {
      required: this.required
    }, {
      label: () => h(TooltipLabel, {
        title: this.$t('label.storage.service.existing.volume.select'),
        tooltip: this.tooltip
      }),
      default: () => [
        h(ASelect, {
          value: this.value,
          loading: this.loading,
          allowClear: true,
          showSearch: true,
          optionFilterProp: 'label',
          onChange: value => this.$emit('update:value', value)
        }, {
          default: () => this.volumes.map(volume => h(ASelectOption, {
            key: volume.id,
            value: volume.id,
            label: this.formatter(volume)
          }, {
            default: () => this.formatter(volume)
          }))
        })
      ]
    })
  }
}

const CapacityInput = {
  components: {
    TooltipLabel
  },
  props: {
    label: {
      type: String,
      required: true
    },
    amount: {
      type: [Number, String],
      default: null
    },
    unit: {
      type: String,
      default: 'GiB'
    },
    units: {
      type: Array,
      default: () => []
    },
    tooltip: {
      type: String,
      default: ''
    },
    required: {
      type: Boolean,
      default: false
    }
  },
  emits: ['update:amount', 'update:unit'],
  render () {
    const AFormItem = resolveComponent('a-form-item')
    const AInputGroup = resolveComponent('a-input-group')
    const AInputNumber = resolveComponent('a-input-number')
    const ASelect = resolveComponent('a-select')
    const ASelectOption = resolveComponent('a-select-option')
    return h(AFormItem, {
      required: this.required
    }, {
      label: () => h(TooltipLabel, {
        title: this.label,
        tooltip: this.tooltip
      }),
      default: () => [
        h(AInputGroup, {
          compact: true,
          class: 'capacity-input-group'
        }, {
          default: () => [
            h(AInputNumber, {
              value: this.amount,
              min: 0,
              onChange: value => this.$emit('update:amount', value)
            }),
            h(ASelect, {
              value: this.unit,
              onChange: value => this.$emit('update:unit', value)
            }, {
              default: () => this.units.map(unitItem => h(ASelectOption, {
                key: unitItem.value,
                value: unitItem.value
              }, {
                default: () => unitItem.label
              }))
            })
          ]
        })
      ]
    })
  }
}

export default {
  name: 'SharedFSTab',
  components: {
    DetailsTab,
    StatsTab,
    EventsTab,
    NicsTab,
    TooltipButton,
    TooltipLabel,
    Status,
    ProtocolHeader,
    EllipsisText,
    VolumeSelect,
    CapacityInput,
    DeleteOutlined,
    DisconnectOutlined,
    EditOutlined,
    ExpandAltOutlined,
    FullscreenExitOutlined,
    FullscreenOutlined,
    LinkOutlined,
    PlusOutlined,
    PoweroffOutlined,
    ReloadOutlined,
    SafetyCertificateOutlined
  },
  mixins: [mixinDevice],
  props: {
    resource: {
      type: Object,
      required: true
    },
    loading: {
      type: Boolean,
      default: false
    }
  },
  emits: ['wide-layout-change'],
  inject: ['parentFetchData'],
  data () {
    return {
      vm: {},
      volume: {},
      volumes: [],
      availableVolumes: [],
      volumeLoading: false,
      storagePools: [],
      storagePoolLoading: false,
      virtualmachines: [],
      currentTab: 'details',
      protocolWideLayout: false,
      dataResource: {},
      storageService: {
        loading: false,
        initialLoading: false,
        refreshing: false,
        loaded: false,
        instance: null,
        health: [],
        inventory: [],
        sessions: [],
        domains: [],
        nfsExports: [],
        smbShares: [],
        iscsiTargets: [],
        nvmeSubsystems: [],
        nfsAcls: [],
        smbAcls: [],
        iscsiAcls: [],
        nvmeHostAcls: [],
        backingVolumes: []
      },
      diskOfferings: [],
      diskOfferingLoading: false,
      actionModal: {
        visible: false,
        type: '',
        context: null,
        loading: false
      },
      actionLoading: {},
      capacityUnits: [
        { value: 'B', label: 'B', multiplier: 1 },
        { value: 'MiB', label: 'MiB', multiplier: 1024 * 1024 },
        { value: 'GiB', label: 'GiB', multiplier: 1024 * 1024 * 1024 },
        { value: 'TiB', label: 'TiB', multiplier: 1024 * 1024 * 1024 * 1024 }
      ],
      forms: {
        enableProtocol: {
          protocol: 'NFS',
          listenipmode: 'EXISTING',
          listenip: '',
          port: null
        },
        nfsExport: {
          name: '',
          path: '',
          volumeid: '',
          volumemode: 'CURRENT',
          newvolumename: '',
          diskofferingid: '',
          storageid: '',
          newvolumesize: null,
          filesystem: 'xfs',
          relativepath: '',
          createdirectory: false,
          quotaamount: null,
          quotaunit: 'GiB',
          endpointmode: 'SELECTED',
          listenips: [],
          readonly: false,
          rootsquash: true,
          allsquash: false,
          anonuid: 65534,
          anongid: 65534,
          owneruid: 65534,
          ownergid: 65534,
          mode: '0775',
          recursivepermission: false,
          sync: true,
          secure: false
        },
        nfsAcl: {
          exportid: '',
          principaltype: 'CIDR',
          principal: '',
          principals: [],
          permission: 'READ_WRITE',
          rootsquash: true,
          allsquash: false,
          anonuid: null,
          anongid: null,
          sync: true,
          secure: false
        },
        smbShare: {
          name: '',
          path: '',
          volumeid: '',
          filesystem: 'xfs',
          quotaamount: null,
          quotaunit: 'GiB',
          readonly: false,
          browseable: true,
          guestok: false
        },
        smbAcl: {
          shareid: '',
          principaltype: 'LOCAL_USER',
          principal: '',
          permission: 'READ_WRITE',
          password: ''
        },
        adJoin: {
          domainname: '',
          username: '',
          password: '',
          organizationalunit: '',
          dnsservers: '',
          workgroup: ''
        },
        iscsiTarget: {
          targetname: '',
          lun: '0',
          volumeid: '',
          lunsizeamount: null,
          lunsizeunit: 'GiB',
          backingpath: ''
        },
        iscsiAcl: {
          targetid: '',
          initiatoriqn: '',
          permission: 'READ_WRITE',
          chapenabled: false,
          chapusername: '',
          chapsecret: '',
          mutualchapenabled: false,
          mutualchapusername: '',
          mutualchapsecret: ''
        },
        nvmePrepare: {
          engine: 'KERNEL_NVMET',
          transport: 'tcp',
          validateonly: true
        },
        nvmeSubsystem: {
          subsystemnqn: '',
          allowanyhost: false,
          engine: 'KERNEL_NVMET',
          transport: 'tcp'
        },
        nvmeHostAcl: {
          subsystemid: '',
          hostnqn: '',
          dhchapenabled: false,
          dhchapkey: '',
          dhchapctrlenabled: false,
          dhchapctrlkey: ''
        },
        attachVolume: {
          id: '',
          volumeid: '',
          path: '',
          filesystem: 'auto',
          importmode: 'MOUNT_EXISTING'
        },
        resizeShare: {
          id: '',
          size: null,
          quotaamount: null,
          quotaunit: 'GiB',
          resizevolume: true
        },
        detachBackingVolume: {
          volumeid: '',
          confirmation: false
        },
        disconnectSession: {
          protocol: '',
          peer: '',
          local: '',
          sessionid: '',
          force: true
        },
        deleteConfirm: {
          resourceType: '',
          command: '',
          id: '',
          protocol: '',
          confirmation: ''
        },
        deleteEndpoint: {
          protocol: 'NFS',
          listenip: '',
          confirmation: ''
        }
      }
    }
  },
  computed: {
    hasStorageServiceApi () {
      return 'listStorageServiceInstances' in this.$store.getters.apis
    },
    fileShares () {
      return [
        ...this.storageService.nfsExports,
        ...this.storageService.smbShares
      ]
    },
    currentBackingVolumes () {
      const volumes = []
      const seen = new Set()
      const add = volume => {
        if (!volume || !volume.id || seen.has(String(volume.id))) {
          return
        }
        if (!this.belongsToCurrentServiceVm(volume)) {
          return
        }
        const type = String(volume.type || '').toUpperCase()
        if (type && type !== 'DATADISK') {
          return
        }
        seen.add(String(volume.id))
        volumes.push(volume)
      }
      this.storageService.backingVolumes.forEach(add)
      if (this.volume.id) {
        add(this.volume)
      }
      return volumes
    },
    selectedCurrentBackingVolume () {
      const selected = String(this.forms.nfsExport.volumeid || '')
      if (!selected) {
        return null
      }
      return this.currentBackingVolumes.find(volume => String(volume.id) === selected || String(volume.uuid) === selected) || null
    },
    selectedNfsDiskOffering () {
      return this.diskOfferings.find(offering => offering.id === this.forms.nfsExport.diskofferingid)
    },
    selectedNfsDiskOfferingTags () {
      return this.extractStorageTags(this.selectedNfsDiskOffering)
    },
    filteredNfsNewVolumeStoragePools () {
      const requiredTags = this.selectedNfsDiskOfferingTags.map(tag => tag.toLowerCase())
      if (!requiredTags.length) {
        return this.storagePools
      }
      return this.storagePools.filter(pool => {
        const poolTags = this.extractStorageTags(pool).map(tag => tag.toLowerCase())
        return requiredTags.every(tag => poolTags.includes(tag))
      })
    },
    serviceNics () {
      return [
        ...(this.vm.nic || []),
        ...(this.resource.nic || [])
      ]
    },
    runtimeNetworkAddresses () {
      const items = []
      const seen = new Set()
      const add = (source, label) => {
        const values = Array.isArray(source) ? source : (source ? [source] : [])
        values.forEach(item => {
          let ipaddress = ''
          let interfaceName = ''
          let cidr = ''
          let role = ''
          let secondary = false
          let dynamic = false
          if (typeof item === 'string') {
            ipaddress = item.includes('/') ? item.split('/', 1)[0] : item
            cidr = item
          } else if (item && typeof item === 'object') {
            ipaddress = item.ipaddress || item.ipAddress || item.ip || item.address || item.local || ''
            interfaceName = item.interface || item.ifname || item.device || ''
            cidr = item.cidr || item.prefix || item.netmask || ''
            role = String(item.role || '').toLowerCase()
            secondary = this.boolValue(item.secondary) || role === 'secondary'
            dynamic = this.boolValue(item.dynamic)
          }
          ipaddress = String(ipaddress || '').trim()
          if (!ipaddress || seen.has(ipaddress)) {
            return
          }
          if (!role) {
            role = secondary ? 'secondary' : 'primary'
          }
          seen.add(ipaddress)
          const roleLabel = role === 'secondary' ? this.$t('label.secondaryips') : this.$t('label.primary')
          items.push({
            ipaddress,
            interfaceName,
            cidr,
            role,
            secondary,
            primary: role === 'primary',
            dynamic,
            label: [label, roleLabel, interfaceName, ipaddress].filter(Boolean).join(' / ')
          })
        })
      }
      const healthNetwork = this.parsedHealth.network || {}
      const inventoryNetwork = this.parsedInventory.network || {}
      add(healthNetwork.addresses || this.parsedHealth.networkAddresses, this.$t('label.storage.service.runtime.endpoint'))
      add(inventoryNetwork.addresses || this.parsedInventory.networkAddresses, this.$t('label.storage.service.runtime.endpoint'))
      return items
    },
    serviceListenIps () {
      const items = []
      const seen = new Set()
      const addIp = (nic, ipaddress, kind) => {
        if (!ipaddress || seen.has(ipaddress)) {
          return
        }
        seen.add(ipaddress)
        const labelParts = [nic.networkname, ipaddress].filter(Boolean)
        if (kind === 'SECONDARY') {
          labelParts.push(this.$t('label.secondaryips'))
        } else if (kind === 'PRIMARY') {
          labelParts.push(this.$t('label.primary'))
        }
        items.push({
          ...nic,
          key: `${kind}-${nic.id || nic.networkid || ipaddress}-${ipaddress}`,
          ipaddress,
          kind,
          label: labelParts.join(' / ')
        })
      }
      this.runtimeNetworkAddresses.forEach(address => {
        addIp({
          id: address.interfaceName || address.ipaddress,
          networkname: [this.$t('label.storage.service.runtime.endpoint'), address.interfaceName].filter(Boolean).join(' / '),
          cidr: address.cidr
        }, address.ipaddress, address.role === 'secondary' ? 'SECONDARY' : 'PRIMARY')
      })
      this.serviceNics.forEach(nic => {
        addIp(nic, nic.ipaddress, 'PRIMARY')
        ;(nic.secondaryip || []).forEach(secondary => addIp(nic, secondary.ipaddress, 'SECONDARY'))
      })
      this.storageService.nfsExports.forEach(share => {
        this.normalizeEndpointIps(share.listenips || share.listenIps || this.parseStorageConfig(share.config).listenIps || this.parseStorageConfig(share.config).listenips).forEach(ipaddress => {
          addIp({
            id: `nfs-${share.id || share.name || ipaddress}`,
            networkname: this.$t('label.storage.service.configured.endpoint')
          }, ipaddress, 'CONFIGURED')
        })
      })
      return items
    },
    serviceEndpoint () {
      const nic = this.serviceListenIps.find(item => item.kind === 'PRIMARY' && item.ipaddress) || this.serviceListenIps.find(item => item.ipaddress) || {}
      return nic.ipaddress || this.vm.ipaddress || this.resource.ipaddress || this.resource.serviceip || this.resource.ip || ''
    },
    serviceEndpoints () {
      const endpoints = this.serviceListenIps.map(item => item.ipaddress).filter(Boolean)
      const fallback = this.serviceEndpoint
      if (fallback && !endpoints.includes(fallback)) {
        endpoints.unshift(fallback)
      }
      return endpoints
    },
    removableServiceEndpoints () {
      return this.serviceListenIps.filter(item => {
        if (!item.ipaddress) return false
        return item.kind !== 'PRIMARY'
      })
    },
    serviceEndpointSummary () {
      return this.serviceEndpoints.length ? this.serviceEndpoints.join(', ') : this.serviceEndpoint
    },
    selectedNfsAclExport () {
      const selected = String(this.forms.nfsAcl.exportid || '')
      return this.storageService.nfsExports.find(share => String(share.id) === selected || String(share.uuid) === selected) || null
    },
    parsedHealth () {
      return this.parseRuntimeResult(this.storageService.health[0])
    },
    parsedInventory () {
      return this.parseRuntimeResult(this.storageService.inventory[0])
    },
    nvmeCapability () {
      return this.parsedHealth?.capabilities?.nvmeof ||
        this.parsedInventory?.capabilities?.nvmeof ||
        this.parsedInventory?.nvmeofRuntime?.capabilities ||
        {}
    },
    nvmeDhChapSupported () {
      return this.boolValue(this.nvmeCapability.dhChapSupported)
    },
    nvmeDhChapCtrlSupported () {
      return this.boolValue(this.nvmeCapability.dhChapCtrlSupported)
    },
    nvmeDhChapUnsupportedMessage () {
      const reason = this.nvmeCapability.reason
      if (reason) {
        return this.$t('message.storage.service.nvme.dhchap.unsupported.with.reason', { reason })
      }
      return this.$t('message.storage.service.nvme.dhchap.unsupported')
    },
    latestHealth () {
      return {
        success: this.parsedHealth.success !== false,
        status: this.parsedHealth.status || this.storageService.health[0]?.status || '-'
      }
    },
    qgaStatus () {
      return this.parsedHealth.qga || this.parsedHealth.qgaStatus || this.parsedHealth.guestAgent || '-'
    },
    allSessions () {
      const runtime = this.parseRuntimeResult(this.storageService.sessions[0])
      return runtime.sessions || []
    },
    activeServiceTypes () {
      const protocols = []
      if (this.storageService.nfsExports.length > 0) protocols.push('NFS')
      if (this.storageService.smbShares.length > 0) protocols.push('SMB')
      if (this.storageService.iscsiTargets.length > 0) protocols.push('iSCSI')
      if (this.storageService.nvmeSubsystems.length > 0) protocols.push('NVMe-oF')
      return protocols
    },
    nfsConnectionCommands () {
      const endpoint = this.serviceEndpoints.length === 1 ? this.serviceEndpoints[0] : `<${this.$t('label.storage.service.endpoint.ip.placeholder')}>`
      return [`mount -t nfs ${endpoint}:/<${this.$t('label.storage.service.export.name.placeholder')}> <${this.$t('label.storage.service.local.mount.path.placeholder')}>`, `showmount -e ${endpoint}`]
    },
    smbConnectionCommands () {
      const endpoint = this.serviceEndpoint || '<service-ip>'
      const share = `<${this.$t('label.storage.service.share.name.placeholder')}>`
      const user = `<${this.$t('label.username')}>`
      return [`\\\\${endpoint}\\${share}`, `net use * \\\\${endpoint}\\${share} /user:${user}`, `smbclient //${endpoint}/${share} -U ${user}`]
    },
    smbEndpoint () {
      return `\\\\${this.serviceEndpoint || '<service-ip>'}\\<${this.$t('label.storage.service.share.name.placeholder')}>`
    },
    smbDomainStatus () {
      return this.storageService.domains.find(domain => {
        const protocol = String(domain.protocol || domain.service || domain.type || 'SMB').toUpperCase()
        return protocol === 'SMB'
      }) || {}
    },
    smbRuntime () {
      const inventory = this.parsedInventory || {}
      const health = this.parsedHealth || {}
      return inventory.smb || health.smb || inventory.samba || health.samba || {}
    },
    smbIdentityMode () {
      const mode = this.smbDomainStatus.identitymode || this.smbDomainStatus.identityMode || this.smbDomainStatus.mode || this.smbRuntime.identityMode || this.smbRuntime.identitymode
      if (String(mode || '').toUpperCase() === 'AD') {
        return this.$t('label.storage.service.smb.identity.ad')
      }
      if (mode) {
        return this.$t('label.storage.service.smb.identity.local')
      }
      return this.storageService.domains.length > 0 ? this.$t('label.storage.service.smb.identity.ad') : this.$t('label.storage.service.smb.identity.local')
    },
    smbDomainName () {
      return this.smbDomainStatus.domainname || this.smbDomainStatus.domainName || this.smbRuntime.domain || this.smbRuntime.domainName || '-'
    },
    smbWorkgroup () {
      return this.smbDomainStatus.workgroup || this.smbRuntime.workgroup || '-'
    },
    smbDomainState () {
      return this.smbDomainStatus.state || this.smbDomainStatus.status || this.smbRuntime.domainState || this.smbRuntime.joinState || '-'
    },
    smbDaemonState () {
      const services = this.smbRuntime.services || this.smbRuntime.daemons || this.parsedHealth.services || {}
      if (Array.isArray(services)) {
        return services.filter(service => /smb|nmb|winbind/i.test(service.name || service.service || '')).map(service => `${service.name || service.service}:${service.state || service.status || '-'}`).join(', ') || '-'
      }
      const values = ['smbd', 'nmbd', 'winbind'].map(name => services[name] ? `${name}:${services[name].state || services[name].status || services[name]}` : '').filter(Boolean)
      return values.join(', ') || this.smbRuntime.daemonState || '-'
    },
    smbSetupIncomplete () {
      return this.isProtocolEnabled('SMB') && this.storageService.smbShares.length === 0
    },
    iscsiConnectionCommands () {
      const endpoint = this.serviceEndpoint || '<service-ip>'
      const target = this.storageService.iscsiTargets[0]?.targetname || '<target-iqn>'
      return [`iscsiadm -m discovery -t sendtargets -p ${endpoint}:3260`, `iscsiadm -m node -T ${target} -p ${endpoint}:3260 --login`]
    },
    nvmeConnectionCommands () {
      const endpoint = this.serviceEndpoint || '<service-ip>'
      const nqn = this.storageService.nvmeSubsystems[0]?.targetname || '<subsystem-nqn>'
      const commands = [`nvme discover -t tcp -a ${endpoint} -s 4420`, `nvme connect -t tcp -a ${endpoint} -s 4420 -n ${nqn}`]
      if (this.nvmeAclRows.some(row => row.authRequired)) {
        commands.push(this.$t('message.storage.service.nvme.auth.command.hint'))
      }
      return commands
    },
    monitorCacheInfo () {
      const health = this.parsedHealth || {}
      const cache = health.cache || health.monitorCache || health.monitor || {}
      const generatedAt = cache.generatedAt || health.generatedAt || health.timestamp || this.storageService.health[0]?.created || ''
      const status = cache.status || cache.state || health.monitorStatus || health.status || '-'
      const stale = cache.stale === true || cache.isStale === true || health.stale === true
      return {
        generatedAt,
        status,
        stale
      }
    },
    monitorCacheLabel () {
      if (!this.storageService.health.length) {
        return this.$t('label.storage.service.cache.not.loaded')
      }
      if (this.monitorCacheInfo.stale) {
        return this.$t('label.storage.service.cache.stale')
      }
      return this.monitorCacheInfo.status || this.$t('label.storage.service.cache.fresh')
    },
    monitorCacheColor () {
      if (!this.storageService.health.length) return 'default'
      return this.monitorCacheInfo.stale ? 'orange' : 'green'
    },
    monitorCacheTimestamp () {
      return this.monitorCacheInfo.generatedAt || '-'
    },
    nfsExportColumns () {
      return [
        { title: this.$t('label.storage.service.export.name'), dataIndex: 'name', key: 'name', fixed: 'left', width: 180, code: true },
        { title: this.$t('label.storage.service.client.mount.root'), dataIndex: 'clientPath', key: 'clientPath', width: 240, code: true },
        { title: this.$t('label.storage.service.internal.path'), dataIndex: 'path', key: 'path', width: 220, code: true },
        { title: this.$t('label.storage.service.ip.port'), dataIndex: 'endpoint', key: 'endpoint', width: 170, code: true },
        { title: this.$t('label.storage.service.permission'), dataIndex: 'permission', key: 'permission', width: 130 },
        { title: this.$t('label.storage.service.root.squash'), dataIndex: 'rootSquash', key: 'rootSquash', width: 130 },
        { title: this.$t('label.storage.service.posix.permission'), dataIndex: 'posixPermission', key: 'posixPermission', width: 220, code: true },
        { title: this.$t('label.storage.service.access.rules'), dataIndex: 'aclSummary', key: 'aclSummary', width: 220 },
        { title: this.$t('label.storage.service.capacity'), dataIndex: 'capacity', key: 'capacity', width: 150 },
        { title: this.$t('label.storage.service.backing.volume'), dataIndex: 'volumeName', key: 'volumeName', width: 210 },
        { title: this.$t('label.state'), dataIndex: 'state', key: 'state', width: 110 },
        { title: this.$t('label.actions'), dataIndex: 'actions', key: 'actions', fixed: 'right', width: 310 }
      ]
    },
    nfsAclColumns () {
      return [
        { title: this.$t('label.storage.service.export.name'), dataIndex: 'exportName', key: 'exportName', fixed: 'left', width: 180, code: true },
        { title: this.$t('label.storage.service.principal'), dataIndex: 'principal', key: 'principal', width: 220, code: true },
        { title: this.$t('label.storage.service.permission'), dataIndex: 'permission', key: 'permission', width: 140 },
        { title: this.$t('label.storage.service.root.squash'), dataIndex: 'rootSquash', key: 'rootSquash', width: 130 },
        { title: this.$t('label.storage.service.all.squash'), dataIndex: 'allSquash', key: 'allSquash', width: 130 },
        { title: this.$t('label.storage.service.anon.uid.gid'), dataIndex: 'anonUidGid', key: 'anonUidGid', width: 150, code: true },
        { title: this.$t('label.storage.service.sync'), dataIndex: 'sync', key: 'sync', width: 110 },
        { title: this.$t('label.storage.service.secure'), dataIndex: 'secure', key: 'secure', width: 130 },
        { title: this.$t('label.state'), dataIndex: 'state', key: 'state', width: 120 },
        { title: this.$t('label.actions'), dataIndex: 'actions', key: 'actions', fixed: 'right', width: 180 }
      ]
    },
    nfsVolumeColumns () {
      return [
        { title: this.$t('label.volumename'), dataIndex: 'name', key: 'name', fixed: 'left', width: 200 },
        { title: this.$t('label.volumeid'), dataIndex: 'id', key: 'id', width: 260, code: true },
        { title: this.$t('label.size'), dataIndex: 'size', key: 'size', width: 130 },
        { title: this.$t('label.storage.service.used.capacity'), dataIndex: 'used', key: 'used', width: 140 },
        { title: this.$t('label.diskoffering'), dataIndex: 'diskOffering', key: 'diskOffering', width: 220 },
        { title: this.$t('label.storagepool'), dataIndex: 'storagePool', key: 'storagePool', width: 220 },
        { title: this.$t('label.filesystem'), dataIndex: 'filesystem', key: 'filesystem', width: 130 },
        { title: this.$t('label.storage.service.attached.export'), dataIndex: 'exportName', key: 'exportName', width: 200, code: true },
        { title: this.$t('label.state'), dataIndex: 'state', key: 'state', width: 120 },
        { title: this.$t('label.actions'), dataIndex: 'actions', key: 'actions', fixed: 'right', width: 290 }
      ]
    },
    nfsSessionColumns () {
      return [
        { title: this.$t('label.storage.service.peer'), dataIndex: 'peer', key: 'peer', fixed: 'left', width: 200, code: true },
        { title: this.$t('label.state'), dataIndex: 'state', key: 'state', width: 130 },
        { title: this.$t('label.storage.service.connected.at'), dataIndex: 'connectedAt', key: 'connectedAt', width: 190 },
        { title: this.$t('label.storage.service.export.name'), dataIndex: 'resourceName', key: 'resourceName', width: 200, code: true },
        { title: this.$t('label.storage.service.local'), dataIndex: 'local', key: 'local', width: 220, code: true },
        { title: this.$t('label.actions'), dataIndex: 'actions', key: 'actions', fixed: 'right', width: 170 }
      ]
    },
    smbShareColumns () {
      return [
        { title: this.$t('label.storage.service.smb.share.name'), dataIndex: 'name', key: 'name', fixed: 'left', width: 190, code: true },
        { title: this.$t('label.storage.service.client.unc.root'), dataIndex: 'clientPath', key: 'clientPath', width: 260, code: true },
        { title: this.$t('label.storage.service.internal.path'), dataIndex: 'path', key: 'path', width: 220, code: true },
        { title: this.$t('label.storage.service.ip.port'), dataIndex: 'endpoint', key: 'endpoint', width: 170, code: true },
        { title: this.$t('label.storage.service.browseable'), dataIndex: 'browseable', key: 'browseable', width: 120 },
        { title: this.$t('label.storage.service.guest.access'), dataIndex: 'guestOk', key: 'guestOk', width: 130 },
        { title: this.$t('label.storage.service.permission'), dataIndex: 'permission', key: 'permission', width: 140 },
        { title: this.$t('label.storage.service.capacity'), dataIndex: 'capacity', key: 'capacity', width: 150 },
        { title: this.$t('label.storage.service.backing.volume'), dataIndex: 'volumeName', key: 'volumeName', width: 210 },
        { title: this.$t('label.state'), dataIndex: 'state', key: 'state', width: 110 },
        { title: this.$t('label.actions'), dataIndex: 'actions', key: 'actions', fixed: 'right', width: 190 }
      ]
    },
    smbAclColumns () {
      return [
        { title: this.$t('label.storage.service.smb.share.name'), dataIndex: 'shareName', key: 'shareName', fixed: 'left', width: 190, code: true },
        { title: this.$t('label.storage.service.principal.type'), dataIndex: 'principalType', key: 'principalType', width: 170 },
        { title: this.$t('label.storage.service.principal'), dataIndex: 'principal', key: 'principal', width: 220, code: true },
        { title: this.$t('label.storage.service.permission'), dataIndex: 'permission', key: 'permission', width: 140 },
        { title: this.$t('label.storage.service.smb.identity.mode'), dataIndex: 'identityMode', key: 'identityMode', width: 190 },
        { title: this.$t('label.state'), dataIndex: 'state', key: 'state', width: 120 }
      ]
    },
    smbVolumeColumns () {
      return [
        { title: this.$t('label.volumename'), dataIndex: 'name', key: 'name', fixed: 'left', width: 200 },
        { title: this.$t('label.volumeid'), dataIndex: 'id', key: 'id', width: 260, code: true },
        { title: this.$t('label.size'), dataIndex: 'size', key: 'size', width: 130 },
        { title: this.$t('label.storage.service.used.capacity'), dataIndex: 'used', key: 'used', width: 140 },
        { title: this.$t('label.diskoffering'), dataIndex: 'diskOffering', key: 'diskOffering', width: 220 },
        { title: this.$t('label.storagepool'), dataIndex: 'storagePool', key: 'storagePool', width: 220 },
        { title: this.$t('label.filesystem'), dataIndex: 'filesystem', key: 'filesystem', width: 130 },
        { title: this.$t('label.storage.service.attached.share'), dataIndex: 'shareName', key: 'shareName', width: 200, code: true },
        { title: this.$t('label.state'), dataIndex: 'state', key: 'state', width: 120 },
        { title: this.$t('label.actions'), dataIndex: 'actions', key: 'actions', fixed: 'right', width: 170 }
      ]
    },
    smbSessionColumns () {
      return [
        { title: this.$t('label.storage.service.peer'), dataIndex: 'peer', key: 'peer', fixed: 'left', width: 200, code: true },
        { title: this.$t('label.username'), dataIndex: 'user', key: 'user', width: 180, code: true },
        { title: this.$t('label.storage.service.smb.share.name'), dataIndex: 'resourceName', key: 'resourceName', width: 200, code: true },
        { title: this.$t('label.storage.service.smb.dialect'), dataIndex: 'dialect', key: 'dialect', width: 150 },
        { title: this.$t('label.state'), dataIndex: 'state', key: 'state', width: 130 },
        { title: this.$t('label.storage.service.connected.at'), dataIndex: 'connectedAt', key: 'connectedAt', width: 190 },
        { title: this.$t('label.storage.service.local'), dataIndex: 'local', key: 'local', width: 220, code: true },
        { title: this.$t('label.storage.service.tree.session.id'), dataIndex: 'sessionId', key: 'sessionId', width: 190, code: true },
        { title: this.$t('label.actions'), dataIndex: 'actions', key: 'actions', fixed: 'right', width: 170 }
      ]
    },
    nfsExportRows () {
      return this.storageService.nfsExports.map((share, index) => {
        const name = this.clientVisibleName(share.name || share.exportname, `nfs${index + 1}`)
        const acls = this.nfsAclsForShare(share)
        const config = this.effectiveNfsExportConfig(this.parseStorageConfig(share.config))
        const volume = this.volumeForShare(share)
        return {
          key: share.id || `nfs-export-${index}`,
          id: share.id,
          name,
          clientPath: this.formatNfsClientMountRoots(share, name),
          path: share.path || share.mountpath || share.backingpath || '-',
          endpoint: this.formatNfsExportEndpoints(share, share.port || 2049),
          permission: this.permissionLabel(share.permission || (share.readonly || config.readOnly ? 'READ_ONLY' : 'READ_WRITE')),
          rootSquash: this.booleanLabel(config.rootSquash),
          posixPermission: this.posixPermissionSummary(config),
          aclSummary: this.aclSummary(acls),
          capacity: this.formatCapacityValue(share.quotabytes || share.quotaBytes || share.capacitybytes || share.sizebytes),
          volumeName: volume.name || volume.displayname || share.volumename || share.volumeName || share.volumeid || '-',
          state: share.state || share.status || '-',
          raw: share
        }
      })
    },
    nfsFailedExportMessages () {
      return this.storageService.nfsExports
        .filter(share => String(share.state || '').toLowerCase() === 'error' || share.configvalid === false || share.configValid === false)
        .map(share => {
          const config = this.parseStorageConfig(share.config)
          const error = config.lastError || config.lasterror || {}
          const name = this.clientVisibleName(share.name || share.exportname, share.id || '-')
          const reason = error.message || share.configerror || share.configError || this.$t('message.storage.service.nfs.export.error.unknown')
          return `${name}: ${reason}`
        })
    },
    nfsAclRows () {
      return this.storageService.nfsAcls.map((acl, index) => {
        const share = this.nfsShareForAcl(acl)
        const config = this.parseStorageConfig(acl.config)
        return {
          key: acl.id || `nfs-acl-${index}`,
          exportName: this.clientVisibleName(share?.name || acl.exportname || acl.resourcename, '-'),
          principal: acl.principal || acl.cidr || acl.client || '-',
          permission: this.permissionLabel(acl.permission || acl.access || '-'),
          rootSquash: this.booleanLabel(acl.rootsquash ?? acl.rootSquash ?? config.rootSquash),
          allSquash: this.booleanLabel(acl.allsquash ?? acl.allSquash ?? config.allSquash),
          anonUidGid: this.uidGidSummary(config.anonUid ?? config.anonuid, config.anonGid ?? config.anongid),
          sync: this.booleanLabel(acl.sync ?? config.sync),
          secure: this.booleanLabel(acl.secure ?? config.secure),
          state: acl.state || acl.status || '-',
          raw: acl
        }
      })
    },
    nfsVolumeRows () {
      const rows = []
      const seen = new Set()
      this.currentBackingVolumes.forEach((volume, index) => {
        const id = volume.id || `backing-volume-${index}`
        if (seen.has(id)) return
        seen.add(id)
        const shares = this.nfsExportsForVolume(volume)
        const mountPath = this.currentBackingVolumeMountPath(volume)
        rows.push({
          key: id,
          id,
          exportId: shares[0]?.id,
          name: volume.name || volume.displayname || '-',
          size: this.formatCapacityValue(volume.size || this.volume.size || this.resource.size),
          used: this.formatCapacityValue(volume.usedfsbytes || volume.usedphysicalsize || volume.physicalsize),
          diskOffering: volume.diskofferingname || this.volume.diskofferingname || this.resource.diskofferingname || '-',
          storagePool: volume.storage || volume.storagepool || volume.storagePoolName || this.volume.storage || this.resource.storage || '-',
          filesystem: this.currentBackingVolumeFilesystem(volume) || this.resource.filesystem || '-',
          exportName: shares.length ? shares.map(share => this.clientVisibleName(share.name || share.exportname, '-')).join(', ') : '-',
          mountPath,
          detachAllowed: shares.length === 0 && String(volume.state || '').toLowerCase() !== 'destroyed',
          state: volume.state || this.volume.state || '-',
          raw: volume
        })
      })
      return rows
    },
    nfsSessionRows () {
      return this.protocolSessions('NFS').map((session, index) => ({
        key: session.sessionId || session.id || `${session.peer || 'session'}-${index}`,
        protocol: session.protocol || 'NFS',
        peer: session.peer || session.client || session.clientIp || '-',
        state: session.state || session.status || '-',
        connectedAt: session.connectedAt || session.since || session.age || '-',
        resourceName: session.resourceName || session.exportName || session.share || '-',
        local: session.local || session.endpoint || '-',
        sessionId: session.sessionId || session.id || '',
        raw: session
      }))
    },
    smbShareRows () {
      return this.storageService.smbShares.map((share, index) => {
        const name = this.clientVisibleName(share.name || share.sharename, `smb${index + 1}`)
        const config = this.parseStorageConfig(share.config)
        const volume = this.volumeForShare(share)
        return {
          key: share.id || `smb-share-${index}`,
          id: share.id,
          name,
          clientPath: `\\\\${this.serviceEndpoint || '<service-ip>'}\\${name}`,
          path: share.path || share.mountpath || share.backingpath || '-',
          endpoint: `${share.listenip || this.serviceEndpoint || '-'}:${share.port || 445}`,
          browseable: this.booleanLabel(share.browseable ?? config.browseable),
          guestOk: this.booleanLabel(share.guestok ?? share.guestOk ?? config.guestOk),
          permission: this.permissionLabel(share.permission || (share.readonly || config.readOnly ? 'READ_ONLY' : 'READ_WRITE')),
          capacity: this.formatCapacityValue(share.quotabytes || share.quotaBytes || share.capacitybytes || share.sizebytes),
          volumeName: volume.name || volume.displayname || share.volumename || share.volumeName || share.volumeid || '-',
          state: share.state || share.status || '-',
          raw: share
        }
      })
    },
    smbAclRows () {
      return this.storageService.smbAcls.map((acl, index) => {
        const share = this.smbShareForAcl(acl)
        const principalType = acl.principaltype || acl.principalType || '-'
        return {
          key: acl.id || `smb-acl-${index}`,
          shareName: this.clientVisibleName(share?.name || acl.sharename || acl.resourcename, '-'),
          principalType: this.principalTypeLabel(principalType),
          principal: acl.principal || acl.username || acl.account || '-',
          permission: this.permissionLabel(acl.permission || acl.access || '-'),
          identityMode: String(principalType).startsWith('AD_') ? this.$t('label.storage.service.smb.identity.ad') : this.$t('label.storage.service.smb.identity.local'),
          state: acl.state || acl.status || '-',
          raw: acl
        }
      })
    },
    smbVolumeRows () {
      const rows = []
      const seen = new Set()
      this.storageService.smbShares.forEach((share, index) => {
        const volume = this.volumeForShare(share)
        const id = volume.id || share.volumeid || share.volumeId || this.volume.id || `smb-share-${index}`
        if (seen.has(id)) return
        seen.add(id)
        rows.push({
          key: id,
          id,
          shareId: share.id,
          name: volume.name || volume.displayname || share.volumename || share.volumeName || this.volume.name || this.volume.displayname || '-',
          size: this.formatCapacityValue(share.volumesize || share.volumeSize || volume.size || this.volume.size || this.resource.size),
          used: this.formatCapacityValue(share.usedbytes || share.usedBytes || volume.usedfsbytes || volume.usedphysicalsize || volume.physicalsize || share.physicalsize),
          diskOffering: share.diskofferingname || share.diskOfferingName || volume.diskofferingname || this.volume.diskofferingname || this.resource.diskofferingname || '-',
          storagePool: share.storage || share.storagepool || share.storagePoolName || volume.storage || this.volume.storage || this.resource.storage || '-',
          filesystem: share.filesystem || share.fsType || this.resource.filesystem || '-',
          shareName: this.clientVisibleName(share.name || share.sharename, '-'),
          state: share.volumestate || share.volumeState || volume.state || this.volume.state || '-',
          raw: share
        })
      })
      return rows
    },
    smbSessionRows () {
      return this.protocolSessions('SMB').map((session, index) => ({
        key: session.sessionId || session.id || `${session.peer || 'session'}-${index}`,
        protocol: session.protocol || 'SMB',
        peer: session.peer || session.client || session.clientIp || '-',
        user: session.user || session.username || '-',
        resourceName: session.resourceName || session.shareName || session.share || '-',
        dialect: session.dialect || session.version || session.protocolVersion || '-',
        state: session.state || session.status || '-',
        connectedAt: session.connectedAt || session.since || session.age || '-',
        local: session.local || session.endpoint || '-',
        sessionId: session.sessionId || session.treeId || session.id || '',
        raw: session
      }))
    },
    iscsiTargetColumns () {
      return [
        { title: this.$t('label.storage.service.target.iqn'), dataIndex: 'targetName', key: 'targetName', fixed: 'left', width: 330, code: true },
        { title: this.$t('label.storage.service.lun'), dataIndex: 'lun', key: 'lun', width: 90, code: true },
        { title: this.$t('label.storage.service.endpoint'), dataIndex: 'endpoint', key: 'endpoint', width: 180, code: true },
        { title: this.$t('label.storage.service.backing.volume'), dataIndex: 'volumeName', key: 'volumeName', width: 220 },
        { title: this.$t('label.storage.service.lun.size'), dataIndex: 'lunSize', key: 'lunSize', width: 150 },
        { title: this.$t('label.storage.service.effective.lun.size'), dataIndex: 'effectiveSize', key: 'effectiveSize', width: 160 },
        { title: this.$t('label.storage.service.runtime.backing.path'), dataIndex: 'backingPath', key: 'backingPath', width: 300, code: true },
        { title: this.$t('label.storage.service.access.rules'), dataIndex: 'aclSummary', key: 'aclSummary', width: 260 },
        { title: this.$t('label.state'), dataIndex: 'state', key: 'state', width: 120 }
      ]
    },
    iscsiAclColumns () {
      return [
        { title: this.$t('label.storage.service.target.iqn'), dataIndex: 'targetName', key: 'targetName', fixed: 'left', width: 330, code: true },
        { title: this.$t('label.storage.service.allowed.initiator.iqn'), dataIndex: 'principal', key: 'principal', width: 300, code: true },
        { title: this.$t('label.storage.service.permission'), dataIndex: 'permission', key: 'permission', width: 140 },
        { title: this.$t('label.storage.service.chap.enabled'), dataIndex: 'chapEnabled', key: 'chapEnabled', width: 130 },
        { title: this.$t('label.storage.service.chap.username'), dataIndex: 'chapUsername', key: 'chapUsername', width: 190, code: true },
        { title: this.$t('label.storage.service.mutual.chap.enabled'), dataIndex: 'mutualChapEnabled', key: 'mutualChapEnabled', width: 160 },
        { title: this.$t('label.state'), dataIndex: 'state', key: 'state', width: 120 }
      ]
    },
    iscsiVolumeColumns () {
      return [
        { title: this.$t('label.volumename'), dataIndex: 'name', key: 'name', fixed: 'left', width: 200 },
        { title: this.$t('label.volumeid'), dataIndex: 'id', key: 'id', width: 260, code: true },
        { title: this.$t('label.size'), dataIndex: 'size', key: 'size', width: 130 },
        { title: this.$t('label.storage.service.used.capacity'), dataIndex: 'used', key: 'used', width: 140 },
        { title: this.$t('label.diskoffering'), dataIndex: 'diskOffering', key: 'diskOffering', width: 220 },
        { title: this.$t('label.storagepool'), dataIndex: 'storagePool', key: 'storagePool', width: 220 },
        { title: this.$t('label.storage.service.iscsi.target'), dataIndex: 'targetName', key: 'targetName', width: 300, code: true },
        { title: this.$t('label.state'), dataIndex: 'state', key: 'state', width: 120 },
        { title: this.$t('label.actions'), dataIndex: 'actions', key: 'actions', fixed: 'right', width: 170 }
      ]
    },
    iscsiSessionColumns () {
      return [
        { title: this.$t('label.storage.service.peer'), dataIndex: 'peer', key: 'peer', fixed: 'left', width: 220, code: true },
        { title: this.$t('label.state'), dataIndex: 'state', key: 'state', width: 130 },
        { title: this.$t('label.storage.service.connected.at'), dataIndex: 'connectedAt', key: 'connectedAt', width: 190 },
        { title: this.$t('label.storage.service.target.iqn'), dataIndex: 'resourceName', key: 'resourceName', width: 300, code: true },
        { title: this.$t('label.storage.service.local'), dataIndex: 'local', key: 'local', width: 220, code: true },
        { title: this.$t('label.actions'), dataIndex: 'actions', key: 'actions', fixed: 'right', width: 170 }
      ]
    },
    nvmeSubsystemColumns () {
      return [
        { title: this.$t('label.storage.service.subsystem.nqn'), dataIndex: 'targetName', key: 'targetName', fixed: 'left', width: 340, code: true },
        { title: this.$t('label.storage.service.namespace'), dataIndex: 'namespace', key: 'namespace', width: 120, code: true },
        { title: this.$t('label.storage.service.endpoint'), dataIndex: 'endpoint', key: 'endpoint', width: 180, code: true },
        { title: this.$t('label.storage.service.engine'), dataIndex: 'engine', key: 'engine', width: 160 },
        { title: this.$t('label.storage.service.backing.volume'), dataIndex: 'volumeName', key: 'volumeName', width: 220 },
        { title: this.$t('label.storage.service.access.rules'), dataIndex: 'aclSummary', key: 'aclSummary', width: 260 },
        { title: this.$t('label.state'), dataIndex: 'state', key: 'state', width: 120 }
      ]
    },
    nvmeAclColumns () {
      return [
        { title: this.$t('label.storage.service.subsystem.nqn'), dataIndex: 'targetName', key: 'targetName', fixed: 'left', width: 340, code: true },
        { title: this.$t('label.storage.service.allowed.host.nqn'), dataIndex: 'principal', key: 'principal', width: 320, code: true },
        { title: this.$t('label.storage.service.nvme.auth.mode'), dataIndex: 'authMode', key: 'authMode', width: 170 },
        { title: this.$t('label.state'), dataIndex: 'state', key: 'state', width: 120 }
      ]
    },
    nvmeSessionColumns () {
      return [
        { title: this.$t('label.storage.service.peer'), dataIndex: 'peer', key: 'peer', fixed: 'left', width: 220, code: true },
        { title: this.$t('label.state'), dataIndex: 'state', key: 'state', width: 130 },
        { title: this.$t('label.storage.service.connected.at'), dataIndex: 'connectedAt', key: 'connectedAt', width: 190 },
        { title: this.$t('label.storage.service.subsystem.nqn'), dataIndex: 'resourceName', key: 'resourceName', width: 320, code: true },
        { title: this.$t('label.storage.service.local'), dataIndex: 'local', key: 'local', width: 220, code: true },
        { title: this.$t('label.actions'), dataIndex: 'actions', key: 'actions', fixed: 'right', width: 170 }
      ]
    },
    iscsiTargetRows () {
      return this.storageService.iscsiTargets.map((target, index) => {
        const config = this.parseStorageConfig(target.config)
        const acls = this.blockAclsForTarget(target, this.storageService.iscsiAcls)
        const volume = this.volumeForTarget(target)
        const runtime = this.runtimeBlockTarget(target, 'iscsiTargets')
        const runtimeConfig = this.parseStorageConfig(runtime.config)
        const lunSizeBytes = this.firstDefined(target.lunsizebytes, target.lunSizeBytes, config.lunSizeBytes, runtime.lunSizeBytes, runtimeConfig.lunSizeBytes)
        const effectiveSizeBytes = this.firstDefined(target.effectivesizebytes, target.effectiveSizeBytes, runtime.effectiveSizeBytes, runtime.actualSizeBytes, lunSizeBytes, target.volumesizebytes, target.volumeSizeBytes, volume.size, this.volume.size, this.resource.size)
        return {
          key: target.id || `iscsi-target-${index}`,
          id: target.id,
          targetName: target.targetname || target.targetName || target.name || '-',
          lun: target.lunornamespace || target.lunOrNamespace || target.lun || '0',
          endpoint: `${target.listenip || this.serviceEndpoint || '-'}:${target.port || 3260}`,
          volumeName: volume.name || volume.displayname || target.volumename || target.volumeName || target.volumeid || '-',
          lunSize: this.formatCapacityValue(lunSizeBytes),
          effectiveSize: this.formatCapacityValue(effectiveSizeBytes),
          backingPath: target.backingpath || target.backingPath || runtime.backingPath || runtime.backingpath || config.backingPath || '-',
          aclSummary: this.aclSummary(acls),
          state: target.state || target.status || '-',
          raw: target
        }
      })
    },
    iscsiAclRows () {
      return this.storageService.iscsiAcls.map((acl, index) => {
        const target = this.blockTargetForAcl(acl, this.storageService.iscsiTargets)
        const config = this.parseStorageConfig(acl.config)
        return {
          key: acl.id || `iscsi-acl-${index}`,
          targetName: target?.targetname || target?.targetName || acl.targetname || acl.resourcename || '-',
          principal: acl.principal || acl.initiatoriqn || '-',
          permission: this.permissionLabel(acl.permission || acl.access || '-'),
          chapEnabled: this.booleanLabel(acl.chapenabled ?? acl.chapEnabled ?? config.chapEnabled),
          chapUsername: acl.chapusername || acl.chapUsername || config.chapUsername || '-',
          mutualChapEnabled: this.booleanLabel(acl.mutualchapenabled ?? acl.mutualChapEnabled ?? config.mutualChapEnabled),
          state: acl.state || acl.status || '-',
          raw: acl
        }
      })
    },
    iscsiVolumeRows () {
      const rows = []
      const seen = new Set()
      this.storageService.iscsiTargets.forEach((target, index) => {
        const volume = this.volumeForTarget(target)
        const id = volume.id || target.volumeid || target.volumeId || this.volume.id || `iscsi-target-${index}`
        if (seen.has(id)) return
        seen.add(id)
        rows.push({
          key: id,
          id,
          targetId: target.id,
          name: volume.name || volume.displayname || target.volumename || target.volumeName || this.volume.name || this.volume.displayname || '-',
          size: this.formatCapacityValue(target.volumesizebytes || target.volumeSizeBytes || target.volumesize || target.volumeSize || volume.size || this.volume.size || this.resource.size),
          used: this.formatCapacityValue(target.usedbytes || target.usedBytes || volume.usedfsbytes || volume.usedphysicalsize || volume.physicalsize || target.physicalsize),
          diskOffering: target.diskofferingname || target.diskOfferingName || volume.diskofferingname || this.volume.diskofferingname || this.resource.diskofferingname || '-',
          storagePool: target.storage || target.storagepool || target.storagePoolName || volume.storage || this.volume.storage || this.resource.storage || '-',
          targetName: target.targetname || target.targetName || '-',
          state: target.volumestate || target.volumeState || volume.state || this.volume.state || '-',
          raw: target
        })
      })
      return rows
    },
    iscsiSessionRows () {
      return this.protocolSessions('ISCSI').map((session, index) => ({
        key: session.sessionId || session.id || `${session.peer || 'session'}-${index}`,
        protocol: session.protocol || 'ISCSI',
        peer: session.peer || session.client || session.clientIp || '-',
        state: session.state || session.status || '-',
        connectedAt: session.connectedAt || session.since || session.age || '-',
        resourceName: session.resourceName || session.targetName || session.target || '-',
        local: session.local || session.endpoint || '-',
        sessionId: session.sessionId || session.id || '',
        raw: session
      }))
    },
    nvmeSubsystemRows () {
      return this.storageService.nvmeSubsystems.map((target, index) => {
        const config = this.parseStorageConfig(target.config)
        const acls = this.blockAclsForTarget(target, this.storageService.nvmeHostAcls)
        const volume = this.volumeForTarget(target)
        return {
          key: target.id || `nvme-subsystem-${index}`,
          id: target.id,
          targetName: target.targetname || target.subsystemnqn || target.targetName || '-',
          namespace: target.lunornamespace || target.namespaceid || target.namespaceId || '-',
          endpoint: `${target.listenip || this.serviceEndpoint || '-'}:${target.port || 4420}`,
          engine: target.engine || config.engine || '-',
          volumeName: volume.name || volume.displayname || target.volumename || target.volumeName || target.volumeid || '-',
          aclSummary: this.aclSummary(acls),
          state: target.state || target.status || '-',
          raw: target
        }
      })
    },
    nvmeAclRows () {
      return this.storageService.nvmeHostAcls.map((acl, index) => {
        const target = this.blockTargetForAcl(acl, this.storageService.nvmeSubsystems)
        const config = this.parseStorageConfig(acl.config || acl.configjson || acl.configJson)
        const authMode = this.nvmeAuthModeFromAcl(acl, config)
        return {
          key: acl.id || `nvme-acl-${index}`,
          targetName: target?.targetname || target?.subsystemnqn || target?.targetName || acl.targetname || acl.resourcename || '-',
          principal: acl.principal || acl.hostnqn || '-',
          authMode: authMode.label,
          authRequired: authMode.required,
          state: acl.state || acl.status || '-',
          raw: acl
        }
      })
    },
    nvmeSessionRows () {
      return this.protocolSessions('NVME_OF').map((session, index) => ({
        key: session.sessionId || session.id || `${session.peer || 'session'}-${index}`,
        protocol: session.protocol || 'NVME_OF',
        peer: session.peer || session.client || session.clientIp || '-',
        state: session.state || session.status || '-',
        connectedAt: session.connectedAt || session.firstSeen || session.since || session.age || '-',
        resourceName: session.resourceName || session.subsystemNqn || session.subsystemNQN || session.targetName || session.subsystem || '-',
        local: session.local || session.endpoint || '-',
        sessionId: session.sessionId || session.id || '',
        raw: session
      }))
    },
    actionModalTitle () {
      const titles = {
        enableProtocol: 'label.storage.service.enable.protocol',
        nfsExport: 'label.storage.service.create.nfs.export',
        editNfsExport: 'label.storage.service.update.nfs.export',
        nfsAcl: 'label.storage.service.create.nfs.acl',
        editNfsAcl: 'label.storage.service.update.nfs.acl',
        deleteConfirm: 'label.storage.service.delete.confirm',
        smbShare: 'label.storage.service.create.smb.share',
        smbAcl: 'label.storage.service.create.smb.acl',
        adJoin: 'label.storage.service.join.ad.domain',
        iscsiTarget: 'label.storage.service.create.iscsi.target',
        iscsiAcl: 'label.storage.service.create.iscsi.acl',
        nvmePrepare: 'label.storage.service.prepare.nvmeof',
        nvmeSubsystem: 'label.storage.service.create.nvme.subsystem',
        nvmeHostAcl: 'label.storage.service.create.nvme.host.acl',
        attachVolume: 'label.storage.service.attach.existing.volume',
        resizeShare: 'label.storage.service.resize.file.share',
        resizeVolume: 'label.storage.service.resize.volume',
        detachBackingVolume: 'label.storage.service.detach.backing.volume',
        disconnectSession: 'label.storage.service.disconnect.session',
        deleteEndpoint: 'label.storage.service.delete.endpoint'
      }
      return this.$t(titles[this.actionModal.type] || 'label.action')
    },
    actionModalOkText () {
      return ['deleteConfirm', 'deleteEndpoint', 'detachBackingVolume'].includes(this.actionModal.type) ? this.$t('label.ok') : this.$t('label.ok')
    },
    actionModalOkButtonProps () {
      const deleteEndpointBlocked = this.actionModal.type === 'deleteEndpoint' && !this.deleteEndpointConfirmationMatched
      return {
        danger: ['deleteConfirm', 'deleteEndpoint', 'detachBackingVolume'].includes(this.actionModal.type),
        disabled: (this.actionModal.type === 'deleteConfirm' && !this.deleteConfirmationMatched) ||
          deleteEndpointBlocked ||
          (this.actionModal.type === 'detachBackingVolume' && !this.forms.detachBackingVolume.confirmation)
      }
    },
    deleteConfirmationMatched () {
      if (this.actionModal.type !== 'deleteConfirm') {
        return true
      }
      return String(this.forms.deleteConfirm.confirmation || '') === String(this.actionModal.context?.name || '')
    },
    deleteEndpointConfirmationMatched () {
      if (this.actionModal.type !== 'deleteEndpoint') {
        return true
      }
      return !!this.forms.deleteEndpoint.listenip &&
        String(this.forms.deleteEndpoint.confirmation || '') === String(this.forms.deleteEndpoint.listenip || '')
    },
    deleteTargetTypeLabel () {
      const labels = {
        protocol: 'label.protocol',
        nfsExport: 'label.storage.service.nfs.export',
        nfsAcl: 'label.storage.service.nfs.acl'
      }
      return this.$t(labels[this.forms.deleteConfirm.resourceType] || 'label.resource')
    }
  },
  created () {
    const self = this
    this.dataResource = this.resource
    this.initStorageDefaults()
    this.fetchData()
    window.addEventListener('popstate', function () {
      self.setCurrentTab()
    })
  },
  watch: {
    resource: {
      deep: true,
      handler (newData, oldData) {
        if (newData !== oldData) {
          this.dataResource = newData
          this.initStorageDefaults()
          this.fetchData()
        }
      }
    },
    '$route.fullPath': function () {
      this.setCurrentTab()
    },
    'forms.enableProtocol.protocol': function (protocol) {
      this.forms.enableProtocol.port = this.defaultProtocolPort(protocol)
    },
    'forms.nfsExport.volumemode': function (mode) {
      if (mode === 'CURRENT' && !this.forms.nfsExport.volumeid) {
        this.forms.nfsExport.volumeid = this.defaultCurrentBackingVolumeId()
      }
      this.syncNfsExportPathToCurrentVolume()
    },
    'forms.nfsExport.volumeid': function () {
      this.syncNfsExportPathToCurrentVolume()
    },
    'forms.nfsExport.name': function (name, previousName) {
      if (!this.actionModal.visible || !['nfsExport', 'editNfsExport'].includes(this.actionModal.type)) {
        return
      }
      const previousPath = previousName ? this.defaultNfsExportPath(previousName) : ''
      if (!this.forms.nfsExport.path || this.forms.nfsExport.path === previousPath) {
        this.forms.nfsExport.path = this.defaultNfsExportPath(name)
      }
    },
    nvmeDhChapSupported: function (supported) {
      if (!supported) {
        this.forms.nvmeHostAcl.dhchapenabled = false
        this.forms.nvmeHostAcl.dhchapctrlenabled = false
        this.forms.nvmeHostAcl.dhchapkey = ''
        this.forms.nvmeHostAcl.dhchapctrlkey = ''
      }
    },
    'forms.nvmeHostAcl.dhchapenabled': function (enabled) {
      if (!enabled) {
        this.forms.nvmeHostAcl.dhchapctrlenabled = false
        this.forms.nvmeHostAcl.dhchapkey = ''
        this.forms.nvmeHostAcl.dhchapctrlkey = ''
      }
    }
  },
  mounted () {
    this.setCurrentTab()
  },
  unmounted () {
    this.$emit('wide-layout-change', false)
  },
  methods: {
    setCurrentTab () {
      const tab = this.$route.query.tab ? this.$route.query.tab : 'details'
      this.currentTab = tab
      this.protocolWideLayout = this.isStorageProtocolTab(tab) && this.$route.query.wide === 'true'
      this.emitWideLayout()
    },
    handleChangeTab (e) {
      this.currentTab = e
      if (['details', 'nfs', 'smb', 'iscsi', 'nvmeof'].includes(e)) {
        this.fetchStorageServiceData()
      }
      if (!this.isStorageProtocolTab(e)) {
        this.protocolWideLayout = false
      }
      this.updateRouteQuery(e)
      this.emitWideLayout()
    },
    isStorageProtocolTab (tab) {
      return ['nfs', 'smb', 'iscsi', 'nvmeof'].includes(tab)
    },
    toggleProtocolWideLayout () {
      if (!this.isStorageProtocolTab(this.currentTab)) {
        return
      }
      this.protocolWideLayout = !this.protocolWideLayout
      this.updateRouteQuery(this.currentTab)
      this.emitWideLayout()
    },
    emitWideLayout () {
      this.$emit('wide-layout-change', this.isStorageProtocolTab(this.currentTab) && this.protocolWideLayout)
    },
    updateRouteQuery (tab) {
      const query = Object.assign({}, this.$route.query)
      query.tab = tab
      if (this.isStorageProtocolTab(tab) && this.protocolWideLayout) {
        query.wide = 'true'
      } else {
        delete query.wide
      }
      const queryString = Object.keys(query).map(key => {
        return (
          encodeURIComponent(key) + '=' + encodeURIComponent(query[key])
        )
      }).join('&')
      history.pushState({}, null, '#' + this.$route.path + (queryString ? '?' + queryString : ''))
    },
    preserveCurrentProtocolRoute () {
      if (!this.isStorageProtocolTab(this.currentTab)) {
        return
      }
      const query = Object.assign({}, this.$route.query, { tab: this.currentTab })
      if (this.protocolWideLayout) {
        query.wide = 'true'
      } else {
        delete query.wide
      }
      this.$router.replace({ path: this.$route.path, query }).catch(() => {})
    },
    fetchInstances () {
      if (!this.resource.virtualmachineid) {
        return
      }
      this.instanceLoading = true
      var params = {
        id: this.resource.virtualmachineid,
        listall: true
      }
      if (this.$store.getters.listAllProjects) {
        params.projectid = '-1'
      }
      getAPI('listVirtualMachines', params).then(json => {
        this.virtualmachines = json.listvirtualmachinesresponse.virtualmachine || []
        this.vm = this.virtualmachines[0] || {}
      })
      this.instanceLoading = false
    },
    fetchVolumes () {
      if (!this.resource.volumeid) {
        return
      }
      this.volumeLoading = true
      var params = {
        id: this.resource.volumeid,
        listsystemvms: 'true',
        listall: true
      }
      getAPI('listVolumes', params).then(json => {
        this.volumes = json.listvolumesresponse.volume || []
        this.volume = this.volumes[0] || {}
      })
      this.volumeLoading = false
    },
    fetchData () {
      this.fetchInstances()
      this.fetchVolumes()
      this.fetchAvailableVolumes()
      this.fetchDiskOfferings()
      this.fetchStoragePools()
      if (this.hasStorageServiceApi) {
        this.fetchStorageServiceData()
      }
    },
    fetchDiskOfferings () {
      if (!('listDiskOfferings' in this.$store.getters.apis)) {
        this.diskOfferings = []
        return
      }
      this.diskOfferingLoading = true
      const params = {
        listall: true
      }
      if (this.resource.zoneid) {
        params.zoneid = this.resource.zoneid
      }
      getAPI('listDiskOfferings', params).then(json => {
        this.diskOfferings = json.listdiskofferingsresponse.diskoffering || []
        this.reconcileNfsNewVolumeStorage()
      }).finally(() => {
        this.diskOfferingLoading = false
      })
    },
    fetchStoragePools () {
      if (!('listStoragePools' in this.$store.getters.apis) || !this.resource.zoneid) {
        this.storagePools = []
        return
      }
      this.storagePoolLoading = true
      getAPI('listStoragePools', {
        zoneid: this.resource.zoneid,
        listall: true,
        showicon: true
      }).then(json => {
        const pools = json.liststoragepoolsresponse.storagepool || []
        this.storagePools = pools.filter(pool => pool.state === 'Up')
        if (!this.forms.nfsExport.storageid) {
          this.forms.nfsExport.storageid = this.defaultNewVolumeStorageId()
        }
        this.reconcileNfsNewVolumeStorage()
      }).finally(() => {
        this.storagePoolLoading = false
      })
    },
    fetchAvailableVolumes () {
      if (!('listVolumes' in this.$store.getters.apis) || !this.resource.zoneid) {
        this.availableVolumes = []
        return
      }
      this.volumeLoading = true
      getAPI('listVolumes', {
        zoneid: this.resource.zoneid,
        listall: true,
        type: 'DATADISK'
      }).then(json => {
        const volumes = json.listvolumesresponse.volume || []
        this.availableVolumes = volumes.filter(volume => {
          return volume.type === 'DATADISK' &&
            volume.state === 'Ready' &&
            !volume.virtualmachineid &&
            !volume.vmname
        })
      }).finally(() => {
        this.volumeLoading = false
      })
    },
    initStorageDefaults () {
      const firstNic = this.serviceListenIps[0] || {}
      this.forms.enableProtocol.listenip = firstNic.ipaddress || this.resource.ipaddress || ''
      this.forms.enableProtocol.port = this.defaultProtocolPort(this.forms.enableProtocol.protocol)
    },
    defaultProtocolPort (protocol) {
      const ports = {
        NFS: 2049,
        SMB: 445,
        ISCSI: 3260,
        NVME_OF: 4420
      }
      return ports[protocol] || null
    },
    async fetchStorageServiceData () {
      if (!this.hasStorageServiceApi || this.storageService.loading) {
        return
      }
      const initialLoad = !this.storageService.loaded
      this.storageService.loading = true
      this.storageService.initialLoading = initialLoad
      this.storageService.refreshing = !initialLoad
      try {
        const params = {
          zoneid: this.resource.zoneid,
          listall: true
        }
        const instances = await this.listApi('listStorageServiceInstances', params, 'storageserviceinstance')
        const instance = instances.find(item => item.virtualmachineid === this.resource.virtualmachineid) || null
        this.storageService.instance = instance
        if (!instance) {
          this.clearStorageServiceRuntime()
          return
        }
        await Promise.all([
          this.fetchRuntime('listStorageServiceHealth', 'health'),
          this.fetchRuntime('listStorageServiceInventory', 'inventory'),
          this.fetchRuntime('listStorageServiceSessions', 'sessions'),
          this.fetchCollection('listStorageServiceDomainStatus', 'domains', 'storageidentitydomain'),
          this.fetchCollection('listStorageNfsExports', 'nfsExports', 'storagenfsexport'),
          this.fetchCollection('listStorageSmbShares', 'smbShares', 'storagesmbshare'),
          this.fetchCollection('listStorageIscsiTargets', 'iscsiTargets', 'storageiscsitarget'),
          this.fetchCollection('listStorageNvmeOfSubsystems', 'nvmeSubsystems', 'storagenvmeofsubsystem')
        ])
        await this.fetchAccessRules()
        await this.fetchBackingVolumes()
        this.storageService.loaded = true
      } catch (error) {
        this.$notifyError(error)
      } finally {
        this.storageService.loading = false
        this.storageService.initialLoading = false
        this.storageService.refreshing = false
      }
    },
    clearStorageServiceRuntime () {
      this.storageService.health = []
      this.storageService.inventory = []
      this.storageService.sessions = []
      this.storageService.domains = []
      this.storageService.nfsExports = []
      this.storageService.smbShares = []
      this.storageService.iscsiTargets = []
      this.storageService.nvmeSubsystems = []
      this.storageService.nfsAcls = []
      this.storageService.smbAcls = []
      this.storageService.iscsiAcls = []
      this.storageService.nvmeHostAcls = []
      this.storageService.backingVolumes = []
    },
    async fetchRuntime (api, key) {
      this.storageService[key] = await this.listApi(api, { instanceid: this.storageService.instance.id }, 'storageserviceruntime')
    },
    async fetchCollection (api, key, objectName) {
      this.storageService[key] = await this.listApi(api, { instanceid: this.storageService.instance.id }, objectName)
    },
    async fetchAccessRules () {
      const nfsAcls = await this.listApi('listStorageNfsAcls', { instanceid: this.storageService.instance.id }, 'storageaccessrule')
      const smbAcls = await Promise.all(this.storageService.smbShares.map(share => this.listApi('listStorageSmbAcls', { shareid: share.id }, 'storageaccessrule')))
      const iscsiAcls = await Promise.all(this.storageService.iscsiTargets.map(target => this.listApi('listStorageIscsiAcls', { targetid: target.id }, 'storageaccessrule')))
      const nvmeHostAcls = await Promise.all(this.storageService.nvmeSubsystems.map(target => this.listApi('listStorageNvmeOfHostAcls', { subsystemid: target.id }, 'storageaccessrule')))
      this.storageService.nfsAcls = nfsAcls.filter(acl => this.nfsShareForAcl(acl))
      this.storageService.smbAcls = smbAcls.flat()
      this.storageService.iscsiAcls = iscsiAcls.flat()
      this.storageService.nvmeHostAcls = nvmeHostAcls.flat()
    },
    async fetchBackingVolumes () {
      if (!('listVolumes' in this.$store.getters.apis)) {
        this.storageService.backingVolumes = []
        return
      }
      const ids = new Set()
      this.storageService.nfsExports.forEach(share => {
        if (share.volumeid || share.volumeId) {
          ids.add(share.volumeid || share.volumeId)
        }
      })
      this.storageService.smbShares.forEach(share => {
        if (share.volumeid || share.volumeId) {
          ids.add(share.volumeid || share.volumeId)
        }
      })
      this.storageService.iscsiTargets.forEach(target => {
        if (target.volumeid || target.volumeId) {
          ids.add(target.volumeid || target.volumeId)
        }
      })
      this.storageService.nvmeSubsystems.forEach(target => {
        if (target.volumeid || target.volumeId) {
          ids.add(target.volumeid || target.volumeId)
        }
      })
      if (this.resource.volumeid) {
        ids.add(this.resource.volumeid)
      }
      if (this.volume.id) {
        ids.add(this.volume.id)
      }
      const volumeRequests = [...ids].map(id => this.listApi('listVolumes', {
        id,
        listall: true,
        listsystemvms: true
      }, 'volume'))
      const vmId = this.resource.virtualmachineid || this.vm.id || this.storageService.instance?.virtualmachineid
      if (vmId) {
        volumeRequests.push(this.listApi('listVolumes', {
          virtualmachineid: vmId,
          listall: true,
          listsystemvms: true
        }, 'volume'))
      }
      const volumeLists = await Promise.all(volumeRequests)
      const seen = new Set()
      this.storageService.backingVolumes = volumeLists.flat().filter(volume => {
        if (!volume?.id || seen.has(String(volume.id))) {
          return false
        }
        seen.add(String(volume.id))
        return true
      })
    },
    async listApi (api, params, objectName) {
      const apiMap = this.$store.getters.apis || {}
      const storageReadApi = api.startsWith('listStorage') || api === 'listVolumes'
      if (!(api in apiMap) && !storageReadApi) {
        return []
      }
      const json = await getAPI(api, params)
      const response = json[api.toLowerCase() + 'response'] || {}
      const items = response[objectName] || Object.values(response).find(value => Array.isArray(value)) || []
      return Array.isArray(items) ? items : [items]
    },
    delay (milliseconds) {
      return new Promise(resolve => window.setTimeout(resolve, milliseconds))
    },
    async waitStorageServiceJob (jobId, apiName, maxAttempts = 120) {
      for (let attempt = 0; attempt < maxAttempts; attempt++) {
        const json = await getAPI('queryAsyncJobResult', { jobId })
        const result = json.queryasyncjobresultresponse || {}
        if (Number(result.jobstatus) === 1) {
          return result
        }
        if (Number(result.jobstatus) === 2) {
          const errorText = result.jobresult?.errortext || result.jobresult?.errorText || result.jobresult?.message || this.$t('error.fetching.async.job.result')
          throw new Error(errorText)
        }
        await this.delay(2000)
      }
      throw new Error(`${apiName || 'Async job'} timed out`)
    },
    async waitVolumeAttachable (volumeId, maxAttempts = 60) {
      const attachableStates = ['Allocated', 'Ready', 'Uploaded']
      for (let attempt = 0; attempt < maxAttempts; attempt++) {
        const volumes = await this.listApi('listVolumes', { id: volumeId, listall: true }, 'volume')
        const volume = volumes[0]
        if (volume && attachableStates.includes(volume.state)) {
          return volume
        }
        if (volume && volume.state && !['Creating', 'Copying', 'UploadOp', 'UploadInProgress', 'Migrating', 'Resizing', 'Attaching', 'Snapshotting'].includes(volume.state)) {
          throw new Error(`Volume ${volumeId} is not attachable. Current state: ${volume.state}`)
        }
        await this.delay(2000)
      }
      throw new Error(`Volume ${volumeId} did not reach an attachable state`)
    },
    validateListenIpSelection () {
      const listenIp = this.forms.enableProtocol.listenip
      if (!listenIp) {
        this.$message.error(this.$t('message.storage.service.listen.ip.required'))
        return false
      }
      if (!this.isValidIpv4(listenIp)) {
        this.$message.error(this.$t('message.storage.service.listen.ip.invalid'))
        return false
      }
      if (this.forms.enableProtocol.listenipmode !== 'NEW') {
        return true
      }
      if (this.serviceEndpoints.includes(String(listenIp).trim())) {
        this.$message.error(this.$t('message.storage.service.listen.ip.already.configured'))
        return false
      }
      const runtimeComparable = this.runtimeNetworkAddresses.filter(address => {
        return address.ipaddress && this.isValidIpv4(address.ipaddress) && address.cidr
      })
      if (runtimeComparable.length) {
        const matchedRuntime = runtimeComparable.find(address => this.isSameCidr(listenIp, address.ipaddress, address.cidr))
        if (!matchedRuntime) {
          this.$message.error(this.$t('message.storage.service.listen.ip.cidr.mismatch'))
          return false
        }
        return true
      }
      const matchedNic = this.serviceNics.find(nic => {
        return nic.ipaddress && this.isValidIpv4(nic.ipaddress) && this.isSameCidr(listenIp, nic.ipaddress, nic.netmask || nic.cidr)
      })
      const hasComparableNic = this.serviceNics.some(nic => nic.ipaddress && (nic.netmask || nic.cidr))
      if (hasComparableNic && !matchedNic) {
        this.$message.error(this.$t('message.storage.service.listen.ip.cidr.mismatch'))
        return false
      }
      return true
    },
    isValidIpv4 (value) {
      const parts = String(value || '').split('.')
      return parts.length === 4 && parts.every(part => /^\d+$/.test(part) && Number(part) >= 0 && Number(part) <= 255)
    },
    ipv4ToNumber (value) {
      return String(value).split('.').reduce((acc, part) => (acc << 8) + Number(part), 0) >>> 0
    },
    netmaskToPrefix (value) {
      if (!value) return null
      if (String(value).includes('/')) {
        const prefix = Number(String(value).split('/').pop())
        return Number.isInteger(prefix) ? prefix : null
      }
      if (!this.isValidIpv4(value)) return null
      const mask = this.ipv4ToNumber(value)
      let prefix = 0
      for (let i = 31; i >= 0; i--) {
        if ((mask & (1 << i)) === 0) break
        prefix++
      }
      return prefix
    },
    isSameCidr (ip, baseIp, netmaskOrCidr) {
      const prefix = this.netmaskToPrefix(netmaskOrCidr)
      if (prefix === null || prefix < 0 || prefix > 32) return false
      const mask = prefix === 0 ? 0 : (0xffffffff << (32 - prefix)) >>> 0
      return (this.ipv4ToNumber(ip) & mask) === (this.ipv4ToNumber(baseIp) & mask)
    },
    async runStorageAction (key, api, params, title) {
      if (!this.storageService.instance || this.actionLoading[key]) {
        return
      }
      const actionTab = this.currentTab
      const actionWideLayout = this.protocolWideLayout
      this.actionLoading[key] = true
      try {
        const response = await postAPI(api, this.cleanParams(params))
        const result = response[api.toLowerCase() + 'response'] || {}
        if (result.jobid) {
          this.$pollJob({
            jobId: result.jobid,
            title,
            description: this.storageService.instance.name || this.resource.name,
            successMessage: this.$t('label.success'),
            errorMessage: this.$t('label.error'),
            loadingMessage: this.$t('label.loading') + '...',
            catchMessage: this.$t('error.fetching.async.job.result'),
            action: { isFetchData: false },
            originalPage: this.$route.path,
            successMethod: () => this.refreshAfterStorageAction(key, actionTab, actionWideLayout)
          })
        } else {
          await this.refreshAfterStorageAction(key, actionTab, actionWideLayout)
        }
      } catch (error) {
        this.$notifyError(error)
      } finally {
        this.actionLoading[key] = false
      }
    },
    async refreshAfterStorageAction (key, actionTab = this.currentTab, actionWideLayout = this.protocolWideLayout) {
      if (!this.storageService.instance) {
        return this.fetchStorageServiceData()
      }
      this.currentTab = actionTab || this.currentTab
      this.protocolWideLayout = !!actionWideLayout
      this.preserveCurrentProtocolRoute()
      this.emitWideLayout()
      this.storageService.refreshing = true
      try {
        await Promise.all([
          this.fetchRuntime('listStorageServiceHealth', 'health'),
          this.fetchRuntime('listStorageServiceInventory', 'inventory'),
          this.fetchRuntime('listStorageServiceSessions', 'sessions')
        ])
        if (['nfsExport', 'editNfsExport', 'nfsAcl', 'editNfsAcl', 'resizeShare', 'resizeVolume', 'detachBackingVolume', 'enableProtocol', 'deleteEndpoint'].includes(key)) {
          await this.fetchCollection('listStorageNfsExports', 'nfsExports', 'storagenfsexport')
          await this.fetchAccessRules()
          await this.fetchBackingVolumes()
        } else if (['smbShare', 'smbAcl', 'adJoin'].includes(key)) {
          await Promise.all([
            this.fetchCollection('listStorageServiceDomainStatus', 'domains', 'storageidentitydomain'),
            this.fetchCollection('listStorageSmbShares', 'smbShares', 'storagesmbshare')
          ])
          await this.fetchAccessRules()
          await this.fetchBackingVolumes()
        } else if (['iscsiTarget', 'iscsiAcl'].includes(key)) {
          await this.fetchCollection('listStorageIscsiTargets', 'iscsiTargets', 'storageiscsitarget')
          await this.fetchAccessRules()
          await this.fetchBackingVolumes()
        } else if (['nvmePrepare', 'nvmeSubsystem', 'nvmeHostAcl'].includes(key)) {
          await this.fetchCollection('listStorageNvmeOfSubsystems', 'nvmeSubsystems', 'storagenvmeofsubsystem')
          await this.fetchAccessRules()
          await this.fetchBackingVolumes()
        }
      } finally {
        this.storageService.loaded = true
        this.storageService.refreshing = false
        this.currentTab = actionTab || this.currentTab
        this.protocolWideLayout = !!actionWideLayout
        this.preserveCurrentProtocolRoute()
        this.emitWideLayout()
      }
    },
    cleanParams (params) {
      const clean = {}
      Object.entries(params).forEach(([key, value]) => {
        if (value !== undefined && value !== null && value !== '') {
          clean[key] = value
        }
      })
      return clean
    },
    storageCellValue (record, column) {
      const key = column.dataIndex || column.key
      const value = record ? record[key] : null
      if (value === undefined || value === null || value === '') {
        return '-'
      }
      return value
    },
    storageTableLocale (emptyKey) {
      return {
        emptyText: h(Empty, {
          image: Empty.PRESENTED_IMAGE_SIMPLE,
          description: this.$t(emptyKey)
        })
      }
    },
    formatCapacityValue (value) {
      if (value === undefined || value === null || value === '') {
        return '-'
      }
      return this.formatBytes(value)
    },
    booleanLabel (value) {
      if (value === undefined || value === null || value === '') {
        return '-'
      }
      if (value === true || value === 'true' || value === 'TRUE' || value === 1 || value === '1') {
        return this.$t('label.yes')
      }
      return this.$t('label.no')
    },
    boolValue (value) {
      return value === true || value === 'true' || value === 'TRUE' || value === 1 || value === '1'
    },
    nvmeAuthModeLabel (dhChapEnabled, dhChapCtrlEnabled) {
      if (dhChapEnabled && dhChapCtrlEnabled) {
        return this.$t('label.storage.service.nvme.auth.mutual')
      }
      if (dhChapEnabled) {
        return this.$t('label.storage.service.nvme.auth.host')
      }
      return this.$t('label.storage.service.nvme.auth.none')
    },
    nvmeAuthModeFromAcl (acl, config = {}) {
      const dhChapEnabled = this.boolValue(this.firstDefined(
        acl.dhchapenabled,
        acl.dhChapEnabled,
        acl.dh_chap_enabled,
        config.dhChapEnabled,
        config.dhchapEnabled,
        config.dh_chap_enabled,
        config.hostAuthEnabled,
        config.authEnabled
      ))
      const dhChapCtrlEnabled = this.boolValue(this.firstDefined(
        acl.dhchapctrlenabled,
        acl.dhChapCtrlEnabled,
        acl.dh_chap_ctrl_enabled,
        config.dhChapCtrlEnabled,
        config.dhchapCtrlEnabled,
        config.dh_chap_ctrl_enabled,
        config.controllerAuthEnabled,
        config.ctrlAuthEnabled
      ))
      return {
        label: this.nvmeAuthModeLabel(dhChapEnabled, dhChapCtrlEnabled),
        required: dhChapEnabled || dhChapCtrlEnabled
      }
    },
    permissionLabel (value) {
      const permission = String(value || '').toUpperCase()
      if (permission === 'READ_ONLY' || permission === 'READONLY' || permission === 'RO') {
        return this.$t('label.storage.service.permission.readonly')
      }
      if (permission === 'READ_WRITE' || permission === 'READWRITE' || permission === 'RW') {
        return this.$t('label.storage.service.permission.readwrite')
      }
      if (permission === 'ADMIN') {
        return this.$t('label.admin')
      }
      return value || '-'
    },
    principalTypeLabel (value) {
      const type = String(value || '').toUpperCase()
      const labels = {
        LOCAL_USER: 'label.storage.service.local.user',
        LOCAL_GROUP: 'label.storage.service.local.group',
        AD_USER: 'label.storage.service.ad.user',
        AD_GROUP: 'label.storage.service.ad.group',
        CIDR: 'label.storage.service.cidr'
      }
      return labels[type] ? this.$t(labels[type]) : (value || '-')
    },
    parseStorageConfig (config) {
      if (!config) {
        return {}
      }
      if (typeof config === 'object') {
        return config
      }
      try {
        return JSON.parse(config)
      } catch (e) {
        return {}
      }
    },
    firstDefined (...values) {
      return values.find(value => value !== undefined && value !== null && value !== '')
    },
    runtimeBlockTarget (target, inventoryKey) {
      const inventory = this.parsedInventory[inventoryKey] || {}
      const targets = Array.isArray(inventory.targets) ? inventory.targets : []
      const ids = [
        target.id,
        target.uuid,
        target.targetname,
        target.targetName,
        target.lunornamespace,
        target.lunOrNamespace
      ].filter(Boolean).map(value => String(value))
      return targets.find(item => {
        const itemIds = [
          item.uuid,
          item.id,
          item.targetName,
          item.targetname,
          item.lunOrNamespace,
          item.lunornamespace
        ].filter(Boolean).map(value => String(value))
        return itemIds.some(id => ids.includes(id))
      }) || {}
    },
    volumeForShare (share) {
      const ids = [share.volumeid, share.volumeId].filter(Boolean).map(value => String(value))
      const exact = this.storageService.backingVolumes.find(volume => ids.includes(String(volume.id)))
      if (exact && this.belongsToCurrentServiceVm(exact)) {
        return exact
      }
      const currentSharedFsVolume = this.storageService.backingVolumes.find(volume => volume.id === this.resource.volumeid || volume.id === this.volume.id)
      return currentSharedFsVolume || exact || this.volume || {}
    },
    nfsExportsForVolume (volume) {
      const ids = [volume?.id, volume?.uuid].filter(Boolean).map(value => String(value))
      if (!ids.length) {
        return []
      }
      return this.storageService.nfsExports.filter(share => {
        const shareIds = [share.volumeid, share.volumeId, share.volumeuuid, share.volumeUuid].filter(Boolean).map(value => String(value))
        return shareIds.some(id => ids.includes(id))
      })
    },
    currentBackingVolumeConfig (volume) {
      const shares = this.nfsExportsForVolume(volume)
      const share = shares.find(item => this.parseStorageConfig(item.config).lastInspection) || shares[0] || null
      return share ? this.parseStorageConfig(share.config) : {}
    },
    currentBackingVolumeMountPath (volume) {
      const config = this.currentBackingVolumeConfig(volume)
      const inspection = config.lastInspection || config.lastinspection || {}
      if (inspection.mountPath || inspection.mountpath) {
        return inspection.mountPath || inspection.mountpath
      }
      const share = this.nfsExportsForVolume(volume)[0]
      if (share?.path || share?.mountpath || share?.backingpath) {
        return share.path || share.mountpath || share.backingpath
      }
      if (volume?.id && (volume.id === this.resource.volumeid || volume.id === this.volume.id)) {
        return '/export'
      }
      return ''
    },
    currentBackingVolumeFilesystem (volume) {
      const config = this.currentBackingVolumeConfig(volume)
      const inspection = config.lastInspection || config.lastinspection || {}
      return inspection.filesystem || inspection.fsType || inspection.fstype || this.nfsExportsForVolume(volume)[0]?.filesystem || volume?.filesystem || ''
    },
    currentBackingVolumeExportSummary (volume) {
      const shares = this.nfsExportsForVolume(volume)
      if (!shares.length) {
        return '-'
      }
      return shares.map(share => this.clientVisibleName(share.name || share.exportname, '-')).join(', ')
    },
    volumeForTarget (target) {
      const ids = [
        target.volumeid,
        target.volumeId,
        target.volumeuuid,
        target.volumeUuid
      ].filter(Boolean).map(value => String(value))
      const exact = this.storageService.backingVolumes.find(volume => {
        return ids.includes(String(volume.id)) || ids.includes(String(volume.uuid))
      })
      if (exact && this.belongsToCurrentServiceVm(exact)) {
        return exact
      }
      const currentSharedFsVolume = this.storageService.backingVolumes.find(volume => volume.id === this.resource.volumeid || volume.id === this.volume.id)
      return currentSharedFsVolume || exact || this.volume || {}
    },
    belongsToCurrentServiceVm (volume) {
      const vmIds = [
        this.resource.virtualmachineid,
        this.vm.id,
        this.storageService.instance?.virtualmachineid
      ].filter(Boolean).map(value => String(value))
      return !volume.virtualmachineid || vmIds.includes(String(volume.virtualmachineid))
    },
    nfsAclsForShare (share) {
      const ids = [share.id, share.uuid, share.resourceid, share.resourceuuid].filter(Boolean).map(value => String(value))
      return this.storageService.nfsAcls.filter(acl => {
        const aclIds = [
          acl.exportid,
          acl.shareid,
          acl.resourceid,
          acl.resourceuuid,
          acl.parentid,
          acl.fileshareid,
          acl.fileShareId
        ].filter(Boolean).map(value => String(value))
        return aclIds.some(id => ids.includes(id))
      })
    },
    nfsShareForAcl (acl) {
      const aclIds = [
        acl.exportid,
        acl.shareid,
        acl.resourceid,
        acl.resourceuuid,
        acl.parentid,
        acl.fileshareid,
        acl.fileShareId
      ].filter(Boolean).map(value => String(value))
      return this.storageService.nfsExports.find(share => {
        const shareIds = [share.id, share.uuid, share.resourceid, share.resourceuuid].filter(Boolean).map(value => String(value))
        return shareIds.some(id => aclIds.includes(id))
      }) || null
    },
    smbAclsForShare (share) {
      const ids = [share.id, share.uuid, share.resourceid, share.resourceuuid].filter(Boolean).map(value => String(value))
      return this.storageService.smbAcls.filter(acl => {
        const aclIds = [
          acl.shareid,
          acl.resourceid,
          acl.resourceuuid,
          acl.parentid,
          acl.fileshareid,
          acl.fileShareId
        ].filter(Boolean).map(value => String(value))
        return aclIds.some(id => ids.includes(id))
      })
    },
    smbShareForAcl (acl) {
      const aclIds = [
        acl.shareid,
        acl.resourceid,
        acl.resourceuuid,
        acl.parentid,
        acl.fileshareid,
        acl.fileShareId
      ].filter(Boolean).map(value => String(value))
      return this.storageService.smbShares.find(share => {
        const shareIds = [share.id, share.uuid, share.resourceid, share.resourceuuid].filter(Boolean).map(value => String(value))
        return shareIds.some(id => aclIds.includes(id))
      }) || null
    },
    blockAclsForTarget (target, rules) {
      const ids = [target.id, target.uuid, target.resourceid, target.resourceuuid].filter(Boolean).map(value => String(value))
      return (rules || []).filter(acl => {
        const aclIds = [
          acl.targetid,
          acl.targetId,
          acl.resourceid,
          acl.resourceuuid,
          acl.parentid,
          acl.blocktargetid,
          acl.blockTargetId
        ].filter(Boolean).map(value => String(value))
        return aclIds.some(id => ids.includes(id))
      })
    },
    blockTargetForAcl (acl, targets) {
      const aclIds = [
        acl.targetid,
        acl.targetId,
        acl.resourceid,
        acl.resourceuuid,
        acl.parentid,
        acl.blocktargetid,
        acl.blockTargetId
      ].filter(Boolean).map(value => String(value))
      return (targets || []).find(target => {
        const targetIds = [target.id, target.uuid, target.resourceid, target.resourceuuid].filter(Boolean).map(value => String(value))
        return targetIds.some(id => aclIds.includes(id))
      }) || null
    },
    isProtocolEnabled (protocol) {
      const normalized = String(protocol || '').toUpperCase()
      const sources = [
        this.parsedHealth.protocols,
        this.parsedHealth.enabledProtocols,
        this.parsedInventory.protocols,
        this.parsedInventory.enabledProtocols
      ].filter(Boolean)
      return sources.some(source => {
        if (Array.isArray(source)) {
          return source.some(item => String(item.protocol || item.name || item).toUpperCase() === normalized && item.enabled !== false && item.state !== 'Disabled')
        }
        if (typeof source === 'object') {
          const value = source[normalized] || source[normalized.toLowerCase()]
          if (value === undefined) return false
          if (typeof value === 'object') return value.enabled !== false && value.state !== 'Disabled'
          return value !== false
        }
        return false
      })
    },
    aclSummary (acls) {
      if (!acls || acls.length === 0) {
        return '-'
      }
      return acls.map(acl => acl.principal || acl.cidr || acl.client || acl.id).filter(Boolean).join(', ')
    },
    capacityMultiplier (unit) {
      return this.capacityUnits.find(item => item.value === unit)?.multiplier || 1
    },
    toCapacityBytes (amount, unit) {
      if (amount === null || amount === undefined || amount === '') {
        return null
      }
      const numericAmount = Number(amount)
      if (Number.isNaN(numericAmount) || numericAmount < 0) {
        return null
      }
      return Math.round(numericAmount * this.capacityMultiplier(unit))
    },
    formatBytes (bytes) {
      const value = Number(bytes)
      if (!value || value < 0) {
        return '-'
      }
      const units = ['B', 'KiB', 'MiB', 'GiB', 'TiB']
      let size = value
      let unitIndex = 0
      while (size >= 1024 && unitIndex < units.length - 1) {
        size = size / 1024
        unitIndex += 1
      }
      return `${size.toFixed(unitIndex === 0 ? 0 : 1)} ${units[unitIndex]}`
    },
    formatVolumeOption (volume) {
      const name = volume.name || volume.displayname || volume.id
      const size = volume.size ? this.formatBytes(volume.size) : '-'
      const zone = volume.zonename || this.resource.zonename || ''
      return [name, size, zone].filter(Boolean).join(' / ')
    },
    formatCurrentBackingVolumeOption (volume) {
      const name = volume.name || volume.displayname || volume.id
      const size = volume.size ? this.formatBytes(volume.size) : '-'
      const mountPath = this.currentBackingVolumeMountPath(volume)
      const exports = this.currentBackingVolumeExportSummary(volume)
      return [name, size, mountPath || this.$t('label.storage.service.mount.path'), exports !== '-' ? exports : null].filter(Boolean).join(' / ')
    },
    storagePoolLabel (pool) {
      const name = pool.name || pool.displaytext || pool.id
      const scope = pool.scope || ''
      const type = pool.type || pool.storagetype || ''
      const tags = this.extractStorageTags(pool)
      return [name, type, scope, tags.length ? tags.join(',') : ''].filter(Boolean).join(' / ')
    },
    extractStorageTags (item) {
      if (!item) {
        return []
      }
      const raw = item.tags ?? item.storageTags ?? item.storagetags ?? item.storagepooltags ?? item.storagePoolTags
      if (Array.isArray(raw)) {
        return raw.map(value => String(value).trim()).filter(Boolean)
      }
      if (raw && typeof raw === 'object') {
        return Object.values(raw).map(value => String(value).trim()).filter(Boolean)
      }
      return String(raw || '')
        .split(',')
        .map(value => value.trim())
        .filter(Boolean)
    },
    reconcileNfsNewVolumeStorage () {
      if (this.forms.nfsExport.volumemode !== 'NEW') {
        return
      }
      if (!this.filteredNfsNewVolumeStoragePools.some(pool => pool.id === this.forms.nfsExport.storageid)) {
        this.forms.nfsExport.storageid = this.filteredNfsNewVolumeStoragePools[0]?.id || ''
      }
    },
    defaultCurrentBackingVolumeId () {
      if (this.currentBackingVolumes.length === 1) {
        return this.currentBackingVolumes[0].id
      }
      return ''
    },
    nfsExportPathForCurrentVolume (name) {
      const safeName = String(name || 'nfs01').trim().replace(/[^A-Za-z0-9._-]+/g, '-').replace(/^-+|-+$/g, '') || 'nfs01'
      return this.defaultNfsExportPath(safeName)
    },
    defaultNewVolumeStorageId () {
      const preferred = this.volume.storageid || this.resource.storageid || this.storageService.backingVolumes?.[0]?.storageid
      if (preferred && this.filteredNfsNewVolumeStoragePools.some(pool => pool.id === preferred)) {
        return preferred
      }
      return this.filteredNfsNewVolumeStoragePools[0]?.id || preferred || ''
    },
    formatProtocolEndpoints (port, preferredIp = null) {
      const ips = preferredIp ? [preferredIp] : this.serviceEndpoints
      const values = ips.filter(Boolean).map(ip => `${ip}:${port}`)
      return values.length ? values.join(', ') : '-'
    },
    normalizeEndpointIps (value) {
      if (!value) {
        return []
      }
      const values = Array.isArray(value) ? value : String(value).split(',')
      const seen = new Set()
      return values.map(item => String(item || '').trim())
        .filter(item => item && !seen.has(item) && seen.add(item))
    },
    nfsExportEndpointIps (share) {
      const config = this.parseStorageConfig(share?.config)
      const rawShareValue = share?.listenips ?? share?.listenIps
      const rawConfigValue = config.listenIps ?? config.listenips
      const rawListenIps = this.normalizeEndpointIps(rawShareValue ?? rawConfigValue)
      const endpointMode = this.nfsExportEndpointMode(share, config, rawListenIps)
      if (endpointMode === 'SELECTED') {
        return rawListenIps
      }
      return this.serviceEndpoints
    },
    nfsExportEndpointMode (share, config = null, rawListenIps = null) {
      const parsedConfig = config || this.parseStorageConfig(share?.config)
      const rawMode = share?.endpointmode ?? share?.endpointMode ?? parsedConfig.endpointMode ?? parsedConfig.endpointmode
      const mode = String(rawMode || '').trim().toUpperCase()
      if (mode === 'ALL' || mode === 'SELECTED') {
        return mode
      }
      const listenIps = rawListenIps || this.normalizeEndpointIps(share?.listenips ?? share?.listenIps ?? parsedConfig.listenIps ?? parsedConfig.listenips)
      return listenIps.length ? 'SELECTED' : 'ALL'
    },
    formatNfsExportEndpoints (share, port = 2049) {
      const values = this.nfsExportEndpointIps(share).map(ip => `${ip}:${port}`)
      return values.length ? values.join(', ') : '-'
    },
    formatNfsClientMountRoots (share, name) {
      const values = this.nfsExportEndpointIps(share).map(ip => `${ip}:/${name}`)
      return values.length ? values.join(', ') : `<${this.$t('label.storage.service.endpoint.ip.placeholder')}>:/${name}`
    },
    selectedNfsExportListenIps () {
      if (this.forms.nfsExport.endpointmode !== 'SELECTED') {
        return []
      }
      return this.normalizeEndpointIps(this.forms.nfsExport.listenips)
    },
    nfsExportImportMode () {
      if (this.forms.nfsExport.volumemode === 'NEW') {
        return 'FORMAT_EMPTY'
      }
      if (this.forms.nfsExport.volumemode === 'EXISTING') {
        return 'MOUNT_EXISTING'
      }
      return 'FORMAT_IF_EMPTY'
    },
    nextNfsExportName () {
      let index = this.storageService.nfsExports.length + 1
      const used = new Set(this.storageService.nfsExports.map(share => this.clientVisibleName(share.name || share.exportname, '').toLowerCase()).filter(Boolean))
      let name = `nfs${String(index).padStart(2, '0')}`
      while (used.has(name.toLowerCase())) {
        index += 1
        name = `nfs${String(index).padStart(2, '0')}`
      }
      return name
    },
    defaultNfsExportPath (name) {
      const safeName = String(name || 'nfs01').trim().replace(/[^A-Za-z0-9._-]+/g, '-').replace(/^-+|-+$/g, '') || 'nfs01'
      return `/export/${safeName}`
    },
    isValidNfsExportName (name) {
      const value = String(name || '').trim()
      return !!value && value !== '.' && value !== '..' && /^[A-Za-z0-9._-]+$/.test(value)
    },
    validateNfsExportNameAndPath () {
      const name = String(this.forms.nfsExport.name || '').trim()
      if (!this.isValidNfsExportName(name)) {
        this.$message.error(this.$t('message.storage.service.nfs.name.invalid'))
        return false
      }
      const expectedPath = `/export/${name}`
      const path = String(this.forms.nfsExport.path || '').trim().replace(/\/+$/g, '')
      if (path !== expectedPath) {
        this.$message.error(this.$t('message.storage.service.nfs.path.must.match.name', { path: expectedPath }))
        return false
      }
      return true
    },
    syncNfsExportPathToCurrentVolume () {
      if (this.actionModal.type === 'editNfsExport' || this.forms.nfsExport.volumemode !== 'CURRENT' || !this.selectedCurrentBackingVolume) {
        return
      }
      const name = this.forms.nfsExport.name || this.nextNfsExportName()
      const nextPath = this.defaultNfsExportPath(name)
      const defaultPath = this.defaultNfsExportPath(name)
      const currentPath = this.forms.nfsExport.path || ''
      if (!currentPath || currentPath === defaultPath || currentPath.endsWith(`/${name}`)) {
        this.forms.nfsExport.path = nextPath
      }
    },
    capacityBytesToInput (bytes) {
      const value = Number(bytes)
      if (!value || value < 0) {
        return { amount: null, unit: 'GiB' }
      }
      const units = [...this.capacityUnits].reverse()
      const exact = units.find(unit => unit.multiplier > 0 && value >= unit.multiplier && value % unit.multiplier === 0)
      const unit = exact || this.capacityUnits.find(item => item.value === 'GiB') || this.capacityUnits[0]
      const amount = unit.multiplier ? value / unit.multiplier : value
      return { amount, unit: unit.value }
    },
    resetNfsExportForm () {
      Object.assign(this.forms.nfsExport, {
        name: '',
        path: '',
        volumeid: this.defaultCurrentBackingVolumeId(),
        volumemode: 'CURRENT',
        newvolumename: '',
        diskofferingid: '',
        storageid: this.defaultNewVolumeStorageId(),
        newvolumesize: null,
        filesystem: 'xfs',
        relativepath: '',
        createdirectory: true,
        quotaamount: null,
        quotaunit: 'GiB',
        endpointmode: 'SELECTED',
        listenips: [],
        readonly: false,
        rootsquash: true,
        allsquash: false,
        anonuid: 65534,
        anongid: 65534,
        owneruid: 65534,
        ownergid: 65534,
        mode: '0775',
        recursivepermission: false,
        sync: true,
        secure: false
      })
    },
    resetNfsAclForm () {
      Object.assign(this.forms.nfsAcl, {
        exportid: '',
        principaltype: 'CIDR',
        principal: '',
        principals: [],
        permission: 'READ_WRITE',
        rootsquash: true,
        allsquash: false,
        anonuid: null,
        anongid: null,
        sync: true,
        secure: false
      })
    },
    populateNfsExportForm (record) {
      const share = record?.raw || record || {}
      const config = this.effectiveNfsExportConfig(this.parseStorageConfig(share.config || share.configjson || share.configJson))
      const quota = this.capacityBytesToInput(share.quotabytes || share.quotaBytes || share.capacitybytes || share.sizebytes)
      const rawListenIps = this.normalizeEndpointIps(share.listenips ?? share.listenIps ?? config.listenIps ?? config.listenips)
      const endpointMode = this.nfsExportEndpointMode(share, config, rawListenIps)
      const volumeId = share.volumeid || share.volumeId || ''
      const currentVolume = this.currentBackingVolumes.find(volume => String(volume.id) === String(volumeId))
      Object.assign(this.forms.nfsExport, {
        name: this.clientVisibleName(share.name || share.exportname, ''),
        path: share.path || share.mountpath || share.backingpath || '',
        volumeid: volumeId,
        volumemode: currentVolume || !volumeId ? 'CURRENT' : 'EXISTING',
        newvolumename: '',
        diskofferingid: '',
        newvolumesize: null,
        filesystem: share.filesystem || share.fsType || 'xfs',
        relativepath: '',
        createdirectory: config.createDirectory === undefined && config.createdirectory === undefined ? true : this.boolValue(config.createDirectory ?? config.createdirectory),
        quotaamount: quota.amount,
        quotaunit: quota.unit,
        endpointmode: endpointMode,
        listenips: endpointMode === 'SELECTED' ? rawListenIps : [],
        readonly: this.boolValue(config.readOnly ?? config.readonly),
        rootsquash: this.boolValue(config.rootSquash ?? config.rootsquash),
        allsquash: this.boolValue(config.allSquash ?? config.allsquash),
        anonuid: config.anonUid ?? config.anonuid ?? null,
        anongid: config.anonGid ?? config.anongid ?? null,
        owneruid: config.ownerUid ?? config.owneruid ?? null,
        ownergid: config.ownerGid ?? config.ownergid ?? null,
        mode: config.mode || '',
        recursivepermission: this.boolValue(config.recursivePermission ?? config.recursivepermission),
        sync: this.boolValue(config.sync),
        secure: this.boolValue(config.secure)
      })
    },
    populateNfsAclForm (record) {
      const acl = record?.raw || record || {}
      const config = this.parseStorageConfig(acl.config || acl.configjson || acl.configJson)
      Object.assign(this.forms.nfsAcl, {
        exportid: acl.resourceid || acl.resourceId || acl.exportid || acl.exportId || this.nfsShareForAcl(acl)?.id || '',
        principaltype: acl.principaltype || acl.principalType || 'CIDR',
        principal: acl.principal || acl.cidr || acl.client || '',
        principals: [acl.principal || acl.cidr || acl.client].filter(Boolean),
        permission: acl.permission || acl.access || 'READ_WRITE',
        rootsquash: this.boolValue(acl.rootsquash ?? acl.rootSquash ?? config.rootSquash),
        allsquash: this.boolValue(acl.allsquash ?? acl.allSquash ?? config.allSquash),
        anonuid: config.anonUid ?? config.anonuid ?? null,
        anongid: config.anonGid ?? config.anongid ?? null,
        sync: this.boolValue(acl.sync ?? config.sync),
        secure: this.boolValue(acl.secure ?? config.secure)
      })
    },
    nfsAclPrincipals () {
      let values
      if (this.actionModal.type === 'editNfsAcl') {
        values = String(this.forms.nfsAcl.principal || '').split(',')
      } else if (Array.isArray(this.forms.nfsAcl.principals) && this.forms.nfsAcl.principals.length) {
        values = this.forms.nfsAcl.principals
      } else {
        values = String(this.forms.nfsAcl.principal || '').split(',')
      }
      const seen = new Set()
      return values.map(value => String(value || '').trim())
        .filter(value => value && !seen.has(value) && seen.add(value))
    },
    openActionModal (type, context = null) {
      this.actionModal.type = type
      this.actionModal.context = context
      this.actionModal.visible = true
      if (type === 'enableProtocol' && context?.protocol) {
        this.forms.enableProtocol.protocol = context.protocol
        this.forms.enableProtocol.port = this.defaultProtocolPort(context.protocol)
      }
      if (type === 'nfsExport') {
        this.resetNfsExportForm()
        this.fetchDiskOfferings()
        this.fetchStoragePools()
        if (!this.forms.nfsExport.name) {
          this.forms.nfsExport.name = this.nextNfsExportName()
        }
        if (!this.forms.nfsExport.path) {
          this.forms.nfsExport.path = this.defaultNfsExportPath(this.forms.nfsExport.name)
        }
        this.forms.nfsExport.endpointmode = 'SELECTED'
        this.forms.nfsExport.listenips = []
        this.applyNfsWritableDefaults()
      }
      if (type === 'editNfsExport') {
        this.populateNfsExportForm(context)
        this.fetchDiskOfferings()
        this.fetchStoragePools()
      }
      if (type === 'nfsAcl') {
        this.resetNfsAclForm()
        if (context?.exportid) {
          this.forms.nfsAcl.exportid = context.exportid
        } else if (this.storageService.nfsExports.length > 0) {
          this.forms.nfsAcl.exportid = this.storageService.nfsExports[0].id
        }
      }
      if (type === 'editNfsAcl') {
        this.populateNfsAclForm(context)
      }
      if (type === 'resizeShare' && context?.id) {
        this.forms.resizeShare.id = context.id
      }
      if (type === 'resizeVolume' && context?.id) {
        this.forms.resizeShare.id = context.id
        this.forms.resizeShare.resizevolume = true
      }
      if (type === 'detachBackingVolume' && context?.id) {
        this.forms.detachBackingVolume = {
          volumeid: context.id,
          confirmation: false
        }
      }
      if (type === 'disconnectSession' && context) {
        this.forms.disconnectSession = {
          protocol: context.protocol || context.service || '',
          peer: context.peer || '',
          local: context.local || '',
          sessionid: context.sessionId || '',
          force: true
        }
      }
      if (type === 'deleteEndpoint') {
        const firstEndpoint = this.removableServiceEndpoints[0] || {}
        this.forms.deleteEndpoint = {
          protocol: context?.protocol || 'NFS',
          listenip: firstEndpoint.ipaddress || '',
          confirmation: ''
        }
      }
    },
    openDeleteModal (resourceType, record) {
      const raw = record?.raw || record || {}
      const names = {
        protocol: record?.name || record?.protocol,
        nfsExport: record?.name || this.clientVisibleName(raw.name || raw.exportname, ''),
        nfsAcl: record?.principal || raw.principal || raw.cidr || raw.client
      }
      const commands = {
        protocol: 'deleteStorageServiceProtocol',
        nfsExport: 'deleteStorageNfsExport',
        nfsAcl: 'deleteStorageNfsAcl'
      }
      this.forms.deleteConfirm = {
        resourceType,
        command: commands[resourceType],
        id: raw.id || record?.id || '',
        protocol: raw.protocol || record?.protocol || '',
        confirmation: ''
      }
      this.actionModal.type = 'deleteConfirm'
      this.actionModal.context = {
        resourceType,
        name: names[resourceType] || raw.id || record?.id || '',
        raw
      }
      this.actionModal.visible = true
    },
    closeActionModal () {
      this.actionModal.visible = false
      this.actionModal.type = ''
      this.actionModal.context = null
      this.actionModal.loading = false
    },
    async submitActionModal () {
      const actions = {
        enableProtocol: this.enableProtocol,
        nfsExport: this.createNfsExport,
        editNfsExport: this.updateNfsExport,
        nfsAcl: this.createNfsAcl,
        editNfsAcl: this.updateNfsAcl,
        deleteConfirm: this.deleteStorageResource,
        deleteEndpoint: this.deleteStorageEndpoint,
        smbShare: this.createSmbShare,
        smbAcl: this.createSmbAcl,
        adJoin: this.joinAdDomain,
        iscsiTarget: this.createIscsiTarget,
        iscsiAcl: this.createIscsiAcl,
        nvmePrepare: this.prepareNvmeOf,
        nvmeSubsystem: this.createNvmeSubsystem,
        nvmeHostAcl: this.createNvmeHostAcl,
        attachVolume: this.attachExistingVolume,
        resizeShare: this.resizeFileShare,
        resizeVolume: this.resizeBackingVolume,
        detachBackingVolume: this.detachBackingVolume,
        disconnectSession: this.disconnectSession
      }
      const action = actions[this.actionModal.type]
      if (!action) {
        this.closeActionModal()
        return
      }
      this.actionModal.loading = true
      await action.call(this)
      this.closeActionModal()
    },
    enableProtocol () {
      if (!this.validateListenIpSelection()) {
        return Promise.resolve()
      }
      return this.runStorageAction('enableProtocol', 'enableStorageServiceProtocol', {
        instanceid: this.storageService.instance.id,
        protocol: this.forms.enableProtocol.protocol,
        listenip: this.forms.enableProtocol.listenip,
        port: this.forms.enableProtocol.port
      }, this.$t('label.storage.service.enable.protocol'))
    },
    async createNfsExport () {
      if (!this.forms.nfsExport.name) {
        this.forms.nfsExport.name = this.nextNfsExportName()
      }
      if (!this.forms.nfsExport.path) {
        this.forms.nfsExport.path = this.defaultNfsExportPath(this.forms.nfsExport.name)
      }
      if (!this.validateNfsExportNameAndPath()) {
        return Promise.resolve()
      }
      const listenIps = this.selectedNfsExportListenIps()
      if (this.forms.nfsExport.endpointmode === 'SELECTED' && listenIps.length === 0) {
        this.$message.error(this.$t('message.storage.service.nfs.endpoint.required'))
        return Promise.resolve()
      }
      this.applyNfsWritableDefaults()
      const volumeId = await this.prepareNfsExportVolume()
      if (volumeId === false) {
        return Promise.resolve()
      }
      return this.runStorageAction('nfsExport', 'createStorageNfsExport', {
        instanceid: this.storageService.instance.id,
        name: this.forms.nfsExport.name,
        path: this.forms.nfsExport.path,
        createdirectory: this.forms.nfsExport.createdirectory,
        volumeid: volumeId,
        filesystem: this.forms.nfsExport.filesystem,
        importmode: this.nfsExportImportMode(),
        quotabytes: this.toCapacityBytes(this.forms.nfsExport.quotaamount, this.forms.nfsExport.quotaunit),
        readonly: this.forms.nfsExport.readonly,
        rootsquash: this.forms.nfsExport.rootsquash,
        allsquash: this.forms.nfsExport.allsquash,
        anonuid: this.forms.nfsExport.anonuid,
        anongid: this.forms.nfsExport.anongid,
        owneruid: this.forms.nfsExport.owneruid,
        ownergid: this.forms.nfsExport.ownergid,
        mode: this.forms.nfsExport.mode,
        recursivepermission: this.forms.nfsExport.recursivepermission,
        sync: this.forms.nfsExport.sync,
        secure: this.forms.nfsExport.secure,
        endpointmode: this.forms.nfsExport.endpointmode,
        listenips: listenIps.join(','),
        cleanupvolumeonfailure: this.forms.nfsExport.volumemode === 'NEW' && !!volumeId
      }, this.$t('label.storage.service.create.nfs.export'))
    },
    async prepareNfsExportVolume () {
      if (this.forms.nfsExport.volumemode === 'CURRENT') {
        if (!this.forms.nfsExport.volumeid) {
          this.$message.error(this.$t('message.storage.service.current.volume.required'))
          return false
        }
        return this.forms.nfsExport.volumeid
      }
      if (this.forms.nfsExport.volumemode === 'EXISTING') {
        if (!this.forms.nfsExport.volumeid) {
          this.$message.error(this.$t('message.storage.service.existing.volume.required'))
          return false
        }
        return this.forms.nfsExport.volumeid
      }
      if (!this.forms.nfsExport.storageid) {
        this.forms.nfsExport.storageid = this.defaultNewVolumeStorageId()
      }
      if (!this.forms.nfsExport.diskofferingid || !this.forms.nfsExport.storageid || !this.forms.nfsExport.newvolumesize) {
        this.$message.error(this.$t('message.storage.service.new.volume.required'))
        return false
      }
      const params = {
        name: this.forms.nfsExport.newvolumename || `${this.forms.nfsExport.name}-volume`,
        zoneid: this.resource.zoneid,
        diskofferingid: this.forms.nfsExport.diskofferingid,
        storageid: this.forms.nfsExport.storageid,
        size: this.forms.nfsExport.newvolumesize
      }
      const json = await postAPI('createVolume', this.cleanParams(params))
      const response = json.createvolumeresponse || {}
      const jobResult = response.jobid ? await this.waitStorageServiceJob(response.jobid, 'createVolume', 180) : null
      const jobVolume = jobResult?.jobresult?.volume || jobResult?.volume || {}
      const id = jobVolume.id || response.id || response.volume?.id
      if (!id) {
        this.$message.error(this.$t('message.storage.service.new.volume.create.failed'))
        return false
      }
      await this.waitVolumeAttachable(id)
      return id
    },
    createNfsAcl () {
      const principals = this.nfsAclPrincipals()
      if (!principals.length) {
        this.$message.error(this.$t('message.storage.service.nfs.acl.required'))
        return Promise.resolve()
      }
      return this.runStorageAction('nfsAcl', 'createStorageNfsAcl', {
        exportid: this.forms.nfsAcl.exportid,
        principaltype: this.forms.nfsAcl.principaltype,
        principal: principals[0],
        principals: principals.join(','),
        permission: this.forms.nfsAcl.permission,
        rootsquash: this.forms.nfsAcl.rootsquash,
        allsquash: this.forms.nfsAcl.allsquash,
        anonuid: this.forms.nfsAcl.anonuid,
        anongid: this.forms.nfsAcl.anongid,
        sync: this.forms.nfsAcl.sync,
        secure: this.forms.nfsAcl.secure
      }, this.$t('label.storage.service.create.nfs.acl'))
    },
    updateNfsExport () {
      const context = this.actionModal.context?.raw || this.actionModal.context || {}
      const listenIps = this.selectedNfsExportListenIps()
      if (this.forms.nfsExport.endpointmode === 'SELECTED' && listenIps.length === 0) {
        this.$message.error(this.$t('message.storage.service.nfs.endpoint.required'))
        return Promise.resolve()
      }
      if (!this.validateNfsExportNameAndPath()) {
        return Promise.resolve()
      }
      this.applyNfsWritableDefaults()
      return this.runStorageAction('editNfsExport', 'updateStorageNfsExport', {
        id: context.id || this.actionModal.context?.id,
        name: this.forms.nfsExport.name,
        path: this.forms.nfsExport.path,
        createdirectory: this.forms.nfsExport.createdirectory,
        volumeid: this.forms.nfsExport.volumeid,
        filesystem: this.forms.nfsExport.filesystem,
        importmode: this.nfsExportImportMode(),
        quotabytes: this.toCapacityBytes(this.forms.nfsExport.quotaamount, this.forms.nfsExport.quotaunit),
        readonly: this.forms.nfsExport.readonly,
        rootsquash: this.forms.nfsExport.rootsquash,
        allsquash: this.forms.nfsExport.allsquash,
        anonuid: this.forms.nfsExport.anonuid,
        anongid: this.forms.nfsExport.anongid,
        owneruid: this.forms.nfsExport.owneruid,
        ownergid: this.forms.nfsExport.ownergid,
        mode: this.forms.nfsExport.mode,
        recursivepermission: this.forms.nfsExport.recursivepermission,
        sync: this.forms.nfsExport.sync,
        secure: this.forms.nfsExport.secure,
        endpointmode: this.forms.nfsExport.endpointmode,
        listenips: listenIps.join(',')
      }, this.$t('label.storage.service.update.nfs.export'))
    },
    updateNfsAcl () {
      const context = this.actionModal.context?.raw || this.actionModal.context || {}
      const principals = this.nfsAclPrincipals()
      if (principals.length !== 1) {
        this.$message.error(this.$t('message.storage.service.nfs.acl.edit.single.required'))
        return Promise.resolve()
      }
      return this.runStorageAction('editNfsAcl', 'updateStorageNfsAcl', {
        id: context.id || this.actionModal.context?.id,
        principal: principals[0],
        permission: this.forms.nfsAcl.permission,
        rootsquash: this.forms.nfsAcl.rootsquash,
        allsquash: this.forms.nfsAcl.allsquash,
        anonuid: this.forms.nfsAcl.anonuid,
        anongid: this.forms.nfsAcl.anongid,
        sync: this.forms.nfsAcl.sync,
        secure: this.forms.nfsAcl.secure
      }, this.$t('label.storage.service.update.nfs.acl'))
    },
    deleteStorageResource () {
      if (!this.deleteConfirmationMatched) {
        this.$message.error(this.$t('message.storage.service.delete.confirm.input'))
        return Promise.resolve()
      }
      const type = this.forms.deleteConfirm.resourceType
      const command = this.forms.deleteConfirm.command
      const key = type === 'protocol' ? 'enableProtocol' : type
      const params = type === 'protocol'
        ? { instanceid: this.storageService.instance.id, protocol: this.forms.deleteConfirm.protocol }
        : { id: this.forms.deleteConfirm.id }
      return this.runStorageAction(key, command, params, this.$t('label.storage.service.delete.confirm'))
    },
    deleteStorageEndpoint () {
      if (!this.deleteEndpointConfirmationMatched) {
        this.$message.error(this.$t('message.storage.service.delete.endpoint.confirm.input'))
        return Promise.resolve()
      }
      return this.runStorageAction('deleteEndpoint', 'deleteStorageServiceProtocol', {
        instanceid: this.storageService.instance.id,
        protocol: this.forms.deleteEndpoint.protocol,
        listenip: this.forms.deleteEndpoint.listenip
      }, this.$t('label.storage.service.delete.endpoint'))
    },
    createSmbShare () {
      return this.runStorageAction('smbShare', 'createStorageSmbShare', {
        instanceid: this.storageService.instance.id,
        name: this.forms.smbShare.name,
        path: this.forms.smbShare.path,
        volumeid: this.forms.smbShare.volumeid,
        filesystem: this.forms.smbShare.filesystem,
        quotabytes: this.toCapacityBytes(this.forms.smbShare.quotaamount, this.forms.smbShare.quotaunit),
        readonly: this.forms.smbShare.readonly,
        browseable: this.forms.smbShare.browseable,
        guestok: this.forms.smbShare.guestok
      }, this.$t('label.storage.service.create.smb.share'))
    },
    createSmbAcl () {
      const result = this.runStorageAction('smbAcl', 'createStorageSmbAcl', this.forms.smbAcl, this.$t('label.storage.service.create.smb.acl'))
      this.forms.smbAcl.password = ''
      return result
    },
    joinAdDomain () {
      const result = this.runStorageAction('adJoin', 'joinStorageServiceToAdDomain', {
        instanceid: this.storageService.instance.id,
        ...this.forms.adJoin
      }, this.$t('label.storage.service.join.ad.domain'))
      this.forms.adJoin.password = ''
      return result
    },
    createIscsiTarget () {
      return this.runStorageAction('iscsiTarget', 'createStorageIscsiTarget', {
        instanceid: this.storageService.instance.id,
        targetname: this.forms.iscsiTarget.targetname,
        lun: this.forms.iscsiTarget.lun,
        volumeid: this.forms.iscsiTarget.volumeid,
        lunsizebytes: this.toCapacityBytes(this.forms.iscsiTarget.lunsizeamount, this.forms.iscsiTarget.lunsizeunit),
        backingpath: this.forms.iscsiTarget.backingpath
      }, this.$t('label.storage.service.create.iscsi.target'))
    },
    createIscsiAcl () {
      const result = this.runStorageAction('iscsiAcl', 'createStorageIscsiAcl', {
        targetid: this.forms.iscsiAcl.targetid,
        initiatoriqn: this.forms.iscsiAcl.initiatoriqn,
        permission: this.forms.iscsiAcl.permission,
        chapenabled: this.forms.iscsiAcl.chapenabled,
        chapusername: this.forms.iscsiAcl.chapenabled ? this.forms.iscsiAcl.chapusername : '',
        chapsecret: this.forms.iscsiAcl.chapenabled ? this.forms.iscsiAcl.chapsecret : '',
        mutualchapenabled: this.forms.iscsiAcl.chapenabled && this.forms.iscsiAcl.mutualchapenabled,
        mutualchapusername: this.forms.iscsiAcl.chapenabled && this.forms.iscsiAcl.mutualchapenabled ? this.forms.iscsiAcl.mutualchapusername : '',
        mutualchapsecret: this.forms.iscsiAcl.chapenabled && this.forms.iscsiAcl.mutualchapenabled ? this.forms.iscsiAcl.mutualchapsecret : ''
      }, this.$t('label.storage.service.create.iscsi.acl'))
      this.forms.iscsiAcl.chapsecret = ''
      this.forms.iscsiAcl.mutualchapsecret = ''
      return result
    },
    prepareNvmeOf () {
      return this.runStorageAction('nvmePrepare', 'prepareStorageServiceNvmeOfVm', {
        instanceid: this.storageService.instance.id,
        ...this.forms.nvmePrepare
      }, this.$t('label.storage.service.prepare.nvmeof'))
    },
    createNvmeSubsystem () {
      return this.runStorageAction('nvmeSubsystem', 'createStorageNvmeOfSubsystem', {
        instanceid: this.storageService.instance.id,
        ...this.forms.nvmeSubsystem
      }, this.$t('label.storage.service.create.nvme.subsystem'))
    },
    createNvmeHostAcl () {
      if (!this.nvmeDhChapSupported) {
        this.forms.nvmeHostAcl.dhchapenabled = false
        this.forms.nvmeHostAcl.dhchapctrlenabled = false
        this.forms.nvmeHostAcl.dhchapkey = ''
        this.forms.nvmeHostAcl.dhchapctrlkey = ''
      }
      const result = this.runStorageAction('nvmeHostAcl', 'createStorageNvmeOfHostAcl', {
        subsystemid: this.forms.nvmeHostAcl.subsystemid,
        hostnqn: this.forms.nvmeHostAcl.hostnqn,
        dhchapenabled: this.forms.nvmeHostAcl.dhchapenabled,
        dhchapkey: this.forms.nvmeHostAcl.dhchapenabled ? this.forms.nvmeHostAcl.dhchapkey : '',
        dhchapctrlenabled: this.forms.nvmeHostAcl.dhchapenabled && this.forms.nvmeHostAcl.dhchapctrlenabled,
        dhchapctrlkey: this.forms.nvmeHostAcl.dhchapenabled && this.forms.nvmeHostAcl.dhchapctrlenabled ? this.forms.nvmeHostAcl.dhchapctrlkey : ''
      }, this.$t('label.storage.service.create.nvme.host.acl'))
      this.forms.nvmeHostAcl.dhchapkey = ''
      this.forms.nvmeHostAcl.dhchapctrlkey = ''
      return result
    },
    attachExistingVolume () {
      return this.runStorageAction('attachVolume', 'attachStorageVolumeToFileShare', this.forms.attachVolume, this.$t('label.storage.service.attach.existing.volume'))
    },
    resizeFileShare () {
      return this.runStorageAction('resizeShare', 'resizeStorageFileShare', {
        id: this.forms.resizeShare.id,
        size: this.forms.resizeShare.size,
        quotabytes: this.toCapacityBytes(this.forms.resizeShare.quotaamount, this.forms.resizeShare.quotaunit),
        resizevolume: this.forms.resizeShare.resizevolume
      }, this.$t('label.storage.service.resize.file.share'))
    },
    resizeBackingVolume () {
      return this.runStorageAction('resizeVolume', 'resizeStorageFileShare', {
        id: this.forms.resizeShare.id,
        size: this.forms.resizeShare.size,
        resizevolume: true
      }, this.$t('label.storage.service.resize.volume'))
    },
    detachBackingVolume () {
      return this.runStorageAction('detachBackingVolume', 'detachStorageServiceBackingVolume', {
        instanceid: this.storageService.instance.id,
        volumeid: this.forms.detachBackingVolume.volumeid
      }, this.$t('label.storage.service.detach.backing.volume'))
    },
    disconnectSession () {
      return this.runStorageAction('disconnectSession', 'disconnectStorageServiceSession', {
        instanceid: this.storageService.instance.id,
        protocol: this.forms.disconnectSession.protocol,
        peer: this.forms.disconnectSession.peer,
        local: this.forms.disconnectSession.local,
        sessionid: this.forms.disconnectSession.sessionid,
        force: this.forms.disconnectSession.force
      }, this.$t('label.storage.service.disconnect.session'))
    },
    parseRuntimeResult (item) {
      if (!item) {
        return {}
      }
      if (item.resultjson && typeof item.resultjson === 'string') {
        try {
          return JSON.parse(item.resultjson.replace(/\\=/g, '='))
        } catch (e) {
          return {}
        }
      }
      return item.resultjson || item
    },
    protocolSessions (protocol) {
      return this.allSessions.filter(session => {
        const sessionProtocol = (session.protocol || session.service || '').toUpperCase()
        return sessionProtocol === protocol
      })
    },
    runtimeColor (item) {
      const status = String(item.status || '').toUpperCase()
      if (item.success === true || status === 'OK' || status === 'READY') {
        return 'green'
      }
      if (status === 'PREPARATION_REQUIRED' || status === 'NOT_ATTACHED') {
        return 'orange'
      }
      if (item.success === false) {
        return 'red'
      }
      return 'blue'
    },
    nicLabel (nic) {
      return [nic.networkname, nic.ipaddress, nic.netmask || nic.cidr].filter(Boolean).join(' / ')
    },
    shareNameLabel (share) {
      return this.clientVisibleName(share?.name || share?.exportname || share?.id, '-')
    },
    shareLabel (share) {
      return [share.protocol, share.name || share.id, share.path].filter(Boolean).join(' / ')
    },
    uidGidSummary (uid, gid) {
      const left = uid === undefined || uid === null || uid === '' ? '-' : uid
      const right = gid === undefined || gid === null || gid === '' ? '-' : gid
      return `${left}:${right}`
    },
    posixPermissionSummary (config = {}) {
      const owner = this.uidGidSummary(config.ownerUid ?? config.owneruid, config.ownerGid ?? config.ownergid)
      const mode = config.mode || '-'
      const recursive = this.boolValue(config.recursivePermission ?? config.recursivepermission) ? this.$t('label.yes') : this.$t('label.no')
      return `${owner} / ${mode} / ${recursive}`
    },
    effectiveNfsExportConfig (config = {}) {
      const next = { ...config }
      const readOnly = this.boolValue(next.readOnly ?? next.readonly)
      const rootSquash = next.rootSquash === undefined && next.rootsquash === undefined ? true : this.boolValue(next.rootSquash ?? next.rootsquash)
      if (!readOnly && rootSquash) {
        if (next.anonUid === undefined && next.anonuid === undefined) next.anonUid = 65534
        if (next.anonGid === undefined && next.anongid === undefined) next.anonGid = 65534
        if (next.ownerUid === undefined && next.owneruid === undefined) next.ownerUid = next.anonUid ?? next.anonuid ?? 65534
        if (next.ownerGid === undefined && next.ownergid === undefined) next.ownerGid = next.anonGid ?? next.anongid ?? 65534
        if (!next.mode) next.mode = '0775'
        if (next.recursivePermission === undefined && next.recursivepermission === undefined) next.recursivePermission = false
      }
      return next
    },
    applyNfsWritableDefaults () {
      if (this.forms.nfsExport.readonly || !this.forms.nfsExport.rootsquash) {
        return
      }
      if (this.forms.nfsExport.anonuid === null || this.forms.nfsExport.anonuid === undefined || this.forms.nfsExport.anonuid === '') {
        this.forms.nfsExport.anonuid = 65534
      }
      if (this.forms.nfsExport.anongid === null || this.forms.nfsExport.anongid === undefined || this.forms.nfsExport.anongid === '') {
        this.forms.nfsExport.anongid = 65534
      }
      if (this.forms.nfsExport.owneruid === null || this.forms.nfsExport.owneruid === undefined || this.forms.nfsExport.owneruid === '') {
        this.forms.nfsExport.owneruid = this.forms.nfsExport.anonuid
      }
      if (this.forms.nfsExport.ownergid === null || this.forms.nfsExport.ownergid === undefined || this.forms.nfsExport.ownergid === '') {
        this.forms.nfsExport.ownergid = this.forms.nfsExport.anongid
      }
      if (!this.forms.nfsExport.mode) {
        this.forms.nfsExport.mode = '0775'
      }
    },
    clientVisibleName (value, fallback) {
      const normalized = String(value || fallback || '').trim().replace(/^\/+|\/+$/g, '')
        .replace(/[^A-Za-z0-9_.-]+/g, '-')
        .replace(/^[.-]+|[.-]+$/g, '')
      return normalized || fallback
    },
    targetLabel (target) {
      return [target.targetname || target.subsystemnqn || target.id, target.lunornamespace].filter(Boolean).join(' / ')
    },
    formatRuntime (items) {
      if (!items || items.length === 0) {
        return '{}'
      }
      return JSON.stringify(items.map(item => {
        const clone = { ...item }
        if (clone.resultjson) {
          try {
            clone.resultjson = JSON.parse(clone.resultjson)
          } catch (e) {}
        }
        return clone
      }), null, 2)
    }
  }
}
</script>

<style lang="scss" scoped>
  .page-header-wrapper-grid-content-main {
    width: 100%;
    height: 100%;
    min-height: 100%;
    transition: 0.3s;
  }
  .info {
    font-size: 0.8rem;
  }
  .storage-service-tabs {
    width: 100%;

    :deep(.ant-tabs-nav) {
      margin-bottom: 16px;
    }

    :deep(.ant-tabs-tab) {
      padding: 10px 14px;
    }

    :deep(.ant-tabs-content-holder) {
      min-width: 0;
    }
  }
  .storage-service {
    color: inherit;
  }
  .storage-service--overview {
    margin-top: 8px;
  }
  .storage-service--protocol {
    width: 100%;
    min-height: calc(100vh - 178px);
  }
  .storage-service--wide {
    min-height: calc(100vh - 156px);
  }
  .storage-service__section-title {
    margin: 18px 0 8px;
    font-size: 15px;
    font-weight: 600;
  }
  .storage-service__header {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 16px;
    margin-bottom: 16px;

    h3 {
      margin-bottom: 4px;
    }

    p {
      margin: 0;
      color: rgba(127, 127, 127, 0.95);
    }
  }
  .storage-service__alert {
    margin-bottom: 16px;
  }
  .storage-service-error-list {
    margin: 4px 0 0;
    padding-left: 18px;
    color: inherit;
  }
  .storage-service-error-list li + li {
    margin-top: 4px;
  }
  .storage-service__grid,
  .storage-service__inner-tabs {
    margin-top: 16px;
  }
  .storage-protocol-topbar {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 16px;
    margin-bottom: 12px;
  }
  .protocol-toolbar--right {
    justify-content: flex-end;
    margin-left: auto;
  }
  .storage-protocol-grid {
    display: grid;
    grid-template-columns: minmax(0, 1.35fr) minmax(280px, 0.65fr);
    gap: 16px;
    margin-bottom: 16px;
  }
  .storage-detail-list {
    margin: 0;
  }
  .storage-detail-list__row {
    display: grid;
    grid-template-columns: minmax(140px, 22%) minmax(0, 1fr);
    gap: 16px;
    padding: 11px 0;
    border-top: 1px solid rgba(127, 127, 127, 0.22);

    dt {
      font-weight: 600;
      color: rgba(127, 127, 127, 0.95);
    }

    dd {
      min-width: 0;
      margin: 0;
      overflow-wrap: anywhere;
    }
  }
  .storage-panel {
    min-height: 100%;
    padding: 16px;
    border: 1px solid rgba(127, 127, 127, 0.22);
    border-radius: 6px;
    background: rgba(127, 127, 127, 0.055);
  }
  .storage-panel__title {
    margin-bottom: 12px;
    font-weight: 600;
    color: inherit;
  }
  .storage-panel__description {
    margin: -4px 0 12px;
    color: rgba(127, 127, 127, 0.95);
  }
  .storage-kv {
    display: grid;
    grid-template-columns: minmax(96px, auto) minmax(0, 1fr);
    gap: 8px 12px;
    margin: 0;

    dt {
      color: rgba(127, 127, 127, 0.95);
    }

    dd {
      min-width: 0;
      margin: 0;
      overflow-wrap: anywhere;
    }
  }
  .storage-kv--compact {
    grid-template-columns: minmax(112px, auto) minmax(0, 1fr);
  }
  .command-line {
    min-width: 0;
    margin-top: 8px;
    padding: 9px 10px;
    overflow: hidden;
    color: inherit;
    font-family: SFMono-Regular, Consolas, 'Liberation Mono', monospace;
    font-size: 12px;
    line-height: 1.45;
    text-overflow: ellipsis;
    white-space: nowrap;
    border: 1px solid rgba(127, 127, 127, 0.18);
    border-radius: 4px;
    background: rgba(127, 127, 127, 0.07);
  }
  .storage-table-section {
    margin-top: 16px;
    padding: 14px 16px 16px;
    border: 1px solid rgba(127, 127, 127, 0.22);
    border-radius: 6px;
    background: rgba(127, 127, 127, 0.04);
  }
  .storage-table-section__header {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 12px;
    margin-bottom: 12px;

    h4 {
      margin: 0 0 4px;
      font-size: 14px;
      font-weight: 600;
      color: inherit;
    }

    p {
      margin: 0;
      color: rgba(127, 127, 127, 0.95);
    }
  }
  .storage-section-actions {
    flex: 0 0 auto;
    justify-content: flex-end;
  }
  .storage-data-table {
    width: 100%;
    color: inherit;

    :deep(.ant-table) {
      color: inherit;
      background: transparent;
    }

    :deep(.ant-table-container) {
      border-color: rgba(127, 127, 127, 0.2);
    }

    :deep(.ant-table-thead > tr > th) {
      color: inherit;
      font-weight: 600;
      background: rgba(127, 127, 127, 0.08);
      border-bottom-color: rgba(127, 127, 127, 0.24);
    }

    :deep(.ant-table-tbody > tr > td) {
      color: inherit;
      border-bottom-color: rgba(127, 127, 127, 0.16);
    }

    :deep(.ant-table-tbody > tr:hover > td) {
      background: rgba(24, 144, 255, 0.08);
    }

    :deep(.ant-table-cell-fix-left),
    :deep(.ant-table-cell-fix-right) {
      color: inherit;
      background: inherit;
    }

    :deep(.ant-empty-normal) {
      color: rgba(127, 127, 127, 0.95);
    }

    :deep(.ant-empty-description) {
      color: rgba(127, 127, 127, 0.95);
    }

    :deep(.ant-table-body) {
      scrollbar-width: thin;
      scrollbar-color: rgba(127, 127, 127, 0.45) transparent;
    }

    :deep(.ant-table-body::-webkit-scrollbar) {
      width: 6px;
      height: 6px;
    }

    :deep(.ant-table-body::-webkit-scrollbar-track) {
      background: transparent;
    }

    :deep(.ant-table-body::-webkit-scrollbar-thumb) {
      border-radius: 999px;
      background: rgba(127, 127, 127, 0.45);
    }
  }
  .storage-ellipsis {
    display: inline-block;
    max-width: 100%;
    overflow: hidden;
    text-overflow: ellipsis;
    vertical-align: bottom;
    white-space: nowrap;
  }
  .storage-ellipsis--code {
    font-family: SFMono-Regular, Consolas, 'Liberation Mono', monospace;
    font-size: 12px;
  }
  .runtime-row {
    display: flex;
    align-items: center;
    gap: 8px;
    min-height: 28px;
    overflow-wrap: anywhere;
  }
  .storage-empty {
    color: rgba(127, 127, 127, 0.95);
  }
  .storage-input-number {
    width: 100%;
  }
  :global(.storage-service-action-modal .ant-modal) {
    max-width: calc(100vw - 32px);
    padding-bottom: 0;
  }
  :global(.storage-service-action-modal .ant-modal-content) {
    display: flex;
    flex-direction: column;
    max-height: calc(100vh - 48px);
    overflow: hidden;
  }
  :global(.storage-service-action-modal .ant-modal-header),
  :global(.storage-service-action-modal .ant-modal-footer) {
    flex: 0 0 auto;
  }
  :global(.storage-service-action-modal .ant-modal-body) {
    flex: 1 1 auto;
    min-height: 0;
    overflow: hidden;
  }
  .storage-modal-body {
    max-height: calc(100vh - 172px);
    overflow-y: auto;
    padding: 0 4px 20px 0;
    scrollbar-width: thin;
    scrollbar-color: rgba(127, 127, 127, 0.45) transparent;
  }
  .storage-modal-body::-webkit-scrollbar {
    width: 6px;
    height: 6px;
  }
  .storage-modal-body::-webkit-scrollbar-track {
    background: transparent;
  }
  .storage-modal-body::-webkit-scrollbar-thumb {
    border-radius: 999px;
    background: rgba(127, 127, 127, 0.45);
  }
  .storage-action-form--vertical {
    display: flex;
    flex-direction: column;
    gap: 12px;

    :deep(.tooltip-icon) {
      margin-left: 4px;
      color: #409eff;
    }

    :deep(.ant-divider) {
      margin: 4px 0 8px;
      color: inherit;
      border-color: rgba(127, 127, 127, 0.22);
    }
  }
  .storage-action-section {
    padding: 12px 14px;
    border: 1px solid rgba(127, 127, 127, 0.22);
    border-radius: 6px;
    background: rgba(127, 127, 127, 0.045);
  }
  .storage-action-section__title {
    margin-bottom: 10px;
    color: inherit;
    font-size: 13px;
    font-weight: 600;
    line-height: 1.35;
  }
  .storage-action-checkbox-grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 8px 14px;
  }
  .storage-action-context-alert {
    margin-top: 12px;
    margin-bottom: 0;
  }
  .storage-action-summary-box {
    display: grid;
    margin-bottom: 8px;
    overflow: hidden;
    border: 1px solid rgba(127, 127, 127, 0.18);
    border-radius: 6px;
    background: rgba(127, 127, 127, 0.035);
  }
  .storage-action-summary-row {
    display: grid;
    grid-template-columns: minmax(160px, 0.34fr) minmax(0, 1fr);
    min-height: 38px;

    & + .storage-action-summary-row {
      border-top: 1px solid rgba(127, 127, 127, 0.14);
    }

    span,
    code {
      display: flex;
      align-items: center;
      min-width: 0;
      padding: 8px 12px;
      overflow-wrap: anywhere;
    }

    span {
      color: rgba(127, 127, 127, 0.98);
      font-weight: 600;
      background: rgba(127, 127, 127, 0.045);
    }

    code {
      color: inherit;
      background: transparent;
    }
  }
  .capacity-input-group {
    display: flex;
    width: 100%;

    :deep(.ant-input-number) {
      flex: 1 1 auto;
      width: auto;
    }

    :deep(.ant-select) {
      flex: 0 0 88px;
    }
  }
  .storage-action-row {
    margin-top: 16px;
  }
  .storage-list {
    margin-top: 16px;
  }
  .storage-service-card-grid {
    margin-top: 0;
  }
  .storage-list__item {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    padding: 10px 0;
    border-top: 1px solid rgba(127, 127, 127, 0.18);

    div {
      display: flex;
      flex-direction: column;
      min-width: 0;
    }

    strong,
    span {
      overflow-wrap: anywhere;
    }

    span {
      color: rgba(127, 127, 127, 0.95);
    }
  }
  .runtime-json {
    max-height: 360px;
    margin: 0;
    overflow: auto;
    color: inherit;
    background: transparent;
  }
  :global(body.dark-mode) .storage-data-table {
    color: rgba(229, 236, 246, 0.9);
  }
  :global(body.dark-mode) .storage-data-table :deep(.ant-table-tbody > tr > td),
  :global(body.dark-mode) .storage-data-table :deep(.ant-table-cell-fix-left),
  :global(body.dark-mode) .storage-data-table :deep(.ant-table-cell-fix-right) {
    color: rgba(229, 236, 246, 0.9);
  }
  :global(body.dark-mode) .storage-data-table :deep(.ant-empty-normal),
  :global(body.dark-mode) .storage-data-table :deep(.ant-empty-description) {
    color: rgba(229, 236, 246, 0.72);
  }
  :global(body.dark-mode) {
    .storage-service-tabs {
      :deep(.ant-tabs-nav::before) {
        border-bottom-color: rgba(255, 255, 255, 0.14);
      }

      :deep(.ant-tabs-tab) {
        color: rgba(229, 236, 246, 0.72);
      }

      :deep(.ant-tabs-tab-active .ant-tabs-tab-btn) {
        color: #40a9ff;
      }
    }

    .storage-table-section,
    .storage-panel {
      background: rgba(255, 255, 255, 0.025);
      border-color: rgba(255, 255, 255, 0.14);
    }

    .storage-data-table {
      :deep(.ant-table-thead > tr > th) {
        color: rgba(229, 236, 246, 0.92);
        background: rgba(255, 255, 255, 0.055);
        border-bottom-color: rgba(255, 255, 255, 0.16);
      }

      :deep(.ant-table-tbody > tr > td) {
        color: rgba(229, 236, 246, 0.9);
        border-bottom-color: rgba(255, 255, 255, 0.1);
      }

      :deep(.ant-table-tbody > tr:hover > td) {
        background: rgba(24, 144, 255, 0.12);
      }

      :deep(.ant-table-body) {
        scrollbar-color: rgba(255, 255, 255, 0.24) transparent;
      }

      :deep(.ant-table-body::-webkit-scrollbar-thumb) {
        background: rgba(255, 255, 255, 0.24);
      }

      :deep(.ant-table-cell-fix-left),
      :deep(.ant-table-cell-fix-right) {
        color: rgba(229, 236, 246, 0.9);
        background: #1f272f;
      }

      :deep(.ant-empty-normal),
      :deep(.ant-empty-description) {
        color: rgba(229, 236, 246, 0.72);
      }

      :deep(.ant-empty-img-simple-ellipse),
      :deep(.ant-empty-img-simple-path) {
        fill: rgba(229, 236, 246, 0.18);
      }

      :deep(.ant-empty-img-simple-g) {
        stroke: rgba(229, 236, 246, 0.34);
      }
    }
  }
  :global(body.dark-mode .storage-service-action-modal .storage-action-section) {
    background: rgba(255, 255, 255, 0.025);
    border-color: rgba(255, 255, 255, 0.14);
  }
  :global(body.dark-mode .storage-service-action-modal .storage-action-summary-box) {
    background: rgba(255, 255, 255, 0.025);
    border-color: rgba(255, 255, 255, 0.12);
  }
  :global(body.dark-mode .storage-service-action-modal .storage-action-summary-row + .storage-action-summary-row) {
    border-top-color: rgba(255, 255, 255, 0.1);
  }
  :global(body.dark-mode .storage-service-action-modal .storage-action-summary-row span) {
    color: rgba(229, 236, 246, 0.76);
    background: rgba(255, 255, 255, 0.035);
  }
  :global(body.dark-mode .storage-service-action-modal .storage-action-context-alert) {
    color: rgba(229, 236, 246, 0.92);
    background: rgba(24, 144, 255, 0.12);
    border-color: rgba(24, 144, 255, 0.34);
  }
  :global(body.dark-mode .storage-service-action-modal .storage-action-context-alert .ant-alert-message),
  :global(body.dark-mode .storage-service-action-modal .storage-action-context-alert .ant-alert-icon) {
    color: rgba(229, 236, 246, 0.92);
  }
  :global(body.dark-mode .storage-service-action-modal .storage-action-delete-alert) {
    color: rgba(255, 239, 186, 0.94);
    background: rgba(250, 173, 20, 0.12);
    border-color: rgba(250, 173, 20, 0.34);
  }
  :global(body.dark-mode .storage-service-action-modal .storage-action-delete-alert .ant-alert-message),
  :global(body.dark-mode .storage-service-action-modal .storage-action-delete-alert .ant-alert-icon) {
    color: rgba(255, 239, 186, 0.94);
  }
  :global(body.dark-mode .storage-service-action-modal .ant-input::placeholder),
  :global(body.dark-mode .storage-service-action-modal .ant-select-selection-placeholder) {
    color: rgba(229, 236, 246, 0.36) !important;
  }
  @media (max-width: 991px) {
    .storage-protocol-topbar {
      flex-direction: column;
    }

    .protocol-toolbar--right {
      justify-content: flex-start;
      margin-left: 0;
    }

    .storage-protocol-grid {
      grid-template-columns: 1fr;
    }

    .storage-table-section__header {
      flex-direction: column;
    }

    .storage-section-actions {
      justify-content: flex-start;
    }
  }
  @media (max-width: 640px) {
    .storage-action-checkbox-grid {
      grid-template-columns: 1fr;
    }
  }
</style>
