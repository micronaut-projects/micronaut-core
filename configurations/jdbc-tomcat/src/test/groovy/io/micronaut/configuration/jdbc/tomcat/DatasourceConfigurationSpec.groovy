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
package io.micronaut.configuration.jdbc.tomcat

import io.micronaut.configuration.jdbc.tomcat.metadata.TomcatDataSourcePoolMetadata
import io.micronaut.context.ApplicationContext
import io.micronaut.context.DefaultApplicationContext
import io.micronaut.context.env.MapPropertySource
import io.micronaut.context.exceptions.NoSuchBeanException
import io.micronaut.inject.qualifiers.Qualifiers
import spock.lang.Ignore
import spock.lang.Specification

import javax.sql.DataSource
import java.sql.ResultSet

class DatasourceConfigurationSpec extends Specification {

    void "test no configuration"() {
        given:
        ApplicationContext applicationContext = new DefaultApplicationContext("test")
        applicationContext.start()

        expect: "No beans are created"
        !applicationContext.containsBean(DataSource)
        !applicationContext.containsBean(DatasourceConfiguration)
        !applicationContext.containsBean(TomcatDataSourcePoolMetadata)

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
        applicationContext.containsBean(TomcatDataSourcePoolMetadata)

        when:
        DataSource dataSource = applicationContext.getBean(DataSource).targetDataSource

        then: //The default configuration is supplied because H2 is on the classpath
        dataSource.url == 'jdbc:h2:mem:default;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE'
        dataSource.username == 'sa'
        dataSource.poolProperties.password == ''
        dataSource.name == 'default'
        dataSource.driverClassName == 'org.h2.Driver'
        dataSource.abandonWhenPercentageFull == 0
        dataSource.accessToUnderlyingConnectionAllowed


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
        DataSource dataSource = applicationContext.getBean(DataSource)
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
        String context = UUID.randomUUID().toString()
        ApplicationContext applicationContext = new DefaultApplicationContext(context)
        applicationContext.environment.addPropertySource(MapPropertySource.of(
                'test',
                ['datasources.default.abandonWhenPercentageFull'          : 99,
                 'datasources.default.accessToUnderlyingConnectionAllowed': false,
                 'datasources.default.alternateUsernameAllowed'           : true,
                 'datasources.default.commitOnReturn'                     : true,
                 'datasources.default.connectionProperties'               : 'prop1=value1;prop2=value2',
                 'datasources.default.jndiName'                           : 'java:comp/env/FooBarPool',
                 'datasources.default.dbProperties.fileLock'              : 'FS',
                 'datasources.default.defaultAutoCommit'                  : true,
                 'datasources.default.defaultCatalog'                     : 'catalog']
        ))
        applicationContext.start()

        expect:
        applicationContext.containsBean(DataSource)
        applicationContext.containsBean(DatasourceConfiguration)
        applicationContext.containsBean(TomcatDataSourcePoolMetadata)

        when:
        DataSource dataSource = applicationContext.getBean(DataSource).targetDataSource

        then:
        dataSource.abandonWhenPercentageFull == 99
        dataSource.accessToUnderlyingConnectionAllowed //Currently no-oped
        dataSource.alternateUsernameAllowed
        dataSource.commitOnReturn
        dataSource.connectionProperties == 'prop1=value1;prop2=value2'
        dataSource.dataSourceJNDI == 'java:comp/env/FooBarPool'
        dataSource.dbProperties.get('FILE_LOCK') == 'FS'
        dataSource.defaultAutoCommit
        dataSource.defaultCatalog == 'catalog'
        dataSource.getPool()

        cleanup:
        applicationContext.close()
    }

    void "test multiple data sources are configured"() {
        given:
        String context = UUID.randomUUID().toString()
        ApplicationContext applicationContext = new DefaultApplicationContext(context)
        applicationContext.environment.addPropertySource(MapPropertySource.of(
                context,
                ['datasources.default': [:],
                 'datasources.foo'    : [:]]
        ))
        applicationContext.start()

        expect:
        applicationContext.containsBean(DataSource)
        applicationContext.containsBean(DatasourceConfiguration)

        when:
        DataSource dataSource = applicationContext.getBean(DataSource).targetDataSource

        then: //The default configuration is supplied because H2 is on the classpath
        dataSource.url == 'jdbc:h2:mem:default;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE'
        dataSource.username == 'sa'
        dataSource.poolProperties.password == ''
        dataSource.name == 'default'
        dataSource.driverClassName == 'org.h2.Driver'

        when:
        dataSource = applicationContext.getBean(DataSource, Qualifiers.byName("foo")).targetDataSource

        then: //The default configuration is supplied because H2 is on the classpath
        dataSource.url == 'jdbc:h2:mem:foo;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE'
        dataSource.username == 'sa'
        dataSource.poolProperties.password == ''
        dataSource.name == 'foo'
        dataSource.driverClassName == 'org.h2.Driver'

        cleanup:
        applicationContext.close()
    }


