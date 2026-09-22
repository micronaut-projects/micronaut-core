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
package io.micronaut.context.python;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import java.lang.reflect.Proxy;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

final class GraalPySlf4jLogHandlerTest {

    @Test
    void mapsSlf4jThresholdToPolyglotLevel() {
        assertLoggerLevel("isTraceEnabled", GraalPySlf4jLogHandler.POLYGLOT_LEVEL_FINEST);
        assertLoggerLevel("isDebugEnabled", GraalPySlf4jLogHandler.POLYGLOT_LEVEL_FINE);
        assertLoggerLevel("isInfoEnabled", GraalPySlf4jLogHandler.POLYGLOT_LEVEL_INFO);
        assertLoggerLevel("isWarnEnabled", GraalPySlf4jLogHandler.POLYGLOT_LEVEL_WARNING);
        assertLoggerLevel("isErrorEnabled", GraalPySlf4jLogHandler.POLYGLOT_LEVEL_SEVERE);
        assertLoggerLevel("", GraalPySlf4jLogHandler.POLYGLOT_LEVEL_OFF);
    }

    private static void assertLoggerLevel(String enabledMethod, String expectedLevel) {
        Logger logger = (Logger) Proxy.newProxyInstance(
            Logger.class.getClassLoader(),
            new Class<?>[] {Logger.class},
            (proxy, method, arguments) -> method.getReturnType() == boolean.class && method.getName().equals(enabledMethod)
        );

        assertEquals(expectedLevel, GraalPySlf4jLogHandler.polyglotLevel(logger));
    }

    @Test
    void mapsConfiguredSlf4jLevelToPolyglotLevel() {
        assertEquals(GraalPySlf4jLogHandler.POLYGLOT_LEVEL_ALL, GraalPySlf4jLogHandler.polyglotLevel("ALL"));
        assertEquals(GraalPySlf4jLogHandler.POLYGLOT_LEVEL_FINEST, GraalPySlf4jLogHandler.polyglotLevel("TRACE"));
        assertEquals(GraalPySlf4jLogHandler.POLYGLOT_LEVEL_FINE, GraalPySlf4jLogHandler.polyglotLevel("DEBUG"));
        assertEquals(GraalPySlf4jLogHandler.POLYGLOT_LEVEL_INFO, GraalPySlf4jLogHandler.polyglotLevel("INFO"));
        assertEquals(GraalPySlf4jLogHandler.POLYGLOT_LEVEL_WARNING, GraalPySlf4jLogHandler.polyglotLevel("WARN"));
        assertEquals(GraalPySlf4jLogHandler.POLYGLOT_LEVEL_SEVERE, GraalPySlf4jLogHandler.polyglotLevel("ERROR"));
        assertEquals(GraalPySlf4jLogHandler.POLYGLOT_LEVEL_OFF, GraalPySlf4jLogHandler.polyglotLevel("OFF"));
        assertEquals(GraalPySlf4jLogHandler.POLYGLOT_LEVEL_OFF, GraalPySlf4jLogHandler.polyglotLevel("false"));
    }

    @Test
    void ignoresUnspecifiedConfiguredSlf4jLevel() {
        assertNull(GraalPySlf4jLogHandler.polyglotLevel("NOT_SPECIFIED"));
    }

    @Test
    void formatsParameterizedJulMessages() {
        LogRecord record = new LogRecord(Level.WARNING, "Polyglot warning: {0}");
        record.setParameters(new Object[] {"fallback runtime"});

        assertEquals("Polyglot warning: fallback runtime", GraalPySlf4jLogHandler.message(record));
    }

    @Test
    void usesPolyglotLoggerNameWhenJulRecordHasNoLoggerName() {
        LogRecord record = new LogRecord(Level.WARNING, "warning");

        assertEquals("org.graalvm.polyglot", GraalPySlf4jLogHandler.loggerName(record));
    }

    @Test
    void publishesRecordsToSlf4jWithoutThrowing() {
        LogRecord record = new LogRecord(Level.WARNING, "warning");
        record.setLoggerName("engine");

        assertDoesNotThrow(() -> new GraalPySlf4jLogHandler().publish(record));
    }
}
