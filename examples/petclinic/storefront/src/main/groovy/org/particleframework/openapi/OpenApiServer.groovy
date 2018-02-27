package org.particleframework.openapi

import groovy.transform.CompileStatic
import groovy.transform.builder.Builder

@Builder
@CompileStatic
class OpenApiServer {
    String url

    Map toMap() {
        Map m = [:]
        if ( url ) {
            m.url = url
        }
        m
    }
}
