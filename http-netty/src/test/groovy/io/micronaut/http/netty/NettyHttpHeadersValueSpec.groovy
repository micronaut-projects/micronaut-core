package io.micronaut.http.netty

import io.micronaut.core.convert.ConversionContext
import io.micronaut.core.convert.ConversionService
import io.micronaut.core.type.Argument
import io.netty.handler.codec.http.CombinedHttpHeaders
import io.netty.handler.codec.http.DefaultHttpHeaders
import spock.lang.Specification

class NettyHttpHeadersValueSpec extends Specification {

    void "a header converts its first value to a single value and all values to a collection"() {
        given:
        def nettyHeaders = new DefaultHttpHeaders()
        nettyHeaders.add("X-Number", "1")
        nettyHeaders.add("X-Number", "2")
        nettyHeaders.add("X-Single", "3")
        nettyHeaders.add("X-Empty", "")
        def headers = new NettyHttpHeaders(nettyHeaders, ConversionService.SHARED)

        expect:
        headers.get("X-Number", ConversionContext.of(Argument.of(Integer))).get() == 1
        headers.get("X-Number", ConversionContext.of(Argument.INT)).get() == 1
        headers.get("X-Number", ConversionContext.of(Argument.STRING)).get() == "1"
        headers.get("X-Number", ConversionContext.of(Argument.of(Optional, Integer))).get() == Optional.of(1)
        headers.get("X-Number", ConversionContext.of(Argument.listOf(Integer))).get() == [1, 2]
        headers.get("X-Single", ConversionContext.of(Argument.listOf(Integer))).get() == [3]
        headers.get("X-Empty", ConversionContext.of(Argument.STRING)).get() == ""
        headers.get("X-Empty", ConversionContext.of(Argument.of(Integer))).isEmpty()
        headers.get("X-Missing", ConversionContext.of(Argument.of(Integer))).isEmpty()
        headers.get("X-Missing", ConversionContext.of(Argument.listOf(Integer))).isEmpty()
    }

    void "a combined header converts its first value as before"() {
        given:
        def nettyHeaders = new CombinedHttpHeaders(true)
        nettyHeaders.add("X-Number", "1")
        nettyHeaders.add("X-Number", "2")
        def headers = new NettyHttpHeaders(nettyHeaders, ConversionService.SHARED)

        expect: "the first of the values getAll splits, not the combined value get gives"
        nettyHeaders.get("X-Number") == "1,2"
        headers.get("X-Number", ConversionContext.of(Argument.STRING)).get() == "1"
        headers.get("X-Number", ConversionContext.of(Argument.of(Integer))).get() == 1
    }
}
