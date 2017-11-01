package org.particleframework.context.env.yaml

import org.particleframework.context.env.Environment
import org.particleframework.context.env.PropertySource
import spock.lang.Specification

/**
 * Created by graemerocher on 15/06/2017.
 */
class YamlPropertySourceLoaderSpec extends Specification {

    void "test load yaml properties source"() {
        given:
        Environment env = Mock(Environment)
        env.isPresent(_) >> true
        env.getActiveNames() >> (["test"] as Set)
        env.getResourceAsStream("application.yml") >> {
            Optional.of(new ByteArrayInputStream('''\
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
env.getResourceAsStream("application-test.yml") >> {
                Optional.of(new ByteArrayInputStream('''\
dataSource:
    jmxExport: true
    username: sa
    password: 'test'
'''.bytes))
        }

        when:
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader()
        Optional<PropertySource> optional = loader.load(env)

        then:
        optional.isPresent()

        when:
        PropertySource propertySource = optional.get()

        then:
        propertySource.get("hibernate.cache.queries") == false
        propertySource.get("dataSource.pooled") == true
        propertySource.get("dataSource.password") == 'test'
        propertySource.get("dataSource.jmxExport") == true
    }
}
