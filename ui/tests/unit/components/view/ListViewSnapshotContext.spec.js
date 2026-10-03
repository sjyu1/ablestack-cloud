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
import ListView from '@/components/view/ListView'
jest.mock('@/api', () => ({ getAPI: jest.fn().mockResolvedValue({}) }))

test.each([['a'], ['a', 'c']])('checked IDs %j do not change the right clicked B target', (...checked) => {
  const rows = [{ id: 'a' }, { id: 'b' }, { id: 'c' }]
  const element = document.createElement('tr')
  element.classList.add('ant-table-row'); element.setAttribute('data-row-key', 'b')
  const vm = { $route: { name: 'vmsnapshot' }, items: rows, actions: [{}], selectedRowKeys: checked.flat(), quickViewEnabled: () => true, generateRowKeyValue: row => row.id, contextMenuActions: [{}], closeContextQuickView: jest.fn() }
  ListView.methods.handleGlobalContextMenu.call(vm, { target: element, clientX: 20, clientY: 40, preventDefault: jest.fn(), stopPropagation: jest.fn() })
  expect(vm.contextQuickViewRecord.id).toBe('b')
  expect(Object.isFrozen(vm.contextQuickViewRecord)).toBe(true)
  rows[1].id = 'replaced'
  expect(vm.contextQuickViewRecord.id).toBe('b')
  const parent = { execAction: jest.fn() }
  ListView.methods.handleContextAction.call({ ...vm, $parent: parent }, { groupAction: true, resource: vm.contextQuickViewRecord })
  expect(parent.execAction.mock.calls[0][1]).toBe(false)
})
