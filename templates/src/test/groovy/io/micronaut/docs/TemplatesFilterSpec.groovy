package io.micronaut.docs

import io.micronaut.context.ApplicationContext
import io.micronaut.context.exceptions.NoSuchBeanException
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.templates.HandlebarsTemplateRenderer
import io.micronaut.templates.TemplatesFilter
import io.micronaut.templates.ThymeleafTemplateRenderer
import io.micronaut.templates.VelocityTemplateRenderer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class TemplatesFilterSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer,
            [
                    'spec.name': 'templatesfilter',
                    'micronaut.templates.enabled': true,
                    'micronaut.templates.thymeleaf.enabled': false,
                    'micronaut.templates.handlebars.enabled': false,
                    'micronaut.templates.velocity.enabled': false,
            ],
            "test")

    def "TemplatesFilter is not loaded unless bean TemplateRenderer exists"() {
        when:
        embeddedServer.applicationContext.getBean(VelocityTemplateRenderer)

        then:
        thrown(NoSuchBeanException)

        when:
        embeddedServer.applicationContext.getBean(HandlebarsTemplateRenderer)

        then:
        thrown(NoSuchBeanException)

        when:
        embeddedServer.applicationContext.getBean(ThymeleafTemplateRenderer)

        then:
        thrown(NoSuchBeanException)

        when:
        embeddedServer.applicationContext.getBean(TemplatesFilter)

        then:
        thrown(NoSuchBeanException)

    }
}
