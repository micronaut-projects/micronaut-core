package org.particleframework.configuration.mongo.reactive

import com.mongodb.reactivestreams.client.MongoClient
import io.reactivex.Flowable
import org.particleframework.context.ApplicationContext
import org.particleframework.core.io.socket.SocketUtils
import spock.lang.Specification

class MongoConfigurationSpec extends Specification {
    
    void "test a basic connection"() {
        when:
        ApplicationContext applicationContext = ApplicationContext.run((MongoSettings.MONGODB_URI): "mongodb://localhost:27017")
        MongoClient mongoClient = applicationContext.getBean(MongoClient)
        
        then:
        Flowable.fromPublisher(mongoClient.listDatabaseNames()).blockingIterable().toList() == ["admin", "local"]

        cleanup:
        applicationContext.stop()
    }

}
