package io.micronaut.docs

import io.micronaut.context.annotation.Requires
import io.micronaut.core.util.CollectionUtils
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Produces
import io.micronaut.views.ModelAndView
import io.micronaut.views.View
import io.reactivex.Single

@Requires(property = "spec.name", value = "velocity")
@Controller("/velocity")
class VelocityController {

    @View("home")
    @Get("/")
    HttpResponse index() {
        return HttpResponse.ok(CollectionUtils.mapOf("loggedIn", true, "username", "sdelamo"));
    }

    @View("home.vm")
    @Get("/pogo")
    HttpResponse<Person> pogo() {
        HttpResponse.ok(new Person(loggedIn: true, username: 'sdelamo'))
    }

    @Get("/home")
    HttpResponse<Person> home() {
        HttpResponse.ok(new Person(loggedIn: true, username: 'sdelamo'))
    }

    @Produces(MediaType.TEXT_PLAIN)
    @View("home.vm")
    @Get("/viewWithNoViewRendererForProduces")
    Person viewWithNoViewRendererForProduces() {
        new Person(loggedIn: true, username: 'sdelamo')
    }

    @View("home")
    @Get("/reactive")
    Single<Person> reactive() {
        return Single.just(new Person(loggedIn: true, username: 'sdelamo'))
    }

    @Get("/modelAndView")
    Single<ModelAndView> modelAndView() {
        ModelAndView modelAndView = new ModelAndView("home",
                new Person(loggedIn: true, username: 'sdelamo'))
        return Single.just(modelAndView)
    }

    @View("bogus")
    @Get("/bogus")
    HttpResponse<Person> bogus() {
        HttpResponse.ok(new Person(loggedIn: true, username: 'sdelamo'))
    }
}
