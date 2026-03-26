package io.micronaut.core.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        IllegalArgumentException fileError = assertThrows(IllegalArgumentException.class,
            () -> ConnectionString.parse("file://localhost:8080/config.properties"));
        assertTrue(fileError.getMessage().contains("Port is not supported"));

        IllegalArgumentException classpathError = assertThrows(IllegalArgumentException.class,
            () -> ConnectionString.parse("classpath://localhost:8080/config.yml"));
        assertTrue(classpathError.getMessage().contains("Port is not supported"));
    }

    @Test
    void supportsClasspathWildcardProtocol() {
        ConnectionString connectionString = ConnectionString.parse("classpath*://config/shared.yml");

        assertEquals("classpath*", connectionString.getProtocol());
        assertEquals("config/shared.yml", connectionString.getPath());
    }
}
