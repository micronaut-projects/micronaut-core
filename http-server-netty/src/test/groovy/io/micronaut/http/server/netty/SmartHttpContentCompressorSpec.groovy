package io.micronaut.http.server.netty

import io.netty.handler.codec.http.DefaultHttpHeaders
import io.netty.handler.codec.http.DefaultHttpResponse
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpHeaders
import io.netty.handler.codec.http.HttpResponse
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion
import spock.lang.Specification
import spock.lang.Unroll

class SmartHttpContentCompressorSpec extends Specification {

    private static String compressible = "text/html"
    private static String inCompressible = "image/png"

    @Unroll
    void "test #type with #length"() {
        expect:
        HttpHeaders headers = new DefaultHttpHeaders()
        if (type != null) {
            headers.add(HttpHeaderNames.CONTENT_TYPE, type)
        }
        if (length != null) {
            headers.add(HttpHeaderNames.CONTENT_LENGTH, length)
        }
        HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, headers)
        new SmartHttpContentCompressor(new DefaultHttpCompressionStrategy(1024, 6)).shouldSkip(response) == expected

        where:
        type           | length | expected
        compressible   | 1024   | false     // compressible type and equal to 1k
        compressible   | 1023   | true      // compressible type but smaller than 1k
        compressible   | null   | false     // compressible type but unknown size
        compressible   | 0      | true      // compressible type no content
        inCompressible | 1      | true      // incompressible, always skip
        inCompressible | 5000   | true      // incompressible, always skip
        inCompressible | null   | true      // incompressible, always skip
        inCompressible | 0      | true      // incompressible, always skip
        null           | null   | true      // if the content type is unknown, skip
    }
}
