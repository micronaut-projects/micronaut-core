package io.micronaut.docs.server.endpoints

//tag::endpointImport[]
import io.micronaut.management.endpoint.Endpoint
//end::endpointImport[]

//tag::readImport[]
import io.micronaut.management.endpoint.Read
//end::readImport[]

//tag::mediaTypeImport[]
import io.micronaut.http.MediaType
//end::mediaTypeImport[]

//tag::writeImport[]
import io.micronaut.management.endpoint.Write
//end::writeImport[]

import javax.annotation.PostConstruct

//tag::endpointClassBegin[]
@Endpoint(id = "date",
        prefix = "custom",
        defaultEnabled = true,
        defaultSensitive = false)
class CurrentDateEndpoint {
//end::endpointClassBegin[]

    //tag::currentDate[]
    Date currentDate
    //end::currentDate[]

    @PostConstruct
    void init() {
        currentDate = new Date()
    }

    //tag::simpleRead[]
    @Read
    Date currentDate() {
        return currentDate
    }
    //end::simpleRead[]

    //tag::readArg[]
    @Read(produces = MediaType.TEXT_PLAIN) //<1>
    String currentDatePrefix(String prefix) {
        return "${prefix}: ${currentDate}"
    }
    //end::readArg[]

    //tag::simpleWrite[]
    @Write
    String reset() {
        currentDate = new Date()

        return "Current date reset"
    }
    //end::simpleWrite[]
//tag::endpointClassEnd[]
}
//end::endpointClassEnd[]

