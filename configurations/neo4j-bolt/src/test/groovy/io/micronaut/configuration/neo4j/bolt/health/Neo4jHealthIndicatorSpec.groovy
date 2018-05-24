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

package io.micronaut.configuration.neo4j.bolt.health

import io.micronaut.configuration.neo4j.bolt.embedded.EmbeddedNeo4jServer
import io.micronaut.context.ApplicationContext
import io.micronaut.core.io.socket.SocketUtils
import io.micronaut.health.HealthStatus
import io.micronaut.management.health.indicator.HealthResult
import io.reactivex.Flowable
import spock.lang.Specification

/**
 * @author graemerocher
 * @since 1.0
 */
class Neo4jHealthIndicatorSpec extends Specification {
    void "test neo4j health indicator"() {
        given:
        ApplicationContext applicationContext = ApplicationContext.run(
                'neo4j.uri':"bolt://localhost:${SocketUtils.findAvailableTcpPort()}",
                'neo4j.embedded.ephemeral':true
        )

        when:
        Neo4jHealthIndicator indicator = applicationContext.getBean(Neo4jHealthIndicator)
        HealthResult result = Flowable.fromPublisher(indicator.getResult()).blockingFirst()
        
        then:
        result.status == HealthStatus.UP
        result.details.nodes instanceof Integer

        when:
        applicationContext.getBean(EmbeddedNeo4jServer).close()
        result = Flowable.fromPublisher(indicator.getResult()).blockingFirst()

        then:
        result.status == HealthStatus.DOWN


        cleanup:
        applicationContext?.stop()
    }
}
