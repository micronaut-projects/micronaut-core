package io.micronaut.docs.datavalidation.pogo;

import io.micronaut.context.annotation.Requires;

//tag::imports[]
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.validation.Validated;
import javax.validation.Valid;
import java.util.Collections;
//end::imports[]

@Requires(property = "spec.name", value = "datavalidationpogo")
//tag::clazz[]
@Validated // <1>
@Controller("/email")
public class EmailController {

    @Post("/send")
    public HttpResponse send(@Body @Valid Email email) { // <2>
        return HttpResponse.ok(Collections.singletonMap("msg", "OK"));    }
}
//end::clazz[]