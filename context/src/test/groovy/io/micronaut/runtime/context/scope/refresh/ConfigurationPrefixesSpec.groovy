package io.micronaut.runtime.context.scope.refresh

import spock.lang.Specification

class ConfigurationPrefixesSpec extends Specification {

    void "the prefix #declared of the entry #name resolves to #resolved"() {
        expect:
        ConfigurationPrefixes.resolve(declared, name) == resolved

        where:
        declared                          | name  | resolved
        "micronaut.http.client"           | null  | "micronaut.http.client"
        "micronaut.http.services.*"       | "up"  | "micronaut.http.services.up"
        "micronaut.http.services.*"       | null  | "micronaut.http.services"
        "micronaut.http.services.*.ssl"   | "up"  | "micronaut.http.services.up.ssl"
        "micronaut.http.services.*.ssl"   | null  | "micronaut.http.services"
        "servers[*]"                      | "0"   | "servers[0]"
        "servers[*].tls"                  | "0"   | "servers[0].tls"
        "outer.*.inner.*.leaf"            | "a"   | "outer.a.inner"
        "keys.*star"                      | "a"   | "keys.*star"
    }
}
