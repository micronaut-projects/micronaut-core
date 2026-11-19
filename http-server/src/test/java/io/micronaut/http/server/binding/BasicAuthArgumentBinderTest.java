package io.micronaut.http.server.binding;

import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.type.Argument;
import io.micronaut.http.BasicAuth;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Java4Shell (CVE-2026-114938): the {@code Authorization} header bound to a {@link BasicAuth} argument is
 * decoded through {@code Base64Utils}, whose message distinguishes its rejection from the JDK decoder's.
 */
class BasicAuthArgumentBinderTest {

    private static final String GUARD_MESSAGE = "Invalid Base64 padding";

    private final BasicAuthArgumentBinder binder = new BasicAuthArgumentBinder();

    @ParameterizedTest
    @ValueSource(strings = {"=", "==", "A=", "A==", "AB=", "==="})
    void credentialsWithJava4ShellTailAreRejectedByTheGuard(String tail) {
        assertRejectedByTheGuard("A".repeat(64) + tail);
        assertRejectedByTheGuard("A".repeat(128) + tail);
    }

    @Test
    void validCredentialsAreBound() {
        // user:password
        Optional<BasicAuth> auth = bind("dXNlcjpwYXNzd29yZA==").getValue();
        assertTrue(auth.isPresent());
        assertEquals("user", auth.get().getUsername());
        assertEquals("password", auth.get().getPassword());
    }

    private void assertRejectedByTheGuard(String credentials) {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> bind(credentials));
        assertEquals(GUARD_MESSAGE, e.getMessage());
    }

    private io.micronaut.core.bind.ArgumentBinder.BindingResult<BasicAuth> bind(String credentials) {
        HttpRequest<?> request = HttpRequest.GET("/").header(HttpHeaders.AUTHORIZATION, "Basic " + credentials);
        return binder.bind(ConversionContext.of(Argument.of(BasicAuth.class)), request);
    }
}
