package io.micronaut.inject.spock.other

import io.micronaut.context.env.Environment
import jakarta.inject.Inject
import spock.lang.Shared
import spock.lang.Specification

abstract class AbstractMicronautTestSpec extends Specification {

    @Inject
    @Shared
    Environment sharedFromParent
}
