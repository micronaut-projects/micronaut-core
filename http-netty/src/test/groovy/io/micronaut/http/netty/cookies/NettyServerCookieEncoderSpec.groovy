package io.micronaut.http.netty.cookies

import io.micronaut.core.order.OrderUtil
import io.micronaut.http.cookie.Cookie
import io.micronaut.http.cookie.SameSite
import io.micronaut.http.cookie.ServerCookieEncoder
import spock.lang.Specification

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class NettyServerCookieEncoderSpec extends Specification {

    void "netty server cookie encoding"() {
        given:
        ServerCookieEncoder cookieEncoder = new NettyServerCookieEncoder()

        when:
        Cookie cookie = Cookie.of("SID", "31d4d96e407aad42").path("/").domain("example.com")

        then:
        "SID=31d4d96e407aad42; Path=/; Domain=example.com" == cookieEncoder.encode(cookie)[0]

        when:
        cookie = Cookie.of("SID", "31d4d96e407aad42").path("/").domain("example.com").sameSite(SameSite.Strict)

        then:
        "SID=31d4d96e407aad42; Path=/; Domain=example.com; SameSite=Strict" == cookieEncoder.encode(cookie)[0]

        when:
        cookie = Cookie.of("SID", "31d4d96e407aad42").path("/").secure().httpOnly()

        then: 'Netty uses HTTPOnly instead of HttpOnly'
        "SID=31d4d96e407aad42; Path=/; Secure; HTTPOnly" == cookieEncoder.encode(cookie)[0]

        when:
        long maxAge = 2592000
        String expected = "id=a3fWa; Max-Age=2592000; " + Cookie.ATTRIBUTE_EXPIRES + "=" + expires(maxAge)
        String expected2 = "id=a3fWa; Max-Age=2592000; " + Cookie.ATTRIBUTE_EXPIRES + "=" + expires(maxAge + 1) // To prevent flakiness
        cookie = Cookie.of("id", "a3fWa").maxAge(maxAge);
        String result = cookieEncoder.encode(cookie).get(0);

        then:
        expected == result || expected2 == result
    }

    void "ServerCookieEncoder is NettyServerCookieEncoder"() {
        expect:
        ServerCookieEncoder.INSTANCE instanceof NettyServerCookieEncoder
    }

    private static String expires(Long maxAgeSeconds) {
        ZoneId gmtZone = ZoneId.of("GMT")
        LocalDateTime localDateTime = LocalDateTime.now(gmtZone).plusSeconds(maxAgeSeconds)
        ZonedDateTime gmtDateTime = ZonedDateTime.of(localDateTime, gmtZone)
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'")
        gmtDateTime.format(formatter)
    }
}
