package io.micronaut.http.server.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
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
import spock.lang.Specification

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class DateHeaderSpec extends Specification {
    def 'date header is RFC 1123 and advances across a second boundary'() {
        given:
        def ctx = ApplicationContext.run(['spec.name': 'DateHeaderSpec'])
        def server = ((NettyHttpServer) ctx.getBean(EmbeddedServer)).buildEmbeddedChannel(false)
        def client = new EmbeddedChannel(new HttpClientCodec(), new HttpObjectAggregator(1024))
        EmbeddedTestUtil.connect(server, client)

        when:
        long before1 = System.currentTimeMillis().intdiv(1000)
        String date1 = exchange(server, client)
        long after1 = System.currentTimeMillis().intdiv(1000)
        // sleep into the next second
        Thread.sleep(1000 - (System.currentTimeMillis() % 1000) + 20)
        long before2 = System.currentTimeMillis().intdiv(1000)
        String date2 = exchange(server, client)
        long after2 = System.currentTimeMillis().intdiv(1000)

        then:
        long second1 = ZonedDateTime.parse(date1, DateTimeFormatter.RFC_1123_DATE_TIME).toEpochSecond()
        long second2 = ZonedDateTime.parse(date2, DateTimeFormatter.RFC_1123_DATE_TIME).toEpochSecond()
        date1.endsWith(' GMT')
        date2.endsWith(' GMT')
        second1 >= before1 && second1 <= after1
        second2 >= before2 && second2 <= after2
        second2 > second1

        cleanup:
        server.finishAndReleaseAll()
        client.finishAndReleaseAll()
        ctx.close()
    }

    def 'date header can be disabled'() {
        given:
        def ctx = ApplicationContext.run(['spec.name': 'DateHeaderSpec', 'micronaut.server.date-header': false])
        def server = ((NettyHttpServer) ctx.getBean(EmbeddedServer)).buildEmbeddedChannel(false)
        def client = new EmbeddedChannel(new HttpClientCodec(), new HttpObjectAggregator(1024))
        EmbeddedTestUtil.connect(server, client)

        expect:
        exchange(server, client) == null

        cleanup:
        server.finishAndReleaseAll()
        client.finishAndReleaseAll()
        ctx.close()
    }

    private static String exchange(EmbeddedChannel server, EmbeddedChannel client) {
        client.writeOutbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, '/date-header'))
        EmbeddedTestUtil.advance(server, client)
        FullHttpResponse response = client.readInbound()
        assert response.status() == HttpResponseStatus.OK
        String date = response.headers().get(HttpHeaderNames.DATE)
        response.release()
        return date
    }

    @Requires(property = 'spec.name', value = 'DateHeaderSpec')
    @Controller('/date-header')
    static class Ctrl {
        @Get
        String get() {
            return 'foo'
        }
    }
}
