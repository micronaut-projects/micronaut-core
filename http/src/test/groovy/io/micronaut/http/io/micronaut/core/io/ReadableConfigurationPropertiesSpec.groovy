package io.micronaut.http.io.micronaut.core.io

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.exceptions.DependencyInjectionException
import io.micronaut.core.io.Readable
import spock.lang.Specification

class ReadableConfigurationPropertiesSpec extends Specification {

    void "test readable binding success"() {
        given:
        ApplicationContext ctx = ApplicationContext.run('test.readable.logback-file':'classpath:logback.xml')
        MyConfig myConfig = ctx.getBean(MyConfig)

        expect:
        myConfig.logbackFile != null
        myConfig.logbackFile.exists()

        cleanup:
        ctx.close()
    }

    void "test readable binding failure"() {
        when:
        ApplicationContext ctx = ApplicationContext.run('test.readable.logback-file':'classpath:nothere.xml')
        MyConfig myConfig = ctx.getBean(MyConfig)

        then:
        def e = thrown(DependencyInjectionException)
        e.message.contains('No resource exists for value: classpath:nothere.xml')

        cleanup:
        ctx.close()
    }

    @ConfigurationProperties("test.readable")
    static class MyConfig {
        Readable logbackFile
    }
}