    void "test multiple datasources are all wired"() {
        given:
        DataSource dataSource
        String context = UUID.randomUUID().toString()
        ApplicationContext applicationContext = new DefaultApplicationContext(context)
        applicationContext.environment.addPropertySource(MapPropertySource.of(
                context,
                [
                        'datasources.default.abandonWhenPercentageFull'          : 99,
                        'datasources.default.accessToUnderlyingConnectionAllowed': false,
                        'datasources.default.alternateUsernameAllowed'           : true,
                        'datasources.default.commitOnReturn'                     : true,
                        'datasources.default.connectionProperties'               : 'prop1=value1;prop2=value2',
                        'datasources.default.jndiName'                           : 'java:comp/env/FooBarPool',
                        'datasources.default.dbProperties.fileLock'              : 'FS',
                        'datasources.default.defaultAutoCommit'                  : true,
                        'datasources.default.defaultCatalog'                     : 'catalog',

                        'datasources.person.abandonWhenPercentageFull'           : 99,
                        'datasources.person.accessToUnderlyingConnectionAllowed' : false,
                        'datasources.person.alternateUsernameAllowed'            : true,
                        'datasources.person.commitOnReturn'                      : true,
                        'datasources.person.connectionProperties'                : 'prop1=value1;prop2=value2',
                        'datasources.person.jndiName'                            : 'java:comp/env/FooBarPool',
                        'datasources.person.dbProperties.fileLock'               : 'FS',
                        'datasources.person.defaultAutoCommit'                   : true,
                        'datasources.person.defaultCatalog'                      : 'catalog'
                ]
        ))
        applicationContext.start()

        expect:
        applicationContext.getBeansOfType(DataSource).size() == 2
        applicationContext.getBeansOfType(DatasourceConfiguration).size() == 2

        when:
        applicationContext.getBean(TomcatDataSourcePoolMetadata, Qualifiers.byName("person2"))

        then:
        thrown(NoSuchBeanException)

        when:
        dataSource = applicationContext.getBean(DataSource, Qualifiers.byName("default")).targetDataSource

        then:
        dataSource.abandonWhenPercentageFull == 99
        dataSource.accessToUnderlyingConnectionAllowed //Currently no-oped
        dataSource.alternateUsernameAllowed
        dataSource.commitOnReturn
        dataSource.connectionProperties == 'prop1=value1;prop2=value2'
        dataSource.dataSourceJNDI == 'java:comp/env/FooBarPool'
        dataSource.dbProperties.get('FILE_LOCK') == 'FS'
        dataSource.defaultAutoCommit
        dataSource.defaultCatalog == 'catalog'

        when:
        dataSource = applicationContext.getBean(DataSource, Qualifiers.byName("person")).targetDataSource

        then:
        dataSource.abandonWhenPercentageFull == 99
        dataSource.accessToUnderlyingConnectionAllowed //Currently no-oped
        dataSource.alternateUsernameAllowed
        dataSource.commitOnReturn
        dataSource.connectionProperties == 'prop1=value1;prop2=value2'
        dataSource.dataSourceJNDI == 'java:comp/env/FooBarPool'
        dataSource.dbProperties.get('FILE_LOCK') == 'FS'
        dataSource.defaultAutoCommit
        dataSource.defaultCatalog == 'catalog'

        cleanup:
        applicationContext.close()
    }

    void "test multiple datasources metadata props"() {
        given:
        String context = UUID.randomUUID().toString()
        ApplicationContext applicationContext = new DefaultApplicationContext(context)
        applicationContext.environment.addPropertySource(MapPropertySource.of(
                context,
                [
                        'datasources.default.abandonWhenPercentageFull'          : 99,
                        'datasources.default.accessToUnderlyingConnectionAllowed': false,
                        'datasources.default.alternateUsernameAllowed'           : true,
                        'datasources.default.commitOnReturn'                     : true,
                        'datasources.default.connectionProperties'               : 'prop1=value1;prop2=value2',
                        'datasources.default.jndiName'                           : 'java:comp/env/FooBarPool',
                        'datasources.default.defaultAutoCommit'                  : true,
                        'datasources.default.defaultCatalog'                     : 'catalog',

                        'datasources.person.abandonWhenPercentageFull'           : 99,
                        'datasources.person.accessToUnderlyingConnectionAllowed' : false,
                        'datasources.person.alternateUsernameAllowed'            : true,
                        'datasources.person.commitOnReturn'                      : true,
                        'datasources.person.connectionProperties'                : 'prop1=value1;prop2=value2',
                        'datasources.person.jndiName'                            : 'java:comp/env/FooBarPool',
                        'datasources.person.defaultAutoCommit'                   : true,
                        'datasources.person.defaultCatalog'                      : 'catalog'
                ]
        ))
        applicationContext.start()

        def tomcatDataSourcePoolMetadataDefault = applicationContext.getBean(TomcatDataSourcePoolMetadata, Qualifiers.byName("default"))
        def tomcatDataSourcePoolMetadataPerson = applicationContext.getBean(TomcatDataSourcePoolMetadata, Qualifiers.byName("person"))

        expect:
        verifyAll {
            applicationContext.getBeansOfType(DataSource).size() == 2
            applicationContext.getBeansOfType(DatasourceConfiguration).size() == 2

            tomcatDataSourcePoolMetadataDefault.validationQuery == 'SELECT 1'
            tomcatDataSourcePoolMetadataDefault.max == 100
            tomcatDataSourcePoolMetadataDefault.min == 10
            tomcatDataSourcePoolMetadataDefault.defaultAutoCommit
            tomcatDataSourcePoolMetadataDefault.active == 0
            tomcatDataSourcePoolMetadataDefault.idle >= 0

            tomcatDataSourcePoolMetadataPerson.validationQuery == 'SELECT 1'
            tomcatDataSourcePoolMetadataPerson.max == 100
            tomcatDataSourcePoolMetadataPerson.min == 10
            tomcatDataSourcePoolMetadataPerson.defaultAutoCommit
            tomcatDataSourcePoolMetadataPerson.active == 0
            tomcatDataSourcePoolMetadataPerson.idle >= 0
        }
    }

