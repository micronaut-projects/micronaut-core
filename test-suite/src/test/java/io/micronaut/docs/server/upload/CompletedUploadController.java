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
package io.micronaut.docs.server.upload;

// tag::class[]
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.multipart.CompletedFileUpload;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static io.micronaut.http.MediaType.MULTIPART_FORM_DATA;
import static io.micronaut.http.MediaType.TEXT_PLAIN;

@Controller("/upload")
public class CompletedUploadController {

    @Post(value = "/completed", consumes = MULTIPART_FORM_DATA, produces = TEXT_PLAIN) // <1>
    public HttpResponse<String> uploadCompleted(CompletedFileUpload file) { // <2>
        try {
            File tempFile = File.createTempFile(file.getFilename(), "temp"); //<3>
            Path path = Paths.get(tempFile.getAbsolutePath());
            Files.write(path, file.getBytes()); //<3>
            return HttpResponse.ok("Uploaded");
        } catch (IOException e) {
            return HttpResponse.badRequest("Upload Failed");
        }
    }
}
// end::class[]
