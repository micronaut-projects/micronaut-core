package demo.controllers;

import demo.services.Html5Composer;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;
import io.micronaut.security.Secured;

@Secured("isAnonymous()")
@Controller("/login")
public class LoginAuthController {
    Html5Composer html5Composer;

    public LoginAuthController(Html5Composer html5Composer) {
        this.html5Composer = html5Composer;
    }


    @Produces(MediaType.TEXT_HTML)
    @Get("/auth")
    public String auth() {
        return loginHtml(false);
    }

    @Produces(MediaType.TEXT_HTML)
    @Get("/authFailed")
    public String authFailed() {
        return loginHtml(true);
    }

    private String loginHtml(boolean failed) {
        StringBuilder sb = new StringBuilder();
        sb.append("<form action=\"/login\" method=\"POST\">");
        sb.append("<ul>");
        sb.append("<li>");
        sb.append("<label for=\"username\">Username</label>");
        sb.append("<input type=\"text\" name=\"username\" id=\"username\"/>");
        sb.append("</li>");
        sb.append("<li>");
        sb.append("<label for=\"password\">Password</label>");
        sb.append("<input type=\"text\" name=\"password\" id=\"password\"/>");
        sb.append("</li>");
        sb.append("<li>");
        sb.append("<input type=\"submit\" value=\"Login\"/>");
        sb.append("</li>");
        if (failed) {
            sb.append("<li>");
            sb.append("<span style=\"color: red;\">Login Failed</span>");
            sb.append("</li>");
        }
        sb.append("</ul>");
        sb.append("</form>");
        return html5Composer.compose(sb.toString());
    }
}
