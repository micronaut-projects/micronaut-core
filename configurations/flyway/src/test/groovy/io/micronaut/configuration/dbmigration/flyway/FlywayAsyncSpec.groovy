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
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.testutils.YamlAsciidocTagCleaner
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import javax.sql.DataSource

class FlywayAsyncSpec extends Specification implements YamlAsciidocTagCleaner {

    @Shared
    Map<String, Object> config = [
        'datasources.default.url'                      : 'jdbc:h2:mem:flywayDb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE',
        'datasources.default.username'                 : 'sa',
        'datasources.default.password'                 : '',
        'datasources.default.driverClassName'          : 'org.h2.Driver',

        'jpa.default.packages-to-scan'                 : ['example.micronaut'],
        'jpa.default.properties.hibernate.hbm2ddl.auto': 'none',
        'jpa.default.properties.hibernate.show_sql'    : true,

        'flyway.default.locations'                     : 'classpath:databasemigrations',
        'flyway.default.async'                         : true,
    ]

    @Shared
    @AutoCleanup
    ApplicationContext applicationContext = ApplicationContext.run(config as Map<String, Object>, Environment.TEST)

    void 'test flyway changelogs are executed asynchronously'() {
        when:
        applicationContext.getBean(DataSource)

        then:
        noExceptionThrown()

        when:
        FlywayConfigurationProperties config = applicationContext.getBean(FlywayConfigurationProperties, Qualifiers.byName('default'))

        then:
        noExceptionThrown()
        config.isAsync()

        when:
        PollingConditions conditions = new PollingConditions(timeout: 5)

        Map db = [url: 'jdbc:h2:mem:flywayDb', user: 'sa', password: '', driver: 'org.h2.Driver']
        Sql sql = Sql.newInstance(db.url, db.user, db.password, db.driver)

        then:
        conditions.eventually {
            sql.rows('select count(*) from books').get(0)[0] == 2
        }
    }
}
