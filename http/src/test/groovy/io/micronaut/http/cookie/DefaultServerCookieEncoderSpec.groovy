package io.micronaut.http.cookie

import spock.lang.Specification

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class DefaultServerCookieEncoderSpec extends Specification {

    void "DefaultServerCookieEncoder can correctly encode a cookie from HttpCookieFactory"() {
        given:
        HttpCookieFactory factory = new HttpCookieFactory();
        ServerCookieEncoder cookieEncoder = new DefaultServerCookieEncoder()

        when:
        Cookie cookie = factory.create("SID", "31d4d96e407aad42").path("/").domain("example.com")

        then:
        "SID=31d4d96e407aad42; Path=/; Domain=example.com" == cookieEncoder.encode(cookie)[0]

        when:
        cookie = factory.create("SID", "31d4d96e407aad42").path("/").domain("example.com").sameSite(SameSite.Strict)

        then:
        "SID=31d4d96e407aad42; Path=/; Domain=example.com; SameSite=Strict" == cookieEncoder.encode(cookie)[0]

        when:
        cookie = factory.create("SID", "31d4d96e407aad42").path("/").secure().httpOnly()

        then: 'Netty uses HTTPOnly instead of HttpOnly'
        "SID=31d4d96e407aad42; Path=/; Secure; HttpOnly" == cookieEncoder.encode(cookie)[0]

        when:
        long maxAge = 2592000
        cookie = factory.create("id", "a3fWa").maxAge(maxAge)
        String result = cookieEncoder.encode(cookie).get(0)
        String expected = "id=a3fWa; Max-Age=2592000; " + Cookie.ATTRIBUTE_EXPIRES + "=" + expires(maxAge)
        String expected2 = "id=a3fWa; Max-Age=2592000; " + Cookie.ATTRIBUTE_EXPIRES + "=" + expires(maxAge + 1) // To prevent flakiness
        String expected3 = "id=a3fWa; Max-Age=2592000; " + Cookie.ATTRIBUTE_EXPIRES + "=" + expires(maxAge - 1) // To prevent flakiness

        then:
        expected == result || expected2 == result || expected3 == result
    }

    private static String expires(Long maxAgeSeconds) {
        ZoneId gmtZone = ZoneId.of("GMT")
        LocalDateTime localDateTime = LocalDateTime.now(gmtZone).plusSeconds(maxAgeSeconds)
        ZonedDateTime gmtDateTime = ZonedDateTime.of(localDateTime, gmtZone)
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'")
        gmtDateTime.format(formatter)
    }
}
