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
package io.micronaut.configuration.jdbc.hikari

import com.zaxxer.hikari.HikariDataSource
import io.micronaut.context.ApplicationContext
import io.micronaut.context.DefaultApplicationContext
import io.micronaut.context.env.MapPropertySource
import io.micronaut.inject.qualifiers.Qualifiers
import spock.lang.Specification

import javax.sql.DataSource
import java.sql.ResultSet

class DatasourceConfigurationSpec extends Specification {

    void "test no configuration"() {
        given:
        ApplicationContext applicationContext = new DefaultApplicationContext("test")
        applicationContext.start()

        expect: "No beans are created"
        !applicationContext.containsBean(HikariDataSource)
        !applicationContext.containsBean(DatasourceConfiguration)

        cleanup:
        applicationContext.close()
    }

    void "test blank configuration"() {
        given:
        ApplicationContext applicationContext = new DefaultApplicationContext("test")
        applicationContext.environment.addPropertySource(MapPropertySource.of(
                'test',
                ['datasources.default': [:]]
        ))
        applicationContext.start()

        expect:
        applicationContext.containsBean(DataSource)
        applicationContext.containsBean(DatasourceConfiguration)

        when:
        HikariUrlDataSource dataSource = applicationContext.getBean(DataSource)

        then: //The default configuration is supplied because H2 is on the classpath
        dataSource.jdbcUrl == 'jdbc:h2:mem:default;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE'
        dataSource.username == 'sa'
        dataSource.password == ''
        dataSource.driverClassName == 'org.h2.Driver'

        cleanup:
        applicationContext.close()
    }

    void "test operations with a blank connection"() {
        given:
        ApplicationContext applicationContext = new DefaultApplicationContext("test")
        applicationContext.environment.addPropertySource(MapPropertySource.of(
                'test',
                ['datasources.default': [:]]
        ))
        applicationContext.start()

        expect:
        applicationContext.containsBean(DataSource)
        applicationContext.containsBean(DatasourceConfiguration)

        when:
        HikariDataSource dataSource = applicationContext.getBean(DataSource)
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
                'test',
                ['datasources.default.connectionTimeout': 500,
                'datasources.default.idleTimeout': 20000,
                'datasources.default.catalog': 'foo',
                'datasources.default.autoCommit': true,
                'datasources.default.healthCheckProperties.foo': 'bar',
                'datasources.default.jndiName': 'java:comp/env/FooBarPool',
                'datasources.default.url': 'jdbc:h2:mem:foo;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE',
                'datasources.default.validationQuery': 'select 3']
        ))
        applicationContext.start()

        expect:
        applicationContext.containsBean(DataSource)
        applicationContext.containsBean(DatasourceConfiguration)

        when:
        HikariDataSource dataSource = applicationContext.getBean(DataSource)

        then:
        dataSource.connectionTimeout == 500
        dataSource.idleTimeout == 20000
        dataSource.catalog == 'foo'
        dataSource.autoCommit
        dataSource.healthCheckProperties.getProperty('foo') == 'bar'
        dataSource.dataSourceJNDI == 'java:comp/env/FooBarPool'
        dataSource.jdbcUrl == 'jdbc:h2:mem:foo;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE'
        dataSource.connectionTestQuery == 'select 3'

        cleanup:
        applicationContext.close()
    }

    void "test multiple data sources are configured"() {
        given:
        ApplicationContext applicationContext = new DefaultApplicationContext("test")
        applicationContext.environment.addPropertySource(MapPropertySource.of(
                'test',
                ['datasources.default': [:],
                'datasources.foo': [:]]
        ))
        applicationContext.start()

        expect:
        applicationContext.containsBean(DataSource)
        applicationContext.containsBean(DatasourceConfiguration)

        when:
        HikariDataSource dataSource = applicationContext.getBean(DataSource)

        then: //The default configuration is supplied because H2 is on the classpath
        dataSource.jdbcUrl == 'jdbc:h2:mem:default;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE'
        dataSource.username == 'sa'
        dataSource.password == ''
        dataSource.driverClassName == 'org.h2.Driver'

        when:
        dataSource = applicationContext.getBean(DataSource, Qualifiers.byName("foo"))

        then: //The default configuration is supplied because H2 is on the classpath
        dataSource.jdbcUrl == 'jdbc:h2:mem:foo;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE'
        dataSource.username == 'sa'
        dataSource.password == ''
        dataSource.driverClassName == 'org.h2.Driver'

        cleanup:
        applicationContext.close()
    }
}
