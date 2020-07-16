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
import io.netty.channel.kqueue.KQueueChannelOption
import io.netty.channel.unix.UnixChannelOption
import io.netty.util.internal.logging.InternalLogger
import io.netty.util.internal.logging.InternalLoggerFactory
import io.micronaut.context.ApplicationContext
import io.micronaut.context.DefaultApplicationContext
import io.micronaut.context.env.PropertySource
import io.micronaut.http.HttpMethod
import io.micronaut.http.netty.channel.EpollEventLoopGroupFactory
import io.micronaut.http.netty.channel.EventLoopGroupFactory
import io.micronaut.http.netty.channel.converters.ChannelOptionFactory
import io.micronaut.http.netty.channel.converters.DefaultChannelOptionFactory
import io.micronaut.http.netty.channel.converters.EpollChannelOptionFactory
import io.micronaut.http.netty.channel.converters.KQueueChannelOptionFactory
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

    void "test netty server epoll native channel option conversion"() {
        given:
        ChannelOptionFactory epollChannelOptionFactory = new EpollChannelOptionFactory()

        when:
        ChannelOption option = epollChannelOptionFactory.channelOption("TCP_QUICKACK")

        then:
        option == EpollChannelOption.TCP_QUICKACK

        when:
        option = epollChannelOptionFactory.channelOption("SO_BACKLOG")

        then:
        option == ChannelOption.SO_BACKLOG

        when:
        option = epollChannelOptionFactory.channelOption("DOMAIN_SOCKET_READ_MODE")

        then:
        option == UnixChannelOption.DOMAIN_SOCKET_READ_MODE

        when:
        option = epollChannelOptionFactory.channelOption("SO_SNDLOWAT")

        then:
        option != KQueueChannelOption.SO_SNDLOWAT

        when:
        option = epollChannelOptionFactory.channelOption("WRITE_BUFFER_WATER_MARK")

        then:
        option == ChannelOption.WRITE_BUFFER_WATER_MARK
    }

    void "test netty server kqueue native channel option conversion"() {
        given:
        ChannelOptionFactory kqueueChannelOptionFactory = new KQueueChannelOptionFactory()

        when:
        ChannelOption option = kqueueChannelOptionFactory.channelOption("TCP_QUICKACK")

        then:
        option != EpollChannelOption.TCP_QUICKACK

        when:
        option = kqueueChannelOptionFactory.channelOption("SO_BACKLOG")

        then:
        option == ChannelOption.SO_BACKLOG

        when:
        option = kqueueChannelOptionFactory.channelOption("DOMAIN_SOCKET_READ_MODE")

        then:
        option == UnixChannelOption.DOMAIN_SOCKET_READ_MODE

        when:
        option = kqueueChannelOptionFactory.channelOption("SO_SNDLOWAT")

        then:
        option == KQueueChannelOption.SO_SNDLOWAT

        when:
        option = kqueueChannelOptionFactory.channelOption("WRITE_BUFFER_WATER_MARK")

        then:
        option == ChannelOption.WRITE_BUFFER_WATER_MARK
    }

    void "test netty server default channel option conversion"() {
        given:
        ChannelOptionFactory channelOptionFactory = new DefaultChannelOptionFactory()

        when:
        ChannelOption option = channelOptionFactory.channelOption("TCP_QUICKACK")

        then:
        option != EpollChannelOption.TCP_QUICKACK

        when:
        option = channelOptionFactory.channelOption("SO_BACKLOG")

        then:
        option == ChannelOption.SO_BACKLOG

        when:
        option = channelOptionFactory.channelOption("DOMAIN_SOCKET_READ_MODE")

        then:
        option != UnixChannelOption.DOMAIN_SOCKET_READ_MODE

        when:
        option = channelOptionFactory.channelOption("SO_SNDLOWAT")

        then:
        option != KQueueChannelOption.SO_SNDLOWAT

        when:
        option = channelOptionFactory.channelOption("WRITE_BUFFER_WATER_MARK")

        then:
        option == ChannelOption.WRITE_BUFFER_WATER_MARK
    }

    @IgnoreIf({ ! Epoll.isAvailable() })
    void "test netty server use native transport configuration"() {
        given:
        ApplicationContext beanContext = new DefaultApplicationContext("test")
        beanContext.environment.addPropertySource(PropertySource.of("test",
              ['micronaut.server.netty.use-native-transport': true,
               'micronaut.server.netty.childOptions.tcpQuickack': true]

        ))
        beanContext.start()

        when:
        NettyHttpServerConfiguration config = beanContext.getBean(NettyHttpServerConfiguration)

        then:
        config.useNativeTransport
        config.childOptions.size() == 1

        when:
        def option = config.childOptions.keySet().first()

        then:
        option == EpollChannelOption.TCP_QUICKACK

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

        then:
        option == EpollChannelOption.TCP_QUICKACK
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
        config.parent.numThreads == 8
        config.worker.numThreads == 8

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

    void "test netty server access logger configuration"() {
        given:
        ApplicationContext beanContext = new DefaultApplicationContext("test")
        beanContext.environment.addPropertySource(PropertySource.of("test",
              ['micronaut.server.netty.access-logger.enabled': true,
               'micronaut.server.netty.access-logger.logger-name': 'mylogger',
               'micronaut.server.netty.access-logger.log-format': "%h %l %u %t \"%r\" %s %b \"%{Referer}i\" \"%{User-Agent}i\""]

        ))
        beanContext.start()

        when:
        NettyHttpServerConfiguration config = beanContext.getBean(NettyHttpServerConfiguration)

        then:
        config.accessLogger

        when:
        def accessLogConfig = config.accessLogger

        then:
        accessLogConfig.enabled
        accessLogConfig.logFormat == "%h %l %u %t \"%r\" %s %b \"%{Referer}i\" \"%{User-Agent}i\""
        accessLogConfig.loggerName == 'mylogger'

        cleanup:
        beanContext.close()
    }

    void "test netty server http2 configuration"() {
        given:
        ApplicationContext beanContext = new DefaultApplicationContext("test")
        beanContext.environment.addPropertySource(PropertySource.of("test",
              ['micronaut.server.netty.http2.max-frame-size': 20000,
               'micronaut.server.netty.http2.max-concurrent-streams': 100,
               'micronaut.server.netty.http2.push-enabled': false,
               'micronaut.server.netty.http2.header-table-size': 200,
               'micronaut.server.netty.http2.initial-window-size': 50,
               'micronaut.server.netty.http2.max-header-list-size': 150]
        ))
        beanContext.start()

        when:
        NettyHttpServerConfiguration config = beanContext.getBean(NettyHttpServerConfiguration)

        then:
        config.http2

        when:
        def http2 = config.http2

        then:
        http2.maxFrameSize == 20000
        http2.maxConcurrentStreams == 100
        !http2.pushEnabled
        http2.headerTableSize == 200
        http2.initialWindowSize == 50
        http2.maxHeaderListSize == 150

        cleanup:
        beanContext.close()
    }
}

class MemoryAppender extends AppenderBase<ILoggingEvent> {
    private final List<String> events = new ArrayList<>();

    @Override
    protected void append(ILoggingEvent e) {
        synchronized (events) {
            events.add(e.toString());
        }
    }

    public List<String> getEvents() {
        return events;
    }

}