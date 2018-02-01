package org.particleframework.configuration.mongo.reactive

import com.mongodb.reactivestreams.client.MongoClient
import io.reactivex.Flowable
import org.particleframework.context.ApplicationContext
import org.particleframework.core.io.socket.SocketUtils

class MongoConfigurationSpec extends FlapdoodleSpec {
    
    void "test a basic connection"() {
        given:
        int port = SocketUtils.findAvailableTcpPort()
        startServers(port)
        
        when:
        ApplicationContext applicationContext = ApplicationContext.run('particle.mongo.uri': "mongodb://localhost:${port}")
        MongoClient mongoClient = applicationContext.getBean(MongoClient)
        
        then:
        Flowable.fromPublisher(mongoClient.listDatabaseNames()).blockingIterable().toList() == ["admin", "local"]

        cleanup:
        applicationContext.stop()
    }

    void "test a clustered connection"() {
        given:
        List<Integer> ports = []
        (1..2).each {
            int offset = it * 1000
            int port = SocketUtils.findAvailableTcpPort(5000 + offset, 6000 + offset - 1)
            ports.add(port)
        }
        List<String> uris = ports.collect { "mongodb://localhost:${it}" }
        startServers(ports.toArray([] as Integer[]))

        when:
        ApplicationContext applicationContext = ApplicationContext.run('particle.mongo.uris': uris)
        MongoClient mongoClient = applicationContext.getBean(MongoClient)

        then:
        mongoClient.settings.clusterSettings.hosts.size() == 5
        Flowable.fromPublisher(mongoClient.listDatabaseNames()).blockingIterable().toList() == ["admin", "local"]

        cleanup:
        applicationContext.stop()
    }
}
