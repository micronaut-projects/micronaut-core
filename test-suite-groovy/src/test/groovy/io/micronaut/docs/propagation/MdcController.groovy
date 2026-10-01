package io.micronaut.docs.propagation

import io.micronaut.context.annotation.Requires
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get

@Controller('/mdc')
@Requires(property = 'mdc.example.service.enabled')
class MdcController {

    private final MdcService mdcService

    MdcController(MdcService mdcService) {
        this.mdcService = mdcService
    }

    @Get(value = '/test', produces = MediaType.TEXT_PLAIN)
    String test() {
        mdcService.createUser('Denis')
    }
}