    void "test pool is created without dupe properties"() {
        given:
        String context = UUID.randomUUID().toString()
        ApplicationContext applicationContext = new DefaultApplicationContext(context)
        applicationContext.environment.addPropertySource(MapPropertySource.of(
                context,
                [
                        'datasources.default.abandonWhenPercentageFull'          : 99,
                        'datasources.default.accessToUnderlyingConnectionAllowed': false,
                        'datasources.default.alternateUsernameAllowed'           : true,
                        'datasources.default.commitOnReturn'                     : true,
                        'datasources.default.connectionProperties'               : 'prop1=value1;prop2=value2',
                        'datasources.default.jndiName'                           : 'java:comp/env/FooBarPool',
                        'datasources.default.defaultAutoCommit'                  : true,
                        'datasources.default.defaultCatalog'                     : 'catalog',

                        'datasources.person.abandonWhenPercentageFull'           : 99,
                        'datasources.person.accessToUnderlyingConnectionAllowed' : false,
                        'datasources.person.alternateUsernameAllowed'            : true,
                        'datasources.person.commitOnReturn'                      : true,
                        'datasources.person.connectionProperties'                : 'prop1=value1;prop2=value2',
                        'datasources.person.jndiName'                            : 'java:comp/env/FooBarPool',
                        'datasources.person.defaultAutoCommit'                   : true,
                        'datasources.person.defaultCatalog'                      : 'catalog'
                ]
        ))
        applicationContext.start()

        when:
        org.apache.tomcat.jdbc.pool.DataSource dataSource = applicationContext.getBean(DataSource, Qualifiers.byName("person")).targetDataSource

        then:
        dataSource.getPool()
    }

    void "test pool is created"() {
        given:
        String context = UUID.randomUUID().toString()
        ApplicationContext applicationContext = new DefaultApplicationContext(context)
        applicationContext.environment.addPropertySource(MapPropertySource.of(
                context,
                [
                        'datasources.default.abandonWhenPercentageFull'          : 99,
                        'datasources.default.accessToUnderlyingConnectionAllowed': false,
                        'datasources.default.alternateUsernameAllowed'           : true,
                        'datasources.default.commitOnReturn'                     : true,
                        'datasources.default.connectionProperties'               : 'prop1=value1;prop2=value2',
                        'datasources.default.jndiName'                           : 'java:comp/env/FooBarPool',
                        'datasources.default.dbProperties.fileLock'              : 'FS',
                        'datasources.default.defaultAutoCommit'                  : true,
                        'datasources.default.defaultCatalog'                     : 'catalog',

                        'datasources.person.abandonWhenPercentageFull'           : 99,
                        'datasources.person.accessToUnderlyingConnectionAllowed' : false,
                        'datasources.person.alternateUsernameAllowed'            : true,
                        'datasources.person.commitOnReturn'                      : true,
                        'datasources.person.connectionProperties'                : 'prop1=value1;prop2=value2',
                        'datasources.person.jndiName'                            : 'java:comp/env/FooBarPool',
                        'datasources.person.dbProperties.fileLock'               : 'FS',
                        'datasources.person.defaultAutoCommit'                   : true,
                        'datasources.person.defaultCatalog'                      : 'catalog'
                ]
        ))
        applicationContext.start()

        when:
        org.apache.tomcat.jdbc.pool.DataSource dataSource = applicationContext.getBean(DataSource, Qualifiers.byName("person")).targetDataSource

        then:
        dataSource.getPool()
    }
}
