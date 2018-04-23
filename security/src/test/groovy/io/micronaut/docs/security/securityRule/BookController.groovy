package io.micronaut.docs.security.securityRule

import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get

@Controller('/books')
class BookController {

    @Get("/")
    String index() {
        return "Index Action"
    }

    @Get('/grails')
    String grails() {
        return "Grails Action"
    }
}
