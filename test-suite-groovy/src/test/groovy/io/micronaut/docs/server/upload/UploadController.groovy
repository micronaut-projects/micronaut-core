package io.micronaut.docs.server.upload

// tag::class[]
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.http.multipart.StreamingFileUpload
import io.reactivex.Single
import org.reactivestreams.Publisher

@Controller("/upload")
class UploadController {

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

}
// end::class[]
