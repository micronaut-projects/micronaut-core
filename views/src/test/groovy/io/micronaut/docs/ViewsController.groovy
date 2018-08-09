package io.micronaut.docs

import io.micronaut.context.annotation.Requires
import io.micronaut.core.util.CollectionUtils
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.views.ModelAndView
import io.micronaut.views.View

@Requires(property = "spec.name", value = "thymeleaf")
//tag::clazz[]
@Controller("/views")
class ViewsController {
//end::clazz[]

    //tag::map[]
    @View("home")
    @Get("/")
    public HttpResponse index() {
        return HttpResponse.ok(CollectionUtils.mapOf("loggedIn", true, "username", "sdelamo"));
    }
    //end::map[]

    //tag::pogo[]
    @View("home")
    @Get("/pogo")
    public HttpResponse<Person> pogo() {
        return HttpResponse.ok(new Person("sdelamo", true));
    }
    //end::pogo[]

    //tag::modelAndView[]
    @Get("/modelAndView")
    ModelAndView modelAndView() {
        return new ModelAndView("home",
                new Person(loggedIn: true, username: 'sdelamo'))
    }
    //end::modelAndView[]

    @Get("/home")
    HttpResponse<Person> home() {
        HttpResponse.ok(new Person(loggedIn: true, username: 'sdelamo'))
    }

    @View("bogus")
    @Get("/bogus")
    HttpResponse<Person> bogus() {
        HttpResponse.ok(new Person(loggedIn: true, username: 'sdelamo'))
    }
}
