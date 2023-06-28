package io.micronaut.docs.context.env

import io.micronaut.context.ApplicationContext
import io.micronaut.context.DefaultApplicationContextBuilder
import io.micronaut.context.env.CachedEnvironment
import io.micronaut.context.env.DefaultEnvironment
import io.micronaut.context.env.Environment
import io.micronaut.core.util.StringUtils
import spock.lang.Specification
import spock.util.environment.RestoreSystemProperties

import java.lang.reflect.Field
import java.util.function.UnaryOperator

class DefaultEnvironmentSpec extends Specification {

    // tag::disableEnvDeduction[]
    void "test disable environment deduction via builder"() {
        when:
        ApplicationContext ctx = ApplicationContext.builder().deduceEnvironment(false).start()

        then:
        !ctx.environment.activeNames.contains(Environment.TEST)

        cleanup:
        ctx.close()
    }
    // end::disableEnvDeduction[]


    void "shouldDeduceCloudEnvironment returns false by default"() {
        given:
        DefaultApplicationContextBuilder config = (DefaultApplicationContextBuilder) ApplicationContext.builder()
        DefaultEnvironment env = new DefaultEnvironment(config)

        expect:
        !env.shouldDeduceCloudEnvironment()
    }

    void "shouldDeduceCloudEnvironment returns true if ApplicationContextBuilder deduceCloudEnvironment sets it to true"() {
        when:
        DefaultApplicationContextBuilder config = (DefaultApplicationContextBuilder) ApplicationContext.builder().deduceCloudEnvironment(true)
        DefaultEnvironment env = new DefaultEnvironment(config)

        then:
        env.shouldDeduceCloudEnvironment()

        when:
        config = (DefaultApplicationContextBuilder) ApplicationContext.builder().deduceCloudEnvironment(false)
        env = new DefaultEnvironment(config)

        then:
        !env.shouldDeduceCloudEnvironment()
    }

    @RestoreSystemProperties
    void "shouldDeduceCloudEnvironment returns true if system property micronaut.env.cloud-deduction is set to true"() {
        given:
        System.setProperty("micronaut.env.cloud-deduction", StringUtils.TRUE)
        DefaultApplicationContextBuilder config = (DefaultApplicationContextBuilder) ApplicationContext.builder()
        DefaultEnvironment env = new DefaultEnvironment(config)

        expect:
        env.shouldDeduceCloudEnvironment()

        when:
        System.setProperty("micronaut.env.cloud-deduction", StringUtils.FALSE)

        then:
        !env.shouldDeduceCloudEnvironment()
    }

    void "shouldDeduceCloudEnvironment returns true if environment variable MICRONAUT_ENV_CLOUD_DEDUCTION is set to true"() {
        given:
        DefaultApplicationContextBuilder config = (DefaultApplicationContextBuilder) ApplicationContext.builder()
        DefaultEnvironment env = new DefaultEnvironment(config)

        expect:
        !env.shouldDeduceCloudEnvironment()

        when:
        Field field = CachedEnvironment.class.getDeclaredField("getenv")
        field.setAccessible(true)
        field.set(null, new UnaryOperator<String>() {
            @Override
            String apply(String s) {
                return s == "MICRONAUT_ENV_CLOUD_DEDUCTION" ? StringUtils.TRUE : null
            }
        })

        then:
        env.shouldDeduceCloudEnvironment()

        when:
        field.set(null, new UnaryOperator<String>() {
            @Override
            String apply(String s) {
                return s == "MICRONAUT_ENV_CLOUD_DEDUCTION" ? StringUtils.FALSE : null
            }
        })

        then:
        !env.shouldDeduceCloudEnvironment()

        cleanup:
        field.set(null, null)
        field.setAccessible(false)
    }
}
