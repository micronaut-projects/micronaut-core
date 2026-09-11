package io.micronaut.http.server.stack;

import io.netty.buffer.ByteBuf;

import java.util.HashMap;
import java.util.Map;

/**
 * System-property switches shared by the HTTP stack benchmarks.
 */
final class BenchOptions {
    // When running through Gradle rather than the jar, pass -Pjmh.dateHeader=true and
    // -Pjmh.accessLog=true: a -D on the Gradle command line does not reach the forked JMH JVM,
    // and the build forwards those properties as the system properties below.
    /**
     * {@code -Dmicronaut.bench.date-header=true} keeps the {@code Date} response header on. The
     * response then changes once per second, so only its length is verified.
     */
    static final boolean DATE_HEADER = Boolean.getBoolean("micronaut.bench.date-header");
    /**
     * {@code -Dmicronaut.bench.access-log=true} enables the access logger. {@code logback.xml}
     * routes the {@code bench-access-log} logger to a NOP appender, so the log line is built but
     * never written.
     */
    static final boolean ACCESS_LOG = Boolean.getBoolean("micronaut.bench.access-log");

    private BenchOptions() {
    }

    static Map<String, Object> serverProperties(String specName) {
        Map<String, Object> properties = new HashMap<>();
        properties.put("spec.name", specName);
        // with the date header off the response is identical each time
        properties.put("micronaut.server.date-header", DATE_HEADER);
        if (ACCESS_LOG) {
            properties.put("micronaut.server.netty.access-logger.enabled", true);
            properties.put("micronaut.server.netty.access-logger.logger-name", "bench-access-log");
        }
        return properties;
    }

    static void verifyResponse(ByteBuf expected, ByteBuf actual) {
        boolean mismatch = DATE_HEADER ? expected.readableBytes() != actual.readableBytes() : !expected.equals(actual);
        if (mismatch) {
            throw new AssertionError("Response did not match");
        }
    }
}
