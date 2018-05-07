package io.micronaut.docs.context.annotation.primary

import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get

@Requires(property = 'spec.name', value = 'primaryspec')
//tag::clazz[]
@Controller("/test")
public class TestController {

    protected final ColorPicker colorPicker;

    TestController(ColorPicker colorPicker) { // <1>
        this.colorPicker = colorPicker;
    }

    @Get('/')
    public String index() {
        return colorPicker.color()
    }
}
//end::clazz[]