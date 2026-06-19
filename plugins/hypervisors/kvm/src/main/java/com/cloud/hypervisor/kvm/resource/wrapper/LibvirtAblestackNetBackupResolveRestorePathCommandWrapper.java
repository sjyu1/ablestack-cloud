//
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
//

package com.cloud.hypervisor.kvm.resource.wrapper;

import com.cloud.agent.api.Answer;
import com.cloud.hypervisor.kvm.resource.LibvirtComputingResource;
import com.cloud.resource.CommandWrapper;
import com.cloud.resource.ResourceWrapper;
import org.apache.cloudstack.backup.AblestackNetBackupResolveRestorePathCommand;
import org.apache.cloudstack.backup.BackupAnswer;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@ResourceWrapper(handles = AblestackNetBackupResolveRestorePathCommand.class)
public class LibvirtAblestackNetBackupResolveRestorePathCommandWrapper extends
        CommandWrapper<AblestackNetBackupResolveRestorePathCommand, Answer, LibvirtComputingResource> {
    private static final Pattern TIMESTAMP_DIR_PATTERN =
            Pattern.compile("^\\d{4}\\.\\d{2}\\.\\d{2}\\.\\d{2}\\.\\d{2}\\.\\d{2}\\.\\d+$");
    private static final long RESTORE_PATH_DISCOVERY_INTERVAL_MS = 5000L;

    @Override
    public Answer execute(final AblestackNetBackupResolveRestorePathCommand command,
            final LibvirtComputingResource serverResource) {
        if (StringUtils.isBlank(command.getBackupId())) {
            return new BackupAnswer(command, false, "NetBackup backup ID is required to resolve restored path.");
        }
        if (CollectionUtils.isEmpty(command.getCandidatePaths())) {
            return new BackupAnswer(command, false, String.format(
                    "No candidate restore paths were provided for NetBackup backup ID [%s].", command.getBackupId()));
        }

        final long deadline = System.currentTimeMillis() + Math.max(0, command.getDiscoveryWindowSeconds()) * 1000L;
        logger.info("Resolving NetBackup restore path on KVM. backupId=[{}], candidatePaths={}, requiredFiles={}, discoveryWindowSeconds=[{}]",
                command.getBackupId(), command.getCandidatePaths(), command.getRequiredFiles(), command.getDiscoveryWindowSeconds());
        do {
            if (CollectionUtils.isNotEmpty(command.getRequiredFiles())) {
                if (areRequiredRestoreSourcesReady(command.getCandidatePaths(), command.getRequiredFiles())) {
                    logger.info("Required NetBackup restore files are ready for backupId [{}]: paths={}, files={}",
                            command.getBackupId(), command.getCandidatePaths(), command.getRequiredFiles());
                    return new BackupAnswer(command, true, StringUtils.join(command.getCandidatePaths(), ","));
                }
            } else {
                for (final String candidatePath : command.getCandidatePaths()) {
                    if (StringUtils.isBlank(candidatePath)) {
                        continue;
                    }
                    final Path path = Paths.get(candidatePath);
                    if (!isValidRestoreCandidate(path)) {
                        continue;
                    }
                    logger.info("Selected NetBackup restore path for backupId [{}]: [{}]", command.getBackupId(), path);
                    return new BackupAnswer(command, true, path.toAbsolutePath().normalize().toString());
                }
            }

            if (System.currentTimeMillis() >= deadline) {
                break;
            }
            if (!sleepBeforeNextDiscoveryAttempt(command.getBackupId())) {
                break;
            }
        } while (true);

        final String failureDetail = CollectionUtils.isNotEmpty(command.getRequiredFiles())
                ? String.format("Required restore paths or files were not found. paths=%s, files=%s",
                        command.getCandidatePaths(), command.getRequiredFiles())
                : "No candidate restore path contained the required files.";
        return new BackupAnswer(command, false, String.format(
                "Unable to resolve restored path for NetBackup backup ID [%s]. %s",
                command.getBackupId(), failureDetail));
    }

    private boolean areRequiredRestoreSourcesReady(final List<String> candidatePaths, final List<String> requiredFiles) {
        if (CollectionUtils.isEmpty(candidatePaths) || CollectionUtils.isEmpty(requiredFiles)) {
            return false;
        }
        for (final String candidatePath : candidatePaths) {
            if (StringUtils.isBlank(candidatePath) || !Files.isDirectory(Paths.get(candidatePath))) {
                return false;
            }
        }
        for (final String requiredFile : requiredFiles) {
            if (StringUtils.isBlank(requiredFile) || !Files.isRegularFile(Paths.get(requiredFile))) {
                return false;
            }
        }
        return true;
    }

    private boolean sleepBeforeNextDiscoveryAttempt(final String backupId) {
        try {
            Thread.sleep(RESTORE_PATH_DISCOVERY_INTERVAL_MS);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Interrupted while waiting for NetBackup restore path discovery. backupId=[{}]", backupId);
            return false;
        }
    }

    private boolean isValidRestoreCandidate(final Path path) {
        if (path == null || !Files.isDirectory(path)
                || !TIMESTAMP_DIR_PATTERN.matcher(path.getFileName().toString()).matches()) {
            return false;
        }
        if (!Files.isRegularFile(path.resolve("domain-config.xml"))) {
            return false;
        }
        if (!Files.isRegularFile(path.resolve("domblklist.xml"))) {
            return false;
        }
        try (Stream<Path> children = Files.list(path)) {
            return children.anyMatch(child -> Files.isRegularFile(child) && isDiskArtifact(child.getFileName().toString()));
        } catch (IOException e) {
            logger.debug("Failed to inspect NetBackup restore candidate path [{}].", path, e);
            return false;
        }
    }

    private boolean isDiskArtifact(final String fileName) {
        return StringUtils.startsWith(fileName, "root.")
                || StringUtils.startsWith(fileName, "datadisk.")
                || StringUtils.endsWith(fileName, ".qcow2")
                || StringUtils.endsWith(fileName, ".raw")
                || StringUtils.endsWith(fileName, ".rbdiff");
    }
}
