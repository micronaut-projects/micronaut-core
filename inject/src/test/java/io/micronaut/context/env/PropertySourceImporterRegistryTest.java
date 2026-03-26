package io.micronaut.context.env;

import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.util.ConnectionString;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PropertySourceImporterRegistryTest {

    @Test
    void mapsImportersByLowercaseProtocol() {
        Map<String, PropertySourceImporter> importers = DefaultEnvironment.toImporterByProtocol(List.of(new TestImporter("File")));

        assertEquals(1, importers.size());
        assertEquals("File", importers.get("file").getProtocol());
    }

    @Test
    void rejectsDuplicateProtocolsIgnoringCase() {
        ConfigurationException e = assertThrows(ConfigurationException.class,
            () -> DefaultEnvironment.toImporterByProtocol(List.of(
                new TestImporter("file"),
                new TestImporter("FILE")
            ))
        );

        assertTrue(e.getMessage().contains("Duplicate property source importer for protocol [file]"));
    }

    private static final class TestImporter implements PropertySourceImporter {
        private final String protocol;

        private TestImporter(String protocol) {
            this.protocol = protocol;
        }

        @Override
        public String getProtocol() {
            return protocol;
        }

        @Override
        public Optional<PropertySource> importPropertySource(ImportContext context) {
            return Optional.of(PropertySource.of(protocol + ":test", Map.of("connection", ConnectionString.parse("file://foo/bar.properties").getCanonicalForm())));
        }
    }
}
