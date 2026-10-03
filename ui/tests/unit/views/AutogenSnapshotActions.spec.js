// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements. See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership. The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License. You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied. See the License for the
// specific language governing permissions and limitations
// under the License.
import AutogenView from '@/views/AutogenView'
import { getAPI, postAPI } from '@/api'
import { clearSnapshotJobs } from '@/utils/vmSnapshotActions'
jest.mock('@/api', () => ({ getAPI: jest.fn(), postAPI: jest.fn(), callAPI: jest.fn() }))
const action = { api: 'createVMSnapshot', resource: Object.freeze({ id: 'vm-b' }), show: vm => vm.state === 'Running', disabled: vm => !!vm.vmsnapshotblockedreason }
let context
beforeEach(() => {
  jest.clearAllMocks(); clearSnapshotJobs()
  context = { $store: { getters: { apis: { createVMSnapshot: {} }, userInfo: { id: 'user' }, project: {} }, state: { user: { token: 'token' } } }, $t: key => key }
  getAPI.mockImplementation((api) => Promise.resolve(api === 'listVirtualMachines' ? { listvirtualmachinesresponse: { virtualmachine: [{ id: 'vm-b', state: 'Running' }] } } : { listvmsnapshotresponse: { vmSnapshot: [], count: 0 } }))
  postAPI.mockResolvedValue({ createvmsnapshotresponse: { jobid: 'job' } })
})
test('create submits the frozen VM UUID after preflight even if mutable form mapping changed', async () => {
  await AutogenView.methods.postSnapshotAwareAction.call(context, action, { virtualmachineid: 'vm-a', name: 'snapshot' })
  expect(postAPI).toHaveBeenCalledWith('createVMSnapshot', { virtualmachineid: 'vm-b', name: 'snapshot' })
})
test('fresh VM eligibility changed before create blocks POST', async () => {
  getAPI.mockImplementation((api) => Promise.resolve(api === 'listVirtualMachines' ? { listvirtualmachinesresponse: { virtualmachine: [{ id: 'vm-b', state: 'Starting' }] } } : { listvmsnapshotresponse: { vmSnapshot: [], count: 0 } }))
  await expect(AutogenView.methods.postSnapshotAwareAction.call(context, action, {})).rejects.toThrow('message.vmsnapshot.vm.state')
  expect(postAPI).not.toHaveBeenCalled()
})
test('project changes during create preflight prevent POST', async () => {
  const original = getAPI.getMockImplementation()
  getAPI.mockImplementation((api, args) => { if (api === 'listVirtualMachines') context.$store.getters.project.id = 'other'; return original(api, args) })
  await expect(AutogenView.methods.postSnapshotAwareAction.call(context, action, {})).rejects.toThrow('message.vmsnapshot.permission')
  expect(postAPI).not.toHaveBeenCalled()
})
