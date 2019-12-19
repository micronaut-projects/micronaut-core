package io.micronaut.docs.server.endpoint;

//tag::endpointImport[]
import io.micronaut.context.annotation.Requires;
import io.micronaut.management.endpoint.annotation.Endpoint;
//end::endpointImport[]

//tag::mediaTypeImport[]
import io.micronaut.http.MediaType;
//end::mediaTypeImport[]

//tag::writeImport[]
import io.micronaut.management.endpoint.annotation.Write;
//end::writeImport[]

//tag::deleteImport[]
import io.micronaut.management.endpoint.annotation.Delete;
//end::deleteImport[]

import io.micronaut.management.endpoint.annotation.Read;

import javax.annotation.PostConstruct;

@Requires(property = "spec.name", value = "MessageEndpointSpec")
//tag::endpointClassBegin[]
@Endpoint(id = "message", defaultSensitive = false)
public class MessageEndpoint {
//end::endpointClassBegin[]

    //tag::message[]
    String message;
    //end::message[]

    @PostConstruct
    public void init() {
        this.message = "default message";
    }

    @Read
    public String message() {
        return this.message;
    }

    //tag::writeArg[]
    @Write(consumes = MediaType.APPLICATION_FORM_URLENCODED, produces = MediaType.TEXT_PLAIN)
    public String updateMessage(String newMessage) {
        this.message = newMessage;

        return "Message updated";
    }
    //end::writeArg[]

    //tag::simpleDelete[]
    @Delete
    public String deleteMessage() {
        this.message = null;

        return "Message deleted";
    }
    //end::simpleDelete[]

//tag::endpointClassEnd[]
}
//end::endpointClassEnd[]
