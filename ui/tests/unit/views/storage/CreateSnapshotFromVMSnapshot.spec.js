// Licensed to the Apache Software Foundation (ASF) under one or more
// contributor license agreements. See the NOTICE file for copyright ownership.
// Licensed under the Apache License, Version 2.0 (the "License"); you may obtain
// a copy at http://www.apache.org/licenses/LICENSE-2.0 . Distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND.
import { shallowMount } from '@vue/test-utils'
import Dialog from '@/views/storage/CreateSnapshotFromVMSnapshot'
import { getAPI, postAPI } from '@/api'
import { clearSnapshotJobs } from '@/utils/vmSnapshotActions'
jest.mock('@/api', () => ({ getAPI: jest.fn(), postAPI: jest.fn() }))
const target = { id: 'snapshot-b', virtualmachineid: 'vm-b', state: 'Ready', hypervisor: 'KVM', type: 'DiskAndMemory' }
const flush = async () => { for (let i = 0; i < 25; i++) await Promise.resolve() }
function mount () {
  return shallowMount(Dialog, { props: { resource: target }, global: { mocks: { $store: { getters: { apis: { createSnapshotFromVMSnapshot: {} }, userInfo: { id: 'u' }, project: {} }, state: { user: { token: 't' } } }, $route: { path: '/vmsnapshot' }, $t: key => key, $pollJob: jest.fn().mockResolvedValue({ jobstatus: 1 }), $notifyError: jest.fn() } } })
}
beforeEach(() => {
  jest.clearAllMocks(); clearSnapshotJobs()
  getAPI.mockImplementation((api, args) => Promise.resolve(api === 'listVolumes' ? { listvolumesresponse: { volume: [{ id: 'volume-b', virtualmachineid: 'vm-b' }] } } : api === 'listVirtualMachines' ? { listvirtualmachinesresponse: { virtualmachine: [{ id: 'vm-b', state: 'Running' }] } } : { listvmsnapshotresponse: { vmSnapshot: args.state ? [] : [target], count: args.state ? 0 : 1 } }))
  postAPI.mockResolvedValue({ createsnapshotfromvmsnapshotresponse: { jobid: 'job-b' } })
})
test('extraction captures B and duplicate submission sends one job', async () => {
  const w = mount(); await flush(); w.vm.formRef.value = { validate: jest.fn().mockResolvedValue(), scrollToField: jest.fn() }; w.vm.form.name = 'extract'
  await w.setProps({ resource: { id: 'snapshot-a', virtualmachineid: 'vm-a' } })
  await Promise.all([w.vm.handleSubmit(), w.vm.handleSubmit()])
  expect(postAPI).toHaveBeenCalledTimes(1)
  expect(postAPI).toHaveBeenCalledWith('createSnapshotFromVMSnapshot', { name: 'extract', volumeid: 'volume-b', vmsnapshotid: 'snapshot-b' })
  w.unmount()
})
test('a detached volume is rejected before extraction POST', async () => {
  const w = mount(); await flush(); w.vm.formRef.value = { validate: jest.fn().mockResolvedValue(), scrollToField: jest.fn() }; w.vm.form.name = 'extract'
  const original = getAPI.getMockImplementation()
  getAPI.mockImplementation((api, args) => api === 'listVolumes' ? Promise.resolve({ listvolumesresponse: { volume: [] } }) : original(api, args))
  await w.vm.handleSubmit()
  expect(postAPI).not.toHaveBeenCalled()
  expect(w.vm.$notifyError).toHaveBeenCalled()
  w.unmount()
})
