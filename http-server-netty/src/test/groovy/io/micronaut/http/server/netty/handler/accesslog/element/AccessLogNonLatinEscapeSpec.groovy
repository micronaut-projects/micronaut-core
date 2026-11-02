package io.micronaut.http.server.netty.handler.accesslog.element

import spock.lang.Specification

/**
 * Characters beyond Latin-1 are logged the same whether or not the value also has a control
 * character: only the line separators and bidirectional controls among them are escaped.
 */
class AccessLogNonLatinEscapeSpec extends Specification {

    void "non-Latin text is kept next to an escaped control character"() {
        expect:
        AccessLog.escape(value) == escaped

        where:
        value               | escaped
        'привет'            | 'привет'
        '\nпривет'          | '\\x0aпривет'
        ' привет'      | '\\u2028привет'
        'a‮b\u0001é'   | 'a\\u202eb\\x01é'
    }
}
