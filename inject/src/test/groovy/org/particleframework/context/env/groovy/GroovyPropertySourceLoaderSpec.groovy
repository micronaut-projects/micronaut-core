package org.particleframework.context.env.groovy

import org.particleframework.context.env.Environment
import org.particleframework.context.env.PropertySource
import spock.lang.Specification

/**
 * Created by graemerocher on 15/06/2017.
 */
class GroovyPropertySourceLoaderSpec extends Specification {

    void "test load yaml properties source"() {
        given:
        Environment env = Mock(Environment)
        env.isPresent(_) >> true
        env.getName() >> "test"
        env.getResourceAsStream("application.groovy") >> {
            Optional.of(new ByteArrayInputStream('''\
hibernate {
    cache {
        queries = false
    }
}
dataSource {
    pooled = true
    driverClassName = "org.h2.Driver"
    username = "sa"
    password = ''   
}
'''.bytes))
        }
        env.getResourceAsStream("application-test.groovy") >> {
            Optional.of(new ByteArrayInputStream('''\
dataSource {
    jmxExport = true
    username = 'sa'
    password = 'test'
}
'''.bytes))
        }

        when:
        GroovyPropertySourceLoader loader = new GroovyPropertySourceLoader()
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

