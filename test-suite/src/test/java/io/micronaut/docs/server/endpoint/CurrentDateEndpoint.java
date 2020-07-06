/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

