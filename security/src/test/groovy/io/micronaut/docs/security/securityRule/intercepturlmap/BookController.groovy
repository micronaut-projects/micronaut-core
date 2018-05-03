package io.micronaut.docs.security.securityRule.intercepturlmap

import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get

@Requires(property = 'spec.name', value = 'docsintercepturlmap')
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
