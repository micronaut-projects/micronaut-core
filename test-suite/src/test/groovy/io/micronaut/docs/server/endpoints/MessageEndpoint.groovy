package io.micronaut.docs.server.endpoints

//tag::endpointImport[]
import io.micronaut.management.endpoint.Endpoint
//end::endpointImport[]

//tag::mediaTypeImport[]
import io.micronaut.http.MediaType
//end::mediaTypeImport[]

//tag::writeImport[]
import io.micronaut.management.endpoint.Write
//end::writeImport[]

//tag::deleteImport[]
import io.micronaut.management.endpoint.Delete
//end::deleteImport[]

import io.micronaut.management.endpoint.Read

import javax.annotation.PostConstruct

//tag::endpointClassBegin[]
@Endpoint(id = "message", defaultSensitive = false)
class MessageEndpoint {
//end::endpointClassBegin[]

    //tag::message[]
    String message
    //end::message[]

    @PostConstruct
    void init() {
        message = "default message"
    }

    @Read
    String message() {
        return message
    }

    //tag::writeArg[]
    @Write(consumes = MediaType.APPLICATION_JSON)
    String updateMessage(String newMessage) {  //<1>
        message = newMessage

        return "Message updated"
    }
    //end::writeArg[]

    //tag::simpleDelete[]
    @Delete
    String deleteMessage() {
        message = null

        return "Message deleted"
    }
    //end::simpleDelete[]

//tag::endpointClassEnd[]
}
//end::endpointClassEnd[]
