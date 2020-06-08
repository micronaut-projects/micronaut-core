package io.micronaut.docs.client.filter;

import io.micronaut.context.env.Environment;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.client.RxHttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientException;
import io.micronaut.test.annotation.MicronautTest;
import junit.framework.TestCase;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import javax.inject.Inject;

import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest(environments = Environment.GOOGLE_COMPUTE)
public class GoogleAuthFilterSpec {

    @Inject
    @Client("/")
    RxHttpClient client;

    @Test
    void testApplyGoogleAuthFilter() {
        HttpClientException e = Assertions.assertThrows(HttpClientException.class, () ->
                client.exchange("/google-auth/api/test").blockingFirst()
        );
        String message = e.getMessage();
        assertTrue(
                message.contains("metadata: nodename nor servname provided") ||
                        message.contains("metadata: Temporary failure in name resolution") ||
                        message.contains("metadata: Name or service not known")
        );

    }
}
