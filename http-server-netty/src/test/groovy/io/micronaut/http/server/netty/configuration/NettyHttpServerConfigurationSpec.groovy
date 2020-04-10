/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.http.server.netty.configuration

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import java.util.ArrayList
import java.util.List

import org.slf4j.LoggerFactory

import io.micronaut.http.server.HttpServerConfiguration
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.ChannelOption
import io.netty.channel.epoll.Epoll
import io.netty.channel.epoll.EpollChannelOption
import io.netty.channel.epoll.EpollServerSocketChannel
import io.netty.util.internal.logging.InternalLogger
import io.netty.util.internal.logging.InternalLoggerFactory
import io.micronaut.context.ApplicationContext
import io.micronaut.context.DefaultApplicationContext
import io.micronaut.context.env.PropertySource
import io.micronaut.http.HttpMethod
import io.micronaut.http.netty.channel.EpollEventLoopGroupFactory
import io.micronaut.http.netty.channel.EventLoopGroupFactory
import io.micronaut.http.server.cors.CorsOriginConfiguration
import io.micronaut.http.server.netty.NettyHttpServer
import io.micronaut.http.server.types.files.SystemFile
import spock.lang.IgnoreIf
import spock.lang.Specification
import spock.lang.Unroll

import java.time.Duration
import java.util.AbstractMap.SimpleEntry

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class NettyHttpServerConfigurationSpec extends Specification {

    @Unroll
    void "test config for #key"() {
        given:
        def ctx = ApplicationContext.run(
                ("micronaut.server.$key".toString()): value
        )
        HttpServerConfiguration config = ctx.getBean(HttpServerConfiguration)


        expect:
        config[property] == expected

        cleanup:
        ctx.close()

        where:
        key                  | property           | value | expected
        'idle-timeout'       | 'idleTimeout'      | '15s' | Duration.ofSeconds(15)
        'read-idle-timeout'  | 'readIdleTimeout'  | '15s' | Duration.ofSeconds(15)
        'write-idle-timeout' | 'writeIdleTimeout' | '15s' | Duration.ofSeconds(15)
        'idle-timeout'       | 'idleTimeout'      | '-1s' | Duration.ofSeconds(-1)
    }

    void "test netty server use native transport configuration"() {
        given:
        ApplicationContext beanContext = new DefaultApplicationContext("test")
        beanContext.environment.addPropertySource(PropertySource.of("test",
              ['micronaut.server.netty.use-native-transport': true,
               'micronaut.server.netty.childOptions.tcpQuickack': true]

        ))
        beanContext.start()
        EpollEventLoopGroupFactory eventLoopGroupFactory = new EpollEventLoopGroupFactory()

        when:
        NettyHttpServerConfiguration config = beanContext.getBean(NettyHttpServerConfiguration)

        then:
        config.useNativeTransport
        config.childOptions.size() == 1

        when:
        def option = config.childOptions.keySet().first()
        def wrappedOption = eventLoopGroupFactory.processChannelOption(null, new SimpleEntry<>(option, true), beanContext.environment).getKey()

        then:
        option != EpollChannelOption.TCP_QUICKACK
        wrappedOption == EpollChannelOption.TCP_QUICKACK

        cleanup:
        beanContext.close()
    }

    @IgnoreIf({ ! Epoll.isAvailable() })
    void "test netty server use native transport"() {
        given:
        InternalLogger logger = InternalLoggerFactory.getInstance(ServerBootstrap.class)
        MemoryAppender appender = new MemoryAppender()
        Logger l = (Logger) LoggerFactory.getLogger(ServerBootstrap.class);
        l.addAppender(appender);
        appender.start()
        ApplicationContext beanContext = new DefaultApplicationContext("test")
        beanContext.environment.addPropertySource(PropertySource.of("test",
              ['micronaut.server.netty.use-native-transport': true,
               'micronaut.server.netty.childOptions.tcpQuickack': true]

        ))
        beanContext.start()

        when:
        NettyHttpServerConfiguration config = beanContext.getBean(NettyHttpServerConfiguration)
        EventLoopGroupFactory eventLoopGroupFactory = beanContext.getBean(EventLoopGroupFactory)

        then:
        config.useNativeTransport
        config.childOptions.size() == 1

        when:
        def option = config.childOptions.keySet().first()
        def wrappedOption = eventLoopGroupFactory.processChannelOption(null, new SimpleEntry<>(option, true), beanContext.environment).getKey()

        then:
        option != EpollChannelOption.TCP_QUICKACK
        wrappedOption == EpollChannelOption.TCP_QUICKACK
        eventLoopGroupFactory.serverSocketChannelClass() == EpollServerSocketChannel.class

        when:
        NettyHttpServer server = beanContext.getBean(NettyHttpServer)
        server.start()

        then:
        server != null

        when:
        server.stop()
        Thread.sleep(1000L)
        def error = null
        for (String message: appender.getEvents()) {
            if (message.contains('Unknown channel option \'TCP_QUICKACK\'')) {
                error = message
                break
            }
        }

        then:
        error == null

        cleanup:
        server.stop()
        beanContext.close()
    }

    void "test netty server configuration"() {
        given:
        ApplicationContext beanContext = new DefaultApplicationContext("test")
        beanContext.environment.addPropertySource(PropertySource.of("test",
                ['micronaut.server.netty.childOptions.autoRead': 'true',
                 'micronaut.server.netty.worker.threads'       : 8,
                 'micronaut.server.netty.parent.threads'       : 8,
                 'micronaut.server.multipart.maxFileSize'      : 2048,
                 'micronaut.server.maxRequestSize'             : '2MB',
                 'micronaut.server.netty.childOptions.write_buffer_water_mark.high': 262143,
                 'micronaut.server.netty.childOptions.write_buffer_water_mark.low' : 65535
                ]

        ))
        beanContext.start()

        when:
        NettyHttpServerConfiguration config = beanContext.getBean(NettyHttpServerConfiguration)

        then:
        !config.useNativeTransport
        config.maxRequestSize == 2097152
        config.multipart.maxFileSize == 2048
        config.childOptions.size() == 2
        config.childOptions.keySet().first() instanceof ChannelOption
        config.childOptions.keySet()[1] instanceof ChannelOption
        config.childOptions.get(ChannelOption.WRITE_BUFFER_WATER_MARK).high == 262143
        config.childOptions.get(ChannelOption.WRITE_BUFFER_WATER_MARK).low == 65535
        !config.host.isPresent()
        config.parent.numOfThreads == 8
        config.worker.numOfThreads == 8

        then:
        NettyHttpServer server = beanContext.getBean(NettyHttpServer)
        server.start()

        then:
        server != null

        cleanup:
        beanContext.close()
    }

    void "test cors configuration"() {
        given:
        ApplicationContext beanContext = new DefaultApplicationContext("test")
        beanContext.environment.addPropertySource(PropertySource.of("test",
                ['micronaut.server.cors.enabled'                            : true,
                 'micronaut.server.cors.configurations.foo.allowedOrigins'  : ['foo.com'],
                 'micronaut.server.cors.configurations.foo.allowedMethods'  : ['GET'],
                 'micronaut.server.cors.configurations.foo.maxAge'          : -1,
                 'micronaut.server.cors.configurations.bar.allowedOrigins'  : ['bar.com'],
                 'micronaut.server.cors.configurations.bar.allowedHeaders'  : ['Content-Type', 'Accept'],
                 'micronaut.server.cors.configurations.bar.exposedHeaders'  : ['x', 'y'],
                 'micronaut.server.cors.configurations.bar.maxAge'          : 150,
                 'micronaut.server.cors.configurations.bar.allowCredentials': false]

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

class MemoryAppender extends AppenderBase<ILoggingEvent> {
    private final List<String> events = new ArrayList<>();

    @Override
    protected void append(ILoggingEvent e) {
        synchronized (events) {
            events.add(e.toString())
        }
    }

    public List<String> getEvents() {
        return events;
    }

}