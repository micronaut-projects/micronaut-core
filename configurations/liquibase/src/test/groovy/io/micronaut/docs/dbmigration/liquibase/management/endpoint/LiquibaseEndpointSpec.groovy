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
import io.micronaut.core.type.Argument
import io.micronaut.dbmigration.liquibase.management.endpoint.LiquibaseEndpoint
import io.micronaut.dbmigration.liquibase.management.endpoint.LiquibaseReport
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.RxHttpClient
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.testutils.YamlAsciidocTagCleaner
import spock.lang.Specification

class LiquibaseEndpointSpec extends Specification {

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
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
                'jpa.default.packages-to-scan': 'example.micronaut',
                'jpa.default.properties.hibernate.hbm2ddl.auto': 'none',
                'jpa.default.properties.hibernate.show_sql': true,
                'liquibase.default.change-log'      : 'classpath:db/liquibase-changelog.xml',
                'endpoints.liquibase.sensitive'     : false,
                'datasources.default.url': 'jdbc:h2:mem:liquibaseDisabledDb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE',
                'datasources.default.username': 'sa',
                'datasources.default.password': '',
                'datasources.default.driver-class-name': 'org.h2.Driver',
        ], Environment.TEST)
        URL server = embeddedServer.getURL()
        RxHttpClient rxClient = embeddedServer.applicationContext.createBean(RxHttpClient, server)

        when:
        HttpResponse<List> response = rxClient.toBlocking().exchange(HttpRequest.GET("/liquibase"),
                Argument.of(List, LiquibaseReport))

        then:
        response.status() == HttpStatus.OK
        response.body().size() == 1
        response.body().size() == 1
        response.body()[0].name == 'default'
        response.body()[0].changeSets.size() == 2
        response.body()[0].changeSets[0].changeLog == 'classpath:db/changelog/01-create-books-schema.xml'
        response.body()[0].changeSets[1].changeLog == 'classpath:db/changelog/02-insert-data-books.xml'

        cleanup:
        rxClient.close()
        embeddedServer.stop()
        embeddedServer.close()
    }
}
