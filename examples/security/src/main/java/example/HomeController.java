package example;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import static io.micronaut.http.HttpResponse.ok;

@Controller("/")
public class HomeController {

    @Get("/")
    HttpResponse index() {
       return ok();
    }

    @Get("/notInInterceptUrlMap")
    HttpResponse notInInterceptUrlMap() {
        return ok();
    }
}
