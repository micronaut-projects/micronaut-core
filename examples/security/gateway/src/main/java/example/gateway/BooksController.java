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
package example.gateway;

import example.books.api.Book;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.client.Client;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import org.reactivestreams.Publisher;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;

/**
 * @author Sergio del Amo
 * @since 1.0
 */
@Controller("/books")
@Singleton
public class BooksController {

    protected final BooksClient booksClient;

    BooksController(BooksClient booksClient) {
        this.booksClient = booksClient;
    }

    @Get("/grails")
    List<Book> grails(@Header String authorization) {
        return booksClient.grails(authorization);
    }

    @Get("/groovy")
    List<Book> groovy(@Header String authorization) {
        return booksClient.groovy(authorization);
    }
}
