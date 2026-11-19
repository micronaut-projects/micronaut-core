package io.micronaut.context.env

import io.micronaut.context.ApplicationContext
import io.micronaut.context.DefaultApplicationContextBuilder
import io.micronaut.core.util.StringUtils
import org.slf4j.LoggerFactory
import spock.lang.Issue
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

    @Issue("https://github.com/micronaut-projects/micronaut-core/issues/14486")
    void "deduceEnvironmentNames does not deduce environments from env vars when deduction is disabled via builder"() {
        given:
            DefaultApplicationContextBuilder config = (DefaultApplicationContextBuilder) ApplicationContext.builder()
                    .deduceEnvironment(false)
            DefaultEnvironmentAndPackageDeducer deducer = new DefaultEnvironmentAndPackageDeducer(LoggerFactory.getLogger(getClass()), config)

        when:
            Set<String> names = withCachedEnv([(envVar): envValue]) {
                deducer.deduceEnvironmentNames()
            }

        then:
            names.isEmpty()

        where:
            envVar                      | envValue
            "KUBERNETES_SERVICE_HOST"   | "10.0.0.1"
            "VCAP_SERVICES"             | "{}"
            "DYNO"                      | "web.1"
            "GAE_ENV"                   | "standard"
    }

    @Issue("https://github.com/micronaut-projects/micronaut-core/issues/14486")
    @RestoreSystemProperties
    void "deduceEnvironmentNames does not deduce k8s or cloud when micronaut.env.deduction is false"() {
        given:
            System.setProperty(Environment.DEDUCE_ENVIRONMENT_PROPERTY, StringUtils.FALSE)
            DefaultApplicationContextBuilder config = (DefaultApplicationContextBuilder) ApplicationContext.builder("api")
            DefaultEnvironmentAndPackageDeducer deducer = new DefaultEnvironmentAndPackageDeducer(LoggerFactory.getLogger(getClass()), config)

        when:
            Set<String> names = withCachedEnv(["KUBERNETES_SERVICE_HOST": "10.0.0.1"]) {
                deducer.deduceEnvironmentNames()
            }

        then:
            names == ["api"] as Set
    }

    @Issue("https://github.com/micronaut-projects/micronaut-core/issues/14486")
    void "deduceEnvironmentNames does not deduce k8s or cloud when MICRONAUT_ENV_DEDUCTION is false"() {
        given:
            DefaultApplicationContextBuilder config = (DefaultApplicationContextBuilder) ApplicationContext.builder()
            DefaultEnvironmentAndPackageDeducer deducer = new DefaultEnvironmentAndPackageDeducer(LoggerFactory.getLogger(getClass()), config)

        when:
            Set<String> names = withCachedEnv([
                    "KUBERNETES_SERVICE_HOST": "10.0.0.1",
                    "MICRONAUT_ENV_DEDUCTION": StringUtils.FALSE
            ]) {
                deducer.deduceEnvironmentNames()
            }

        then:
            names.isEmpty()
    }

    @Issue("https://github.com/micronaut-projects/micronaut-core/issues/14486")
    void "deduceEnvironmentNames does not deduce k8s or cloud when enableDefaultPropertySources is false"() {
        given:
            DefaultApplicationContextBuilder config = (DefaultApplicationContextBuilder) ApplicationContext.builder()
                    .enableDefaultPropertySources(false)
            DefaultEnvironmentAndPackageDeducer deducer = new DefaultEnvironmentAndPackageDeducer(LoggerFactory.getLogger(getClass()), config)

        when:
            Set<String> names = withCachedEnv(["KUBERNETES_SERVICE_HOST": "10.0.0.1"]) {
                deducer.deduceEnvironmentNames()
            }

        then:
            names.isEmpty()
    }

    @Issue("https://github.com/micronaut-projects/micronaut-core/issues/14486")
    @RestoreSystemProperties
    void "deduceEnvironmentNames does not deduce cloud platform even if deduceCloudEnvironment is true when deduction is disabled"() {
        given:
            System.setProperty(Environment.CLOUD_PLATFORM_PROPERTY, "GOOGLE_COMPUTE")
            System.setProperty(Environment.DEDUCE_ENVIRONMENT_PROPERTY, StringUtils.FALSE)
            DefaultApplicationContextBuilder config = (DefaultApplicationContextBuilder) ApplicationContext.builder()
                    .deduceCloudEnvironment(true)
            DefaultEnvironmentAndPackageDeducer deducer = new DefaultEnvironmentAndPackageDeducer(LoggerFactory.getLogger(getClass()), config)

        when:
            Set<String> names = withCachedEnv(["KUBERNETES_SERVICE_HOST": "10.0.0.1"]) {
                deducer.deduceEnvironmentNames()
            }

        then:
            names.isEmpty()
    }

    @Issue("https://github.com/micronaut-projects/micronaut-core/issues/14486")
    void "deduceEnvironmentNames does not deduce lambda environment when FUNCTION is active and deduction is disabled"() {
        given:
            DefaultApplicationContextBuilder disabledConfig = (DefaultApplicationContextBuilder) ApplicationContext.builder(Environment.FUNCTION)
                    .deduceEnvironment(false)
            DefaultEnvironmentAndPackageDeducer disabledDeducer = new DefaultEnvironmentAndPackageDeducer(LoggerFactory.getLogger(getClass()), disabledConfig)

            DefaultApplicationContextBuilder enabledConfig = (DefaultApplicationContextBuilder) ApplicationContext.builder(Environment.FUNCTION)
            DefaultEnvironmentAndPackageDeducer enabledDeducer = new DefaultEnvironmentAndPackageDeducer(LoggerFactory.getLogger(getClass()), enabledConfig)

        when:
            Set<String> disabledNames = withCachedEnv(["AWS_LAMBDA_FUNCTION_NAME": "my-fn"]) {
                disabledDeducer.deduceEnvironmentNames()
            }
            Set<String> enabledNames = withCachedEnv(["AWS_LAMBDA_FUNCTION_NAME": "my-fn"]) {
                enabledDeducer.deduceEnvironmentNames()
            }

        then:
            disabledNames == [Environment.FUNCTION] as Set
            enabledNames == [Environment.FUNCTION, Environment.AMAZON_EC2, Environment.CLOUD] as Set
    }

    UnaryOperator<String> generateOperator(String value) {
        { String s -> s == "MICRONAUT_ENV_CLOUD_DEDUCTION" ? value : null }
    }

    private static <T> T withCachedEnv(Map<String, String> envVars, Closure<T> action) {
        Field field = CachedEnvironment.class.getDeclaredField("getenv")
        field.setAccessible(true)
        Object previous = field.get(null)
        field.set(null, { String s -> envVars.get(s) } as UnaryOperator<String>)
        try {
            return action.call()
        } finally {
            field.set(null, previous)
            field.setAccessible(false)
        }
    }

}
