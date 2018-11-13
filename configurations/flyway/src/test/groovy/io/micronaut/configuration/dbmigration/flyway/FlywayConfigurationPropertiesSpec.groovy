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

import groovy.sql.Sql
import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import org.flywaydb.core.Flyway
import spock.lang.Specification

class FlywayConfigurationPropertiesSpec extends Specification {

    void "test change default database migrations locations"() {
        given:
        ApplicationContext applicationContext = ApplicationContext.run(
            ['spec.name'                         : FlywayConfigurationPropertiesSpec.simpleName,
             'datasources.books.url'             : 'jdbc:h2:mem:flywayBooksDB1;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE',
             'datasources.books.username'        : 'sa',
             'datasources.books.password'        : '',
             'datasources.books.driverClassName' : 'org.h2.Driver',
             'flyway.datasources.books.locations': 'classpath:databasemigrations',
            ] as Map,
            Environment.TEST
        )

        when:
        applicationContext.getBean(FlywayStartupEventListener)

        then:
        noExceptionThrown()

        when:
        applicationContext.getBean(Flyway)

        then:
        noExceptionThrown()

        when:
        Map db = [url: 'jdbc:h2:mem:flywayBooksDB1', user: 'sa', password: '', driver: 'org.h2.Driver']
        Sql sql = Sql.newInstance(db.url, db.user, db.password, db.driver)

        then:
        sql.rows('select count(*) from books').get(0)[0] == 2

        cleanup:
        applicationContext.close()
    }

    void "test define multiple locations from database migrations"() {
        given:
        ApplicationContext applicationContext = ApplicationContext.run(
            ['spec.name'                         : FlywayConfigurationPropertiesSpec.simpleName,
             'datasources.books.url'             : 'jdbc:h2:mem:flywayBooksDB2;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE',
             'datasources.books.username'        : 'sa',
             'datasources.books.password'        : '',
             'datasources.books.driverClassName' : 'org.h2.Driver',
             'flyway.datasources.books.locations': 'classpath:databasemigrations,classpath:othermigrations',
            ] as Map,
            Environment.TEST
        )

        when:
        applicationContext.getBean(FlywayStartupEventListener)

        then:
        noExceptionThrown()

        when:
        applicationContext.getBean(Flyway)

        then:
        noExceptionThrown()

        when:
        Map db = [url: 'jdbc:h2:mem:flywayBooksDB2', user: 'sa', password: '', driver: 'org.h2.Driver']
        Sql sql = Sql.newInstance(db.url, db.user, db.password, db.driver)

        then:
        sql.rows('select count(*) from books').get(0)[0] == 3

        cleanup:
        applicationContext.close()
    }

    void 'test define flyway database connection and not use Micronaut datasource'() {
        given:
        ApplicationContext applicationContext = ApplicationContext.run(
            ['spec.name'                         : FlywayConfigurationPropertiesSpec.simpleName,
             'flyway.datasources.books.locations': 'classpath:databasemigrations',
             'flyway.datasources.books.url'      : 'jdbc:h2:mem:flywayBooksDB3;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE',
             'flyway.datasources.books.user'     : 'sa',
             'flyway.datasources.books.password' : '',
            ] as Map,
            Environment.TEST
        )

        when:
        applicationContext.getBean(FlywayStartupEventListener)

        then:
        noExceptionThrown()

        when:
        applicationContext.getBean(Flyway)

        then:
        noExceptionThrown()

        when:
        Map db = [url: 'jdbc:h2:mem:flywayBooksDB3', user: 'sa', password: '', driver: 'org.h2.Driver']
        Sql sql = Sql.newInstance(db.url, db.user, db.password, db.driver)

        then:
        sql.rows('select count(*) from books').get(0)[0] == 2

        cleanup:
        applicationContext.close()
    }
}
