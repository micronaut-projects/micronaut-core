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
package io.micronaut.docs.server.upload;

// tag::class[]
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

@Controller("/upload")
class BytesUploadController {

    @Post(value = "/bytes", consumes = MediaType.MULTIPART_FORM_DATA) // <1>
    HttpResponse<String> uploadBytes(byte[] file, String fileName) { // <2>
        try {
            File tempFile = File.createTempFile(fileName, "temp")
            Path path = Paths.get(tempFile.absolutePath)
            Files.write(path, file) // <3>
            HttpResponse.ok("Uploaded")
        } catch (IOException exception) {
            HttpResponse.badRequest("Upload Failed")
        }
    }
}
// end::class[]
