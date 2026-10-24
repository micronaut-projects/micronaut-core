package io.micronaut.context.env;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.DefaultApplicationContextBuilder;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.util.ConnectionString;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PropertySourceImporterRegistryTest {

    @AfterEach
    void resetTrackingImporter() {
        TrackingImporter.reset();
        FailingImporter.closedCount.set(0);
        CheckedFailingImporter.closedCount.set(0);
    }

    @Test
    void mapsImportersByLowercaseProvider() {
        Map<String, PropertySourceImporter<?>> importers = ConfigImportPropertySourcesLocator.toImporterByProvider(List.of(new TestImporter("File")));

        assertEquals(1, importers.size());
        assertEquals("File", importers.get("file").getProvider());
    }

    @Test
    void rejectsDuplicateProvidersIgnoringCase() {
        ConfigurationException e = assertThrows(ConfigurationException.class, PropertySourceImporterRegistryTest::createDuplicateImportersByProvider);

        assertTrue(e.getMessage().contains("Duplicate property source importer for provider [file]"));
    }

    @Test
    void closesImportersAfterEachConfigurationLoadCycle() {
        DefaultApplicationContextBuilder builder = (DefaultApplicationContextBuilder) ApplicationContext.builder();
        builder.propertySourcesLocator(new ConfigImportPropertySourcesLocator(() -> List.of(new TrackingImporter())));
        DefaultEnvironment environment = new DefaultEnvironment(builder);

        environment.start();

        assertEquals(1, TrackingImporter.closedCount.get());
        assertEquals(List.of(1), TrackingImporter.instanceIds);

        environment.refreshAndDiff();

        assertEquals(2, TrackingImporter.closedCount.get());
        assertEquals(List.of(1, 2), TrackingImporter.instanceIds);

        environment.close();

        assertEquals(2, TrackingImporter.closedCount.get());
        assertEquals(TrackingImporter.createdCount.get(), TrackingImporter.closedCount.get());
    }

    @Test
    void closesImportersOfALoadThatFailsAndKeepsTheImportFailure() {
        DefaultApplicationContextBuilder builder = (DefaultApplicationContextBuilder) ApplicationContext.builder();
        builder.propertySourcesLocator(new ConfigImportPropertySourcesLocator(() -> List.of(new FailingImporter())));
        DefaultEnvironment environment = new DefaultEnvironment(builder);
        environment.addPropertySource(PropertySource.of("app", Map.of("micronaut.config.import", "failing://config")));

        IllegalStateException e = assertThrows(IllegalStateException.class, environment::start);

        assertEquals("import failed", e.getMessage());
        assertEquals(1, FailingImporter.closedCount.get());
        assertEquals(1, e.getSuppressed().length);
        assertEquals("close failed", e.getSuppressed()[0].getCause().getMessage());
    }

    @Test
    void closesImportersOfALoadThatFailsWithACheckedException() {
        DefaultApplicationContextBuilder builder = (DefaultApplicationContextBuilder) ApplicationContext.builder();
        builder.propertySourcesLocator(new ConfigImportPropertySourcesLocator(() -> List.of(new CheckedFailingImporter())));
        DefaultEnvironment environment = new DefaultEnvironment(builder);
        environment.addPropertySource(PropertySource.of("app", Map.of("micronaut.config.import", "checked://config")));

        IOException e = assertThrows(IOException.class, environment::start);

        assertEquals("import failed", e.getMessage());
        assertEquals(1, CheckedFailingImporter.closedCount.get());
        assertEquals(1, e.getSuppressed().length);
        assertEquals("close failed", e.getSuppressed()[0].getCause().getMessage());
    }

    private static void createDuplicateImportersByProvider() {
        ConfigImportPropertySourcesLocator.toImporterByProvider(List.of(
            new TestImporter("file"),
            new TestImporter("FILE")
        ));
    }

    private static final class TestImporter implements PropertySourceImporter<ConnectionString> {
        private final String protocol;

        private TestImporter(String protocol) {
            this.protocol = protocol;
        }

        @Override
        public String getProvider() {
            return protocol;
        }

        @Override
        public ConnectionString newImportDeclaration(ConnectionString connectionString) {
            return connectionString;
        }

        @Override
        public ConnectionString newImportDeclaration(io.micronaut.core.convert.value.ConvertibleValues<Object> values) {
            return ConnectionString.parse(getProvider() + "://" + values.get("resource-path", String.class).orElse("missing"));
        }

        @Override
        public Optional<PropertySource> importPropertySource(ImportContext<ConnectionString> context) {
            return Optional.of(PropertySource.of(protocol + ":test", Map.of("connection", context.importDeclaration().getCanonicalForm())));
        }
    }

    private static final class FailingImporter implements PropertySourceImporter<ConnectionString> {
        private static final AtomicInteger closedCount = new AtomicInteger();

        @Override
        public String getProvider() {
            return "failing";
        }

        @Override
        public ConnectionString newImportDeclaration(ConnectionString connectionString) {
            return connectionString;
        }

        @Override
        public ConnectionString newImportDeclaration(io.micronaut.core.convert.value.ConvertibleValues<Object> values) {
            return ConnectionString.parse(getProvider() + "://" + values.get("resource-path", String.class).orElse("missing"));
        }

        @Override
        public Optional<PropertySource> importPropertySource(ImportContext<ConnectionString> context) {
            throw new IllegalStateException("import failed");
        }

        @Override
        public void close() {
            closedCount.incrementAndGet();
            throw new IllegalStateException("close failed");
        }
    }

    /**
     * Throws checked exceptions, as an importer written in Groovy or Kotlin can.
     */
    private static final class CheckedFailingImporter implements PropertySourceImporter<ConnectionString> {
        private static final AtomicInteger closedCount = new AtomicInteger();

        @Override
        public String getProvider() {
            return "checked";
        }

        @Override
        public ConnectionString newImportDeclaration(ConnectionString connectionString) {
            return connectionString;
        }

        @Override
        public ConnectionString newImportDeclaration(io.micronaut.core.convert.value.ConvertibleValues<Object> values) {
            return ConnectionString.parse(getProvider() + "://" + values.get("resource-path", String.class).orElse("missing"));
        }

        @Override
        public Optional<PropertySource> importPropertySource(ImportContext<ConnectionString> context) {
            sneakyThrow(new IOException("import failed"));
            return Optional.empty();
        }

        @Override
        public void close() {
            closedCount.incrementAndGet();
            sneakyThrow(new IOException("close failed"));
        }

        @SuppressWarnings("unchecked")
        private static <E extends Throwable> void sneakyThrow(Throwable e) throws E {
            throw (E) e;
        }
    }

    private static final class TrackingImporter implements PropertySourceImporter<ConnectionString> {
        private static final AtomicInteger createdCount = new AtomicInteger();
        private static final AtomicInteger closedCount = new AtomicInteger();
        private static final List<Integer> instanceIds = new ArrayList<>();

        private final int instanceId = createdCount.incrementAndGet();

        @Override
        public String getProvider() {
            return "tracking";
        }

        @Override
        public ConnectionString newImportDeclaration(ConnectionString connectionString) {
            return connectionString;
        }

        @Override
        public ConnectionString newImportDeclaration(io.micronaut.core.convert.value.ConvertibleValues<Object> values) {
            return ConnectionString.parse(getProvider() + "://" + values.get("resource-path", String.class).orElse("missing"));
        }

        @Override
        public Optional<PropertySource> importPropertySource(ImportContext<ConnectionString> context) {
            return Optional.of(PropertySource.of("tracking:" + instanceId, Map.of("tracking.value", instanceId)));
        }

        @Override
        public void close() {
            closedCount.incrementAndGet();
            instanceIds.add(instanceId);
        }

        private static void reset() {
            createdCount.set(0);
            closedCount.set(0);
            instanceIds.clear();
        }
    }
}
