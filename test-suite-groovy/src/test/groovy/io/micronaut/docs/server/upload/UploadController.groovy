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
package io.micronaut.docs.server.upload

import io.micronaut.core.async.annotation.SingleResult

// tag::class[]
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.http.multipart.StreamingFileUpload
import org.reactivestreams.Publisher
import reactor.core.publisher.Mono

import static io.micronaut.http.HttpStatus.CONFLICT
import static io.micronaut.http.MediaType.MULTIPART_FORM_DATA
import static io.micronaut.http.MediaType.TEXT_PLAIN

@Controller("/upload")
class UploadController {

    @Post(value = "/", consumes = MULTIPART_FORM_DATA, produces = TEXT_PLAIN) // <1>
    Mono<HttpResponse<String>> upload(StreamingFileUpload file) { // <2>

        File tempFile = File.createTempFile(file.filename, "temp")
        Publisher<Boolean> uploadPublisher = file.transferTo(tempFile) // <3>

        Mono.from(uploadPublisher)  // <4>
            .map({ success ->
                if (success) {
                    HttpResponse.ok("Uploaded")
                } else {
                    HttpResponse.<String>status(CONFLICT)
                            .body("Upload Failed")
                }
            })
    }

    @Post(value = "/outputStream", consumes = MULTIPART_FORM_DATA, produces = TEXT_PLAIN) // <1>
    @SingleResult
    Mono<HttpResponse<String>> uploadOutputStream(StreamingFileUpload file) { // <2>

        OutputStream outputStream = new ByteArrayOutputStream() // <3>

        Publisher<Boolean> uploadPublisher = file.transferTo(outputStream) // <4>

        Mono.from(uploadPublisher)  // <5>
                .map({ success ->
                    if (success) {
                        HttpResponse.ok("Uploaded")
                    } else {
                        HttpResponse.<String>status(CONFLICT)
                                .body("Upload Failed")
                    }
                })
    }
}
// end::class[]
