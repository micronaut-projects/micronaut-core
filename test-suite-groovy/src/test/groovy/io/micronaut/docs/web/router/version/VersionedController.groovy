package io.micronaut.docs.web.router.version

// tag::imports[]

import io.micronaut.core.version.annotation.Version
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
// end::imports[]


// tag::clazz[]
@Controller("/versioned")
class VersionedController {

    @Version("1") // <1>
    @Get("/hello")
    String helloV1() {
        "helloV1"
    }

    @Version("2") // <2>
    @Get("/hello")
    String helloV2() {
        "helloV2"
    }
// end::clazz[]

    @Version("2")
    @Get("/hello")
    String duplicatedHelloV2() {
        "duplicatedHelloV2"
    }

    @Get("/hello")
    String hello() {
        "hello"
    }

}
