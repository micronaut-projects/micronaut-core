package io.micronaut.docs.web.router.version;

// tag::imports[]

import io.micronaut.core.version.annotation.Version;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
// end::imports[]


// tag::clazz[]
@Controller("/versioned")
class VersionedController {

    @Version("1") // <1>
    @Get("/hello")
    String helloV1() {
        return "helloV1";
    }

    @Version("2") // <2>
    @Get("/hello")
    String helloV2() {
        return "helloV2";
    }
// end::clazz[]

    @Version("2")
    @Get("/hello")
    String duplicatedHelloV2() {
        return "duplicatedHelloV2";
    }

    @Get("/hello")
    String hello() {
        return "hello";
    }

}
