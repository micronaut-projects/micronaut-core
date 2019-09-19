package io.micronaut.docs.server.response;

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
class StatusController {

    @Get(value = "/simple", produces = MediaType.TEXT_PLAIN)
    String simple() {
        "success"
    }

    //tag::atstatus[]
    @Status(HttpStatus.CREATED)
    @Get(produces = MediaType.TEXT_PLAIN)
    String index() {
        return "success"
    }
    //end::atstatus[]

    @Status(HttpStatus.CREATED)
    @Get(value = "/voidreturn")
    void voidReturn() {
    }

    @Status(HttpStatus.CREATED)
    @Get(value = "/completableVoid")
    CompletableFuture<Void> voidCompletableFuture() {
        CompletableFuture.completedFuture(null)
    }

    @Status(HttpStatus.CREATED)
    @Get(value = "/maybeVoid")
    Maybe<Void> maybeVoid() {
        Maybe.empty()
    }

    @Status(HttpStatus.NOT_FOUND)
    @Get(value = "/simple404", produces = MediaType.TEXT_PLAIN)
    String simple404() {
        return "success"
    }
}
