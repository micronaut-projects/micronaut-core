/*
 * Copyright 2017 original authors
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
package org.particleframework.http.server.netty

import org.particleframework.context.ApplicationContext
import org.particleframework.runtime.ParticleApplication
import spock.lang.Specification

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class NettyHttpServerSpec extends Specification {

    void "test simple Netty HTTP server"() {
        when:
        NettyHttpServer server = new NettyHttpServer()
        server.start()

        then:
        new URL("http://localhost:8080").getText() == "hello world"
        server.port  == 8080

        cleanup:
        server.stop()
    }

    void "test Particle server running"() {
        when:
        ApplicationContext applicationContext = ParticleApplication.run()

        then:
        new URL("http://localhost:8080").getText() == "hello world"

        cleanup:
        applicationContext.stop()
    }

}
