package io.micronaut.docs.context.annotation.primary

import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get

@Requires(property = "spec.name", value = "primaryspec")
//tag::clazz[]
@Controller("/test")
class TestController(val colorPicker: ColorPicker) { // <1>

    @Get
    fun index(): String {
        return colorPicker.color()
    }
}
//end::clazz[]