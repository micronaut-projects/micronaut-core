package io.micronaut.configuration.mongo.reactive

import com.mongodb.MongoClient
import io.micronaut.configuration.mongo.reactive.health.MongoHealthIndicator
import io.micronaut.context.ApplicationContext
import io.micronaut.core.io.socket.SocketUtils
import io.micronaut.health.HealthStatus
import io.micronaut.management.health.indicator.HealthResult
import io.reactivex.Flowable
import spock.lang.Specification

import java.util.concurrent.TimeUnit

class MongoHealthIndicatorSpec extends Specification {

    void "test mongo health indicator DOWN"() {
        when:
        ApplicationContext applicationContext = ApplicationContext.run(
                (MongoSettings.MONGODB_URI): "mongodb://localhost:${SocketUtils.findAvailableTcpPort()}",
                (MongoSettings.EMBEDDED): false
        )
        try {
            Flowable.fromPublisher(applicationContext.getBean(com.mongodb.reactivestreams.client.MongoClient).listDatabaseNames()).timeout(2, TimeUnit.SECONDS).blockingFirst()
        } catch (e) {
        }
        MongoHealthIndicator healthIndicator = applicationContext.getBean(MongoHealthIndicator)

        then:
        Flowable.fromPublisher(healthIndicator.result).blockingFirst().status == HealthStatus.DOWN

        cleanup:
        applicationContext.close()
    }

    void "test mongo health indicator UP"() {
        when:
        ApplicationContext applicationContext = ApplicationContext.run(
                (MongoSettings.MONGODB_URI): "mongodb://localhost:${SocketUtils.findAvailableTcpPort()}"
        )
        try {
            Flowable.fromPublisher(applicationContext.getBean(com.mongodb.reactivestreams.client.MongoClient).listDatabaseNames()).timeout(2, TimeUnit.SECONDS).blockingFirst()
        } catch (e) {
        }
        MongoHealthIndicator healthIndicator = applicationContext.getBean(MongoHealthIndicator)

        def healthResult = Flowable.fromPublisher(healthIndicator.result).blockingFirst()
        then:
        healthResult.status == HealthStatus.UP
        healthResult.details.containsKey("mongodb (Primary)")

        cleanup:
        applicationContext.close()
    }
}
