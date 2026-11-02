package io.micronaut.http.server.netty.handler.accesslog.element

import spock.lang.Specification

/**
 * Access log formats quote values such as the request line and the user agent, so a backslash or a double quote in a
 * value is escaped as well: otherwise a client could end the quoted field early and forge the fields after it.
 */
class AccessLogQuoteBackslashSpec extends Specification {

    void "backslashes and double quotes are escaped"() {
        expect:
        AccessLog.escape(value) == escaped

        where:
        value                                       | escaped
        'a"b'                                       | 'a\\"b'
        'a\\b'                                      | 'a\\\\b'
        'Mozilla/5.0 (X11; Linux) "quoted" \\ é'    | 'Mozilla/5.0 (X11; Linux) \\"quoted\\" \\\\ é'
        '" 200 1 "-'                                | '\\" 200 1 \\"-'
        '\\x1b'                                     | '\\\\x1b'
        '"\u001b\\ '                           | '\\"\\x1b\\\\\\u2028'
    }
}
