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

import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/**
 * The lock of a project's class outputs, shared by every development process of the project: development mode and
 * test mode run at once, as {@code mnDev} beside {@code mnTest}, both compile the application's sources into the same
 * output, with the same staging, backup and mapping files beside it, and each snapshots what the other writes. A
 * compilation, from reading the output's state to promoting what it produced, holds the lock, and so does every read
 * of the outputs as a whole, a generation's snapshot or the digest of what changed: neither process sees the other's
 * compilation half done.
 *
 * <p>Between processes it is a {@link FileChannel#lock() file lock} on a file beside the output; within one it is a
 * reentrant lock, since the JVM holds a file lock for the process and not for a thread. The file lock is taken by the
 * outermost hold and released with it.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
public final class OutputLock {

    private static final Logger LOG = LoggerFactory.getLogger(OutputLock.class);
    private static final OutputLock NONE = new OutputLock(null);
    // one state per lock file in this JVM, whichever instance holds it
    private static final Map<Path, State> STATES = new ConcurrentHashMap<>();
    private static final AtomicBoolean WAITED = new AtomicBoolean();

    private final @Nullable Path file;

    private OutputLock(@Nullable Path file) {
        this.file = file;
    }

    /**
     * @return A lock that locks nothing, for a runtime that compiles nothing
     */
    public static OutputLock none() {
        return NONE;
    }

    /**
     * The lock of an output.
     *
     * @param file The lock file, created when first locked
     * @return The lock
     */
    public static OutputLock at(Path file) {
        return new OutputLock(file.toAbsolutePath().normalize());
    }

    /**
     * @return The lock file, null for {@link #none()}
     */
    public @Nullable Path file() {
        return file;
    }

    /**
     * Takes the lock, waiting for another process that holds it.
     *
     * @return The hold, to close
     * @throws UncheckedIOException if the lock file cannot be locked
     */
    public Hold lock() {
        Path lockFile = file;
        if (lockFile == null) {
            return () -> { };
        }
        State state = STATES.computeIfAbsent(lockFile, path -> new State());
        state.lock.lock();
        if (state.lock.getHoldCount() == 1) {
            try {
                state.lockFile(lockFile);
            } catch (IOException | RuntimeException e) {
                state.lock.unlock();
                throw e instanceof IOException io ? new UncheckedIOException("Cannot lock " + lockFile, io) : (RuntimeException) e;
            }
        }
        return state::unlock;
    }

    @Override
    public String toString() {
        return "OutputLock[" + file + "]";
    }

    /**
     * A hold of the lock, released when closed.
     */
    @FunctionalInterface
    public interface Hold extends AutoCloseable {
        @Override
        void close();
    }

    /**
     * The lock of one file in this JVM: the threads' lock and, while held, the file lock.
     */
    private static final class State {
        private final ReentrantLock lock = new ReentrantLock();
        private @Nullable FileChannel channel;

        void lockFile(Path lockFile) throws IOException {
            Path parent = lockFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            FileChannel opened = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            try {
                FileLock taken = opened.tryLock();
                if (taken == null) {
                    // said once: with development and test mode both running, one waits for the other at each edit
                    if (WAITED.compareAndSet(false, true)) {
                        LOG.info("Another development process of the project is writing its class output: waiting for it ({})", lockFile);
                    } else {
                        LOG.debug("Waiting for another development process writing {}", lockFile);
                    }
                    opened.lock();
                }
            } catch (IOException | RuntimeException e) {
                opened.close();
                throw e;
            }
            channel = opened;
        }

        void unlock() {
            try {
                if (lock.getHoldCount() == 1) {
                    FileChannel held = channel;
                    channel = null;
                    if (held != null) {
                        // closing the channel releases its lock
                        held.close();
                    }
                }
            } catch (IOException e) {
                LOG.debug("Cannot release the output lock", e);
            } finally {
                lock.unlock();
            }
        }
    }
}
