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
package io.micronaut.discovery.config;

import io.micronaut.context.env.Environment;
import io.micronaut.context.env.PropertySource;
import io.micronaut.context.env.PropertySourceImporter;
import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.core.io.ResourceLoader;
import io.micronaut.core.util.ConnectionString;
import io.micronaut.retry.RetryOperations;
import io.micronaut.retry.RetryOperationsFactory;
import io.micronaut.retry.RetryPolicy;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetryablePropertySourceImporterTest {

    @Test
    void readsRetryPolicyFromConnectionStringUsingStandardProperties() {
        RecordingRetryOperationsFactory retryOperationsFactory = new RecordingRetryOperationsFactory();
        TestRetryableImporter importer = new TestRetryableImporter(retryOperationsFactory);

        RetryablePropertySourceImporter.RetryableImportDeclaration<String> declaration = importer.newImportDeclaration(
            ConnectionString.parse("test://localhost/config?retry-count=4&retry-attempts=5&retry-delay=250ms&retry-max-delay=3s&retry-multiplier=1.5&retry-jitter=0.2")
        );

        assertEquals("config", declaration.declaration());
        assertEquals(5, declaration.retryPolicy().maxAttempts());
        assertEquals(Duration.ofMillis(250), declaration.retryPolicy().delay());
        assertEquals(Optional.of(Duration.ofSeconds(3)), declaration.retryPolicy().getMaxDelay());
        assertEquals(1.5d, declaration.retryPolicy().multiplier());
        assertEquals(0.2d, declaration.retryPolicy().jitter());
    }

    @Test
    void readsRetryPolicyFromStructuredValuesUsingStandardProperties() {
        RecordingRetryOperationsFactory retryOperationsFactory = new RecordingRetryOperationsFactory();
        TestRetryableImporter importer = new TestRetryableImporter(retryOperationsFactory);

        RetryablePropertySourceImporter.RetryableImportDeclaration<String> declaration = importer.newImportDeclaration(
            ConvertibleValues.of(Map.of(
                "value", "structured",
                "retry-attempts", 6,
                "retry-delay", "100ms",
                "retry-max-delay", "2s",
                "retry-multiplier", "2.0",
                "retry-jitter", "0.1"
            ))
        );

        assertEquals("structured", declaration.declaration());
        assertEquals(6, declaration.retryPolicy().maxAttempts());
        assertEquals(Duration.ofMillis(100), declaration.retryPolicy().delay());
        assertEquals(Optional.of(Duration.ofSeconds(2)), declaration.retryPolicy().getMaxDelay());
        assertEquals(2.0d, declaration.retryPolicy().multiplier());
        assertEquals(0.1d, declaration.retryPolicy().jitter());
    }

    @Test
    void importUsesConfiguredRetryPolicy() {
        RecordingRetryOperationsFactory retryOperationsFactory = new RecordingRetryOperationsFactory();
        TestRetryableImporter importer = new TestRetryableImporter(retryOperationsFactory);
        RetryablePropertySourceImporter.RetryableImportDeclaration<String> declaration = importer.newImportDeclaration(
            ConnectionString.parse("test://localhost/config?retry-attempts=4&retry-delay=10ms")
        );

        Optional<PropertySource> propertySource = importer.importPropertySource(new TestImportContext(declaration));

        assertEquals(Optional.of(4), Optional.ofNullable(retryOperationsFactory.lastPolicy).map(RetryPolicy::maxAttempts));
        assertEquals(Duration.ofMillis(10), retryOperationsFactory.lastPolicy.delay());
        assertEquals(Optional.of("test:config"), propertySource.map(PropertySource::getName));
        assertEquals(1, importer.importCalls.get());
    }

    @Test
    void rejectsInvalidRetrySetting() {
        RecordingRetryOperationsFactory retryOperationsFactory = new RecordingRetryOperationsFactory();
        TestRetryableImporter importer = new TestRetryableImporter(retryOperationsFactory);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () ->
            importer.newImportDeclaration(ConnectionString.parse("test://localhost/config?retry-jitter=2.0"))
        );

        assertEquals("jitter must be between 0 and 1", error.getMessage());
    }

    @Test
    void defaultConstructorCreatesOwnExecutorAndClosesIt() throws Exception {
        DefaultConstructorImporter importer = new DefaultConstructorImporter();

        ScheduledExecutorService executorService = (ScheduledExecutorService) readField(importer, "executorService");
        RetryOperationsFactory retryOperationsFactory = (RetryOperationsFactory) readField(importer, "retryOperationsFactory");

        assertNotNull(executorService);
        assertNotNull(retryOperationsFactory);
        assertFalse(executorService.isShutdown());

        importer.close();

        assertTrue(importer.closed.get());
        assertTrue(executorService.isShutdown());
    }

    private static Object readField(Object instance, String name) throws Exception {
        Field field = RetryablePropertySourceImporter.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(instance);
    }

    private static final class TestRetryableImporter extends RetryablePropertySourceImporter<String> {
        private final AtomicInteger importCalls = new AtomicInteger();

        private TestRetryableImporter(RetryOperationsFactory retryOperationsFactory) {
            super(retryOperationsFactory);
        }

        @Override
        public String getProvider() {
            return "test";
        }

        @Override
        protected String newImportDeclaration(ConnectionString connectionString, RetryPolicy retryPolicy) {
            return connectionString.getPath();
        }

        @Override
        protected String newImportDeclaration(ConvertibleValues<Object> values, RetryPolicy retryPolicy) {
            return values.get("value", String.class).orElse("missing");
        }

        @Override
        protected Optional<PropertySource> importRetryablePropertySource(PropertySourceImporter.ImportContext<String> context) {
            importCalls.incrementAndGet();
            return Optional.of(PropertySource.of("test:" + context.importDeclaration(), Map.of("value", context.importDeclaration())));
        }
    }

    private static final class DefaultConstructorImporter extends RetryablePropertySourceImporter<String> {
        private final AtomicBoolean closed = new AtomicBoolean();

        @Override
        public String getProvider() {
            return "default";
        }

        @Override
        protected String newImportDeclaration(ConnectionString connectionString, RetryPolicy retryPolicy) {
            return connectionString.getPath();
        }

        @Override
        protected String newImportDeclaration(ConvertibleValues<Object> values, RetryPolicy retryPolicy) {
            return values.get("value", String.class).orElse("missing");
        }

        @Override
        protected Optional<PropertySource> importRetryablePropertySource(PropertySourceImporter.ImportContext<String> context) {
            return Optional.empty();
        }

        @Override
        protected void closeRetryableImporter() {
            closed.set(true);
        }
    }

    private static final class RecordingRetryOperationsFactory implements RetryOperationsFactory {
        private RetryPolicy lastPolicy;

        @Override
        public RetryOperations createRetryOperations(RetryPolicy retryPolicy) {
            lastPolicy = retryPolicy;
            return new RetryOperations() {
                @Override
                public <T> T execute(java.util.function.Supplier<T> supplier) {
                    return supplier.get();
                }

                @Override
                public <T> java.util.concurrent.CompletionStage<T> executeCompletionStage(java.util.function.Supplier<? extends java.util.concurrent.CompletionStage<T>> supplier) {
                    return supplier.get();
                }

                @Override
                public <T> org.reactivestreams.Publisher<T> executePublisher(java.util.function.Supplier<? extends org.reactivestreams.Publisher<T>> supplier) {
                    return supplier.get();
                }
            };
        }
    }

    private record TestImportContext(RetryablePropertySourceImporter.RetryableImportDeclaration<String> importDeclaration) implements PropertySourceImporter.ImportContext<RetryablePropertySourceImporter.RetryableImportDeclaration<String>> {
        @Override
        public Environment environment() {
            throw new UnsupportedOperationException();
        }

        @Override
        public @Nullable ConnectionString connectionString() {
            return ConnectionString.parse("test://localhost/config");
        }

        @Override
        public PropertySource.Origin parentOrigin() {
            return null;
        }

        @Override
        public Optional<PropertySource> importPropertySource(ResourceLoader resourceLoader, String resourcePath, String sourceName, PropertySource.Origin origin) {
            return Optional.empty();
        }

        @Override
        public Optional<PropertySource> importPropertySource(String content, String sourceName, String extension, PropertySource.Origin origin) {
            return Optional.empty();
        }

        @Override
        public Optional<PropertySource> importClasspathPropertySource(String resourcePath, String sourceName, PropertySource.Origin origin, boolean allowMultiple) {
            return Optional.empty();
        }
    }
}
