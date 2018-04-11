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
