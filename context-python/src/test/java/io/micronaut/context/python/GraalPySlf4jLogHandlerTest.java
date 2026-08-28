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

import java.util.logging.Level;
import java.util.logging.LogRecord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

final class GraalPySlf4jLogHandlerTest {

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
