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
package io.micronaut.docs.respondingnotfound;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.reactivex.Maybe;

import java.util.Map;

@Requires(property = "spec.name", value = "respondingnotfound")
//tag::clazz[]
@Controller("/books")
public class BooksController {

    @Get("/stock/{isbn}")
    public Map stock(String isbn) {
        return null; //<1>
    }

    @Get("/maybestock/{isbn}")
    public Maybe<Map> maybestock(String isbn) {
        return Maybe.empty(); //<2>
    }
}
//end::clazz[]
