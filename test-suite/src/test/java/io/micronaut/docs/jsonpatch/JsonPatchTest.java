package io.micronaut.docs.jsonpatch;

import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@Property(name = "spec.name", value = "JsonPatchTest")
@MicronautTest
class JsonPatchTest {
    @Test
    void testJsonPatch(@Client("/") HttpClient httpClient) {
        BlockingHttpClient client = httpClient.toBlocking();
        String expected = "{\"op\":\"replace\",\"path\":\"/baz\",\"value\":\"boo\"}";
        String json = assertDoesNotThrow(() -> client.retrieve(
            HttpRequest.PATCH("/jsonpatch", "{\"op\":\"replace\",\"path\":\"/baz\",\"value\":\"boo\"}")
                .contentType("application/json-patch+json")
                .accept("application/json-patch+json")
        ));
        assertNotNull(json);
        assertEquals(expected, json);
    }

    @Requires(property = "spec.name", value = "JsonPatchTest")
    @Controller("/jsonpatch")
    static class JsonPatchController {
        @Consumes(MediaType.APPLICATION_JSON_PATCH)
        @Produces(MediaType.APPLICATION_JSON_PATCH)
        @Patch
        PatchOperation patch(@Body PatchOperation patch) {
            return patch;
        }
    }

    @Introspected
    record PatchOperation(String op, String path, String value) {
    }
}
