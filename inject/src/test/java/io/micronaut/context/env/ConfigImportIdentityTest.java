package io.micronaut.context.env;

import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.util.ConnectionString;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConfigImportIdentityTest {


    @Test
    void extractsResourcePathFromCanonicalLocation() {
        assertEquals("/tmp/app.yml", ConfigImportIdentity.extractResourcePath("file:/tmp/app.yml"));
        assertEquals("config/app.yml", ConfigImportIdentity.extractResourcePath("classpath:config/app.yml"));
    }

    @Test
    void canonicalizesFileAliasesWithinSameTier() {
        ConnectionString one = ConnectionString.parse("file://foo/../foo/app.properties");
        ConnectionString two = ConnectionString.parse("optional:file://foo/app.properties");

        ConfigImportIdentity.ImportIdentity first = ConfigImportIdentity.identity(one, PropertySource.Origin.of("file:/tmp/application.properties"), -250);
        ConfigImportIdentity.ImportIdentity second = ConfigImportIdentity.identity(two, PropertySource.Origin.of("file:/tmp/application.properties"), -250);

        assertEquals(first.canonicalLocation(), second.canonicalLocation());
        assertEquals(first.tierOrder(), second.tierOrder());
    }

    @Test
    void preservesTierDifferenceForSameCanonicalLocation() {
        ConnectionString target = ConnectionString.parse("file://foo/app.properties");

        ConfigImportIdentity.ImportIdentity low = ConfigImportIdentity.identity(target, PropertySource.Origin.of("file:/tmp/application.properties"), -300);
        ConfigImportIdentity.ImportIdentity high = ConfigImportIdentity.identity(target, PropertySource.Origin.of("file:/tmp/application.properties"), -250);

        assertEquals(low.canonicalLocation(), high.canonicalLocation());
        assertNotEquals(low.tierOrder(), high.tierOrder());
    }

    @Test
    void canonicalizesClasspathRelativeLocation() {
        ConnectionString target = ConnectionString.parse("classpath://config/extra.yml");

        String canonical = ConfigImportIdentity.canonicalLocation(target, PropertySource.Origin.of("classpath:app/application.yml"));

        assertEquals("classpath:app/config/extra.yml", canonical);
    }

    @Test
    void rejectsAbsoluteClasspathImports() {
        ConnectionString target = ConnectionString.parse("classpath:///config/extra.yml");

        assertThrows(ConfigurationException.class, () -> canonicalizeAbsoluteClasspathImport(target));
    }

    @Test
    void canonicalizesClasspathWildcardRelativeLocation() {
        ConnectionString target = ConnectionString.parse("classpath*://config/extra.yml");

        String canonical = ConfigImportIdentity.canonicalLocation(target, PropertySource.Origin.of("classpath:app/application.yml"));

        assertEquals("classpath*:app/config/extra.yml", canonical);
    }

    private static void canonicalizeAbsoluteClasspathImport(ConnectionString target) {
        ConfigImportIdentity.canonicalLocation(target, PropertySource.Origin.of("classpath:app/application.yml"));
    }
}
