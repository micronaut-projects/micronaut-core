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
