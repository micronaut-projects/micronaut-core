package org.particleframework.configuration.mongo.reactive

import com.mongodb.reactivestreams.client.MongoClient
import io.reactivex.Flowable
import org.particleframework.context.ApplicationContext
import org.particleframework.core.io.socket.SocketUtils

class MongoConfigurationSpec extends FlapdoodleSpec {
    
    void "test a basic connection"() {
        given:
        int port = SocketUtils.findAvailableTcpPort()
        startServer(port)
        
        when:
        ApplicationContext applicationContext = ApplicationContext.run('particle.mongo.uri': "mongodb://localhost:${port}")
        MongoClient mongoClient = applicationContext.getBean(MongoClient)
        
        then:
        Flowable.fromPublisher(mongoClient.listDatabaseNames()).blockingIterable().toList() == ["admin", "local"]

        cleanup:
        applicationContext.stop()
    }

}
