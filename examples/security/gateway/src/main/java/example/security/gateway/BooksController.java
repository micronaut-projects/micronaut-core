/*
 * Copyright 2017 original authors
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
package example.security.gateway;

import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.client.Client;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import org.reactivestreams.Publisher;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * @author Sergio del Amo
 * @since 1.0
 */
@Controller("/books")
@Singleton
public class BooksController {

    @Client(id = "books")
    @Inject
    HttpClient client;

    @Get("/grails")
    public Publisher grails(@Header String authorization) {
        return fetchPath("/books/grails", authorization);
    }

    @Get("/groovy")
    public Publisher groovy(@Header String authorization) {
        return fetchPath("/books/groovy", authorization);
    }

    protected Publisher fetchPath(String path, String authorization) {
        HttpRequest request = HttpRequest.create(HttpMethod.GET, path)
                .header("Authorization", authorization);
        return client.retrieve(request);
    }

}
