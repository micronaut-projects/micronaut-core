package io.micronaut.docs.security.session

import io.micronaut.context.annotation.Requires
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Produces
import io.micronaut.security.Secured

@Requires(property = "spec.name", value = "securitysession")
@Secured("isAnonymous()")
@Controller("/login")
class LoginAuthController {

    @Produces(MediaType.TEXT_HTML)
    @Get("/auth")
    String auth() {
        return html(false)
    }

    @Produces(MediaType.TEXT_HTML)
    @Get("/authFailed")
    String authFailed() {
        return html(true)
    }

    private String html(boolean errors) {
        StringBuilder sb = new StringBuilder()
        sb.append("<!DOCTYPE html>")
        sb.append("<html>")
        sb.append("<head>")
        if( errors ) {
            sb.append("<title>Login Failed</title>")
        } else {
            sb.append("<title>Login</title>")
        }
        sb.append("</head>")
        sb.append("<body>")
        sb.append("<form action=\"/login\" method=\"POST\">")
        sb.append("<ol>")
        sb.append("<li>")
        sb.append("<label for=\"username\">Username</label>")
        sb.append("<input type=\"text\" name=\"username\" id=\"username\"/>")
        sb.append("</li>")
        sb.append("<li>")
        sb.append("<label for=\"password\">Password</label>")
        sb.append("<input type=\"text\" name=\"password\" id=\"password\"/>")
        sb.append("</li>")
        sb.append("<li>")
        sb.append("<input type=\"submit\" value=\"Login\"/>")
        sb.append("</li>")
        if( errors ) {
            sb.append("<li id=\"errors\">")
            sb.append("<span style=\"color:red\">Login Failed</span>")
            sb.append("</li>")
        }
        sb.append("</ol>")
        sb.append("</form>")
        sb.append("</body>")
        sb.append("</html>")
        return sb.toString()
    }
}
