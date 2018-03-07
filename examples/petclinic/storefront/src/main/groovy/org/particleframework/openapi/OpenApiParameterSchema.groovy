package io.micronaut.openapi

import groovy.transform.CompileStatic

@CompileStatic
class OpenApiParameterSchema {
    String type

    static OpenApiParameterSchema of(Class parameterClass) {
        new OpenApiParameterSchema(type: parameterClass.simpleName.toLowerCase())
    }

    Map topMap() {
        Map m = [:]
        if ( type ) {
            m['type'] = type
        }
        m
    }
}
