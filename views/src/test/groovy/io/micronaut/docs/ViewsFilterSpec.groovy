package io.micronaut.docs

import io.micronaut.context.ApplicationContext
import io.micronaut.context.exceptions.NoSuchBeanException
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.views.handlebars.HandlebarsViewsRenderer
import io.micronaut.views.ViewsFilter
import io.micronaut.views.thymeleaf.ThymeleafViewsRenderer
import io.micronaut.views.velocity.VelocityViewsRenderer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class ViewsFilterSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer,
            [
                    'spec.name': 'templatesfilter',
                    'micronaut.views.enabled': true,
                    'micronaut.views.thymeleaf.enabled': false,
                    'micronaut.views.handlebars.enabled': false,
                    'micronaut.views.velocity.enabled': false,
            ],
            "test")

    def "TemplatesFilter is not loaded unless bean TemplateRenderer exists"() {
        when:
        embeddedServer.applicationContext.getBean(VelocityViewsRenderer)

        then:
        thrown(NoSuchBeanException)

        when:
        embeddedServer.applicationContext.getBean(HandlebarsViewsRenderer)

        then:
        thrown(NoSuchBeanException)

        when:
        embeddedServer.applicationContext.getBean(ThymeleafViewsRenderer)

        then:
        thrown(NoSuchBeanException)

        when:
        embeddedServer.applicationContext.getBean(ViewsFilter)

        then:
        thrown(NoSuchBeanException)

    }
}
