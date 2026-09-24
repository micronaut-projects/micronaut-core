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
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 5.2.6
 */
@Internal
public final class JfrSupport {
    /**
     * Whether a Flight Recorder was seen. Not volatile: {@link FlightRecorder#isInitialized()}
     * never reverts, and a stale read only costs one more call to it.
     */
    private static boolean recorderSeen;

    private JfrSupport() {
    }

    /**
     * Whether JFR events may be recorded now. This is {@code false} in a native image, when the
     * {@code jdk.jfr} module is not in the runtime, and while no Flight Recorder exists. It never
     * initializes an event class.
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
