package io.micronaut.docs.server.response

import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Status
import io.reactivex.Maybe

import java.util.concurrent.CompletableFuture

@Requires(property = "spec.name", value = "httpstatus")
@Controller("/status")
class StatusController {

    @Get(value = "/simple", produces = [MediaType.TEXT_PLAIN])
    fun simple(): String {
        return "success"
    }

    //tag::atstatus[]
    @Status(HttpStatus.CREATED)
    @Get(produces = [MediaType.TEXT_PLAIN])
    fun index(): String {
        return "success"
    }
    //end::atstatus[]

    @Status(HttpStatus.CREATED)
    @Get(value = "/voidreturn")
    fun voidReturn() {
    }

    @Status(HttpStatus.CREATED)
    @Get(value = "/completableVoid")
    fun voidCompletableFuture(): CompletableFuture<Void> {
        return CompletableFuture.completedFuture(null)
    }

    @Status(HttpStatus.CREATED)
    @Get(value = "/maybeVoid")
    fun maybeVoid(): Maybe<Void> {
        return Maybe.empty()
    }

    @Status(HttpStatus.NOT_FOUND)
    @Get(value = "/simple404", produces = [MediaType.TEXT_PLAIN])
    fun simple404(): String {
        return "success"
    }
}
