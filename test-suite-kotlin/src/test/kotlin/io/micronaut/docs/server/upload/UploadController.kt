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

// tag::class[]
import io.micronaut.core.async.annotation.SingleResult
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus.CONFLICT
import io.micronaut.http.MediaType.MULTIPART_FORM_DATA
import io.micronaut.http.MediaType.TEXT_PLAIN
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.http.multipart.StreamingFileUpload
import org.reactivestreams.Publisher
import reactor.core.publisher.Mono
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream

@Controller("/upload")
class UploadController {

    // tag::file[]
    @Post(value = "/", consumes = [MULTIPART_FORM_DATA], produces = [TEXT_PLAIN]) // <1>
    fun upload(file: StreamingFileUpload): Mono<HttpResponse<String>> { // <2>

        val tempFile = File.createTempFile(file.filename, "temp")
        val uploadPublisher = file.transferTo(tempFile) // <3>

        return Mono.from(uploadPublisher)  // <4>
            .map { success ->
                if (success) {
                    HttpResponse.ok("Uploaded")
                } else {
                    HttpResponse.status<String>(CONFLICT)
                        .body("Upload Failed")
                }
            }
    }
    // end::file[]

    // tag::outputStream[]
    @Post(value = "/outputStream", consumes = [MULTIPART_FORM_DATA], produces = [TEXT_PLAIN]) // <1>
    @SingleResult
    fun uploadOutputStream(file: StreamingFileUpload): Mono<HttpResponse<String>> { // <2>
        val outputStream  = ByteArrayOutputStream() // <3>
        val uploadPublisher = file.transferTo(outputStream) // <4>

        return Mono.from(uploadPublisher) // <5>
            .map { success: Boolean ->
                return@map if (success) {
                    HttpResponse.ok("Uploaded")
                } else {
                    HttpResponse.status<String>(CONFLICT)
                        .body("Upload Failed")
                }
            }
    }
    // end::outputStream[]
}
// end::class[]
