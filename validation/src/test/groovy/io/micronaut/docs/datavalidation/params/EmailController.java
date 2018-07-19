package io.micronaut.docs.datavalidation.params;

import io.micronaut.context.annotation.Requires;

//tag::imports[]
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.validation.Validated;
import javax.validation.constraints.NotBlank;
import java.util.Collections;
//end::imports[]

@Requires(property = "spec.name", value = "datavalidationparams")
//tag::clazz[]
@Validated // <1>
@Controller("/email")
public class EmailController {

    @Get("/send")
    public HttpResponse send(@NotBlank String recipient, // <2>
                             @NotBlank String subject) { // <2>
        return HttpResponse.ok(Collections.singletonMap("msg", "OK"));
    }
}
//end::clazz[]