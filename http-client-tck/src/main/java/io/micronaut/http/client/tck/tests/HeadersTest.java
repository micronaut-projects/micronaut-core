/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.http.client.tck.tests;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.tck.ServerUnderTest;
import io.micronaut.http.tck.ServerUnderTestProviderUtils;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class HeadersTest {
    public static final String SPEC_NAME = "HeadersTest";

    @Test
    void testHeaders() throws IOException {
        try (ServerUnderTest server = ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME)) {
            Client client = server.getApplicationContext().getBean(Client.class);

            assertEquals("{\"status\":\"ok\"}", client.getOkAsJson());
            // custom header name with mixed case
            assertEquals("{\"status\":\"okok\"}", client.getFooAsJson("fOO",  "ok"));
            // A different use-case with using @Header(name="..")
            assertEquals("{\"status\":\"okok\"}", client.getFooAsJson2("fOO",  "ok"));
        }
    }

    @Controller("/foo")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class ProduceController implements API {
        @Get(value = "/ok", produces = MediaType.APPLICATION_JSON)
        public String getOkAsJson() {
            return "{\"status\":\"ok\"}";
        }

        @Get(value = "/bar", produces = MediaType.APPLICATION_JSON)
        public  String getFooAsJson(@Header("Foo") String header1, @Header("fOo") String header2) {
            return "{\"status\":\"" + header1 + header2 + "\"}";
        }

        @Get(value = "/bar2", produces = MediaType.APPLICATION_JSON)
        public  String getFooAsJson2(@Header(name = "Foo") String header1, @Header(name = "fOo") String header2) {
            return "{\"status\":\"" + header1 + header2 + "\"}";
        }
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @io.micronaut.http.client.annotation.Client("/foo")
    interface Client extends API {
    }

    interface API {

        @Get(value = "/ok", produces = MediaType.APPLICATION_JSON)
        String getOkAsJson();

        @Get(value = "/bar", produces = MediaType.APPLICATION_JSON)
        String getFooAsJson(@Header("Foo") String header1, @Header("fOo") String header2);

        @Get(value = "/bar2", produces = MediaType.APPLICATION_JSON)
        String getFooAsJson2(@Header(name = "Foo") String header1, @Header(name = "fOo") String header2);
    }

}
