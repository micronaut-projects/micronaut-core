package io.micronaut.openapi

import groovy.transform.CompileStatic
import groovy.transform.builder.Builder

@Builder
@CompileStatic
class OpenApiInfo {
    String version
    String title

    Map toMap() {
        Map m = [:]
        if ( version ) {
            m.version = version
        }
        if ( title ) {
            m.title = title
        }
        m
    }
}
