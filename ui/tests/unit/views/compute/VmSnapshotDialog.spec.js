// Licensed to the Apache Software Foundation (ASF) under one or more
// contributor license agreements. See the NOTICE file for copyright ownership.
// Licensed under the Apache License, Version 2.0 (the "License"); you may obtain
// a copy at http://www.apache.org/licenses/LICENSE-2.0 . Distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND.
import { shallowMount } from '@vue/test-utils'
import VmSnapshotDialog from '@/views/compute/VmSnapshotDialog'
import { getAPI, postAPI } from '@/api'
import { clearSnapshotJobs } from '@/utils/vmSnapshotActions'
jest.mock('@/api', () => ({ getAPI: jest.fn(), postAPI: jest.fn() }))
jest.mock('@/config/section/compute', () => ({ children: [{ name: 'vm', actions: [{ api: 'createVMSnapshot', show: () => true, disabled: () => false }] }] }))
const row = { id: 'b', virtualmachineid: 'vb', displayname: 'B', type: 'Disk', state: 'Ready', virtualmachinestate: 'Stopped' }
const flush = async () => { for (let i = 0; i < 25; i++) await Promise.resolve() }
let vmState
let poll
function mount (mode = 'delete', targets = [Object.freeze({ ...row })]) {
  return shallowMount(VmSnapshotDialog, {
    props: { resource: row, currentAction: { snapshotMode: mode, snapshotTargets: Object.freeze(targets) } },
    global: { mocks: { $store: { getters: { apis: { listVMSnapshot: {}, listVirtualMachines: {}, revertToVMSnapshot: {}, deleteVMSnapshot: {} }, userInfo: { id: 'user' }, project: {} }, state: { user: { token: 'token' } } }, $route: { path: '/vmsnapshot', fullPath: '/vmsnapshot' }, $t: key => key, $toLocaleDate: value => value, $notifyError: jest.fn(), $pollJob: poll } }
  })
}
beforeEach(() => {
  jest.clearAllMocks(); clearSnapshotJobs(); vmState = 'Stopped'
  poll = jest.fn().mockResolvedValue({ jobstatus: 1 })
  getAPI.mockImplementation((api, args) => Promise.resolve(api === 'listVirtualMachines' ? { listvirtualmachinesresponse: { virtualmachine: [{ id: 'vb', state: vmState }] } } : { listvmsnapshotresponse: { vmSnapshot: args.state ? [] : [row], count: args.state ? 0 : 1 } }))
  postAPI.mockResolvedValue({ deletevmsnapshotresponse: { jobid: 'job-b' }, reverttovmsnapshotresponse: { jobid: 'job-b' } })
})

test('captured B remains the submitted UUID after resource refresh and requires acknowledgement', async () => {
  const wrapper = mount(); await flush()
  await wrapper.vm.submit()
  expect(postAPI).not.toHaveBeenCalled()
  await wrapper.setProps({ resource: { ...row, id: 'a', virtualmachineid: 'va' } })
  wrapper.vm.acknowledged = true
  await wrapper.vm.submit()
  expect(postAPI).toHaveBeenCalledWith('deleteVMSnapshot', { vmsnapshotid: 'b' })
  expect(wrapper.vm.results[0].id).toBe('b')
  wrapper.unmount()
})

test('VM state changed after confirmation blocks Disk restore before POST', async () => {
  const wrapper = mount('restore'); await flush()
  expect(wrapper.vm.blockedReason).toBe('')
  vmState = 'Running'; wrapper.vm.acknowledged = true
  await wrapper.vm.submit()
  expect(wrapper.vm.blockedReason).toBe('message.vmsnapshot.stop.first')
  expect(postAPI).not.toHaveBeenCalled()
  wrapper.unmount()
})

test('double submit sends one job and unknown result stays distinct from success', async () => {
  poll.mockResolvedValue({ jobstatus: null, trackingStatus: 'unknown' })
  const wrapper = mount(); await flush(); wrapper.vm.acknowledged = true
  await Promise.all([wrapper.vm.submit(), wrapper.vm.submit()])
  expect(postAPI).toHaveBeenCalledTimes(1)
  expect(wrapper.vm.results[0].outcome).toBe('unknown')
  wrapper.unmount()
})

test('ACL withdrawal at fresh lookup prevents every captured target from starting', async () => {
  const wrapper = mount(); await flush(); wrapper.vm.acknowledged = true
  getAPI.mockResolvedValue({ listvmsnapshotresponse: { count: 0 }, listvirtualmachinesresponse: {} })
  await wrapper.vm.submit()
  expect(wrapper.vm.blockedReason).toBe('message.vmsnapshot.not.ready')
  expect(postAPI).not.toHaveBeenCalled()
  wrapper.unmount()
})


test('project changes during the last fresh lookup prevent POST', async () => {
  const wrapper = mount(); await flush()
  const security = wrapper.vm.security
  const original = getAPI.getMockImplementation()
  getAPI.mockImplementation((api, args) => {
    if (api === 'listVirtualMachines') wrapper.vm.$store.getters.project.id = 'different-project'
    return original(api, args)
  })
  await expect(wrapper.vm.execute(row, security)).rejects.toThrow('message.vmsnapshot.permission')
  expect(postAPI).not.toHaveBeenCalled()
  wrapper.unmount()
})
