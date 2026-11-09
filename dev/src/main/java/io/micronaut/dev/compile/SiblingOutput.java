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
package io.micronaut.dev.compile;

import io.micronaut.core.annotation.Experimental;
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
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Where the compilation of a sibling's sources goes: the sources of another module of the build, which the application
 * depends on and which development mode compiles with the application's own. The build tool packages the application's
 * class output as it finds it, so nothing of a sibling may be left there; it goes under a directory of its own, which the
 * build tool's package step does not read and whose {@link #classes()} every generation and every later compilation
 * reads ahead of the sibling's own build output.
 *
 * <p>The directory holds what only this session's edits produced: the build tool compiled the siblings before it
 * launched the application, so what a previous session left, ended by whatever means, would shadow newer build output.
 * {@link #prepare()} empties it when a launcher starts, unless another development process of the project is using it, as
 * test mode beside development mode does, or the launcher is the relaunch of one whose generation budget was spent, whose
 * classes no build compiled.</p>
 *
 * @param directory The directory, which the launcher owns
 * @param sources The source directories of the siblings
 * @author graemerocher
 * @since 5.3.0
 */
@Experimental
public record SiblingOutput(Path directory, List<Path> sources) {

    private static final Logger LOG = LoggerFactory.getLogger(SiblingOutput.class);
    /**
     * Longer than a launcher takes to close and be started again, and what is left by a build tool stopped in between is
     * not taken for a relaunch by the next session.
     */
    private static final Duration RELAUNCH_MARK_VALIDITY = Duration.ofMinutes(2);
    /**
     * What this JVM holds: a launcher started again in the same process continues its own session.
     *
     * <p>The map is per class loader. That is safe because of how the launcher is loaded: {@code MicronautDevMain} is the
     * main class of the JVM the build tool starts, so this class is loaded once, by the application class loader, which
     * is never collected, and the held channels stay reachable for the life of the JVM. The generations load the
     * application, never the launcher. Were this class loaded again by a loader that can be collected, its share would be
     * released once that loader went, while a runtime of another loader might still use the directory; a second loader's
     * {@link #claim()} finds the lock held by this JVM and holds no share of its own. A launcher that extends
     * {@code MicronautDevMain} is loaded the same way; and a manifest with Python sources uses no sibling output at all.</p>
     */
    private static final Map<Path, FileLock> CLAIMED = new ConcurrentHashMap<>();

    /**
     * Validating constructor.
     *
     * @param directory The directory, made absolute
     * @param sources The source directories, made absolute
     */
    public SiblingOutput {
        directory = Objects.requireNonNull(directory, "directory").toAbsolutePath().normalize();
        sources = Objects.requireNonNull(sources, "sources").stream().map(source -> source.toAbsolutePath().normalize()).toList();
    }

    /**
     * @return Where the class files and the resources a compiler writes for a sibling go
     */
    public Path classes() {
        return directory.resolve("classes");
    }

    /**
     * Where the sources generated for a sibling go.
     *
     * @param kind The language
     * @return The directory
     */
    public Path generatedSources(SourceKind kind) {
        return directory.resolve("generated-sources-" + kind.name().toLowerCase(Locale.ROOT));
    }

    /**
     * Whether a source belongs to a sibling.
     *
     * @param source The source
     * @return True if it lies under one of the siblings' source directories
     */
    public boolean owns(Path source) {
        Path absolute = source.toAbsolutePath().normalize();
        for (Path root : sources) {
            if (absolute.startsWith(root) || absolute.startsWith(realPath(root))) {
                return true;
            }
        }
        return false;
    }

    /**
     * The compiler names a source it found on its source path by its real path, whichever path the root was given by.
     */
    private static Path realPath(Path directory) {
        try {
            return directory.toRealPath();
        } catch (IOException e) {
            return directory;
        }
    }

    /**
     * Readies the directory for a launcher: emptied unless it continues a session, see {@link SiblingOutput}. The caller
     * holds the {@link OutputLock}, which every development process of the project takes first.
     *
     * @throws UncheckedIOException if the directory cannot be prepared
     */
    public void prepare() {
        try {
            Files.createDirectories(directory);
            boolean relaunched = takeRelaunchMark();
            if (claim() && !relaunched) {
                OutputTransaction.deleteRecursively(directory);
            }
            Files.createDirectories(classes());
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot prepare " + directory, e);
        }
    }

    /**
     * Tells the launcher started next that it continues this one: the classes here are what the next process starts from.
     * Asked of a process that closes for its generation budget.
     */
    public void markRelaunch() {
        try {
            Files.writeString(relaunchMark(), "");
        } catch (IOException e) {
            LOG.warn("Cannot keep {} for the relaunched process: the sibling changes compiled so far are lost until they are edited again", directory, e);
        }
    }

    private boolean takeRelaunchMark() throws IOException {
        Path mark = relaunchMark();
        if (!Files.isRegularFile(mark)) {
            return false;
        }
        FileTime written = Files.getLastModifiedTime(mark);
        Files.delete(mark);
        return written.toInstant().isAfter(Instant.now().minus(RELAUNCH_MARK_VALIDITY));
    }

    private Path relaunchMark() {
        return directory.resolveSibling(directory.getFileName() + ".relaunch");
    }

    /**
     * Takes this process's share of the directory, held for the life of the JVM and released however the process ends.
     *
     * @return True when this process is the first of a session: no other process holds a share, and this one held none
     */
    private boolean claim() throws IOException {
        if (CLAIMED.containsKey(directory)) {
            return false;
        }
        Path lockFile = directory.resolveSibling(directory.getFileName() + ".lock");
        // the channel of a held share stays open, with its lock, until the JVM exits
        FileChannel channel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE); // NOSONAR java:S2095
        boolean first = false;
        try {
            FileLock alone = channel.tryLock();
            if (alone != null) {
                first = true;
                alone.release();
            }
        } catch (OverlappingFileLockException e) {
            LOG.debug("Another runtime of this JVM holds {}", lockFile, e);
        }
        try {
            CLAIMED.put(directory, channel.lock(0, Long.MAX_VALUE, true));
        } catch (OverlappingFileLockException e) {
            // another loader of this JVM shares it: nothing more to hold
            channel.close();
        }
        return first;
    }

    /**
     * Releases this process's share, as the end of the process does.
     */
    void release() throws IOException {
        FileLock lock = CLAIMED.remove(directory);
        if (lock != null) {
            lock.acquiredBy().close();
        }
    }
}
