package io.micronaut.docs.server.uris;

import io.micronaut.http.uri.UriMatchTemplate;
import io.micronaut.http.uri.UriTemplate;
import static org.junit.Assert.*;
import org.junit.Test;

import java.util.Collections;

public class UriTemplateTest {

    @Test
    public void testUriTemplate() {

        // tag::match[]
        UriMatchTemplate template = UriMatchTemplate.of("/hello/{name}");

        assertTrue(template.match("/hello/John").isPresent()); // <1>
        assertEquals(template.expand(  // <2>
                Collections.singletonMap("name", "John")
        ), "/hello/John");
        // end::match[]
    }
}
