package io.micronaut.docs.security.securityRule

//tag::exampleControllerPlusImports[]
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.security.Secured
import io.micronaut.security.authentication.Authentication
import io.micronaut.security.rules.SecurityRule

@Controller("/example")
@Secured(SecurityRule.IS_AUTHENTICATED) // <1>
class ExampleController {

    @Get("/admin")
    @Secured(["ROLE_ADMIN", "ROLE_X"]) // <2>
    String withroles() {
        "You have ROLE_ADMIN or ROLE_X roles"
    }

    @Get('/anonymous')
    @Secured(SecurityRule.IS_ANONYMOUS)  // <3>
    String anonymous() {
        "You are anonymous"
    }

    @Get("/authenticated") // <1>
    String authenticated(Authentication authentication) {
        "${authentication.id} is authenticated"
    }
}
//end::exampleControllerPlusImports[]