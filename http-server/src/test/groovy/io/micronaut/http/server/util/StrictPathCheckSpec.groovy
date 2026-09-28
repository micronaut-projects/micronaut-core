package io.micronaut.http.server.util

import spock.lang.Specification

class StrictPathCheckSpec extends Specification {

    def "accepts #path"() {
        expect:
        StrictPathCheck.rejection(path, false) == null

        where:
        path << ['/', '/a', '/a/b.txt', '/a/', '/...', '/.a', '/a.', '/a%20b', '/%2e%2e%2e', '/%C3%A9', '/%E2%82%AC',
                 '/%F0%9F%98%80', '/a?', '/a-b_c~d', "/a'b", '/a|b']
    }

    def "rejects #path: #reason"() {
        expect:
        StrictPathCheck.rejection(path, false) == reason

        where:
        path                   | reason
        ''                     | 'The path must start with a slash'
        'a'                    | 'The path must start with a slash'
        '/.'                   | 'A dot segment in the path'
        '/a/..'                | 'A dot segment in the path'
        '/a/../b'              | 'A dot segment in the path'
        '/%2e%2e/b'            | 'A dot segment in the path'
        '/.%2E/b'              | 'A dot segment in the path'
        '/a%2Fb'               | 'An encoded slash or backslash in the path'
        '/a%5cb'               | 'An encoded slash or backslash in the path'
        '/a\\b'                | 'A backslash in the path'
        '/%252e%252e/a'        | 'An encoded percent sign in the path'
        '/a;b'                 | 'A path parameter (;) in the path'
        '/a%3Bb'               | 'A path parameter (;) in the path'
        '/..%3b/admin'         | 'A path parameter (;) in the path'
        '/..#'                 | 'A fragment (#) in the path'
        '/x/%2e%2e#'           | 'A fragment (#) in the path'
        '/aé'             | 'A character outside ASCII in the path'
        '/À®'        | 'A character outside ASCII in the path'
        '/a\u0085'             | 'A character outside ASCII in the path'
        '/a%00b'               | 'A control character in the path'
        '/a%0A'                | 'A control character in the path'
        '/a%7F'                | 'A control character in the path'
        '/a\tb'                | 'A control character in the path'
        '/a%C2%85'             | 'A control character in the path'
        '/a%'                  | 'Malformed percent-encoding in the path'
        '/a%2'                 | 'Malformed percent-encoding in the path'
        '/a%zz'                | 'Malformed percent-encoding in the path'
        '/%C0%AE/b'            | 'An invalid UTF-8 sequence in the path'
        '/%C1%81'              | 'An invalid UTF-8 sequence in the path'
        '/%E0%80%AE%E0%80%AE/a'| 'An invalid UTF-8 sequence in the path'
        '/%F0%80%80%AE'        | 'An invalid UTF-8 sequence in the path'
        '/%ED%A0%80'           | 'An invalid UTF-8 sequence in the path'
        '/%80'                 | 'An invalid UTF-8 sequence in the path'
        '/%FF'                 | 'An invalid UTF-8 sequence in the path'
        '/%C3'                 | 'An invalid UTF-8 sequence in the path'
    }

    def "with semicolons allowed accepts #path"() {
        expect:
        StrictPathCheck.rejection(path, true) == null

        where:
        path << ['/a;b', '/a;jsessionid=1/b', '/a%3Bb', '/a;..', '/%C3%A9;x']
    }

    def "with semicolons allowed rejects #path"() {
        expect:
        StrictPathCheck.rejection(path, true) == reason

        where:
        path              | reason
        '/..;/admin'      | 'A dot segment in the path'
        '/.;x/b'          | 'A dot segment in the path'
        '/..%3b/admin'    | 'A dot segment in the path'
        '/%2e%2e;x/b'     | 'A dot segment in the path'
        '/a%2Fb;x'        | 'An encoded slash or backslash in the path'
    }
}
