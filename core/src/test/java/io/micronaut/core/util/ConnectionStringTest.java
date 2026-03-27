package io.micronaut.core.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectionStringTest {

    @Test
    void parsesOptionalRelativeFileImport() {
        ConnectionString connectionString = ConnectionString.parse("optional:file://foo/bar.properties");

        assertTrue(connectionString.isOptional());
        assertEquals("optional", connectionString.getPrefix().orElse(null));
        assertEquals("file", connectionString.getProtocol());
        assertTrue(connectionString.getHosts().isEmpty());
        assertEquals("foo/bar.properties", connectionString.getPath());
    }

    @Test
    void parsesAbsoluteFileImport() {
        ConnectionString connectionString = ConnectionString.parse("file:///tmp/bar.properties");

        assertFalse(connectionString.isOptional());
        assertEquals("file", connectionString.getProtocol());
        assertEquals("/tmp/bar.properties", connectionString.getPath());
        assertEquals("file:///tmp/bar.properties", connectionString.getCanonicalForm());
        assertEquals("properties", connectionString.getExtension().orElse(null));
        assertEquals("/tmp/bar.properties", connectionString.getResourcePath());
    }

    @Test
    void parsesAuthorityAuthAndOptions() {
        ConnectionString connectionString = ConnectionString.parse("consul://user:pass@localhost:8500/config/app?dc=local");

        assertEquals("consul", connectionString.getProtocol());
        assertEquals("user", connectionString.getUsername().orElse(null));
        assertEquals("pass", connectionString.getPassword().orElse(null));
        assertEquals(1, connectionString.getHosts().size());
        assertEquals("localhost", connectionString.getHosts().get(0).host());
        assertEquals(8500, connectionString.getHosts().get(0).port());
        assertEquals("config/app", connectionString.getPath());
        assertEquals("local", connectionString.getOptions().get("dc"));
    }

    @Test
    void rejectsInvalidProtocol() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> ConnectionString.parse("1file://foo/bar.properties"));
        assertTrue(e.getMessage().contains("Protocol"));
    }

    @Test
    void rejectsEmptyPath() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> ConnectionString.parse("consul://localhost:8500/"));
        assertTrue(e.getMessage().contains("path"));
    }

    @Test
    void rejectsDuplicateOptions() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> ConnectionString.parse("consul://localhost:8500/config/app?dc=local&dc=remote"));
        assertTrue(e.getMessage().contains("Duplicate option 'dc'"));
    }

    @Test
    void rejectsMalformedOption() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> ConnectionString.parse("consul://localhost:8500/config/app?dc"));
        assertTrue(e.getMessage().contains("Malformed option"));
    }

    @Test
    void supportsHostModeWithOptionalPath() {
        ConnectionString connectionString = ConnectionString.parse("consul://localhost:8500", ConnectionString.ParseMode.HOST);

        assertEquals(ConnectionString.ParseMode.HOST, connectionString.getParseMode());
        assertEquals("consul", connectionString.getProtocol());
        assertEquals("localhost", connectionString.getHosts().get(0).host());
        assertEquals(8500, connectionString.getHosts().get(0).port());
        assertEquals("", connectionString.getPath());
        assertEquals("consul://localhost:8500", connectionString.getCanonicalForm());
    }

    @Test
    void rejectsMissingHostInHostMode() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> ConnectionString.parse("consul:///config", ConnectionString.ParseMode.HOST));
        assertTrue(e.getMessage().contains("host is required"));
    }

    @Test
    void rejectsPortsForFileAndClasspathProtocols() {
        IllegalArgumentException fileError = assertThrows(IllegalArgumentException.class, ConnectionStringTest::parseFileWithPort);
        assertTrue(fileError.getMessage().contains("Port is not supported"));

        IllegalArgumentException classpathError = assertThrows(IllegalArgumentException.class, ConnectionStringTest::parseClasspathWithPort);
        assertTrue(classpathError.getMessage().contains("Port is not supported"));
    }

    @Test
    void supportsClasspathWildcardProtocol() {
        ConnectionString connectionString = ConnectionString.parse("classpath*://config/shared.yml");

        assertEquals("classpath*", connectionString.getProtocol());
        assertEquals("config/shared.yml", connectionString.getPath());
    }

    @Test
    void supportsLocalhostAuthorityWithoutPort() {
        ConnectionString withPath = ConnectionString.parse("consul://localhost/config/app");

        assertEquals("consul", withPath.getProtocol());
        assertEquals(1, withPath.getHosts().size());
        assertEquals("localhost", withPath.getHosts().get(0).host());
        assertNull(withPath.getHosts().get(0).port());
        assertEquals("config/app", withPath.getPath());

        ConnectionString withoutPath = ConnectionString.parse("consul://localhost", ConnectionString.ParseMode.HOST);

        assertEquals("consul", withoutPath.getProtocol());
        assertEquals(1, withoutPath.getHosts().size());
        assertEquals("localhost", withoutPath.getHosts().get(0).host());
        assertNull(withoutPath.getHosts().get(0).port());
        assertEquals("", withoutPath.getPath());
    }

    @Test
    void rejectsRelativeParentTraversalSegments() {
        IllegalArgumentException relativeError = assertThrows(IllegalArgumentException.class, ConnectionStringTest::canonicalizeRelativeTraversal);
        assertTrue(relativeError.getMessage().contains("Parent path segments are not allowed"));

        IllegalArgumentException nestedError = assertThrows(IllegalArgumentException.class, ConnectionStringTest::canonicalizeNestedTraversal);
        assertTrue(nestedError.getMessage().contains("Parent path segments are not allowed"));
    }

    @Test
    void toStringRendersOriginalUri() {
        ConnectionString connectionString = ConnectionString.parse("optional:file://foo/bar.properties");

        assertEquals("optional:file://foo/bar.properties", connectionString.toString());
    }

    @Test
    void equalsAndHashCodeUseSemanticComponents() {
        ConnectionString first = ConnectionString.parse("file://foo/bar.properties");
        ConnectionString second = ConnectionString.parse("file://foo/bar.properties");
        ConnectionString different = ConnectionString.parse("optional:file://foo/bar.properties");

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertNotEquals(first, different);
    }

    @Test
    void canonicalFormSortsOptionsByKey() {
        ConnectionString aFirst = ConnectionString.parse("consul://localhost:8500/config?a=1&b=2");
        ConnectionString bFirst = ConnectionString.parse("consul://localhost:8500/config?b=2&a=1");

        assertEquals(aFirst.getCanonicalForm(), bFirst.getCanonicalForm());
        assertEquals(aFirst, bFirst);
        assertEquals(aFirst.hashCode(), bFirst.hashCode());
    }

    private static void parseFileWithPort() {
        ConnectionString.parse("file://localhost:8080/config.properties");
    }

    private static void parseClasspathWithPort() {
        ConnectionString.parse("classpath://localhost:8080/config.yml");
    }

    private static void canonicalizeRelativeTraversal() {
        ConnectionString connectionString = ConnectionString.parse("file://../secrets.yml");
        connectionString.getCanonicalPath();
    }

    private static void canonicalizeNestedTraversal() {
        ConnectionString connectionString = ConnectionString.parse("classpath://config/../../secrets.yml");
        connectionString.getCanonicalPath();
    }
}
