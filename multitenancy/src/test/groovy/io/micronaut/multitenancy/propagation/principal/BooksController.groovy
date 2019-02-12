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
package io.micronaut.multitenancy.propagation.principal

import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.security.annotation.Secured
import io.micronaut.security.rules.SecurityRule
import io.micronaut.security.utils.SecurityService

@Requires(property = 'spec.name', value = 'multitenancy.principal.gorm')
@CompileStatic
@Controller("/api")
class BooksController {

    private final BookService bookService
    private final SecurityService securityService

    BooksController(BookService bookService,
                    SecurityService securityService) {
        this.bookService = bookService
        this.securityService = securityService
    }

    @Secured(SecurityRule.IS_AUTHENTICATED)
    @Get("/books")
    List<String> books() {
        List<Book> books = bookService.list()
        books*.title
    }
}
