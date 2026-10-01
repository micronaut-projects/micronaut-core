package io.micronaut.http.server.netty.binding

import io.micronaut.core.convert.DefaultMutableConversionService
import io.micronaut.http.HttpHeaders
import io.micronaut.http.netty.body.NettyByteBodyFactory
import io.micronaut.http.server.netty.configuration.NettyHttpServerConfiguration
import io.micronaut.http.server.netty.NettyHttpRequest
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.DefaultFullHttpRequest
import io.netty.handler.codec.http.HttpVersion
import spock.lang.Specification

import static io.netty.handler.codec.http.HttpMethod.POST

class NettyHttpRequestFormTypeSpec extends Specification {

    private NettyHttpRequest request(String contentType) {
        DefaultFullHttpRequest nettyRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, POST, "/form")
        if (contentType != null) {
            nettyRequest.headers().set(HttpHeaders.CONTENT_TYPE, contentType)
        }
        return new NettyHttpRequest(nettyRequest, NettyByteBodyFactory.empty(), Mock(ChannelHandlerContext), new DefaultMutableConversionService(), new NettyHttpServerConfiguration())
    }

    void "hasFormBody for #contentType is #expected, and stays so on repeated calls"() {
        given:
        NettyHttpRequest request = request(contentType)

        expect:
        request.hasFormBody() == expected
        request.hasFormBody() == expected
        request.hasFormBody() == expected

        where:
        contentType                                          | expected
        null                                                 | false
        'application/json'                                   | false
        'text/plain'                                         | false
        'application/x-www-form-urlencoded'                  | true
        'application/x-www-form-urlencoded; charset=utf-8'   | true
        'APPLICATION/X-WWW-FORM-URLENCODED'                  | true
        'multipart/form-data; boundary=abc'                  | true
        'multipart/form-data; boundary="abc"'                | true
        'multipart/form-data'                                | false
        'multipart/mixed; boundary=abc'                      | false
    }

    void "the form type follows a changed Content-Type header"() {
        given:
        NettyHttpRequest request = request('application/json')

        expect:
        !request.hasFormBody()

        when:
        request.mutate().contentType('application/x-www-form-urlencoded')

        then:
        request.hasFormBody()

        when:
        request.mutate().contentType('multipart/form-data; boundary=xyz')

        then:
        request.hasFormBody()

        when:
        request.mutate().contentType('multipart/form-data')

        then:
        !request.hasFormBody()

        when:
        request.mutate().contentType('application/json')

        then:
        !request.hasFormBody()
    }

    void "getRawFormFields rejects a non-form request"() {
        given:
        NettyHttpRequest request = request('application/json')

        when:
        request.getRawFormFields()

        then:
        thrown(IllegalStateException)
    }
}
