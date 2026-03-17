package io.micronaut.context.env

import io.micronaut.context.ApplicationContext
import io.micronaut.context.DefaultApplicationContextBuilder
import io.micronaut.core.util.StringUtils
import org.slf4j.LoggerFactory
import spock.lang.Specification
import spock.util.environment.RestoreSystemProperties

import java.lang.reflect.Field
import java.util.function.UnaryOperator

class DefaultEnvironmentAndPackageDeducerSpec extends Specification {

    void "shouldDeduceCloudEnvironment returns false by default"() {
        given:
            DefaultApplicationContextBuilder config = (DefaultApplicationContextBuilder) ApplicationContext.builder()
            DefaultEnvironmentAndPackageDeducer deducer = new DefaultEnvironmentAndPackageDeducer(LoggerFactory.getLogger(getClass()), config)

        expect:
            !deducer.shouldDeduceCloudEnvironment()
    }

    void "shouldDeduceCloudEnvironment returns true if ApplicationContextBuilder deduceCloudEnvironment sets it to true"() {
        when:
            DefaultApplicationContextBuilder config = (DefaultApplicationContextBuilder) ApplicationContext.builder().deduceCloudEnvironment(true)
            DefaultEnvironmentAndPackageDeducer deducer = new DefaultEnvironmentAndPackageDeducer(LoggerFactory.getLogger(getClass()), config)
        then:
            deducer.shouldDeduceCloudEnvironment()

        when:
            config = (DefaultApplicationContextBuilder) ApplicationContext.builder().deduceCloudEnvironment(false)
            deducer = new DefaultEnvironmentAndPackageDeducer(LoggerFactory.getLogger(getClass()), config)

        then:
            !deducer.shouldDeduceCloudEnvironment()
    }

    @RestoreSystemProperties
    void "shouldDeduceCloudEnvironment returns true if system property micronaut.env.cloud-deduction is set to true"() {
        given:
            System.setProperty("micronaut.env.cloud-deduction", StringUtils.TRUE)
            DefaultApplicationContextBuilder config = (DefaultApplicationContextBuilder) ApplicationContext.builder()
            DefaultEnvironmentAndPackageDeducer deducer = new DefaultEnvironmentAndPackageDeducer(LoggerFactory.getLogger(getClass()), config)

        expect:
            deducer.shouldDeduceCloudEnvironment()

        when:
            System.setProperty("micronaut.env.cloud-deduction", StringUtils.FALSE)

        then:
            !deducer.shouldDeduceCloudEnvironment()
    }

    void "shouldDeduceCloudEnvironment returns true if environment variable MICRONAUT_ENV_CLOUD_DEDUCTION is set to true"() {
        given:
            DefaultApplicationContextBuilder config = (DefaultApplicationContextBuilder) ApplicationContext.builder()
            DefaultEnvironmentAndPackageDeducer deducer = new DefaultEnvironmentAndPackageDeducer(LoggerFactory.getLogger(getClass()), config)

        expect:
            !deducer.shouldDeduceCloudEnvironment()

        when:
            Field field = CachedEnvironment.class.getDeclaredField("getenv")
            field.setAccessible(true)
            field.set(null, generateOperator(StringUtils.TRUE))

        then:
            deducer.shouldDeduceCloudEnvironment()

        when:
            field.set(null, generateOperator(StringUtils.FALSE))

        then:
            !deducer.shouldDeduceCloudEnvironment()

        cleanup:
            field.set(null, null)
            field.setAccessible(false)
    }

    UnaryOperator<String> generateOperator(String value) {
        { String s -> s == "MICRONAUT_ENV_CLOUD_DEDUCTION" ? value : null }
    }

}
