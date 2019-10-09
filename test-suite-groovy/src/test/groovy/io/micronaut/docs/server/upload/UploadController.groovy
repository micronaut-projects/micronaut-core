package io.micronaut.docs.server.upload

// tag::imports[]
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.http.multipart.CompletedFileUpload
import io.micronaut.http.multipart.StreamingFileUpload
import io.reactivex.Single
import org.reactivestreams.Publisher

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
// end::imports[]

// tag::class[]
@Controller("/upload")
class UploadController {
// end::class[]

    // tag::upload[]
    @Post(value = "/", consumes = MediaType.MULTIPART_FORM_DATA) // <1>
    Single<HttpResponse<String>> upload(StreamingFileUpload file) { // <2>
        File tempFile = File.createTempFile(file.filename, "temp")
        Publisher<Boolean> uploadPublisher = file.transferTo(tempFile) // <3>
        Single.fromPublisher(uploadPublisher)  // <4>
            .map({ success ->
                if (success) {
                    HttpResponse.ok("Uploaded")
                } else {
                    HttpResponse.<String>status(HttpStatus.CONFLICT)
                            .body("Upload Failed")
                }
            })
    }
    // end::upload[]

    // tag::completedUpload[]
    @Post(value = "/completed", consumes = MediaType.MULTIPART_FORM_DATA) // <1>
    HttpResponse<String> uploadCompleted(CompletedFileUpload file) { // <2>
        try {
            File tempFile = File.createTempFile(file.filename, "temp") //<3>
            Path path = Paths.get(tempFile.absolutePath)
            Files.write(path, file.bytes)//<3>
            HttpResponse.ok("Uploaded")
        } catch (IOException exception) {
            HttpResponse.badRequest("Upload Failed")
        }
    }
    // end::completedUpload[]

    // tag::bytesUpload[]
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
    // end::bytesUpload[]

}
