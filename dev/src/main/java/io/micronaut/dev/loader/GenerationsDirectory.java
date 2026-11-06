/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.dev.loader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The claim of a generations directory by one process: the loader empties its directory when it starts, and a second
 * development process of the project, test mode beside development mode or two of either, given the same directory,
 * would delete the snapshots the first one's generations load their classes from. A process claims its directory with
 * a file lock on {@code <directory>.lock} beside it, held for the life of the JVM; a directory another process holds
 * is left to it, and the next of {@code <directory>-2}, {@code <directory>-3}... is taken instead.
 *
 * @author graemerocher
 * @since 5.3.0
 */
final class GenerationsDirectory {

    private static final Logger LOG = LoggerFactory.getLogger(GenerationsDirectory.class);
    private static final int MAX_CANDIDATES = 32;
    // the directories this JVM holds: a loader created again in the same process, as a relaunch in process does, reuses its own
    private static final Map<Path, FileLock> CLAIMED = new ConcurrentHashMap<>();

    private GenerationsDirectory() {
    }

    /**
     * Claims the directory, or the next one no other process holds.
     *
     * @param requested The directory the manifest names
     * @return The directory claimed
     * @throws UncheckedIOException if no directory can be claimed
     */
    static Path claim(Path requested) {
        Path directory = requested.toAbsolutePath().normalize();
        for (int i = 1; i <= MAX_CANDIDATES; i++) {
            Path candidate = i == 1 ? directory : directory.resolveSibling(directory.getFileName() + "-" + i);
            if (tryClaim(candidate)) {
                if (i > 1) {
                    LOG.info("Another development process of the project uses {}: the generations of this one go to {}", directory, candidate);
                }
                return candidate;
            }
        }
        throw new UncheckedIOException(new IOException("Every generations directory from " + directory + " to -" + MAX_CANDIDATES + " is held by another process"));
    }

    private static synchronized boolean tryClaim(Path candidate) {
        if (CLAIMED.containsKey(candidate)) {
            return true;
        }
        Path lockFile = candidate.resolveSibling(candidate.getFileName() + ".lock");
        try {
            Path parent = lockFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            // the channel of a claimed directory stays open, with its lock, until the JVM exits
            FileChannel channel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE); // NOSONAR java:S2095
            boolean claimed = false;
            try {
                FileLock lock;
                try {
                    lock = channel.tryLock();
                } catch (OverlappingFileLockException | IOException e) {
                    lock = null;
                }
                if (lock != null) {
                    CLAIMED.put(candidate, lock);
                    claimed = true;
                }
                return claimed;
            } finally {
                if (!claimed) {
                    channel.close();
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot lock " + lockFile, e);
        }
    }
}
