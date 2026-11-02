package io.micronaut.http.server.netty.handler.accesslog.element

import spock.lang.Specification

/**
 * A value mixing escaped and unescaped characters keeps every unescaped character in place, in order.
 */
class AccessLogMixedEscapingSpec extends Specification {

    void "a value mixing escaped and unescaped characters is escaped in place"() {
        expect:
        AccessLog.escape(value) == escaped

        where:
        value                                    | escaped
        'é\u001bé'                               | 'é\\x1bé'
        'start\u2028mid\u001bend'                | 'start\\u2028mid\\x1bend'
        '\u202eпривет\r\n'                       | '\\u202eпривет\\x0d\\x0a'
        '\u0085\u00a0\u00ff\u2027\u202a\u2065'   | '\\x85\u00a0\u00ff\u2027\\u202a\u2065'
        'a\u0000b\u009fc\u061cd\u200fe'          | 'a\\x00b\\x9fc\\u061cd\\u200fe'
    }
}
