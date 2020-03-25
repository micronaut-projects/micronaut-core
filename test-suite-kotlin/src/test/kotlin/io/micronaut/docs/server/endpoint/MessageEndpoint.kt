/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.docs.server.endpoint

//tag::endpointImport[]
import io.micronaut.context.annotation.Requires
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

@Requires(property = "spec.name", value = "MessageEndpointSpec")
//tag::endpointClassBegin[]
@Endpoint(id = "message", defaultSensitive = false)
class MessageEndpoint {
    //end::endpointClassBegin[]

    //tag::message[]
    internal var message: String? = null
    //end::message[]

    @PostConstruct
    fun init() {
        this.message = "default message"
    }

    @Read
    fun message(): String? {
        return this.message
    }

    //tag::writeArg[]
    @Write(consumes = [MediaType.APPLICATION_FORM_URLENCODED], produces = [MediaType.TEXT_PLAIN])
    fun updateMessage(newMessage: String): String {  //<1>
        this.message = newMessage

        return "Message updated"
    }
    //end::writeArg[]

    //tag::simpleDelete[]
    @Delete
    fun deleteMessage(): String {
        this.message = null

        return "Message deleted"
    }
    //end::simpleDelete[]

    //tag::endpointClassEnd[]
}
//end::endpointClassEnd[]
