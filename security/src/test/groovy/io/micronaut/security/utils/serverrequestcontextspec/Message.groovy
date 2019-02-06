package io.micronaut.security.utils.serverrequestcontextspec

import groovy.transform.CompileStatic

@CompileStatic
class Message {
    String message

    Message() {}

    Message(String message) {
        this.message = message
    }
}
