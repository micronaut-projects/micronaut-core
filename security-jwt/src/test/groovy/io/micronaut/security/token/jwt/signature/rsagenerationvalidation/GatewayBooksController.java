/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.security.token.jwt.signature.rsagenerationvalidation;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Header;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;

import java.util.List;

@Requires(property = "spec.name", value = "rsajwtgateway")
@Controller("/books")
@Secured(SecurityRule.IS_AUTHENTICATED)
public class GatewayBooksController {

    private final BooksClient booksClient;

    public GatewayBooksController(BooksClient booksClient) {
        this.booksClient = booksClient;
    }

    @Get
    List<Book> findAll(@Header("Authorization") String authorization) {
        return booksClient.findAll(authorization);
    }
}
