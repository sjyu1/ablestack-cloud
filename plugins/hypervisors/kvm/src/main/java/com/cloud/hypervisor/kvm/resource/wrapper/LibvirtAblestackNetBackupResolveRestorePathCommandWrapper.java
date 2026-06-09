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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@ResourceWrapper(handles = AblestackNetBackupResolveRestorePathCommand.class)
public class LibvirtAblestackNetBackupResolveRestorePathCommandWrapper extends
        CommandWrapper<AblestackNetBackupResolveRestorePathCommand, Answer, LibvirtComputingResource> {
    private static final Pattern TIMESTAMP_DIR_PATTERN =
            Pattern.compile("^\\d{4}\\.\\d{2}\\.\\d{2}\\.\\d{2}\\.\\d{2}\\.\\d{2}\\.\\d+$");

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

        final long deadlineMillis =
                System.currentTimeMillis() - (Math.max(command.getDiscoveryWindowSeconds(), 1) * 1000L);
        final List<RestoreCandidate> matches = new ArrayList<>();

        for (final String candidatePath : command.getCandidatePaths()) {
            if (StringUtils.isBlank(candidatePath)) {
                continue;
            }
            final Path path = Paths.get(candidatePath);
            if (!isValidRestoreCandidate(path)) {
                continue;
            }
            final long modifiedAt = getCandidateModifiedAt(path);
            if (modifiedAt >= deadlineMillis) {
                matches.add(new RestoreCandidate(path.toAbsolutePath().normalize().toString(), modifiedAt));
            }
        }

        if (matches.isEmpty()) {
            return new BackupAnswer(command, false, String.format(
                    "Unable to resolve restored path for NetBackup backup ID [%s]. "
                            + "No valid candidate path was found within the discovery window.",
                    command.getBackupId()));
        }

        matches.sort(Comparator.comparingLong(RestoreCandidate::getModifiedAt).reversed());
        if (matches.size() > 1 && matches.get(0).getModifiedAt() == matches.get(1).getModifiedAt()) {
            return new BackupAnswer(command, false, String.format(
                    "Ambiguous restored paths detected for NetBackup backup ID [%s]: %s",
                    command.getBackupId(), String.join(", ", toPaths(matches))));
        }

        return new BackupAnswer(command, true, matches.get(0).getPath());
    }

    private List<String> toPaths(final List<RestoreCandidate> candidates) {
        final List<String> paths = new ArrayList<>();
        for (final RestoreCandidate candidate : candidates) {
            paths.add(candidate.getPath());
        }
        return paths;
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

    private long getCandidateModifiedAt(final Path path) {
        long latest = 0L;
        try {
            latest = Files.getLastModifiedTime(path).toMillis();
            try (Stream<Path> children = Files.walk(path)) {
                for (final Path child : (Iterable<Path>) children::iterator) {
                    try {
                        latest = Math.max(latest, Files.getLastModifiedTime(child).toMillis());
                    } catch (IOException ignored) {
                        logger.debug("Failed to read modified time of NetBackup restore artifact [{}].", child);
                    }
                }
            }
        } catch (IOException e) {
            logger.debug("Failed to read modified time of NetBackup restore candidate [{}].", path, e);
        }
        return latest;
    }

    private static final class RestoreCandidate {
        private final String path;
        private final long modifiedAt;

        private RestoreCandidate(final String path, final long modifiedAt) {
            this.path = path;
            this.modifiedAt = modifiedAt;
        }

        private String getPath() {
            return path;
        }

        private long getModifiedAt() {
            return modifiedAt;
        }
    }
}
