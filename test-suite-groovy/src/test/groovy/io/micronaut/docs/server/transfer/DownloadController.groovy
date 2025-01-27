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
package io.micronaut.docs.server.transfer

import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import reactor.core.publisher.Flux

@Controller("/download")
class DownloadController {
    // tag::class[]
    @Get("/csv")
     HttpResponse<Flux<String>> downloadCsv() {
        Flux<String> data = Flux.just(
                "data1,data2",
                "data3,data4"
        )
        return HttpResponse.ok(data)
                .header(HttpHeaders.CONTENT_DISPOSITION, 'attachment; filename="data.csv"')
                .contentType(MediaType.TEXT_PLAIN_TYPE)
    }
    // end::class[]
}
