package example.micronaut;

import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;

@Controller("/hello")
public class HelloController {

    @Get
    public MyRecord index() {
        return new MyRecord("Denis", 123);
    }
}
