package io.micronaut.context.env;

import io.micronaut.context.exceptions.ConfigurationException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConfigImportDeclarationsTest {

    private final ConfigImportResolver resolver = new ConfigImportResolver(new DefaultEnvironment(() -> List.of("test")));

    @Test
    void normalizesScalarDeclarationAndRemovesMetadata() {
        PropertySource source = PropertySource.of(
            "application",
            Map.of(
                "micronaut.config.import", "file://foo/bar.properties",
                "app.name", "demo"
            ),
            PropertySource.PropertyConvention.ENVIRONMENT_VARIABLE,
            PropertySource.Origin.of("classpath:application.yml")
        );

        ConfigImportResolver.ResolvedImportDeclarations parsed = resolver.normalize(source);

        assertEquals(1, parsed.imports().size());
        assertEquals("file://foo/bar.properties", parsed.imports().get(0).getCanonicalForm());
        assertEquals("demo", parsed.propertySource().get("app.name"));
        assertEquals(null, parsed.propertySource().get("micronaut.config.import"));
        assertEquals(source.getConvention(), parsed.propertySource().getConvention());
        assertEquals(source.getOrder(), parsed.propertySource().getOrder());
        assertEquals(source.getOrigin().location(), parsed.propertySource().getOrigin().location());
    }

    @Test
    void normalizesIndexedDeclarationsInOrder() {
        PropertySource source = PropertySource.of(
            "application",
            Map.of(
                "micronaut.config.import[0]", "file://foo/one.properties",
                "micronaut.config.import[1]", "file://foo/two.properties"
            )
        );

        ConfigImportResolver.ResolvedImportDeclarations parsed = resolver.normalize(source);

        assertEquals(2, parsed.imports().size());
        assertEquals("file://foo/one.properties", parsed.imports().get(0).getCanonicalForm());
        assertEquals("file://foo/two.properties", parsed.imports().get(1).getCanonicalForm());
    }

    @Test
    void supportsListDeclaration() {
        PropertySource source = PropertySource.of(
            "application",
            Map.of("micronaut.config.import", List.of("file://a.properties", "file://b.properties"))
        );

        ConfigImportResolver.ResolvedImportDeclarations parsed = resolver.normalize(source);

        assertEquals(2, parsed.imports().size());
        assertEquals("file://a.properties", parsed.imports().get(0).getCanonicalForm());
        assertEquals("file://b.properties", parsed.imports().get(1).getCanonicalForm());
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

        ConfigurationException e = assertThrows(ConfigurationException.class, () -> resolver.normalize(source));
        assertTrue(e.getMessage().contains("Cannot combine"));
    }

    @Test
    void rejectsSparseIndexes() {
        PropertySource source = PropertySource.of(
            "application",
            Map.of("micronaut.config.import[1]", "file://b.properties")
        );

        ConfigurationException e = assertThrows(ConfigurationException.class, () -> resolver.normalize(source));
        assertTrue(e.getMessage().contains("contiguous"));
    }

    @Test
    void rejectsUnsupportedRootValueType() {
        PropertySource source = PropertySource.of(
            "application",
            Map.of("micronaut.config.import", 10)
        );

        ConfigurationException e = assertThrows(ConfigurationException.class, () -> resolver.normalize(source));
        assertTrue(e.getMessage().contains("must be a string or list"));
    }
}
