package io.micronaut.core.io.service;

import static org.junit.jupiter.api.Assumptions.abort;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jdk.jfr.FlightRecorder;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedThread;
import jdk.jfr.consumer.RecordingFile;

/**
 * Records with JFR the errors that a thread throws.
 *
 * <p>JUnit reads the methods of every class of the test classes directory when it looks for tests, and that fails
 * for a class with a JFR type in a method signature or a field when the JVM has no {@code jdk.jfr} module: no test
 * of the module would run there. So the JFR types are only used for local variables here, without a lambda or a
 * helper method that takes or returns one, and the test classes do not use them at all. A caller checks
 * {@link #isSupported()} before it records.</p>
 */
final class JfrErrorRecorder {

    private JfrErrorRecorder() {
    }

    /**
     * @return Whether this JVM has the {@code jdk.jfr} module
     */
    static boolean isSupported() {
        return ModuleLayer.boot().findModule("jdk.jfr").isPresent();
    }

    /**
     * Runs the action and returns the errors that the calling thread threw while it ran. JFR records the errors of
     * every thread of the JVM, so the ones thrown by code that runs next to the test are left out.
     *
     * <p>The test is aborted, not failed, when JFR cannot record in this JVM, or when the recording does not have
     * the errors of the calling thread.</p>
     *
     * @param file   The file to write the recording to
     * @param action The action to run on the calling thread
     * @return The errors, each as the name of its class followed by its message
     * @throws IOException When the action throws it
     */
    static List<String> errorsThrownByCurrentThread(Path file, Action action) throws IOException {
        assumeTrue(FlightRecorder.isAvailable(), "JFR is not available in this JVM");
        long threadId = Thread.currentThread().threadId();
        Recording recording;
        try {
            recording = new Recording();
        } catch (RuntimeException e) {
            // for example when the JFR repository cannot be created
            return abort("JFR cannot record in this JVM: " + e);
        }
        String probe;
        List<RecordedEvent> events;
        try (recording) {
            try {
                recording.enable("jdk.JavaErrorThrow");
                recording.start();
            } catch (RuntimeException e) {
                return abort("JFR cannot record in this JVM: " + e);
            }
            action.run();
            // JFR records an error when it is constructed: this one tells whether the errors of this thread
            // are recorded
            probe = new Error("probe " + UUID.randomUUID()).toString();
            try {
                recording.stop();
                recording.dump(file);
                events = RecordingFile.readAllEvents(file);
            } catch (IOException e) {
                return abort("JFR cannot write the recording in this JVM: " + e);
            }
        }
        List<String> errors = new ArrayList<>();
        for (RecordedEvent event : events) {
            RecordedThread thread = event.getThread();
            if (thread != null && thread.getJavaThreadId() == threadId) {
                String message = event.getString("message");
                errors.add(event.getClass("thrownClass").getName() + ": " + (message == null ? "" : message));
            }
        }
        assumeTrue(errors.contains(probe), "JFR did not record the errors of this thread");
        return errors;
    }

    /**
     * The code whose errors are recorded.
     */
    @FunctionalInterface
    interface Action {
        void run() throws IOException;
    }
}
