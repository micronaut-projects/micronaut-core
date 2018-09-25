package io.micronaut.security.authorization

import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.security.annotation.Secured
import io.micronaut.security.authentication.Authentication
import io.reactivex.Single

import java.security.Principal

@Requires(property = 'spec.name', value = 'authorization')
@Controller('/argumentbinder')
@Secured("isAuthenticated()")
class PrincipalArgumentBinderController {

    @Get("/singleprincipal")
    Single<String> singlehello(Principal principal) {
        Single.just("You are ${principal.getName()}") as Single<String>
    }

    @Get("/singleauthentication")
    Single<String> singleauthentication(Authentication authentication) {
        Single.just("You are ${authentication.getName()}") as Single<String>
    }
}
