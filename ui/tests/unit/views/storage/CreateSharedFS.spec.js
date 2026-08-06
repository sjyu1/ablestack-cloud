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

import CreateSharedFS from '@/views/storage/CreateSharedFS'

const baseContext = customizedIops => ({
  isCustomizedDiskIOps: customizedIops,
  owner: { domainid: 'domain-1', account: 'admin' },
  createSharedFsSize: () => undefined,
  hasFormValue: CreateSharedFS.methods.hasFormValue,
  cleanParams: CreateSharedFS.methods.cleanParams
})

const values = {
  name: 'sharedfs-test',
  description: '',
  zoneid: 'zone-1',
  serviceofferingid: 'service-offering-1',
  diskofferingid: 'disk-offering-1',
  networkid: 'network-1',
  filesystem: 'XFS',
  storageid: 'storage-1'
}

describe('CreateSharedFS request normalization', () => {
  it('omits IOPS for a non-customized IOPS offering', () => {
    const context = baseContext(false)
    const request = CreateSharedFS.methods.buildCreateSharedFsRequest.call(context, {
      ...values,
      miniops: undefined,
      maxiops: undefined
    })

    expect(request.miniops).toBeUndefined()
    expect(request.maxiops).toBeUndefined()
    expect(request.storageid).toBe('storage-1')
  })

  it('includes a complete customized IOPS pair as numbers', () => {
    const context = baseContext(true)
    const request = CreateSharedFS.methods.buildCreateSharedFsRequest.call(context, {
      ...values,
      miniops: '1000',
      maxiops: '2000'
    })

    expect(request.miniops).toBe(1000)
    expect(request.maxiops).toBe(2000)
  })
})
