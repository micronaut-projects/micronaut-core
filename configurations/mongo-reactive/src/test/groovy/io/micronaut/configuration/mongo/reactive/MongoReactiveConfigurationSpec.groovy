package io.micronaut.configuration.mongo.reactive

import com.mongodb.async.client.MongoClientSettings
import com.mongodb.reactivestreams.client.MongoClient
import groovy.transform.NotYetImplemented
import io.reactivex.Flowable
import io.micronaut.context.ApplicationContext
import io.micronaut.core.io.socket.SocketUtils
import io.micronaut.inject.qualifiers.Qualifiers
import spock.lang.Specification
import spock.lang.Unroll

class MongoReactiveConfigurationSpec extends Specification {

    void "test a basic connection"() {
        when:
        ApplicationContext applicationContext = ApplicationContext.run((MongoSettings.MONGODB_URI): "mongodb://localhost:${SocketUtils.findAvailableTcpPort()}")
        MongoClient mongoClient = applicationContext.getBean(MongoClient)

        then:
        !Flowable.fromPublisher(mongoClient.listDatabaseNames()).blockingIterable().toList().isEmpty()

        cleanup:
        applicationContext.stop()
    }

    @Unroll
    void "test configure #property pool setting"() {
        given:
        ApplicationContext context = ApplicationContext.run(
                (MongoSettings.MONGODB_URI): "mongodb://localhost:${SocketUtils.findAvailableTcpPort()}",
                ("${MongoSettings.PREFIX}.connectionPool.${property}".toString()): value
        )

        ReactiveMongoConfiguration configuration = context.getBean(ReactiveMongoConfiguration)
        MongoClientSettings clientSettings = configuration.buildSettings()



        expect:
        clientSettings.connectionPoolSettings."$property" == value

        and:
        context.stop()

        where:
        property  | value
        "maxSize" | 10
        "minSize" | 5


    }

    @Unroll
    @NotYetImplemented
    // FIXME: specifying URI overrides cluster settings
    void "test configure #property cluster setting"() {
        given:
        ApplicationContext context = ApplicationContext.run(
                ("${MongoSettings.PREFIX}.cluster.${property}".toString()): value,
                (MongoSettings.MONGODB_URI): "mongodb://localhost:27017"

        )

        ReactiveMongoConfiguration configuration = context.getBean(ReactiveMongoConfiguration)
        MongoClientSettings clientSettings = configuration.buildSettings()

        expect:
        clientSettings.clusterSettings."$property" == value

        and:
        context.stop()

        where:
        property           | value
        "maxWaitQueueSize" | 5
    }

    @Unroll
    void "test configure #property cluster setting for hosts"() {
        given:
        ApplicationContext context = ApplicationContext.run(
                ("${MongoSettings.PREFIX}.cluster.${property}".toString()): value,
                ("${MongoSettings.PREFIX}.cluster.hosts".toString()): "localhost:27017"

        )

        ReactiveMongoConfiguration configuration = context.getBean(ReactiveMongoConfiguration)
        MongoClientSettings clientSettings = configuration.buildSettings()

        expect:
        clientSettings.clusterSettings."$property" == value
        clientSettings.clusterSettings.hosts.size() == 1
        clientSettings.clusterSettings.hosts[0].host == 'localhost'
        clientSettings.clusterSettings.hosts[0].port == 27017


        and:
        context.stop()

        where:
        property           | value
        "maxWaitQueueSize" | 5
        "description"      | "my hosts"
    }

    @Unroll
    void "test configure #property pool setting for named server"() {
        given:
        ApplicationContext context = ApplicationContext.run(
                'mongodb.servers.myServer.uri': "mongodb://localhost:27017",
                ("mongodb.servers.myServer.connectionPool.${property}".toString()): value
        )

        NamedReactiveMongoConfiguration configuration = context.getBean(NamedReactiveMongoConfiguration, Qualifiers.byName('myServer'))
        MongoClientSettings clientSettings = configuration.buildSettings()
        MongoClient mongoClient = context.getBean(MongoClient, Qualifiers.byName('myServer'))

        expect:
        mongoClient != null
        clientSettings.connectionPoolSettings."$property" == value

        where:
        property  | value
        "maxSize" | 10
    }
}
