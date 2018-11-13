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

package io.micronaut.configuration.dbmigration.flyway.management.endpoint

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.RxHttpClient
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification

class FlywayEndpointSpec extends Specification {

    void "test flyway endpoint bean is available"() {
        given:
        ApplicationContext applicationContext = ApplicationContext.run(Environment.TEST)

        expect:
        applicationContext.containsBean(FlywayEndpoint)

        cleanup:
        applicationContext.close()
    }

    void "test the flyway endpoint bean can be disabled"() {
        given:
        ApplicationContext applicationContext = ApplicationContext.run(
            ['endpoints.flyway.enabled': false] as Map,
            Environment.TEST
        )

        expect:
        !applicationContext.containsBean(FlywayEndpoint)

        cleanup:
        applicationContext.close()
    }

    void "test the flyway endpoint bean is not available with all endpoints disabled"() {
        given:
        ApplicationContext applicationContext = ApplicationContext.run(
            ['endpoints.all.enabled': false] as Map,
            Environment.TEST
        )

        expect:
        !applicationContext.containsBean(FlywayEndpoint)

        cleanup:
        applicationContext.close()
    }

    void "test the flyway endpoint bean is available will all disabled but having it enabled"() {
        given:
        ApplicationContext applicationContext = ApplicationContext.run(
            ['endpoints.all.enabled'   : false,
             'endpoints.flyway.enabled': true] as Map,
            Environment.TEST
        )

        expect:
        applicationContext.containsBean(FlywayEndpoint)

        cleanup:
        applicationContext.close()
    }

    void 'test flyway endpoint'() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(
            EmbeddedServer,
            ['jpa.default.packages-to-scan'                 : 'example.micronaut',
             'jpa.default.properties.hibernate.hbm2ddl.auto': 'none',
             'jpa.default.properties.hibernate.show_sql'    : true,
             'flyway.datasources.default.locations'         : 'classpath:databasemigrations',
             'endpoints.flyway.sensitive'                   : false,
             'datasources.default.url'                      : 'jdbc:h2:mem:flywayDb1;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE',
             'datasources.default.username'                 : 'sa',
             'datasources.default.password'                 : '',
             'datasources.default.driver-class-name'        : 'org.h2.Driver'] as Map,
            Environment.TEST
        )
        URL server = embeddedServer.getURL()
        RxHttpClient rxClient = embeddedServer.applicationContext.createBean(RxHttpClient, server)

        when:
        HttpResponse<List<Map>> response = rxClient.toBlocking()
            .exchange(HttpRequest.GET("/flyway"), Argument.of(List, Map))

        then:
        response.status() == HttpStatus.OK
        List<FlywayReport> result = response.body()
        result.size() == 1
        result[0].name == 'default'
        result[0].migrations.size() == 2
        result[0].migrations[0].script == 'V1__create-books-schema.sql'
        result[0].migrations[1].script == 'V2__insert-data-books.sql'

        cleanup:
        rxClient.close()
        embeddedServer.close()
    }

    void 'test flyway endpoint with multiple datasources'() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(
            EmbeddedServer,
            ['jpa.default.packages-to-scan'                 : 'example.micronaut',
             'jpa.default.properties.hibernate.hbm2ddl.auto': 'none',
             'jpa.default.properties.hibernate.show_sql'    : true,
             'flyway.datasources.default.locations'         : 'classpath:databasemigrations',
             'flyway.datasources.other.locations'           : 'classpath:moremigrations',
             'endpoints.flyway.sensitive'                   : false,
             'datasources.default.url'                      : 'jdbc:h2:mem:flywayDb2;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE',
             'datasources.default.username'                 : 'sa',
             'datasources.default.password'                 : '',
             'datasources.default.driver-class-name'        : 'org.h2.Driver',
             'datasources.other.url'                        : 'jdbc:h2:mem:flywayDb3;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE',
             'datasources.other.username'                   : 'sa',
             'datasources.other.password'                   : '',
             'datasources.other.driver-class-name'          : 'org.h2.Driver'] as Map,
            Environment.TEST
        )
        URL server = embeddedServer.getURL()
        RxHttpClient rxClient = embeddedServer.applicationContext.createBean(RxHttpClient, server)

        when:
        HttpResponse<List> response = rxClient.toBlocking()
            .exchange(HttpRequest.GET("/flyway"), Argument.of(List, Map))

        then:
        response.status() == HttpStatus.OK
        List<FlywayReport> result = response.body()
        result.sort { it.name }
        result[0].name == 'default'
        result[0].migrations.size() == 2
        result[0].migrations[0].script == 'V1__create-books-schema.sql'
        result[0].migrations[1].script == 'V2__insert-data-books.sql'
        result[1].name == 'other'
        result[1].migrations.size() == 1
        result[1].migrations[0].script == 'V1__create-books-schema.sql'

        cleanup:
        rxClient.close()
        embeddedServer.close()
    }

    void 'test flyway endpoint without migrations'() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(
            EmbeddedServer,
            ['jpa.default.packages-to-scan'                 : 'example.micronaut',
             'jpa.default.properties.hibernate.hbm2ddl.auto': 'none',
             'jpa.default.properties.hibernate.show_sql'    : true,
             'endpoints.flyway.sensitive'                   : false,
             'datasources.default.url'                      : 'jdbc:h2:mem:flywayDb4;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE',
             'datasources.default.username'                 : 'sa',
             'datasources.default.password'                 : '',
             'datasources.default.driver-class-name'        : 'org.h2.Driver'] as Map,
            Environment.TEST
        )
        URL server = embeddedServer.getURL()
        RxHttpClient rxClient = embeddedServer.applicationContext.createBean(RxHttpClient, server)

        when:
        HttpResponse<List> response = rxClient.toBlocking()
            .exchange(HttpRequest.GET("/flyway"), Argument.of(List, Map))

        then:
        response.status() == HttpStatus.OK
        List<FlywayReport> result = response.body()
        result.size() == 0

        cleanup:
        rxClient.close()
        embeddedServer.close()
    }
}
