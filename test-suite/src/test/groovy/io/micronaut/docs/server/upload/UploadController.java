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
package io.micronaut.docs.server.upload;

// tag::imports[]
import io.micronaut.http.*;
import io.micronaut.http.annotation.*;
import io.micronaut.http.multipart.StreamingFileUpload;
import io.reactivex.Single;
import org.reactivestreams.Publisher;

import java.io.File;
// end::imports[]

// tag::completedImports[]
import io.micronaut.http.multipart.CompletedFileUpload;

import java.io.IOException;
import java.nio.file.*;
// end::completedImports[]
/**
 * @author Graeme Rocher
 * @since 1.0
 */
// tag::class[]
@Controller
public class UploadController {
// end::class[]

    // tag::upload[]
    @Post(value = "/", consumes = MediaType.MULTIPART_FORM_DATA) // <1>
    public Single<HttpResponse<String>> upload(StreamingFileUpload file) throws IOException { // <2>
        File tempFile = File.createTempFile(file.getFilename(), "temp");
        Publisher<Boolean> uploadPublisher = file.transferTo(tempFile); // <3>
        return Single.fromPublisher(uploadPublisher)  // <4>
            .map(success -> {
                if (success) {
                    return HttpResponse.ok("Uploaded");
                } else {
                    return HttpResponse.<String>status(HttpStatus.CONFLICT)
                                       .body("Upload Failed");
                }
            });
    }
    // end::upload[]

    // tag::completedUpload[]
    @Post(value = "/completed", consumes = MediaType.MULTIPART_FORM_DATA) // <1>
    public HttpResponse<String> uploadCompleted(CompletedFileUpload file) { // <2>
        try {
            File tempFile = File.createTempFile(file.getFilename(), "temp"); //<3>
            Path path = Paths.get(tempFile.getAbsolutePath());
            Files.write(path, file.getBytes()); //<3>
            return HttpResponse.ok("Uploaded");
        } catch (IOException exception) {
            return HttpResponse.badRequest("Upload Failed");
        }
    }
    // end::completedUpload[]

    // tag::bytesUpload[]
    @Post(value = "/bytes", consumes = MediaType.MULTIPART_FORM_DATA) // <1>
    public HttpResponse<String> uploadBytes(byte[] file, String fileName) { // <2>
        try {
            File tempFile = File.createTempFile(fileName, "temp");
            Path path = Paths.get(tempFile.getAbsolutePath());
            Files.write(path, file); // <3>
            return HttpResponse.ok("Uploaded");
        } catch (IOException exception) {
            return HttpResponse.badRequest("Upload Failed");
        }
    }
    // end::bytesUpload[]

}
