package io.micronaut.docs.server.endpoint;

//tag::endpointImport[]
import io.micronaut.management.endpoint.annotation.Endpoint;
//end::endpointImport[]

//tag::readImport[]
import io.micronaut.management.endpoint.annotation.Read;
//end::readImport[]

//tag::mediaTypeImport[]
import io.micronaut.http.MediaType;
import io.micronaut.management.endpoint.annotation.Selector;
//end::mediaTypeImport[]

//tag::writeImport[]
import io.micronaut.management.endpoint.annotation.Write;
//end::writeImport[]

import javax.annotation.PostConstruct;
import java.util.Date;

//tag::endpointClassBegin[]
@Endpoint(id = "date",
        prefix = "custom",
        defaultEnabled = true,
        defaultSensitive = false)
public class CurrentDateEndpoint {
//end::endpointClassBegin[]

    //tag::methodSummary[]
    //.. endpoint methods
    //end::methodSummary[]

    //tag::currentDate[]
    private Date currentDate;
    //end::currentDate[]

    @PostConstruct
    public void init() {
        currentDate = new Date();
    }

    //tag::simpleRead[]
    @Read
    public Date currentDate() {
        return currentDate;
    }
    //end::simpleRead[]

    //tag::readArg[]
    @Read(produces = MediaType.TEXT_PLAIN) //<1>
    public String currentDatePrefix(@Selector String prefix) {
        return prefix + ": " + currentDate;
    }
    //end::readArg[]

    //tag::simpleWrite[]
    @Write
    public String reset() {
        currentDate = new Date();

        return "Current date reset";
    }
    //end::simpleWrite[]
//tag::endpointClassEnd[]
}
//end::endpointClassEnd[]

