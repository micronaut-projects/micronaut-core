package io.micronaut.docs.server.suspend

// tag::suspendClient[]
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.annotation.Client
import io.reactivex.Single

@Client("/suspend")
interface SimpleClient {
    @Get("/simple")
    fun simple() : Single<String>
}
// end::suspendClient[]