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
package io.micronaut.docs.dbmigration.liquibase.management.endpoint

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.dbmigration.liquibase.management.endpoint.LiquibaseEndpoint
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.RxHttpClient
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.testutils.YamlAsciidocTagCleaner
import spock.lang.Specification

class LiquibaseEndpointSpec extends Specification implements YamlAsciidocTagCleaner {

    void "test the endpoint bean is available"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.
            run(EmbeddedServer,
                ['endpoints.beans.sensitive': false] as Map,
                Environment.TEST)

        expect:
        embeddedServer.applicationContext.containsBean(LiquibaseEndpoint)

        cleanup:
        embeddedServer.stop()
    }

    void "test the endpoint bean can be disabled"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext
            .run(EmbeddedServer,
                 ['endpoints.liquibase.enabled': false] as Map,
                 Environment.TEST)

        expect:
        !embeddedServer.applicationContext.containsBean(LiquibaseEndpoint)

        cleanup:
        embeddedServer.stop()
    }

    void "test the endpoint bean is not available will all disabled"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext
            .run(EmbeddedServer,
                 ['endpoints.all.enabled': false] as Map,
                 Environment.TEST)

        expect:
        !embeddedServer.applicationContext.containsBean(LiquibaseEndpoint)

        cleanup:
        embeddedServer.stop()
    }

    void "test the endpoint bean is available will all disabled but having it enabled"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext
            .run(EmbeddedServer,
                 ['endpoints.all.enabled'      : false,
                  'endpoints.liquibase.enabled': true] as Map,
                 Environment.TEST)

        expect:
            embeddedServer.applicationContext.containsBean(LiquibaseEndpoint)

        cleanup:
            embeddedServer.stop()
    }

    void 'test liquibase endpoint'() {
        given:
        Map<String, Object> liquibaseMap = [
            'jpa.default'                       : [
                'packages-to-scan': 'example.micronaut',
                properties        : [
                    hibernate: [
                        'hbm2ddl.auto': 'none',
                        'show_sql'    : true,
                    ]
                ]
            ],
            'liquibase.default.change-log'      : 'classpath:db/liquibase-changelog.xml',
            'endpoints.liquibase.sensitive'     : false,
            datasources                         : [
                default: [
                    url            : 'jdbc:h2:mem:liquibaseDisabledDb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE',
                    username       : 'sa',
                    password       : '',
                    driverClassName: 'org.h2.Driver',
                ]
            ],
            ////////////////////////// REMOVE THIS //////////////////////
            'micronaut.http.client.read-timeout': '1000s'
            ////////////////////////// REMOVE THIS //////////////////////
        ]

        Map<String, Object> config = [:] << flatten(liquibaseMap)

        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, config, Environment.TEST)
        URL server = embeddedServer.getURL()
        RxHttpClient rxClient = embeddedServer.applicationContext.createBean(RxHttpClient, server)

        when:
        def response = rxClient.exchange("/liquibase", Map).blockingFirst()
        def result = response.body()

        then:
        println "=" * 100
        println "result -> " + result
        println "=" * 100
        response.code() == HttpStatus.OK.code
        result != null
    }
}
