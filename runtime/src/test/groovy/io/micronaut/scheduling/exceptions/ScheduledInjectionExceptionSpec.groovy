package io.micronaut.scheduling.exceptions

import io.micronaut.context.ApplicationContext
import spock.lang.Ignore
import spock.lang.Specification

class ScheduledInjectionExceptionSpec extends Specification {

    @Ignore
    void "testing bean injections in scheduled beans logs an error"() {
        given:
        def oldOut = System.out
        ByteArrayOutputStream baos = new ByteArrayOutputStream()
        System.out = new PrintStream(baos)
        ApplicationContext ctx = ApplicationContext.run('injection-exception.task.enabled':'true')

        when:
        String output = baos.toString("UTF-8")
        baos.close()

        then:
        output.contains("DependencyInjectionException: Failed to inject value for field [notInjectable]")

        cleanup:
        System.out = oldOut
        ctx.close()
    }
}
