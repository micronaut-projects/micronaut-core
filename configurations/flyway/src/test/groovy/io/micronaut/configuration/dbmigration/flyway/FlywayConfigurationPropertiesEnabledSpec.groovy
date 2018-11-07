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

package io.micronaut.configuration.dbmigration.flyway

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.context.exceptions.NoSuchBeanException
import io.micronaut.inject.qualifiers.Qualifiers
import org.flywaydb.core.Flyway
import spock.lang.Specification

class FlywayConfigurationPropertiesEnabledSpec extends Specification {

    void 'if no flyway configuration then no FlywayConfigurationProperties and Flyway beans are created'() {
        given:
        ApplicationContext applicationContext = ApplicationContext.run(
            ['spec.name': FlywayConfigurationPropertiesEnabledSpec.simpleName] as Map,
            Environment.TEST
        )

        when:
        applicationContext.getBean(FlywayConfigurationProperties)

        then:
        def e = thrown(NoSuchBeanException)
        e.message.contains('No bean of type [' + FlywayConfigurationProperties.name + '] exists.')

        when:
        applicationContext.getBean(Flyway)

        then:
        e = thrown(NoSuchBeanException)
        e.message.contains('No bean of type [' + Flyway.name + '] exists.')

        cleanup:
        applicationContext.close()
    }

    void 'if flyway configuration then FlywayConfigurationProperties and Flyway beans are created'() {
        given:
        ApplicationContext applicationContext = ApplicationContext.run(
            ['spec.name'                         : FlywayConfigurationPropertiesEnabledSpec.simpleName,
             'flyway.movies.enabled'             : true,
             'datasources.movies.url'            : 'jdbc:h2:mem:flyway2Db;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE',
             'datasources.movies.username'       : 'sa',
             'datasources.movies.password'       : '',
             'datasources.movies.driverClassName': 'org.h2.Driver',

             'flyway.books.enabled'              : true,
             'datasources.books.url'             : 'jdbc:h2:mem:flywayDb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE',
             'datasources.books.username'        : 'sa',
             'datasources.books.password'        : '',
             'datasources.books.driverClassName' : 'org.h2.Driver',

            ] as Map
            , Environment.TEST
        )

        when:
        applicationContext.getBean(FlywayConfigurationProperties, Qualifiers.byName('movies'))
        applicationContext.getBean(Flyway, Qualifiers.byName('movies'))

        then:
        noExceptionThrown()

        when:
        applicationContext.getBean(FlywayConfigurationProperties, Qualifiers.byName('books'))
        applicationContext.getBean(Flyway, Qualifiers.byName('books'))

        then:
        noExceptionThrown()

        cleanup:
        applicationContext.close()
    }

    void 'if flyway is disabled, then FlywayConfigurationProperties bean is created but Flyway bean is not'() {
        given:
        ApplicationContext applicationContext = ApplicationContext.run(
            ['spec.name'                         : FlywayConfigurationPropertiesEnabledSpec.simpleName,
             'flyway.movies.enabled'             : false,
             'datasources.movies.url'            : 'jdbc:h2:mem:flyway2Db;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE',
             'datasources.movies.username'       : 'sa',
             'datasources.movies.password'       : '',
             'datasources.movies.driverClassName': 'org.h2.Driver',
            ] as Map
            , Environment.TEST
        )

        when:
        applicationContext.getBean(FlywayConfigurationProperties, Qualifiers.byName('movies'))

        then:
        noExceptionThrown()

        when:
        applicationContext.getBean(Flyway, Qualifiers.byName('movies'))

        then:
        def e = thrown(NoSuchBeanException)
        e.message.contains('No bean of type [' + Flyway.name + '] exists')

        cleanup:
        applicationContext.close()
    }
}
