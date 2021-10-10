package io.micronaut.docs.datavalidation.params

//tag::imports[]
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.validation.Validated

import javax.validation.constraints.NotBlank
import java.util.Collections

//end::imports[]

@Requires(property = "spec.name", value = "datavalidationparams")
//tag::clazz[]
@Validated // <1>
@Controller("/email")
open class EmailController {

    @Get("/send")
    open fun send(@NotBlank recipient: String, // <2>
             @NotBlank subject: String): HttpResponse<*> { // <2>
        return HttpResponse.ok(Collections.singletonMap("msg", "OK"))
    }
}
//end::clazz[]