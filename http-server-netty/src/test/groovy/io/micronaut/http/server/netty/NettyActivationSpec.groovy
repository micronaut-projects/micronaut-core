/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.http.server.netty

import io.micronaut.buffer.netty.NettyByteBufferFactory
import io.micronaut.context.ApplicationContext
import io.micronaut.http.client.netty.DefaultNettyHttpClientRegistry
import io.micronaut.http.netty.channel.DefaultEventLoopGroupRegistry
import spock.lang.Specification
import spock.lang.Unroll

class NettyActivationSpec extends Specification {

    @Unroll
    void "Netty activation global=#global server=#server client=#client"() {
        given:
        Map<String, Object> properties = [:]
        if (global != null) {
            properties['netty.enabled'] = global
        }
        if (server != null) {
            properties['micronaut.server.netty.enabled'] = server
        }
        if (client != null) {
            properties['micronaut.http.client.netty.enabled'] = client
        }

        when:
        ApplicationContext context = ApplicationContext.run(properties)

        then:
        context.containsBean(NettyByteBufferFactory) == sharedEnabled
        context.containsBean(DefaultEventLoopGroupRegistry) == sharedEnabled
        context.containsBean(DefaultNettyEmbeddedServerFactory) == serverEnabled
        context.containsBean(DefaultNettyHttpClientRegistry) == clientEnabled

        cleanup:
        context.close()

        where:
        global | server | client || sharedEnabled | serverEnabled | clientEnabled
        null   | null   | null   || true          | true          | true
        true   | true   | true   || true          | true          | true
        false  | null   | null   || false         | false         | false
        true   | false  | null   || true          | false         | true
        true   | null   | false  || true          | true          | false
    }
}
