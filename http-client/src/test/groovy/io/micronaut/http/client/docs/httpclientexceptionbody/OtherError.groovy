package io.micronaut.http.client.docs.httpclientexceptionbody

import groovy.transform.CompileStatic

@CompileStatic
class OtherError {

    Integer status
    String error
    String message
    String path

    OtherError(Integer status, String error, String message, String path) {
        this.status = status
        this.error = error
        this.message = message
        this.path = path
    }
}
