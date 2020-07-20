/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.docs.server.uris;

import io.micronaut.http.uri.UriMatchTemplate;
import static org.junit.Assert.*;
import org.junit.Test;

import java.util.Collections;

public class UriTemplateTest {

    @Test
    public void testUriTemplate() {

        // tag::match[]
        UriMatchTemplate template = UriMatchTemplate.of("/hello/{name}");

        assertTrue(template.match("/hello/John").isPresent()); // <1>
        assertEquals("/hello/John", template.expand(  // <2>
                Collections.singletonMap("name", "John")
        ));
        // end::match[]
    }
}
