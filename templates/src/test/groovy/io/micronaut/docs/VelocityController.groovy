package io.micronaut.docs

import io.micronaut.context.annotation.Requires
import io.micronaut.core.util.CollectionUtils
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.templates.Template
import io.reactivex.Single

@Requires(property = "spec.name", value = "velocity")
@Controller("/velocity")
class VelocityController {

    @Template("home")
    @Get("/")
    HttpResponse index() {
        return HttpResponse.ok(CollectionUtils.mapOf("loggedIn", true, "username", "sdelamo"));
    }

    @Template("home.vm")
    @Get("/pogo")
    HttpResponse<Person> pogo() {
        HttpResponse.ok(new Person(loggedIn: true, username: 'sdelamo'))
    }

    @Get("/home")
    HttpResponse<Person> home() {
        HttpResponse.ok(new Person(loggedIn: true, username: 'sdelamo'))
    }

    @Template("home")
    @Get("/reactive")
    Single<Person> reactive() {
        return Single.just(new Person(loggedIn: true, username: 'sdelamo'))
    }

    @Template("bogus")
    @Get("/bogus")
    HttpResponse<Person> bogus() {
        HttpResponse.ok(new Person(loggedIn: true, username: 'sdelamo'))
    }
}
