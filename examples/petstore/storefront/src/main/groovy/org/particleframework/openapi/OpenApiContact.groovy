package io.micronaut.openapi

import groovy.transform.CompileStatic
import groovy.transform.builder.Builder

@Builder
@CompileStatic
class OpenApiContact {
    String name
    String url
    String email

    Map toMap() {
        Map m = [:]
        if ( name) {
            m.name = name
        }
        if ( url) {
            m.url = url
        }
        if ( email) {
            m.email = email
        }
        m
    }
}
