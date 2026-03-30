package io.micronaut.context.env;

import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.util.ConnectionString;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

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
    }

    @Test
    void mapsImportersByLowercaseProtocol() {
        Map<String, PropertySourceImporter<?>> importers = DefaultEnvironment.toImporterByKind(List.of(new TestImporter("File")));

        assertEquals(1, importers.size());
        assertEquals("File", importers.get("file").getPropertySourceKind());
    }

    @Test
    void rejectsDuplicateProtocolsIgnoringCase() {
        ConfigurationException e = assertThrows(ConfigurationException.class, PropertySourceImporterRegistryTest::createDuplicateImportersByProtocol);

        assertTrue(e.getMessage().contains("Duplicate property source importer for kind [file]"));
    }

    @Test
    void closesImportersAfterEachConfigurationLoadCycle() {
        DefaultEnvironment environment = new DefaultEnvironment(() -> List.of("test"), () -> List.of(new TrackingImporter()));

        environment.start();
        environment.refreshAndDiff();

        assertEquals(2, TrackingImporter.closedCount.get());
        assertEquals(List.of(1, 2), TrackingImporter.instanceIds);
    }

    private static void createDuplicateImportersByProtocol() {
        DefaultEnvironment.toImporterByKind(List.of(
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
        public String getPropertySourceKind() {
            return protocol;
        }

        @Override
        public ConnectionString newImportDeclaration(ConnectionString connectionString) {
            return connectionString;
        }

        @Override
        public Optional<PropertySource> importPropertySource(ImportContext<ConnectionString> context) {
            return Optional.of(PropertySource.of(protocol + ":test", Map.of("connection", context.importDeclaration().getCanonicalForm())));
        }
    }

    private static final class TrackingImporter implements PropertySourceImporter<ConnectionString> {
        private static final AtomicInteger createdCount = new AtomicInteger();
        private static final AtomicInteger closedCount = new AtomicInteger();
        private static final List<Integer> instanceIds = new ArrayList<>();

        private final int instanceId = createdCount.incrementAndGet();

        @Override
        public String getPropertySourceKind() {
            return "tracking";
        }

        @Override
        public ConnectionString newImportDeclaration(ConnectionString connectionString) {
            return connectionString;
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
