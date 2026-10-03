/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.http;

import io.micronaut.http.annotation.Produces;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MediaTypeFromTypeTest {

    @Test
    void classWithProducesResolvesTheFirstValue() {
        Optional<MediaType> first = MediaType.fromType(Xml.class);
        Optional<MediaType> second = MediaType.fromType(Xml.class);

        assertEquals(Optional.of(MediaType.APPLICATION_XML_TYPE), first);
        assertEquals(first, second);
    }

    @Test
    void classWithDefaultProducesResolvesJson() {
        assertEquals(Optional.of(MediaType.APPLICATION_JSON_TYPE), MediaType.fromType(DefaultProduces.class));
    }

    @Test
    void producesIsInherited() {
        assertEquals(Optional.of(MediaType.APPLICATION_XML_TYPE), MediaType.fromType(XmlChild.class));
    }

    @Test
    void producesWithParametersIsParsed() {
        MediaType mediaType = MediaType.fromType(WithCharset.class).orElseThrow();

        assertEquals("text/plain", mediaType.getName());
        assertEquals("UTF-8", mediaType.getCharset().orElseThrow().name());
        assertEquals(mediaType, MediaType.fromType(WithCharset.class).orElseThrow());
    }

    @Test
    void classWithoutProducesOrWithEmptyValueResolvesNothing() {
        assertTrue(MediaType.fromType(Plain.class).isEmpty());
        assertTrue(MediaType.fromType(Plain.class).isEmpty());
        assertTrue(MediaType.fromType(EmptyProduces.class).isEmpty());
        assertTrue(MediaType.fromType(String.class).isEmpty());
        assertTrue(MediaType.fromType(int.class).isEmpty());
    }

    @Produces(MediaType.APPLICATION_XML)
    static class Xml {
    }

    static class XmlChild extends Xml {
    }

    @Produces
    static class DefaultProduces {
    }

    @Produces("text/plain;charset=UTF-8")
    static class WithCharset {
    }

    @Produces({})
    static class EmptyProduces {
    }

    static class Plain {
    }
}
