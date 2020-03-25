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
package io.micronaut.http.client.docs.httpstatus;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Status;
import io.reactivex.Maybe;

import java.util.concurrent.CompletableFuture;

@Requires(property = "spec.name", value = "httpstatus")
@Controller("/status")
public class StatusController {

    @Get(value = "/simple", produces = MediaType.TEXT_PLAIN)
    public String simple() {
        return "success";
    }

    //tag::atstatus[]
    @Status(HttpStatus.CREATED)
    @Get(produces = MediaType.TEXT_PLAIN)
    public String index() {
        return "success";
    }
    //end::atstatus[]

    @Status(HttpStatus.CREATED)
    @Get(value = "/voidreturn")
    public void voidReturn() {
    }

    @Status(HttpStatus.CREATED)
    @Get(value = "/completableVoid")
    public CompletableFuture<Void> voidCompletableFuture() {
        return CompletableFuture.completedFuture(null);
    }

    @Status(HttpStatus.CREATED)
    @Get(value = "/maybeVoid")
    public Maybe<Void> maybeVoid() {
        return Maybe.empty();
    }

    @Status(HttpStatus.NOT_FOUND)
    @Get(value = "/simple404", produces = MediaType.TEXT_PLAIN)
    public String simple404() {
        return "success";
    }
}
