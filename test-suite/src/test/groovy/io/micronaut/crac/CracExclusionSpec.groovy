package io.micronaut.crac

import io.micronaut.context.BeanContext
import io.micronaut.context.annotation.Property
import io.micronaut.crac.support.GlobalCracContextFactory
import io.micronaut.crac.support.OrderedCracResourceRegistrar
import io.micronaut.http.server.netty.NettyEmbeddedServerCracHander
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest
@Property(name = "spec.name", value = "CracExclusionSpec")
class CracExclusionSpec extends Specification {

    @Inject
    BeanContext context

    void "config is configured"() {
        when:
        def config = context.getBean(CracConfiguration)

        then:
        config.enabled
        config.cracCompatClass == null
    }

    void "CRaC condition prevents support on non-CRaC jvm"() {
        expect:
        !context.findBean(OrderedCracResourceRegistrar).present
        !context.findBean(GlobalCracContextFactory).present
    }

    void "no Netty Crac handler is defined"() {
        given:
        def cracHandler = context.findBean(NettyEmbeddedServerCracHander)

        expect:
        !cracHandler.present
    }
}
