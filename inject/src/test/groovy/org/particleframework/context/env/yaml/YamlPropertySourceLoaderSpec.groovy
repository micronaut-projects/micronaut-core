package org.particleframework.context.env.yaml

import org.particleframework.context.env.DefaultEnvironment
import org.particleframework.context.env.Environment
import org.particleframework.context.env.PropertySource
import org.particleframework.context.env.PropertySourceLoader
import org.particleframework.core.io.service.ServiceDefinition
import org.particleframework.core.io.service.SoftServiceLoader
import spock.lang.Specification

/**
 * Created by graemerocher on 15/06/2017.
 */
class YamlPropertySourceLoaderSpec extends Specification {

    void "test load yaml properties source"() {
        given:
        def mock = Mock(SoftServiceLoader)
        def serviceDefinition = Mock(ServiceDefinition)
        serviceDefinition.isPresent() >> true
        serviceDefinition.load() >> new YamlPropertySourceLoader()
        mock.iterator() >> [serviceDefinition].iterator()

        Environment env = new DefaultEnvironment(["test"] as String[]) {
            @Override
            protected SoftServiceLoader<PropertySourceLoader> readPropertySourceLoaders() {
                return mock
            }

            @Override
            Optional<InputStream> getResourceAsStream(String path) {
                if(path.endsWith('-test.yml')) {
                    return Optional.of(new ByteArrayInputStream('''\
dataSource:
    jmxExport: true
    username: sa
    password: 'test'
'''.bytes))
                }
                else {
                    return Optional.of(new ByteArrayInputStream('''\
hibernate:
    cache:
        queries: false
dataSource:
    pooled: true
    driverClassName: org.h2.Driver
    username: sa
    password: ''    
'''.bytes))
                }
            }
        }


        when:
        env.start()

        then:
        env.get("hibernate.cache.queries", Boolean).get() == false
        env.get("dataSource.pooled", Boolean).get() == true
        env.get("dataSource.password", String).get() == 'test'
        env.get("dataSource.jmxExport", boolean).get() == true
    }
}
