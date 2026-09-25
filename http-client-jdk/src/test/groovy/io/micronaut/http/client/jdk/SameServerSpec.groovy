package io.micronaut.http.client.jdk

import spock.lang.Specification

/**
 * A request that a client filter sent elsewhere keeps the load-balanced instance only while it
 * goes to the same server: the scheme and host ignore case, and a missing port is the default
 * port of the scheme.
 */
class SameServerSpec extends Specification {

    void "#a and #b are the same server: #same"() {
        expect:
        AbstractJdkHttpClient.sameServer(URI.create(a), URI.create(b)) == same

        where:
        a                           | b                           | same
        'http://svc:8080/a'         | 'http://svc:8080/b'         | true
        'http://svc/a'              | 'http://svc:80/b'           | true
        'https://svc/a'             | 'https://svc:443/b'         | true
        'HTTP://SVC:8080/a'         | 'http://svc:8080/b'         | true
        'http://user:pw@svc:8080/a' | 'http://svc:8080/b'         | true
        'http://svc:8080/a'         | 'http://svc:8081/a'         | false
        'http://svc/a'              | 'https://svc/a'             | false
        'http://svc:8080/a'         | 'http://other:8080/a'       | false
    }
}
