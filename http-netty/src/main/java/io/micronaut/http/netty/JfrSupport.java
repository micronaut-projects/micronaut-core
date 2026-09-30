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
package io.micronaut.http.netty;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.NativeImageUtils;
import jdk.jfr.FlightRecorder;

/**
 * Guards the initialization of Micronaut's JFR event classes.
 * <p>
 * HotSpot registers a JFR event class with the Flight Recorder when the class is initialized,
 * and the first registration sets up JFR's metadata, even when nothing is recording. Until a
 * Flight Recorder exists no event can be recorded, so the event classes need not be touched.
 * Every way of starting a recording ({@code -XX:StartFlightRecording}, {@code jcmd JFR.start},
 * {@code new Recording()}, {@code RecordingStream}, the MXBean) creates the Flight Recorder
 * first.
 * <p>
 * The event classes are therefore initialized, and their event types registered with JFR, by the
 * first request or carrier task that starts after a Flight Recorder exists. Until then
 * {@link FlightRecorder#getEventTypes()} does not list them, even if requests were served before.
 * A recording that enables an event by name before its type is registered still gets the events,
 * because JFR applies the settings of a recording to an event type when the type is registered.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 5.3.0
 */
@Internal
public final class JfrSupport {
    /**
     * Whether a Flight Recorder was seen. Deliberately a plain field, not {@code volatile} or an
     * {@code AtomicBoolean}: it is read on every request, {@link FlightRecorder#isInitialized()}
     * never reverts, and a thread that reads a stale {@code false} only makes one more call to
     * that method, which is itself a volatile read.
     */
    private static boolean recorderSeen;

    private JfrSupport() {
    }

    /**
     * Whether JFR events may be recorded now. This is {@code false} in a native image, when the
     * {@code jdk.jfr} module is not in the runtime, and while no Flight Recorder exists. It never
     * initializes an event class.
     * <p>
     * Callers check this, and then whether their event is enabled, once when a request or a
     * carrier task starts. A request that is already in flight when a recording enables the
     * event therefore produces no event; the requests that start after it do.
     *
     * @return {@code true} if a Flight Recorder exists, so that JFR event classes may be used
     */
    public static boolean isRecorderInitialized() {
        if (!NativeImageUtils.JFR_AVAILABLE) {
            return false;
        }
        if (recorderSeen) {
            return true;
        }
        if (FlightRecorder.isInitialized()) {
            recorderSeen = true;
            return true;
        }
        return false;
    }
}
