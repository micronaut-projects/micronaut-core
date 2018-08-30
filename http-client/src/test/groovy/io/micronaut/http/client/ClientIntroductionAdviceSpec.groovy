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
package io.micronaut.http.client

import io.micronaut.context.ApplicationContext
import io.micronaut.discovery.ServiceInstance
import io.micronaut.discovery.ServiceInstanceList
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import javax.inject.Singleton

class ClientIntroductionAdviceSpec extends Specification {

    void "test implement HTTP client"() {
        given:
        ApplicationContext context = ApplicationContext.run()
        context.getBean(EmbeddedServer).start()
        MyClient myService = context.getBean(MyClient)

        expect:
        myService.index() == 'success'

        cleanup:
        context.close()
    }

    void "test multiple clients with the same id and different paths"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer)
        ApplicationContext client = ApplicationContext.run()
        client.registerSingleton(new TestServiceInstanceList(server.getURI()))

        expect:
        client.getBean(PolicyClient).index() == 'policy'
        client.getBean(OfferClient).index() == 'offer'

        cleanup:
        server.close()
        client.close()
    }

    @Controller('/aop')
    static class AopController implements MyApi {
        @Override
        String index() {
            return "success"
        }
    }

    @Controller('/policies')
    static class PolicyController {
        @Get
        String index() {
            "policy"
        }
    }

    @Controller('/offers')
    static class OfferController {
        @Get
        String index() {
            "offer"
        }
    }


    static interface MyApi {
        @Get(produces = MediaType.TEXT_PLAIN, consumes = MediaType.TEXT_PLAIN)
        String index()
    }

    @Client('/aop')
    static interface MyClient extends MyApi {
    }

    @Client(id="test-service", path="/policies")
    static interface PolicyClient {
        @Get
        String index()
    }

    @Client(id="test-service", path="/offers")
    static interface OfferClient {
        @Get
        String index()
    }

    class TestServiceInstanceList implements ServiceInstanceList {

        private final URI uri

        TestServiceInstanceList(URI uri) {
            this.uri = uri
        }

        @Override
        String getID() {
            return "test-service"
        }

        @Override
        List<ServiceInstance> getInstances() {
            [ServiceInstance.of("test-service-1", uri)]
        }
    }

}
