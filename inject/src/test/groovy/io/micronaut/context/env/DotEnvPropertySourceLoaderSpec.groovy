package io.micronaut.context.env

import io.micronaut.core.io.service.ServiceDefinition
import io.micronaut.core.io.service.SoftServiceLoader
import spock.lang.Specification

class DotEnvPropertySourceLoaderSpec extends Specification {

    void "test dot-env property source loader"() {
        given:
        def serviceDefinition = Mock(ServiceDefinition)
        serviceDefinition.isPresent() >> true
        serviceDefinition.load() >> new DotEnvPropertySourceLoader()

        Environment env = new DefaultEnvironment({ ["test"] }) {
            @Override
            protected SoftServiceLoader<PropertySourceLoader> readPropertySourceLoaders() {
                GroovyClassLoader gcl = new GroovyClassLoader()
                gcl.addURL(DotEnvPropertySourceLoader.getResource("/META-INF/services/io.micronaut.context.env.PropertySourceLoader"))
                return new SoftServiceLoader<PropertySourceLoader>(PropertySourceLoader, gcl)
            }

            @Override
            Optional<InputStream> getResourceAsStream(String path) {
                return Optional.of(new ByteArrayInputStream('''\
HIBERNATE_CACHE_QUERIES=false
DATASOURCE_POOLED   = true
DATASOURCE_DRIVER-CLASS-NAME="org.h2.Driver"
'''.bytes))
            }
        }

        when:
        env.start()

        then:
        env.get("hibernate.cache.queries", Boolean).get() == false
        env.get("datasource.pooled", Boolean).get() == true
        env.get("datasource.driver-class-name", String).get() == 'org.h2.Driver'

    }
}
