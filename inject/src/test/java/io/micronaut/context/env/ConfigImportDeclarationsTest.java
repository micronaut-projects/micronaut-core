package io.micronaut.context.env;

import io.micronaut.context.exceptions.ConfigurationException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigImportDeclarationsTest {

    private final ConfigImportPropertySourcesLocator locator = new ConfigImportPropertySourcesLocator();

    @Test
    void normalizesIndexedDeclarationsInOrder() {
        PropertySource source = PropertySource.of(
            "application",
            Map.of(
                "micronaut.config.import[0]", "file://foo/one.properties",
                "micronaut.config.import[1]", "file://foo/two.properties"
            )
        );

        ConfigImportPropertySourcesLocator.ResolvedImportDeclarations parsed = locator.normalize(source);

        assertEquals(2, parsed.imports().size());
    }

    @Test
    void supportsListDeclaration() {
        PropertySource source = PropertySource.of(
            "application",
            Map.of("micronaut.config.import", List.of("file://a.properties", "file://b.properties"))
        );

        ConfigImportPropertySourcesLocator.ResolvedImportDeclarations parsed = locator.normalize(source);

        assertEquals(2, parsed.imports().size());
    }

    @Test
    void supportsMapDeclaration() {
        PropertySource source = PropertySource.of(
            "application",
            Map.of("micronaut.config.import", Map.of("provider", "file", "resource-path", "/tmp/demo.properties"))
        );

        ConfigImportPropertySourcesLocator.ResolvedImportDeclarations parsed = locator.normalize(source);

        assertEquals(1, parsed.imports().size());
    }

    @Test
    void supportsIndexedMapDeclarations() {
        PropertySource source = PropertySource.of(
            "application",
            Map.of(
                "micronaut.config.import[0].provider", "file",
                "micronaut.config.import[0].resource-path", "/tmp/one.properties",
                "micronaut.config.import[1].provider", "file",
                "micronaut.config.import[1].resource-path", "/tmp/two.properties"
            )
        );

        ConfigImportPropertySourcesLocator.ResolvedImportDeclarations parsed = locator.normalize(source);

        assertEquals(2, parsed.imports().size());
    }

    @Test
    void rejectsMixedRootAndIndexedDeclarations() {
        PropertySource source = PropertySource.of(
            "application",
            Map.of(
                "micronaut.config.import", "file://a.properties",
                "micronaut.config.import[0]", "file://b.properties"
            )
        );

        ConfigurationException e = assertThrows(ConfigurationException.class, () -> locator.normalize(source));
        assertTrue(e.getMessage().contains("Cannot combine"));
    }

    @Test
    void rejectsSparseIndexes() {
        PropertySource source = PropertySource.of(
            "application",
            Map.of("micronaut.config.import[1]", "file://b.properties")
        );

        ConfigurationException e = assertThrows(ConfigurationException.class, () -> locator.normalize(source));
        assertTrue(e.getMessage().contains("contiguous"));
    }

    @Test
    void rejectsUnsupportedRootValueType() {
        PropertySource source = PropertySource.of(
            "application",
            Map.of("micronaut.config.import", 10)
        );

        ConfigurationException e = assertThrows(ConfigurationException.class, () -> locator.normalize(source));
        assertTrue(e.getMessage().contains("must be a string, map, or list"));
    }

    @Test
    void rejectsMixedScalarAndStructuredIndexedDeclarations() {
        PropertySource source = PropertySource.of(
            "application",
            Map.of(
                "micronaut.config.import[0]", "file://a.properties",
                "micronaut.config.import[1].provider", "file"
            )
        );

        ConfigurationException e = assertThrows(ConfigurationException.class, () -> locator.normalize(source));
        assertTrue(e.getMessage().contains("Cannot combine scalar and structured indexed"));
    }
}
