package io.micronaut.docs

import io.micronaut.context.annotation.Requires
import io.micronaut.core.util.CollectionUtils
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Error
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
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
        return HttpResponse.ok(CollectionUtils.mapOf("loggedIn", true, "username", "sdelamo"))
    }
    //end::map[]

    //tag::pogo[]
    @View("home")
    @Get("/pogo")
    public HttpResponse<Person> pogo() {
        return HttpResponse.ok(new Person("sdelamo", true))
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

    @View("/home")
    @Get("/nullbody")
    HttpResponse nullBody() {
        HttpResponse.ok()
    }

    @Post("/error")
    HttpStatus error(Boolean status, Boolean exception, Boolean global) {
        if (status) {
            return global ? HttpStatus.ENHANCE_YOUR_CALM : HttpStatus.NOT_FOUND
        }
        if (exception) {
            if (global) {
                throw new GlobalException()
            } else {
                throw new LocalException()
            }
        }
    }

    @View("notFound")
    @Error(status = HttpStatus.NOT_FOUND)
    HttpResponse errorNotFound() {
        HttpResponse.notFound(CollectionUtils.mapOf("username", "sdelamo", "status", "404"))
    }

    @View("notFound")
    @Error(status = HttpStatus.ENHANCE_YOUR_CALM, global = true)
    HttpResponse errorEnhanceCalm() {
        HttpResponse.notFound(CollectionUtils.mapOf("username", "sdelamo", "status", "420"))
    }

    @View("notFound")
    @Error(exception = LocalException)
    HttpResponse localException() {
        HttpResponse.notFound(CollectionUtils.mapOf("username", "sdelamo", "status", "", "exception", "local"))
    }

    @View("notFound")
    @Error(exception = GlobalException, global = true)
    HttpResponse globalException() {
        HttpResponse.notFound(CollectionUtils.mapOf("username", "sdelamo", "status", "", "exception", "global"))
    }

    static class LocalException extends RuntimeException {

    }

    static class GlobalException extends RuntimeException {

    }
}
