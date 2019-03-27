/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

    @View("relativeLink")
    @Get("/relative-link")
    HttpResponse relativeLink() {
        HttpResponse.ok()
    }

    static class LocalException extends RuntimeException {

    }

    static class GlobalException extends RuntimeException {

    }
}
