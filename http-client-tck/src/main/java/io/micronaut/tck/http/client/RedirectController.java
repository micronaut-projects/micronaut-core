package io.micronaut.tck.http.client;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.Produces;

@Requires(property = "spec.name", value = "RedirectTest")
@Requires(property = "redirect.server", value = StringUtils.TRUE)
@Controller("/redirect")
public class RedirectController {

    @Get("/host-header")
    @Produces("text/plain")
    HttpResponse<?> hostHeader(@Header String host) {
        return HttpResponse.ok(host);
    }
}
