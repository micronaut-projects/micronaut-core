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
package io.micronaut.docs.http.server.exception;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;
import io.micronaut.http.annotation.Error;
import io.micronaut.http.exceptions.HttpStatusException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Requires(property = "spec.name", value = "ExceptionHandlerSpec")
//tag::clazz[]
@Controller("/books")
public class BookController {
    @Produces(MediaType.TEXT_PLAIN)
    @Get("/stock/{isbn}")
    Integer stock(String isbn) {
        throw new OutOfStockException();
    }

    @Produces(MediaType.TEXT_PLAIN)
    @Get("/stock/future/{isbn}")
    CompletableFuture<Integer> stockFuture(String isbn) {
        CompletableFuture future = new CompletableFuture();
        future.completeExceptionally(new HttpStatusException(HttpStatus.OK, 1234));
        return future;
    }

    @Produces(MediaType.TEXT_PLAIN)
    @Get("/stock/blocking/{isbn}")
    Integer stockBlocking(String isbn) throws InterruptedException, ExecutionException {
        CompletableFuture<Integer> future = new CompletableFuture<>();
        future.completeExceptionally(new HttpStatusException(HttpStatus.OK, 1234));
        return future.get();
    }

    @Produces(MediaType.TEXT_PLAIN)
    @Get("/null-pointer")
    Integer npe() {
        throw new NullPointerException();
    }

    @Error(exception = NullPointerException.class)
    @Produces(MediaType.TEXT_PLAIN)
    @Status(HttpStatus.MULTI_STATUS)
    String npeHandler() {
        return "NPE";
    }
}
//end::clazz[]
