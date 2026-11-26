package io.micronaut.http.server.netty.handler.accesslog

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.server.netty.EmbeddedTestUtil
import io.micronaut.http.server.netty.NettyHttpServer
import io.micronaut.runtime.server.EmbeddedServer
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http.DefaultFullHttpRequest
import io.netty.handler.codec.http.FullHttpResponse
import io.netty.handler.codec.http.HttpClientCodec
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpObjectAggregator
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion
import org.slf4j.LoggerFactory
import spock.lang.Specification

import java.nio.charset.StandardCharsets

class AccessLogRequestCookieSpec extends Specification {

    def 'request cookie elements log the cookies the request sees'(List<String> cookieHeaders, String expectedSession) {
        given:
        def ctx = ApplicationContext.run([
                'spec.name': 'AccessLogRequestCookieSpec',
                'micronaut.server.netty.access-logger.enabled': true,
                'micronaut.server.netty.access-logger.logger-name': 'AccessLogRequestCookieSpec',
                'micronaut.server.netty.access-logger.log-format': '%{session}C %C',
        ])
        def server = ((NettyHttpServer) ctx.getBean(EmbeddedServer)).buildEmbeddedChannel(false)
        def client = new EmbeddedChannel(new HttpClientCodec(), new HttpObjectAggregator(1024))
        EmbeddedTestUtil.connect(server, client)

        def listAppender = new ListAppender<ILoggingEvent>()
        listAppender.start()
        def logger = (Logger) LoggerFactory.getLogger('AccessLogRequestCookieSpec')
        logger.addAppender(listAppender)

        def request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, '/access-log-cookie')
        cookieHeaders.each { request.headers().add(HttpHeaderNames.COOKIE, it) }

        when:
        client.writeOutbound(request)
        EmbeddedTestUtil.advance(server, client)
        FullHttpResponse response = client.readInbound()
        String body = response.content().toString(StandardCharsets.UTF_8)
        def (String requestSession, String requestAll) = body.split(' ', 2)

        then:
        response.status() == HttpResponseStatus.OK
        requestSession == expectedSession
        listAppender.list.size() == 1
        def (String loggedSession, String loggedAll) = listAppender.list[0].formattedMessage.split(' ', 2)
        loggedSession == requestSession
        cookieEntries(loggedAll) == requestAll

        cleanup:
        response?.release()
        logger.detachAppender(listAppender)
        server.finishAndReleaseAll()
        client.finishAndReleaseAll()
        ctx.close()

        where:
        cookieHeaders                                | expectedSession
        ['session=first; other=x', 'session=second'] | 'second'
        ['session=first', 'other=x', 'more=y']       | 'first'
        ['other=x', 'more=y']                        | '-'
    }

    private static String cookieEntries(String loggedCookies) {
        return loggedCookies.replaceAll(/^\[|\]$/, '').split(',').sort().join(',')
    }

    @Requires(property = 'spec.name', value = 'AccessLogRequestCookieSpec')
    @Controller('/access-log-cookie')
    static class Ctrl {
        @Get
        String get(HttpRequest<?> request) {
            String session = request.cookies.findCookie('session').map { it.value }.orElse('-')
            String all = request.cookies.values().collect { it.name + ':' + it.value }.sort().join(',')
            return session + ' ' + all
        }
    }
}
