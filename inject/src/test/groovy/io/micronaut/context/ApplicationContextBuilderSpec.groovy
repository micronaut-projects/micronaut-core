package io.micronaut.context

import io.micronaut.context.env.PropertySource
import spock.lang.Specification

class ApplicationContextBuilderSpec extends Specification {

    void "test disable default property sources"() {
        given:
        ApplicationContextBuilder builder = ApplicationContext.builder()
        builder.enableDefaultPropertySources(false)
            .propertySources(PropertySource.of("custom", [foo:'bar']))
        when:
        def ctx = builder.build().start()

        then:
        ctx.environment.propertySources.size() == 1
        ctx.environment.propertySources.first().name == 'custom'

        cleanup:
        ctx.close()
    }

    void "test default configuration"() {
        given:
        ApplicationContextBuilder builder = ApplicationContext.builder()
        ApplicationContextConfiguration config = (ApplicationContextConfiguration) builder

        expect:
        !config.deduceEnvironments.isPresent()
        !config.deduceCloudEnvironment
        config.bannerEnabled
        config.enableDefaultPropertySources
        config.environmentPropertySource
        !config.eagerInitConfiguration
        !config.eagerInitSingletons
    }

    void "test enable cloud environment deduce"() {
        given:
        ApplicationContextBuilder builder = ApplicationContext.builder()
        ApplicationContextConfiguration config = (ApplicationContextConfiguration) builder

        when:
        builder.deduceCloudEnvironment(true)

        then:
        config.deduceCloudEnvironment
    }

    void "test context configuration"() {
        given:
        ApplicationContextBuilder builder = ApplicationContext.builder()
        def loader = new GroovyClassLoader()
        builder.classLoader(loader)
               .environments("foo")
               .deduceEnvironment(false)
               .deduceCloudEnvironment(true)

        ApplicationContextConfiguration config = (ApplicationContextConfiguration) builder

        expect:
        config.classLoader.is(loader)
        config.resourceLoader.classLoader.is(loader)
        config.environments.contains('foo')
        config.deduceEnvironments.get() == false
    }

    void "test context configurer with own class loader"() {
        given:
        def loader = new GroovyClassLoader()
        loader.parseClass("""
    package io.micronaut.context
    import io.micronaut.context.*

    class TestContextConfigurer implements ApplicationContextConfigurer {

        @Override
        void configure(ApplicationContextBuilder builder) {
            builder.environments("success")
        }
    }
""")
        loader.addURL(getClass().getResource("/test-meta-inf/"))

        ApplicationContext context = ApplicationContext.builder(loader)
            .start()

        expect:
        context.getEnvironment().getActiveNames().contains("success")
    }
}
