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
package io.micronaut.docs.server.body

// tag::imports[]
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import javax.validation.constraints.Size
// end::imports[]
// end::importsreactive[]
import org.reactivestreams.Publisher
import io.micronaut.core.async.annotation.SingleResult
import reactor.core.publisher.Flux
// end::importsreactive[]
// tag::class[]
@Controller("/receive")
open class MessageController {
// end::class[]

    // tag::echo[]
    @Post(value = "/echo", consumes = [MediaType.TEXT_PLAIN]) // <1>
    open fun echo(@Size(max = 1024) @Body text: String): String { // <2>
        return text // <3>
    }
    // end::echo[]

    // tag::echoReactive[]
    @Post(value = "/echo-publisher", consumes = [MediaType.TEXT_PLAIN]) // <1>
    @SingleResult
    open fun echoFlow(@Body text: Publisher<String>): Publisher<HttpResponse<String>> { //<2>
        return Flux.from(text)
            .collect({ StringBuffer() }, { obj, str -> obj.append(str) }) // <3>
            .map { buffer -> HttpResponse.ok(buffer.toString()) }
    }
    // end::echoReactive[]

// tag::endclass[]
}
// end::endclass[]
