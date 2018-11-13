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

package io.micronaut.configuration.dbmigration.liquibase

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.context.exceptions.NoSuchBeanException
import io.micronaut.inject.qualifiers.Qualifiers
import spock.lang.Specification

class LiquibaseConfigurationPropertiesEnabledSpec extends Specification {

    void 'if no Liquibase configuration then LiquibaseConfigurationProperties bean is created'() {
        given:
        ApplicationContext applicationContext = ApplicationContext.run(
            ['spec.name': LiquibaseConfigurationPropertiesEnabledSpec.simpleName] as Map,
            Environment.TEST
        )

        when:
        applicationContext.getBean(LiquibaseConfigurationProperties)

        then:
        def e = thrown(NoSuchBeanException)
        e.message.contains('No bean of type [' + LiquibaseConfigurationProperties.name + '] exists.')

        cleanup:
        applicationContext.close()
    }

    void 'if Liquibase configuration then LiquibaseConfigurationProperties is created'() {
        given:
        ApplicationContext applicationContext = ApplicationContext.run(
            ['spec.name'                              : LiquibaseConfigurationPropertiesEnabledSpec.simpleName,
             'liquibase.datasources.movies.enabled'   : true,
             'liquibase.datasources.movies.change-log': 'classpath:db/liquibase-changelog.xml',
             'datasources.movies.url'                 : 'jdbc:h2:mem:liquibaseMoviesDb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE',
             'datasources.movies.username'            : 'sa',
             'datasources.movies.password'            : '',
             'datasources.movies.driverClassName'     : 'org.h2.Driver',
             'liquibase.datasources.books.enabled'    : true,
             'liquibase.datasources.books.change-log' : 'classpath:db/liquibase-changelog.xml',
             'datasources.books.url'                  : 'jdbc:h2:mem:liquibaseBooksDb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE',
             'datasources.books.username'             : 'sa',
             'datasources.books.password'             : '',
             'datasources.books.driverClassName'      : 'org.h2.Driver',
            ] as Map
            , Environment.TEST
        )
        when:
        applicationContext.getBean(LiquibaseConfigurationProperties, Qualifiers.byName('movies'))

        then:
        noExceptionThrown()

        when:
        applicationContext.getBean(LiquibaseConfigurationProperties, Qualifiers.byName('books'))

        then:
        noExceptionThrown()

        cleanup:
        applicationContext.close()
    }
}
