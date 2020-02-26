/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.http.server.netty.java;

import io.micronaut.http.*;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;

import java.util.Optional;

/**
 * @author Graeme Rocher
 * @since 1.0
 */
@Controller("/java/response")
public class ResponseController {

    @Get("/disallow")
    public HttpResponse disallow() {
        return HttpResponse.notAllowed(HttpMethod.DELETE);
    }

    @Get("/accepted")
    public HttpResponse accepted() {
        return HttpResponse.accepted();
    }

    @Get("/accepted-uri")
    public HttpResponse acceptedUri() {
        return HttpResponse.accepted(HttpResponse.uri("http://example.com"));
    }

    @Get("/created-uri")
    public HttpResponse createdUri() {
        return HttpResponse.created(HttpResponse.uri("http://test.com"));
    }

    @Get("/created-body")
    public HttpResponse createdBody() {
        return HttpResponse.created(new Foo("blah", 10));
    }

    @Get("/created-body-uri")
    public HttpResponse createdBodyUri() {
        return HttpResponse.created(new Foo("blah", 10), HttpResponse.uri("http://test.com"));
    }

    @Get("/ok")
    public HttpResponse ok() {
        return HttpResponse.ok();
    }

    @Get(value = "/ok-with-body", produces = MediaType.TEXT_PLAIN)
    public HttpResponse okWithBody() {
        return HttpResponse.ok("some text");
    }

    @Get(value = "/error-with-body", produces = MediaType.TEXT_PLAIN)
    public HttpResponse errorWithBody() {
        return HttpResponse.serverError().body("some text");
    }

    @Get("/ok-with-body-object")
    public HttpResponse<Foo> okWithBodyObject() {
        return HttpResponse.ok(new Foo("blah", 10))
                           .headers((headers)->
                                headers.contentType(MediaType.APPLICATION_JSON_TYPE)
                           );
    }

    @Get("/status")
    public HttpMessage status() {
        return HttpResponse.status(HttpStatus.MOVED_PERMANENTLY);
    }

    @Get("/custom-headers")
    public HttpResponse customHeaders() {
       return HttpResponse.ok("abc").contentType("text/plain").contentLength(7);
    }

    @Get("/optional-response/{empty}")
    public Optional<HttpResponse> optionalResponse(Boolean empty) {
        if (empty) {
            return Optional.empty();
        } else {
            return Optional.of(HttpResponse.ok());
        }
    }
}
