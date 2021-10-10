package io.micronaut.docs.server.upload;

// tag::class[]
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.http.multipart.CompletedFileUpload

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

@Controller("/upload")
class CompletedUploadController {

    @Post(value = "/completed", consumes = MediaType.MULTIPART_FORM_DATA) // <1>
    HttpResponse<String> uploadCompleted(CompletedFileUpload file) { // <2>
        try {
            File tempFile = File.createTempFile(file.filename, "temp") //<3>
            Path path = Paths.get(tempFile.absolutePath)
            Files.write(path, file.bytes) //<3>
            HttpResponse.ok("Uploaded")
        } catch (IOException exception) {
            HttpResponse.badRequest("Upload Failed")
        }
    }
}
// end::class[]
