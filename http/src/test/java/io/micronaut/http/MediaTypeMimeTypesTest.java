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
package io.micronaut.http;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the loading of {@code META-INF/http/mime.types}. Every case goes through
 * {@link MediaType#loadMimeTypes(ClassLoader)}, which does not touch the static table that
 * {@link MediaType#forExtension(String)} caches, so the cache is left intact for the rest of the suite.
 */
class MediaTypeMimeTypesTest {

    private static final String RESOURCE = "META-INF/http/mime.types";

    @Test
    void loadsTheTableFromTheClassPath() {
        Map<String, String> types = MediaType.loadMimeTypes(MediaTypeMimeTypesTest.class.getClassLoader());

        assertFalse(types.isEmpty());
        assertEquals(MediaType.APPLICATION_JSON, types.get("json"));
        assertEquals(MediaType.TEXT_HTML, types.get("html"));
    }

    @Test
    void returnsAnEmptyTableAndWarnsWhenTheResourceIsMissing() {
        List<ILoggingEvent> events = capturingWarnings(
            () -> assertTrue(MediaType.loadMimeTypes(new ResourceHidingClassLoader()).isEmpty(),
                "A missing mime.types must yield an empty table rather than an NPE")
        );

        assertEquals(1, events.size());
        ILoggingEvent event = events.get(0);
        assertEquals(Level.WARN, event.getLevel());
        assertTrue(event.getFormattedMessage().contains(RESOURCE),
            "The warning must name the resource, was: " + event.getFormattedMessage());
    }

    @Test
    void returnsAnEmptyTableAndWarnsWithTheCauseWhenTheResourceCannotBeRead() {
        List<ILoggingEvent> events = capturingWarnings(
            () -> assertTrue(MediaType.loadMimeTypes(new FailingClassLoader()).isEmpty())
        );

        assertEquals(1, events.size());
        ILoggingEvent event = events.get(0);
        assertEquals(Level.WARN, event.getLevel());
        assertTrue(event.getFormattedMessage().contains(RESOURCE));
        assertTrue(event.getThrowableProxy() != null && event.getThrowableProxy().getMessage().contains("mime.types is unreadable"),
            "The IOException must reach the logger as the cause");
    }

    @Test
    void fallsBackToTheSystemResourcesForTheBootstrapClassLoader() {
        // MediaType.class.getClassLoader() is null under the bootstrap loader, which used to NPE.
        assertFalse(MediaType.loadMimeTypes(null).isEmpty());
    }

    @Test
    void theCachedTableIsUnaffected() {
        Optional<MediaType> json = MediaType.forExtension("json");

        assertTrue(json.isPresent());
        assertEquals(MediaType.APPLICATION_JSON, json.get().getName());
        assertEquals(MediaType.TEXT_PLAIN, MediaType.forFilename("notes.no-such-extension").getName());
    }

    private static List<ILoggingEvent> capturingWarnings(Runnable runnable) {
        var logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(MediaType.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        boolean additive = logger.isAdditive();
        logger.setAdditive(false); // keep the expected warnings out of the build output
        logger.addAppender(appender);
        try {
            runnable.run();
        } finally {
            logger.detachAppender(appender);
            logger.setAdditive(additive);
            appender.stop();
        }
        return appender.list;
    }

    /**
     * A class loader that cannot see the resource, as a shaded or repackaged jar that dropped it.
     */
    private static final class ResourceHidingClassLoader extends ClassLoader {
        ResourceHidingClassLoader() {
            super(null);
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            return null;
        }
    }

    /**
     * A class loader whose stream fails part way through the read.
     */
    private static final class FailingClassLoader extends ClassLoader {
        FailingClassLoader() {
            super(null);
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            return new InputStream() {
                @Override
                public int read() throws IOException {
                    throw new IOException("mime.types is unreadable");
                }
            };
        }
    }
}
