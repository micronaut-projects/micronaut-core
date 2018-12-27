package io.micronaut.security.session

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.context.exceptions.NoSuchBeanException
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.security.handlers.RedirectRejectionHandler
import io.micronaut.security.handlers.RejectionHandler
import spock.lang.Shared
import spock.lang.Specification

class RejectionHandlerResolutionSpec extends Specification {

    static final SPEC_NAME_PROPERTY = 'spec.name'

    @Shared
    Map<String, Object> config = [
            'micronaut.security.enabled': true,
            'micronaut.security.session.enabled': true,
    ]

    void "RedirectRejectionHandler is the default rejection handler resolved"() {
        Map<String, Object> conf = [
            'micronaut.security.session.legacy-rejection-handler': false
        ]
        conf.putAll(config)
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, conf, Environment.TEST)
        ApplicationContext context = embeddedServer.applicationContext

        when:
        context.getBean(ExtendedSessionSecurityfilterRejectionHandler)

        then:
        thrown(NoSuchBeanException)

        when:
        RejectionHandler rejectionHandler = context.getBean(RejectionHandler)

        then:
        noExceptionThrown()
        rejectionHandler instanceof RedirectRejectionHandler

        cleanup:
        context.close()

        and:
        embeddedServer.close()
    }

    void "If a bean extended SessionSecurityfilterRejectionHandler that is used as Rejection Handler"() {
        given:
        Map<String, Object> conf = [
                (SPEC_NAME_PROPERTY): getClass().simpleName,
        ]
        conf.putAll(config)
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, conf, Environment.TEST)
        ApplicationContext context = embeddedServer.applicationContext

        when:
        context.getBean(ExtendedSessionSecurityfilterRejectionHandler)

        then:
        noExceptionThrown()

        when:
        RejectionHandler rejectionHandler = context.getBean(RejectionHandler)

        then:
        noExceptionThrown()
        rejectionHandler instanceof ExtendedSessionSecurityfilterRejectionHandler

        cleanup:
        context.close()

        and:
        embeddedServer.close()
    }
}
