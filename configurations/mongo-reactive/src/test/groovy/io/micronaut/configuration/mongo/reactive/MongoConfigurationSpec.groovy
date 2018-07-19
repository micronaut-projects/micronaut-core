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

import com.mongodb.MongoClient
import com.mongodb.MongoClientOptions
import io.micronaut.context.ApplicationContext
import io.micronaut.core.io.socket.SocketUtils
import org.bson.Document
import spock.lang.Specification

/**
 * @author graemerocher
 * @since 1.0
 */
class MongoConfigurationSpec extends Specification {

    void "test a basic blocking driver connection"() {
        when:
        ApplicationContext applicationContext = ApplicationContext.run((MongoSettings.MONGODB_URI): "mongodb://localhost:${SocketUtils.findAvailableTcpPort()}")
        MongoClient mongoClient = applicationContext.getBean(MongoClient)

        then:
        !mongoClient.listDatabaseNames().isEmpty()

        when:
        def coll=   mongoClient.getDatabase("foo").getCollection("bar")

       coll.insertOne(new Document("foo", "bar"))

        then:
        coll.find().first().get("foo") == "bar"

        cleanup:
        applicationContext?.stop()
    }

    void "test build mongo client options"() {
        given:
        ApplicationContext applicationContext = ApplicationContext.run((MongoSettings.MONGODB_URI): "mongodb://localhost:${SocketUtils.findAvailableTcpPort()}",
            "mongodb.options.maxConnectionIdleTime":'10000'
        )

        DefaultMongoConfiguration configuration = applicationContext.getBean(DefaultMongoConfiguration)
        MongoClientOptions clientOptions = configuration.buildOptions()


        expect:
        clientOptions.getMaxConnectionIdleTime() == 10000

        cleanup:
        applicationContext?.close()

    }
}
