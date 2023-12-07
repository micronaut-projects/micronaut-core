package io.micronaut.docs.jsonpatch;

import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@Property(name = "spec.name", value = "JsonMergePatchTest")
@MicronautTest
public class JsonMergePatchTest {
    @Test
    void acceptMergePatchJsonContentType(@Client("/") HttpClient client) {
        String expected, body = expected = "{\"f\":\"foo\",\"b\":\"bar\"}";
        String response = assertDoesNotThrow(() -> client.toBlocking().retrieve(
            HttpRequest.PATCH("/json-merge-patch", body)
                .contentType("application/merge-patch+json")
                .accept("application/merge-patch+json")
        ));
        assertNotNull(response);
        assertEquals(expected, response);
    }

    @Requires(property = "spec.name", value = "JsonMergePatchTest")
    @Controller("/json-merge-patch")
    static class JsonMergePatchController {
        @Consumes(MediaType.APPLICATION_JSON_MERGE_PATCH)
        @Produces(MediaType.APPLICATION_JSON_MERGE_PATCH)
        @Patch
        MergePatch mergePatch(@Body MergePatch body) {
            return body;
        }
    }

    @Introspected
    record MergePatch(String f, String b) {
    }
}
