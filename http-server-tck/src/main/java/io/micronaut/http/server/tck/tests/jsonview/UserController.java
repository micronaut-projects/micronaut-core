/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.http.server.tck.tests.jsonview;

import com.fasterxml.jackson.annotation.JsonView;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@SuppressWarnings({
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
@Requires(property = "spec.name", value = JsonViewsTest.SPEC_NAME)
@Controller("/views")
class UserController {

    private static final User USER = new User(1, "Joe", "secret");

    @JsonView(Views.Public.class)
    @Get("/pojo")
    public User getUserPojo() {
        return USER;
    }

    @JsonView(Views.Public.class)
    @Get("/list")
    public List<User> getUserList() {
        return List.of(USER);
    }

    @JsonView(Views.Public.class)
    @Get("/optional")
    public Optional<User> getUserOptional() {
        return Optional.of(USER);
    }

    @JsonView(Views.Public.class)
    @Get("/future")
    public CompletableFuture<User> getUserFuture() {
        return CompletableFuture.completedFuture(USER);
    }

    @JsonView(Views.Public.class)
    @Get("/mono")
    public Mono<User> getUserMono() {
        return Mono.just(USER);
    }

    @JsonView(Views.Public.class)
    @Get("/flux")
    public Flux<User> getUserFlux() {
        return Flux.just(USER);
    }
}
