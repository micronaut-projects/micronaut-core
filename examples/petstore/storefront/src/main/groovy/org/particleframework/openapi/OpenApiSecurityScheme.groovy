package io.micronaut.openapi

import groovy.transform.CompileStatic
import groovy.transform.builder.Builder

@Builder
@CompileStatic
class OpenApiSecurityScheme {
    String path
    String type
    String description
    String name
    String inField
    String flow
    String authorizationUrl
    String tokenUrl
    List<Map> scopes

    Map toMap() {
        Map m = [:]
        if ( type ) {
            m.type = type
        }
        if ( description ) {
            m.description = description
        }
        if ( name ) {
            m.name = name
        }
        if ( inField ) {
            m['in'] = inField
        }
        if ( flow ) {
            m.flow = flow
        }
        if ( authorizationUrl ) {
            m.authorizationUrl = authorizationUrl
        }
        if ( tokenUrl ) {
            m.tokenUrl = tokenUrl
        }
        if ( scopes ) {
            m.scopes = scopes
        }
        m
    }
}
