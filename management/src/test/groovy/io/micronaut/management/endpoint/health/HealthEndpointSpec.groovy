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
package io.micronaut.management.endpoint.health

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.MapPropertySource
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.RxHttpClient
import io.micronaut.management.health.aggregator.RxJavaHealthAggregator
import io.micronaut.management.health.indicator.diskspace.DiskSpaceIndicator
import io.micronaut.management.health.indicator.jdbc.JdbcIndicator
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification

import javax.sql.DataSource

class HealthEndpointSpec extends Specification {

    void "test the beans are available"() {
        given:
        ApplicationContext context = ApplicationContext.build("test").build()
        context.registerSingleton(Mock(DataSource))
        context.start()

        expect:
        context.containsBean(HealthEndpoint)
        context.containsBean(DiskSpaceIndicator)
        context.containsBean(RxJavaHealthAggregator)
        context.containsBean(JdbcIndicator)

        cleanup:
        context.close()
    }

    void "test the disk space bean can be disabled"() {
        given:
        ApplicationContext context = ApplicationContext.run(['endpoints.health.disk-space.enabled': false])

        expect:
        context.containsBean(HealthEndpoint)
        !context.containsBean(DiskSpaceIndicator)
        context.containsBean(RxJavaHealthAggregator)
        context.containsBean(JdbcIndicator)

        cleanup:
        context.close()
    }

    void "test that jdbc bean can be disabled"() {
        given:
        ApplicationContext context = ApplicationContext.run(['endpoints.health.jdbc.enabled': false])

        expect:
        context.containsBean(HealthEndpoint)
        context.containsBean(DiskSpaceIndicator)
        context.containsBean(RxJavaHealthAggregator)
        !context.containsBean(JdbcIndicator)

        cleanup:
        context.close()
    }

    void "test the beans are not available with health disabled"() {
        given:
        ApplicationContext context = ApplicationContext.run(['endpoints.health.enabled': false])

        expect:
        !context.containsBean(HealthEndpoint)
        !context.containsBean(DiskSpaceIndicator)
        !context.containsBean(RxJavaHealthAggregator)
        !context.containsBean(JdbcIndicator)

        cleanup:
        context.close()
    }

    void "test the beans are not available with all disabled"() {
        given:
        ApplicationContext context = ApplicationContext.run(['endpoints.all.enabled': false])

        expect:
        !context.containsBean(HealthEndpoint)
        !context.containsBean(DiskSpaceIndicator)
        !context.containsBean(RxJavaHealthAggregator)
        !context.containsBean(JdbcIndicator)

        cleanup:
        context.close()
    }

    void "test the beans are available with all disabled and health enabled"() {
        given:
        ApplicationContext context = ApplicationContext.run(['endpoints.all.enabled': false, 'endpoints.health.enabled': true])

        context.start()

        expect:
        context.containsBean(HealthEndpoint)
        context.containsBean(DiskSpaceIndicator)
        context.containsBean(RxJavaHealthAggregator)
        context.containsBean(JdbcIndicator)

        cleanup:
        context.close()
    }

    void "test health endpoint"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
                'datasources.one.url': 'jdbc:h2:mem:oneDb;MVCC=TRUE;LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE',
                'datasources.two.url': 'jdbc:h2:mem:twoDb;MVCC=TRUE;LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE'
        ])
        URL server = embeddedServer.getURL()
        RxHttpClient rxClient = embeddedServer.applicationContext.createBean(RxHttpClient, server)

        when:
        def response = rxClient.exchange("/health", Map).blockingFirst()
        Map result = response.body()


        then:
        response.code() == HttpStatus.OK.code
        result.status == "UP"
        result.details
        result.details.diskSpace.status == "UP"
        result.details.diskSpace.details.free > 0
        result.details.diskSpace.details.total > 0
        result.details.diskSpace.details.threshold == 1024L * 1024L * 10
        result.details.jdbc.status == "UP"
        result.details.jdbc.details."jdbc:h2:mem:oneDb".status == "UP"
        result.details.jdbc.details."jdbc:h2:mem:oneDb".details.database == "H2"
        result.details.jdbc.details."jdbc:h2:mem:oneDb".details.version == "1.4.196 (2017-06-10)"
        result.details.jdbc.details."jdbc:h2:mem:twoDb".status == "UP"
        result.details.jdbc.details."jdbc:h2:mem:twoDb".details.database == "H2"
        result.details.jdbc.details."jdbc:h2:mem:twoDb".details.version == "1.4.196 (2017-06-10)"

        cleanup:
        embeddedServer.close()
    }

    void "test health endpoint with a high diskspace threshold"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, ['endpoints.health.disk-space.threshold': '9999GB'])
        URL server = embeddedServer.getURL()
        RxHttpClient rxClient = embeddedServer.applicationContext.createBean(RxHttpClient, server)

        when:
        def response = rxClient.exchange("/health", Map).blockingFirst()
        Map result = response.body()

        then:
        response.code() == HttpStatus.OK.code
        result.status == "DOWN"
        result.details
        result.details.diskSpace.status == "DOWN"
        result.details.diskSpace.details.error.startsWith("Free disk space below threshold.")

        cleanup:
        embeddedServer.close()
    }

    void "test health endpoint with a non response jdbc datasource"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
                'datasources.one.url': 'jdbc:h2:mem:oneDb;MVCC=TRUE;LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE',
                'datasources.two.url': 'jdbc:mysql://localhost:59654/foo'
        ])
        URL server = embeddedServer.getURL()
        RxHttpClient rxClient = embeddedServer.applicationContext.createBean(RxHttpClient, server)

        when:
        def response = rxClient.exchange("/health", Map).blockingFirst()
        Map result = response.body()

        then:
        response.code() == HttpStatus.OK.code
        result.status == "DOWN"
        result.details
        result.details.jdbc.status == "DOWN"
        result.details.jdbc.details."jdbc:mysql://localhost:59654/foo".status == "DOWN"
        result.details.jdbc.details."jdbc:mysql://localhost:59654/foo".details.error.startsWith("com.mysql.cj.jdbc.exceptions.CommunicationsException")
        result.details.jdbc.details."jdbc:h2:mem:oneDb".status == "UP"

        cleanup:
        embeddedServer.close()

    }
}