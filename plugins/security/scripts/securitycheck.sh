#!/bin/bash
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.

# # mysql 서비스 확인
# systemctl status mysqld | grep -i running &> /dev/null
# if [[ $? == 0 ]]; then
#     echo "mysql,true"
# else
#     echo "mysql,false"
# fi

# # firewalld 서비스 확인
# systemctl status firewalld | grep -i running &> /dev/null
# if [[ $? == 0 ]]; then
#     echo "firewalld,true"
# else
#     echo "firewalld,false"
# fi

# # cloudstack-management 서비스 확인
# systemctl status cloudstack-management | grep -i running &> /dev/null
# if [[ $? == 0 ]]; then
#     echo "management,true"
# else
#     echo "management,false"
# fi

# 테스트 코드
echo "mysql,true"
echo "firewalld,true"
echo "management,false"