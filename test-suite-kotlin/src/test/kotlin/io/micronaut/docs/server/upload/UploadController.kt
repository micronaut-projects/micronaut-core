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
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus.CONFLICT
import io.micronaut.http.MediaType.MULTIPART_FORM_DATA
import io.micronaut.http.MediaType.TEXT_PLAIN
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.http.multipart.StreamingFileUpload
import reactor.core.publisher.Mono
import java.io.File

@Controller("/upload")
class UploadController {

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
}
// end::class[]
