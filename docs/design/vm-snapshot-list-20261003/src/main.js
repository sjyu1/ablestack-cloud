/* Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file for details.
 * Licensed under the Apache License, Version 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0
 */
import { createApp, h } from 'vue'
import Antd from 'ant-design-vue'
import { ReloadOutlined } from '@ant-design/icons-vue'
import 'ant-design-vue/dist/antd.css'
import './mold-theme.less'
import '@/style/theme/tokens.less'
import '@/style/theme/base.less'
import '@/style/theme/components.less'
import '@/style/theme/feedback.less'
import '@/style/theme/scrollbar.less'
import App from './App.vue'

const app = createApp(App)
app.use(Antd)
app.component('RenderIcon', {
  props: ['icon', 'props'],
  render () {
    return this.icon === 'reload-outlined' ? h(ReloadOutlined, this.props) : null
  }
})
const labels = { 'state.ready': '사용 가능', 'state.running': '실행 중', 'state.stopped': '정지됨', 'state.error': '오류', Ready: '사용 가능', Creating: '생성 중', Reverting: '복원 중', Expunging: '삭제 중', Error: '오류' }
app.config.globalProperties.$t = key => labels[key] || key
app.directive('clipboard', {})
app.mount('#app')
