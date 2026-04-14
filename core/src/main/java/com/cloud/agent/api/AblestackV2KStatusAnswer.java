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
package com.cloud.agent.api;

public class AblestackV2KStatusAnswer extends Answer {

    private String phase;
    private String migrationState;
    private String migrationStep;
    private String syncPhysical;
    private String workdir;

    public AblestackV2KStatusAnswer(Command command, boolean success, String details) {
        super(command, success, details);
    }

    public AblestackV2KStatusAnswer(Command command, boolean success, String details,
                                    String phase, String migrationState, String migrationStep,
                                    String syncPhysical, String workdir) {
        super(command, success, details);
        this.phase = phase;
        this.migrationState = migrationState;
        this.migrationStep = migrationStep;
        this.syncPhysical = syncPhysical;
        this.workdir = workdir;
    }

    public String getPhase() {
        return phase;
    }

    public String getMigrationState() {
        return migrationState;
    }

    public String getMigrationStep() {
        return migrationStep;
    }

    public String getSyncPhysical() {
        return syncPhysical;
    }

    public String getWorkdir() {
        return workdir;
    }
}
