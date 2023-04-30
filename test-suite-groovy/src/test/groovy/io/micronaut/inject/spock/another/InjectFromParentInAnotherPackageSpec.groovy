package io.micronaut.inject.spock.another


import io.micronaut.core.convert.ConversionService
import io.micronaut.inject.spock.other.AbstractMicronautTestSpec
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Shared

@MicronautTest
class InjectFromParentInAnotherPackageSpec extends AbstractMicronautTestSpec {

    @Inject
    EmbeddedServer embeddedServer

    @Inject
    @Shared
    ConversionService sharedTest

    void "test parent injected"() {
        expect:"parent and child beans are injected"
        embeddedServer != null
        sharedTest != null
        sharedFromParent != null
    }
}
