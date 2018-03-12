package io.micronaut.openapi

import groovy.transform.CompileStatic
import groovy.transform.builder.Builder

@Builder
@CompileStatic
class OpenApiLicense {
    String name
    String url

    Map toMap() {
        [
                name: name,
                url: url,
        ]
    }
}
