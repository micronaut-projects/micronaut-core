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
package io.micronaut.docs.httpclientexceptionbody;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;

import java.util.HashMap;
import java.util.Map;

@Requires(property = "spec.name", value = "BindHttpClientExceptionBodySpec")
//tag::clazz[]
@Controller("/books")
public class BooksController {

    @Get("/{isbn}")
    public HttpResponse find(String isbn) {
        if (isbn.equals("1680502395")) {
            Map<String, Object> m = new HashMap<>();
            m.put("status", 401);
            m.put("error", "Unauthorized");
            m.put("message", "No message available");
            m.put("path", "/books/"+isbn);
            return HttpResponse.status(HttpStatus.UNAUTHORIZED).body(m);

        }
        return HttpResponse.ok(new Book("1491950358", "Building Microservices"));
    }
}
//end::clazz[]
