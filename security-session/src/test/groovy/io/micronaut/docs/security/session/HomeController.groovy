/*
 * Copyright 2017-2018 original authors
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
package io.micronaut.docs.security.session

import io.micronaut.context.annotation.Requires
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Produces
import io.micronaut.security.Secured

import javax.annotation.Nullable
import java.security.Principal

@Requires(property = "spec.name", value = "securitysession")
@Secured("isAnonymous()")
@Controller("/")
class HomeController {

    @Produces(MediaType.TEXT_HTML)
    @Get("/")
    String index(@Nullable Principal principal) {
        return html(principal != null, principal != null ? principal.getName() : null)
    }

    private String html(boolean loggedIn, String username) {
        StringBuilder sb = new StringBuilder()
        sb.append("<!DOCTYPE html>")
        sb.append("<html>")
        sb.append("<head>")
        sb.append("<title>Home</title>")
        sb.append("</head>")
        sb.append("<body>")
        if( loggedIn ) {
            sb.append("<h1>username: <span> "+username+"</span></h1>")
        } else {
            sb.append("<h1>You are not logged in</h1>")
        }
        if( loggedIn ) {
            sb.append("<form action=\"logout\" method=\"POST\">")
            sb.append("<input type=\"submit\" value=\"Logout\" />")
            sb.append("</form>")
        } else {
            sb.append("<p><a href=\"/login/auth\">Login</a></p>")
        }
        sb.append("</body>")
        sb.append("</html>")
        return sb.toString()
    }
}
