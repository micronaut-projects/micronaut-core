package io.micronaut.openapi

import groovy.transform.CompileStatic
import groovy.transform.builder.Builder

@Builder
@CompileStatic
class OpenApiDocument {
    String swagger
    String title
    String description
    String termsOfService
    OpenApiInfo info
    OpenApiContact contact
    OpenApiLicense license
    List<OpenApiServer> servers
    List<OpenApiPath> paths
    String version
    List<OpenApiSecurityScheme> securityDefinitions

    Map toMap() {
        Map m = [:]
        if ( swagger ) {
            m.swagger = swagger
        }
        if ( info ) {
            m.info = info.toMap()
        }
        if ( title) {
            m.title = title
        }
        if ( description) {
            m.description = description
        }
        if ( termsOfService ) {
            m.termsOfService = termsOfService
        }

        Map contactMap = contact?.toMap()
        if ( contactMap) {
            m.contact = contactMap
        }
        Map licenseMap = license?.toMap()
        if ( licenseMap) {
            m.license = licenseMap
        }
        if ( servers ) {
            List<Map> l = []
            for ( OpenApiServer server : servers ) {
                l << server.toMap()
            }
            m.servers = l
        }
        Map pathsMap = OpenApiPath.toMap(paths)
        if ( pathsMap ) {
            m.paths = pathsMap
        }
        if ( securityDefinitions ) {
            m.securityDefinitions = [:]
            for ( OpenApiSecurityScheme securityScheme : securityDefinitions ) {
                m.securityDefinitions[securityScheme.path] = securityScheme.toMap()
            }
        }
        if ( version) {
            m.version = version
        }
        m
    }
}
