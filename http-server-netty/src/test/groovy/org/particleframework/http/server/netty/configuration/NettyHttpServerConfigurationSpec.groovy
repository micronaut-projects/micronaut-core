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
import org.particleframework.context.env.PropertySource
import org.particleframework.http.HttpMethod
import org.particleframework.http.server.cors.CorsOriginConfiguration
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
        beanContext.environment.addPropertySource(PropertySource.of("test",
                ['particle.server.netty.childOptions.autoRead':'true',
                'particle.server.netty.worker.threads':8,
                'particle.server.netty.parent.threads':8,
                'particle.server.multipart.maxFileSize':2048,
                'particle.server.maxRequestSize':'2MB']

        ))
        beanContext.start()

        when:
        NettyHttpServerConfiguration config = beanContext.getBean(NettyHttpServerConfiguration)
        NettyHttpServer server = beanContext.getBean(NettyHttpServer)
        server.start()

        then:
        server != null
        config.maxRequestSize == 2097152
        config.multipart.maxFileSize == 2048
        config.childOptions.size() == 1
        config.childOptions.keySet().first() instanceof ChannelOption
        !config.host.isPresent()
        config.parent.threads == 8
        config.worker.threads == 8

        cleanup:
        beanContext.close()
    }

    void "test cors configuration"() {
        given:
        ApplicationContext beanContext = new DefaultApplicationContext("test")
        beanContext.environment.addPropertySource(PropertySource.of("test",
                ['particle.server.cors.enabled': true,
                'particle.server.cors.configurations.foo.allowedOrigins': ['foo.com'],
                'particle.server.cors.configurations.foo.allowedMethods': ['GET'],
                'particle.server.cors.configurations.foo.maxAge': -1,
                'particle.server.cors.configurations.bar.allowedOrigins': ['bar.com'],
                'particle.server.cors.configurations.bar.allowedHeaders': ['Content-Type', 'Accept'],
                'particle.server.cors.configurations.bar.exposedHeaders': ['x', 'y'],
                'particle.server.cors.configurations.bar.maxAge': 150,
                'particle.server.cors.configurations.bar.allowCredentials': false]

        ))
        beanContext.start()

        when:
        NettyHttpServerConfiguration config = beanContext.getBean(NettyHttpServerConfiguration)
        NettyHttpServer server = beanContext.getBean(NettyHttpServer)
        server.start()

        then:
        config.cors.enabled
        config.cors.configurations.size() == 2
        config.cors.configurations.containsKey('foo')
        config.cors.configurations.get('foo') instanceof CorsOriginConfiguration
        config.cors.configurations.get('foo').allowedOrigins == ['foo.com']
        config.cors.configurations.get('foo').allowedMethods == [HttpMethod.GET]
        config.cors.configurations.get('foo').allowedHeaders == ['*']
        !config.cors.configurations.get('foo').exposedHeaders
        config.cors.configurations.get('foo').allowCredentials
        config.cors.configurations.get('foo').maxAge == -1
        config.cors.configurations.containsKey('bar')
        config.cors.configurations.get('bar') instanceof CorsOriginConfiguration
        config.cors.configurations.get('bar').allowedOrigins == ['bar.com']
        config.cors.configurations.get('bar').allowedMethods == CorsOriginConfiguration.ANY_METHOD
        config.cors.configurations.get('bar').allowedHeaders == ['Content-Type', 'Accept']
        config.cors.configurations.get('bar').exposedHeaders == ['x', 'y']
        !config.cors.configurations.get('bar').allowCredentials
        config.cors.configurations.get('bar').maxAge == 150

        cleanup:
        beanContext.close()
    }
}
