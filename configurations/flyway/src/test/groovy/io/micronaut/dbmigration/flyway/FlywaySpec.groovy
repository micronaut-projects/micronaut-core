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

package io.micronaut.dbmigration.flyway

import groovy.sql.Sql
import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.testutils.YamlAsciidocTagCleaner
import org.yaml.snakeyaml.Yaml
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import javax.sql.DataSource

class FlywaySpec extends Specification implements YamlAsciidocTagCleaner {

    String yamlConfig = '''\
//tag::yamlconfig[]
datasources:
    default: # <2>
        url: 'jdbc:h2:mem:flywayDb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE'
        username: 'sa'
        password: ''
        driverClassName: 'org.h2.Driver'
jpa:
    default:
        packages-to-scan:
            - 'example.micronaut'
        properties:
            hibernate:
                hbm2ddl:
                    auto: none # <1>
                show_sql: true
flyway:
    default: # <2>
        enabled: true
'''//end::yamlconfig[]

    @Shared
    Map<String, Object> flywayMap = [
        jpa        : [
            default: [
                'packages-to-scan': ['example.micronaut'],
                properties        : [
                    hibernate: [
                        hbm2ddl   : [
                            auto: 'none'
                        ],
                        'show_sql': true,
                    ]
                ]

            ]
        ],
        flyway     : [
            default: [
                enabled: true
            ]
        ],
        datasources: [
            default: [
                url            : 'jdbc:h2:mem:flywayDb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE',
                username       : 'sa',
                password       : '',
                driverClassName: 'org.h2.Driver',
            ]
        ]
    ]

    @Shared
    Map<String, Object> config = [:] << flatten(flywayMap)

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, config as Map<String, Object>, Environment.TEST)

    void 'test flyway changelogs are executed'() {
        when:
        embeddedServer.applicationContext.getBean(DataSource)

        then:
        noExceptionThrown()

        when:
        FlywayConfigurationProperties config = embeddedServer.applicationContext.getBean(FlywayConfigurationProperties, Qualifiers.byName('default'))

        then:
        noExceptionThrown()
        !config.isAsync()

        when:
        Map m = new Yaml().load(cleanYamlAsciidocTag(yamlConfig))

        then:
        m == flywayMap

        when:
        Map db = [url: 'jdbc:h2:mem:flywayDb', user: 'sa', password: '', driver: 'org.h2.Driver']
        Sql sql = Sql.newInstance(db.url, db.user, db.password, db.driver)

        then:
        sql.rows('select count(*) from books').get(0)[0] == 2
    }
}
