package io.micronaut.openapi

import groovy.transform.CompileStatic
import io.micronaut.http.annotation.CookieValue
import io.micronaut.http.annotation.Header

import java.lang.annotation.Annotation
import java.lang.reflect.Parameter

@CompileStatic
enum OpenApiParameterIn {
    PATH, QUERY, HEADER, COOKIE

    static OpenApiParameterIn of(String path, Parameter parameter, Annotation[] annotations) {
        if ( annotations != null ) {
            for ( Annotation annotation : annotations ) {
                if ( annotation instanceof CookieValue) {
                    return OpenApiParameterIn.COOKIE
                }
                if ( annotation instanceof Header) {
                    return OpenApiParameterIn.HEADER
                }
            }
        }
        String name = OpenApiParameter.name(parameter, annotations)
        if ( path.contains('{'+name+'}') ) {
            return OpenApiParameterIn.PATH
        }
        return OpenApiParameterIn.QUERY
    }
}
