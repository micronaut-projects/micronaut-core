package io.micronaut.discovery.consul

import io.micronaut.runtime.server.EmbeddedServer
import io.reactivex.Flowable

trait MockConsulSpec {

    void waitFor(EmbeddedServer embeddedServer) {
        int attempts = 0
        while (!embeddedServer.isRunning()) {
            Thread.sleep(500)
            attempts++
            if (attempts > 5) {
                break
            }
        }
    }

    void waitForService(EmbeddedServer consulServer, String serviceName) {
        MockConsulServer consul = consulServer.applicationContext.getBean(MockConsulServer)
        int attempts = 0
        while (!Flowable.fromPublisher(consul.getServices()).blockingFirst().containsKey(serviceName)) {
            Thread.sleep(500)
            attempts++
            if (attempts > 5) {
                break
            }
        }
    }
}
