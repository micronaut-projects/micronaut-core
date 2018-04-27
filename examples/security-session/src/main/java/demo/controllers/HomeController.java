package demo.controllers;

import demo.services.HomeComposer;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;
import io.micronaut.security.Secured;

import javax.annotation.Nullable;
import java.security.Principal;

@Secured("isAnonymous()")
@Controller("/")
public class HomeController {

    protected final HomeComposer homeComposer;

    public HomeController(HomeComposer homeComposer) {
        this.homeComposer = homeComposer;
    }

    @Produces(MediaType.TEXT_HTML)
    @Get("/")
    String index(@Nullable Principal principal) {
        return homeComposer.compose(principal);
    }
}
