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
package io.micronaut.dev;

import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.ref.Cleaner;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.Path;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * The native macOS watch service of the development runtime: FSEvents tells which directories changed, and the
 * directories are compared with their previous state as {@link PollingWatchService} compares them, right away rather
 * than at its interval. The service of {@code micronaut-runtime-osx}'s library this replaces let the garbage collector
 * free a stream's callback while FSEvents could still call it, and released a stream from another thread, or from its
 * own callback, while its run loop ran: either crashed the JVM.
 *
 * <p>The directories are also compared at a long interval, and at the polling service's own once FSEvents could not
 * watch a directory. A service dropped
 * without being closed is closed once it is unreachable: its run loop would otherwise hold its streams, which FSEvents
 * allows a process a limited number of, until the process ends.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
final class FsEventsWatchService implements WatchService {

    /**
     * How often every directory is compared whether or not FSEvents reported a change.
     */
    static final Duration SAFETY_INTERVAL = Duration.ofSeconds(5);

    private static final Cleaner CLEANER = Cleaner.create();
    private static final Logger LOG = LoggerFactory.getLogger(FsEventsWatchService.class);

    private final PollingWatchService polling;
    private final FsEventStreams streams;
    private final Cleaner.Cleanable cleanable;
    private final Duration fallbackInterval;

    /**
     * Starts the service.
     *
     * @param latency How long FSEvents holds the events that follow a first one, in seconds
     * @param fallbackInterval How often the directories are compared once FSEvents could not watch one of them
     * @throws IOException if FSEvents cannot be used
     */
    FsEventsWatchService(double latency, Duration fallbackInterval) throws IOException {
        this(new JnaFsEvents(), latency, fallbackInterval);
    }

    /**
     * Starts the service.
     *
     * @param api The calls to FSEvents
     * @param latency How long FSEvents holds the events that follow a first one, in seconds
     * @param fallbackInterval How often the directories are compared once FSEvents could not watch one of them
     * @throws IOException if FSEvents cannot be used
     */
    FsEventsWatchService(FsEventsApi api, double latency, Duration fallbackInterval) throws IOException {
        this.fallbackInterval = fallbackInterval;
        // locals: the run loop thread holds what the streams hold, which must not reach this service, or the cleaner
        // would never close one dropped unclosed
        PollingWatchService comparisons = new PollingWatchService(SAFETY_INTERVAL);
        this.polling = comparisons;
        try {
            this.streams = new FsEventStreams(api, latency, comparisons::compareUnder, () -> fallBack(comparisons, fallbackInterval, "The FSEvents run loop ended"));
        } catch (IOException | RuntimeException | LinkageError e) {
            comparisons.close();
            throw e;
        }
        cleanable = CLEANER.register(this, new Release(streams, polling));
    }

    /**
     * Registers a directory: the tree under it is watched with FSEvents unless a tree watched already holds it.
     *
     * @param directory The directory
     * @return The key
     * @throws IOException if the directory cannot be read or watched
     */
    WatchKey register(Path directory) throws IOException {
        // the directory is read first, then the stream starts, then the directory is compared once, before this
        // returns: a change made before the stream reports changes, which no event announces, is found by that
        // comparison rather than lost in the first state
        Path absolute = directory.toAbsolutePath().normalize();
        WatchKey key = polling.register(absolute);
        try {
            streams.watch(absolute);
        } catch (ClosedWatchServiceException e) {
            throw e;
        } catch (IOException e) {
            fallBack(polling, fallbackInterval, String.valueOf(e.getMessage()));
        }
        polling.compareNow(key);
        return key;
    }

    /**
     * @param latency How long FSEvents holds the events that follow a first one
     * @return How long a write may take to be reported: the latency, or the interval once FSEvents could not watch a
     * directory
     */
    Duration detectionDelay(Duration latency) {
        return polling.isPollingEvery(fallbackInterval) ? fallbackInterval : latency;
    }

    private static void fallBack(PollingWatchService polling, Duration fallbackInterval, String why) {
        if (!polling.isPollingEvery(fallbackInterval)) {
            LOG.warn("{}: comparing the watched directories every {} ms instead", why, fallbackInterval.toMillis());
            polling.pollEvery(fallbackInterval);
        }
    }

    @Override
    public void close() {
        cleanable.clean();
    }

    @Override
    public @Nullable WatchKey poll() {
        return polling.poll();
    }

    @Override
    public @Nullable WatchKey poll(long timeout, TimeUnit unit) throws InterruptedException {
        return polling.poll(timeout, unit);
    }

    @Override
    public WatchKey take() throws InterruptedException {
        return polling.take();
    }

    /**
     * Closes the streams, then the comparisons, of a service closed or unreachable. Holds no reference to the service.
     *
     * @param streams The streams
     * @param polling The comparisons
     */
    private record Release(FsEventStreams streams, PollingWatchService polling) implements Runnable {
        @Override
        public void run() {
            streams.close();
            polling.close();
        }
    }
}
