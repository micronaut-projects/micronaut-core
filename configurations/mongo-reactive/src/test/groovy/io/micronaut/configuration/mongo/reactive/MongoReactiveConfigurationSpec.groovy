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

package io.micronaut.configuration.mongo.reactive

import com.mongodb.MongoClientSettings
import com.mongodb.ReadConcern
import com.mongodb.ReadPreference
import com.mongodb.WriteConcern
import com.mongodb.reactivestreams.client.MongoClient
import com.mongodb.reactivestreams.client.Success
import groovy.transform.NotYetImplemented
import io.reactivex.Flowable
import io.micronaut.context.ApplicationContext
import io.micronaut.core.io.socket.SocketUtils
import io.micronaut.inject.qualifiers.Qualifiers
import io.reactivex.Single
import spock.lang.Specification
import spock.lang.Unroll

class MongoReactiveConfigurationSpec extends Specification {

    void "test connection with connection string"() {
        when:
        ApplicationContext applicationContext = ApplicationContext.run((MongoSettings.MONGODB_URI): "mongodb://localhost:${SocketUtils.findAvailableTcpPort()}")
        MongoClient mongoClient = applicationContext.getBean(MongoClient)

        then:
        !Flowable.fromPublisher(mongoClient.listDatabaseNames()).blockingIterable().toList().isEmpty()

        when:"A POJO is saved"
        Success success = Single.fromPublisher(mongoClient.getDatabase("test").getCollection("test", Book).insertOne(new Book(
                title: "The Stand"
        ))).blockingGet()

        then:
        success != null

        cleanup:
        applicationContext.stop()
    }

    void "test connection with host"() {
        when:
        ApplicationContext applicationContext = ApplicationContext.run((MongoSettings.MONGODB_HOST): "")
        MongoClient mongoClient = applicationContext.getBean(MongoClient)

        then:
        !Flowable.fromPublisher(mongoClient.listDatabaseNames()).blockingIterable().toList().isEmpty()

        when:"A POJO is saved"
        Success success = Single.fromPublisher(mongoClient.getDatabase("test").getCollection("test", Book).insertOne(new Book(
                title: "The Stand"
        ))).blockingGet()

        then:
        success != null

        cleanup:
        applicationContext.stop()
    }


    @Unroll
    void "test configure #property client setting"() {
        given:
        ApplicationContext context = ApplicationContext.run(
                (MongoSettings.EMBEDDED): false,
                ("${MongoSettings.PREFIX}.${property}".toString()): value
        )

        DefaultReactiveMongoConfiguration configuration = context.getBean(DefaultReactiveMongoConfiguration)
        MongoClientSettings clientSettings = configuration.buildSettings()


        expect:
        clientSettings."$property" == expected

        and:
        context.stop()

        where:
        property         | value          | expected
        "readConcern"    | "LINEARIZABLE" | ReadConcern.LINEARIZABLE
        "writeConcern"   | "W1"           | WriteConcern.W1
        "readPreference" | "SECONDARY"    | ReadPreference.secondary()

    }


    @Unroll
    void "test configure #property pool setting"() {
        given:
        ApplicationContext context = ApplicationContext.run(
                (MongoSettings.EMBEDDED): false,
                (MongoSettings.MONGODB_URI): "mongodb://localhost:${SocketUtils.findAvailableTcpPort()}",
                ("${MongoSettings.PREFIX}.connectionPool.${property}".toString()): value
        )

        DefaultReactiveMongoConfiguration configuration = context.getBean(DefaultReactiveMongoConfiguration)
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
                (MongoSettings.EMBEDDED): false,
                ("${MongoSettings.PREFIX}.cluster.${property}".toString()): value,
                (MongoSettings.MONGODB_URI): "mongodb://localhost:27017"

        )

        DefaultReactiveMongoConfiguration configuration = context.getBean(DefaultReactiveMongoConfiguration)
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
                (MongoSettings.EMBEDDED): false,
                ("${MongoSettings.PREFIX}.cluster.${property}".toString()): value,

        )

        DefaultReactiveMongoConfiguration configuration = context.getBean(DefaultReactiveMongoConfiguration)
        MongoClientSettings clientSettings = configuration.buildSettings()

        expect:
        clientSettings.clusterSettings."$property" == value


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
                (MongoSettings.EMBEDDED): false,
                'mongodb.servers.myServer.uri': "mongodb://localhost:27017",
                ("mongodb.servers.myServer.connectionPool.${property}".toString()): value
        )

        NamedReactiveMongoConfiguration configuration = context.getBean(NamedReactiveMongoConfiguration, Qualifiers.byName('my-server'))
        MongoClientSettings clientSettings = configuration.buildSettings()
        MongoClient mongoClient = context.getBean(MongoClient, Qualifiers.byName('my-server'))

        expect:
        mongoClient != null
        clientSettings.connectionPoolSettings."$property" == value

        where:
        property  | value
        "maxSize" | 10
    }

     static class Book {
         String title
     }
}
