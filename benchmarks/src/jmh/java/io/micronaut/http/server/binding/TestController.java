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
package io.micronaut.http.server.binding;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.annotation.RequestBean;

@Controller("/arguments")
public class TestController {

    @Get("/foo/{name}/{age}")
    String show(String name, int age) {
        return name + " is " + age;
    }

    @Get("/books/{id}")
    String book(Long id, @Header("X-Tenant") String tenant, @QueryValue int page) {
        return id + " " + tenant + " " + page;
    }

    @Get("/unannotated")
    String unannotated(String a, String b, int c) {
        return a + b + c;
    }

    @Get("/bean/{id}")
    String bean(@RequestBean BookRequest request) {
        return request.id() + " " + request.tenant() + " " + request.page();
    }

    @Introspected
    public record BookRequest(@PathVariable Long id, @Header("X-Tenant") String tenant, @QueryValue int page) {
    }
}
