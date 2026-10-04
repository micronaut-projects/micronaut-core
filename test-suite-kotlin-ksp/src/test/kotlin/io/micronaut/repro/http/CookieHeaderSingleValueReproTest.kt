package io.micronaut.repro.http

import io.micronaut.http.HttpHeaders
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.client.netty.NettyClientHttpRequestFactory
import io.micronaut.http.cookie.Cookie
import io.micronaut.http.simple.SimpleHttpRequestFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CookieHeaderSingleValueReproTest {

    private val cookies = linkedSetOf(
        Cookie.of("a", "1"),
        Cookie.of("b", "2"),
        Cookie.of("a", "3") // latest value for 'a' should win
    )

    // Simple request factory
    @Test
    fun simpleRequest_addCookies_oneByOne_resultsInSingleCookieHeader() {
        val request: MutableHttpRequest<Any> = SimpleHttpRequestFactory().get<Any>("/")
        assertSingleCookieHeaderAfterAddingCookiesIndividually(request)
    }

    @Test
    fun simpleRequest_addCookies_bulk_resultsInSingleCookieHeader() {
        val request: MutableHttpRequest<Any> = SimpleHttpRequestFactory().get<Any>("/")
        assertSingleCookieHeaderAfterAddingCookiesBulk(request)
    }

    // Netty client request factory
    @Test
    fun nettyClientRequest_addCookies_oneByOne_resultsInSingleCookieHeader() {
        val request: MutableHttpRequest<Any> = NettyClientHttpRequestFactory().get<Any>("/")
        assertSingleCookieHeaderAfterAddingCookiesIndividually(request)
    }

    @Test
    fun nettyClientRequest_addCookies_bulk_resultsInSingleCookieHeader() {
        val request: MutableHttpRequest<Any> = NettyClientHttpRequestFactory().get<Any>("/")
        assertSingleCookieHeaderAfterAddingCookiesBulk(request)
    }

    private fun assertSingleCookieHeaderAfterAddingCookiesIndividually(request: MutableHttpRequest<*>) {
        cookies.forEach { request.cookie(it) }
        assertSingleCookieHeaderAndValues(request)
    }

    private fun assertSingleCookieHeaderAfterAddingCookiesBulk(request: MutableHttpRequest<*>) {
        request.cookies(cookies)
        assertSingleCookieHeaderAndValues(request)
    }

    private fun assertSingleCookieHeaderAndValues(request: MutableHttpRequest<*>) {
        val cookieHeaders = request.headers.getAll(HttpHeaders.COOKIE)
        assertEquals(1, cookieHeaders.size, "Expected a single Cookie header value")

        val value = cookieHeaders[0]
        // Validate semantics (latest value wins, all expected cookies present)
        val parts = value.split(";".toRegex()).map { it.trim() }.filter { it.isNotEmpty() }
        assertTrue(parts.contains("a=3"), "Expected latest value for cookie 'a' to be present")
        assertTrue(parts.contains("b=2"), "Expected cookie 'b' to be present")
        assertFalse(parts.contains("a=1"), "Expected old value for cookie 'a' to be replaced")
    }
}
