package io.micronaut.http.server.netty.handler.accesslog.element

import spock.lang.Specification

/**
 * A backslash and a double quote are escaped, so that a client cannot forge an escape sequence or
 * close a quoted field of the access log.
 */
class AccessLogQuoteBackslashEscapeSpec extends Specification {

    void "backslashes and double quotes are escaped"() {
        expect:
        AccessLog.escape(value) == escaped

        where:
        value                                    | escaped
        'a\\x0ab'                                | 'a\\\\x0ab'
        'a"b'                                    | 'a\\"b'
        'Mozilla/5.0 (X11; Linux) "quoted" \\ é' | 'Mozilla/5.0 (X11; Linux) \\"quoted\\" \\\\ é'
        '"\n'                                    | '\\"\\x0a'
    }
}
