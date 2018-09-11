/*
 * Copyright 2017-2018 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.micronaut.docs.server.endpoints

//tag::endpointImport[]
import io.micronaut.management.endpoint.annotation.Endpoint
//end::endpointImport[]

//tag::mediaTypeImport[]
import io.micronaut.http.MediaType
//end::mediaTypeImport[]

//tag::writeImport[]
import io.micronaut.management.endpoint.annotation.Write
//end::writeImport[]

//tag::deleteImport[]
import io.micronaut.management.endpoint.annotation.Delete
//end::deleteImport[]

import io.micronaut.management.endpoint.annotation.Read

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
