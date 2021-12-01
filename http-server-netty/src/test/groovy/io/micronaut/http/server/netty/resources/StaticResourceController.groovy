package io.micronaut.http.server.netty.resources

import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post

@Requires(property = 'spec.name', value = 'StaticResourceResolutionSpec.test static file on same path as controller')
@Controller
class StaticResourceController {
    @Post('/static/index.html')
    String index(@Body String msg) {
        return msg
    }
}
