package io.micronaut.docs.web.router.version

// tag::imports[]

import io.micronaut.core.version.annotation.Version
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get

// end::imports[]


// tag::clazz[]
@Controller("/versioned")
internal class VersionedController {

    @Version("1") // <1>
    @Get("/hello")
    fun helloV1(): String {
        return "helloV1"
    }

    @Version("2") // <2>
    @Get("/hello")
    fun helloV2(): String {
        return "helloV2"
    }
    // end::clazz[]

    @Version("2")
    @Get("/hello")
    fun duplicatedHelloV2(): String {
        return "duplicatedHelloV2"
    }

    @Get("/hello")
    fun hello(): String {
        return "hello"
    }

}
