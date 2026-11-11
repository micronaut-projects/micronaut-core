package io.micronaut.http.uri

import spock.lang.Specification
import spock.lang.Unroll

class UriTemplateMatcherTraversalSpec extends Specification {

    @Unroll
    void "an encoded separator cannot make #uri escape #template"() {
        expect:
        !new UriTemplateMatcher(template).match(uri).isPresent()

        where:
        template                    | uri
        '/seg/{name}'               | '/seg/%2Fetc%2Fpasswd'
        '/seg/{name}'               | '/seg/%5Cetc'
        '/re/{name:[^/\\\\]+}'      | '/re/%2Fetc%2Fpasswd'
        '/mv/{a:.*}/{b:[^/]+}'      | '/mv/foo/..%2F..%2Fetc'
    }

    @Unroll
    void "#uri still binds to #template"() {
        expect:
        new UriTemplateMatcher(template).match(uri).get().variableValues == values

        where:
        template                    | uri                   | values
        '/seg/{name}'               | '/seg/a%2Fb'          | [name: 'a%2Fb']
        '/re/{name:[^/\\\\]+}'      | '/re/a%2Fb'           | [name: 'a%2Fb']
        '/mv/{a:.*}/{b:[^/]+}'      | '/mv/foo/bar/baz'     | [a: 'foo/bar', b: 'baz']
    }
}
