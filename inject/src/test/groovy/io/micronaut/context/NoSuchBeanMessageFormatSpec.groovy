package io.micronaut.context

import io.micronaut.context.annotation.Requires
import io.micronaut.context.exceptions.NoSuchBeanException
import jakarta.inject.Singleton
import spock.lang.Issue
import spock.lang.Specification

@Issue("https://github.com/micronaut-projects/micronaut-core/issues/11320")
class NoSuchBeanMessageFormatSpec extends Specification {

    void "NoSuchBeanException top line has no surrounding brackets"() {
        expect:
        new NoSuchBeanException(MyBean).message.startsWith(
                "No bean of type io.micronaut.context.NoSuchBeanMessageFormatSpec\$MyBean exists.")
    }

    void "disabled bean candidates and their failure reasons are listed without brackets"() {
        given:
        def ctx = ApplicationContext.run('spec.name': 'NoSuchBeanMessageFormatSpec')

        when:
        ctx.getBean(MyBean)

        then:
        def ex = thrown(NoSuchBeanException)

        and: "the candidate is listed by its user-facing simple name, not a generated proxy class name"
        ex.message.contains("* MyBean is disabled because:")
        !ex.message.contains("[MyBean]")
        !ex.message.contains('$Definition')
        !ex.message.contains('$Intercepted')

        and: "the failure reason from a bean-presence requirement has no brackets"
        ex.message.contains(
                "- No bean of type io.micronaut.context.NoSuchBeanMessageFormatSpec\$RequiredBean present within context")
        !ex.message.contains(
                "- No bean of type [io.micronaut.context.NoSuchBeanMessageFormatSpec\$RequiredBean] present within context")

        cleanup:
        ctx.close()
    }

    static interface RequiredBean {}

    @Singleton
    @Requires(property = "spec.name", value = "NoSuchBeanMessageFormatSpec")
    @Requires(beans = RequiredBean)
    static class MyBean {}
}
