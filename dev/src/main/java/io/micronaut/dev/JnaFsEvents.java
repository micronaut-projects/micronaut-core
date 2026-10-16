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

import com.sun.jna.Callback;
import com.sun.jna.FunctionMapper;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;
import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;

/**
 * The calls to FSEvents and CoreFoundation, through JNA. Nothing here decides when a call is made: CoreServices is
 * loaded at the first call, on the run loop thread of {@link FsEventStreams}.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
final class JnaFsEvents implements FsEventsApi {

    private static final long SINCE_NOW = -1;
    private static final int CREATE_NO_DEFER = 0x00000002;

    @Override
    public RunLoop currentRunLoop() {
        Pointer mode = NativeLibrary.getInstance("CoreFoundation").getGlobalVariableAddress("kCFRunLoopDefaultMode").getPointer(0);
        return new JnaRunLoop(CoreServices.INSTANCE.cfRunLoopGetCurrent(), mode);
    }

    @Override
    public NativeStream create(String realPath, double latency, FsEventStreams.Events events) throws IOException {
        EventCallback callback = new EventCallback(events);
        Pointer path = CoreServices.INSTANCE.cfStringCreateWithCharacters(null, realPath.toCharArray(), realPath.length());
        if (path == null) {
            throw new IOException("Cannot create the path of " + realPath);
        }
        try {
            Pointer paths = CoreServices.INSTANCE.cfArrayCreate(null, new Pointer[] {path}, 1, null);
            if (paths == null) {
                throw new IOException("Cannot create the paths of " + realPath);
            }
            try {
                Pointer stream = CoreServices.INSTANCE.fsEventStreamCreate(null, callback, null, paths, SINCE_NOW, latency, CREATE_NO_DEFER);
                if (stream == null) {
                    throw new IOException("FSEvents refused to watch " + realPath);
                }
                return new JnaStream(stream, callback);
            } finally {
                // the stream copied the paths
                CoreServices.INSTANCE.cfRelease(paths);
            }
        } finally {
            CoreServices.INSTANCE.cfRelease(path);
        }
    }

    /**
     * A run loop and the mode its streams are scheduled in.
     *
     * @param loop The run loop
     * @param mode The default mode
     */
    private record JnaRunLoop(Pointer loop, Pointer mode) implements RunLoop {
        @Override
        public void run(double seconds) {
            CoreServices.INSTANCE.cfRunLoopRunInMode(mode, seconds, (byte) 0);
        }

        @Override
        public void stop() {
            CoreServices.INSTANCE.cfRunLoopStop(loop);
        }
    }

    /**
     * A stream and the callback the native side calls for it, kept reachable as long as the stream is.
     *
     * @param stream The stream
     * @param callback The callback
     */
    private record JnaStream(Pointer stream, EventCallback callback) implements NativeStream {
        @Override
        public void schedule(RunLoop runLoop) {
            JnaRunLoop loop = (JnaRunLoop) runLoop;
            CoreServices.INSTANCE.fsEventStreamScheduleWithRunLoop(stream, loop.loop(), loop.mode());
        }

        @Override
        public boolean start() {
            return CoreServices.INSTANCE.fsEventStreamStart(stream) != 0;
        }

        @Override
        public void stop() {
            CoreServices.INSTANCE.fsEventStreamStop(stream);
        }

        @Override
        public void invalidate() {
            CoreServices.INSTANCE.fsEventStreamInvalidate(stream);
        }

        @Override
        public void release() {
            CoreServices.INSTANCE.fsEventStreamRelease(stream);
        }
    }

    /**
     * Reads the events FSEvents reports and hands them over. Never throws: an exception thrown into the native side
     * would end the process.
     */
    static final class EventCallback implements Callback {
        private final FsEventStreams.Events events;

        EventCallback(FsEventStreams.Events events) {
            this.events = events;
        }

        /**
         * Called by FSEvents on the run loop thread, with the arguments of an {@code FSEventStreamCallback}.
         *
         * @param stream The stream
         * @param info The stream's context, none
         * @param count How many events there are
         * @param paths The paths of the events, C strings
         * @param flags The flags of the events
         * @param ids The identifiers of the events
         */
        public void invoke(@Nullable Pointer stream, @Nullable Pointer info, long count, Pointer paths, Pointer flags, @Nullable Pointer ids) { // NOSONAR the native callback's signature: JNA passes every argument of FSEventStreamCallback
            try {
                int events = (int) count;
                this.events.report(paths.getStringArray(0, events, "UTF-8"), flags.getIntArray(0, events));
            } catch (RuntimeException | LinkageError e) {
                // the tree is compared as a whole instead
                this.events.rescan();
            }
        }
    }

    /**
     * The functions of CoreServices and CoreFoundation the streams use.
     */
    interface CoreServices extends Library {
        // the Java names start in lower case: cfRelease is CFRelease, fsEventStreamStart FSEventStreamStart
        CoreServices INSTANCE = Native.load("CoreServices", CoreServices.class, Map.of(
            Library.OPTION_FUNCTION_MAPPER, (FunctionMapper) (library, method) -> method.getName().toUpperCase(Locale.ROOT).substring(0, 2) + method.getName().substring(2)));

        @Nullable Pointer cfStringCreateWithCharacters(@Nullable Pointer allocator, char[] chars, long length);

        @Nullable Pointer cfArrayCreate(@Nullable Pointer allocator, Pointer[] values, long count, @Nullable Pointer callbacks);

        void cfRelease(Pointer object);

        @Nullable Pointer fsEventStreamCreate(@Nullable Pointer allocator, Callback callback, @Nullable Pointer context, Pointer paths, long sinceWhen, double latency, int flags);

        void fsEventStreamScheduleWithRunLoop(Pointer stream, Pointer runLoop, Pointer mode);

        byte fsEventStreamStart(Pointer stream);

        void fsEventStreamStop(Pointer stream);

        void fsEventStreamInvalidate(Pointer stream);

        void fsEventStreamRelease(Pointer stream);

        Pointer cfRunLoopGetCurrent();

        int cfRunLoopRunInMode(Pointer mode, double seconds, byte returnAfterSourceHandled);

        void cfRunLoopStop(Pointer runLoop);
    }
}
