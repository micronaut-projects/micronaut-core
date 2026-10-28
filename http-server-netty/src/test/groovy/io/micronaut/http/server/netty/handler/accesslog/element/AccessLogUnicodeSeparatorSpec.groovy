package io.micronaut.http.server.netty.handler.accesslog.element

import spock.lang.Specification

/**
 * Response header values may carry Unicode line separators and bidirectional controls, which
 * break or visually reorder a log line.
 */
class AccessLogUnicodeSeparatorSpec extends Specification {

    void "Unicode line separators and bidi controls are escaped"() {
        expect:
        AccessLog.escape(value) == escaped

        where:
        value               | escaped
        'a b'          | 'a\\u2028b'
        'a b'          | 'a\\u2029b'
        'a‮b'          | 'a\\u202eb'
        'a‪b‭c'   | 'a\\u202ab\\u202dc'
        'a⁦b⁩c'   | 'a\\u2066b\\u2069c'
        'a‎b‏c'   | 'a\\u200eb\\u200fc'
        'a؜b'          | 'a\\u061cb'
    }

    void "other non-ASCII characters are logged unchanged"() {
        expect:
        AccessLog.escape(value).is(value)

        where:
        value << ['é', '‧ ⁥⁪', 'привет']
    }
}
