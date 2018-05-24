/*
 * Copyright 2017-2018 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.configuration.jdbc.dbcp

import org.apache.commons.dbcp2.BasicDataSource
import io.micronaut.context.ApplicationContext
import io.micronaut.context.DefaultApplicationContext
import io.micronaut.context.env.MapPropertySource
import io.micronaut.inject.qualifiers.Qualifiers
import spock.lang.Specification

import java.sql.ResultSet

class DatasourceConfigurationSpec extends Specification {

    void "test no configuration"() {
        given:
        ApplicationContext applicationContext = new DefaultApplicationContext("test")
        applicationContext.start()

        expect: "No beans are created"
        !applicationContext.containsBean(BasicDataSource)
        !applicationContext.containsBean(DatasourceConfiguration)

        cleanup:
        applicationContext.close()
    }

    void "test blank configuration"() {
        given:
        ApplicationContext applicationContext = new DefaultApplicationContext("test")
        applicationContext.environment.addPropertySource(MapPropertySource.of(
                "test",
                ['datasources.default': [:]]
        ))
        applicationContext.start()

        expect:
        applicationContext.containsBean(BasicDataSource)
        applicationContext.containsBean(DatasourceConfiguration)

        when:
        BasicDataSource dataSource = applicationContext.getBean(BasicDataSource)

        then: //The default configuration is supplied because H2 is on the classpath
        dataSource.url == 'jdbc:h2:mem:default;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE'
        dataSource.username == 'sa'
        dataSource.password == ''
        dataSource.driverClassName == 'org.h2.Driver'
        dataSource.validationQuery == 'SELECT 1'

        cleanup:
        applicationContext.close()
    }

    void "test operations with a blank connection"() {
        given:
        ApplicationContext applicationContext = new DefaultApplicationContext("test")
        applicationContext.environment.addPropertySource(MapPropertySource.of(
                "test",
                ['datasources.default': [:]]
        ))
        applicationContext.start()

        expect:
        applicationContext.containsBean(BasicDataSource)
        applicationContext.containsBean(DatasourceConfiguration)

        when:
        BasicDataSource dataSource = applicationContext.getBean(BasicDataSource)
        ResultSet resultSet = dataSource.getConnection().prepareStatement("SELECT H2VERSION() FROM DUAL").executeQuery()
        resultSet.next()
        String version = resultSet.getString(1)

        then:
        version == '1.4.196'

        cleanup:
        applicationContext.close()
    }

    void "test properties are bindable"() {
        given:
        ApplicationContext applicationContext = new DefaultApplicationContext("test")
        applicationContext.environment.addPropertySource(MapPropertySource.of(
                "test",
                ['datasources.default.maxWaitMillis': 5000,
                'datasources.default.connectionProperties': 'prop1=value1;prop2=value2',
                'datasources.default.defaultAutoCommit': true,
                'datasources.default.defaultCatalog': 'catalog']
        ))
        applicationContext.start()

        expect:
        applicationContext.containsBean(BasicDataSource)
        applicationContext.containsBean(DatasourceConfiguration)

        when:
        BasicDataSource dataSource = applicationContext.getBean(BasicDataSource)

        then:
        dataSource.maxWaitMillis == 5000
        dataSource.connectionProperties.getProperty('prop1') == 'value1'
        dataSource.connectionProperties.getProperty('prop2') == 'value2'
        dataSource.defaultAutoCommit
        dataSource.defaultCatalog == 'catalog'

        cleanup:
        applicationContext.close()
    }

    void "test multiple data sources are configured"() {
        given:
        ApplicationContext applicationContext = new DefaultApplicationContext("test")
        applicationContext.environment.addPropertySource(MapPropertySource.of(
                "test",
                ['datasources.default': [:],
                'datasources.foo': [:]]
        ))
        applicationContext.start()

        expect:
        applicationContext.containsBean(BasicDataSource)
        applicationContext.containsBean(DatasourceConfiguration)

        when:
        BasicDataSource dataSource = applicationContext.getBean(BasicDataSource)

        then: //The default configuration is supplied because H2 is on the classpath
        dataSource.url == 'jdbc:h2:mem:default;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE'
        dataSource.username == 'sa'
        dataSource.password == ''
        dataSource.driverClassName == 'org.h2.Driver'

        when:
        dataSource = applicationContext.getBean(BasicDataSource, Qualifiers.byName("foo"))

        then: //The default configuration is supplied because H2 is on the classpath
        dataSource.url == 'jdbc:h2:mem:foo;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE'
        dataSource.username == 'sa'
        dataSource.password == ''
        dataSource.driverClassName == 'org.h2.Driver'

        cleanup:
        applicationContext.close()
    }
}
