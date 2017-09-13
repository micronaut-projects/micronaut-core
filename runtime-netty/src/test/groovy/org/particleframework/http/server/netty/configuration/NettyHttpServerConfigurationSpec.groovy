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
package org.particleframework.http.server.netty.configuration

import io.netty.channel.ChannelOption
import org.particleframework.context.ApplicationContext
import org.particleframework.context.DefaultApplicationContext
import org.particleframework.context.env.MapPropertySource
import org.particleframework.http.server.netty.NettyHttpServer
import spock.lang.Specification

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class NettyHttpServerConfigurationSpec extends Specification {

    void "test netty server configuration"() {
        given:
        ApplicationContext beanContext = new DefaultApplicationContext("test")
        beanContext.environment.addPropertySource(new MapPropertySource(
                'particle.server.netty.childOptions.autoRead':'true',
                'particle.server.netty.worker.threads':8
        ))
        beanContext.start()

        when:
        NettyHttpServerConfiguration config = beanContext.getBean(NettyHttpServerConfiguration)
        NettyHttpServer server = beanContext.getBean(NettyHttpServer)
        server.start()

        then:
        server != null
        config.childOptions.size() == 1
        config.childOptions.keySet().first() instanceof ChannelOption
        config.host == 'localhost'
//        config.worker.threads == 8

        cleanup:
        beanContext.close()
    }
}
