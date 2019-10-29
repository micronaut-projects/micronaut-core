package io.micronaut.docs.server.upload

// tag::class[]
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.http.multipart.StreamingFileUpload
import io.reactivex.Single
import java.io.File

@Controller("/upload")
class UploadController {

    @Post(value = "/", consumes = [MediaType.MULTIPART_FORM_DATA]) // <1>
    fun upload(file: StreamingFileUpload): Single<HttpResponse<String>> { // <2>
        val tempFile = File.createTempFile(file.filename, "temp")
        val uploadPublisher = file.transferTo(tempFile) // <3>
        return Single.fromPublisher(uploadPublisher)  // <4>
                .map { success ->
                    if (success) {
                        HttpResponse.ok("Uploaded")
                    } else {
                        HttpResponse.status<String>(HttpStatus.CONFLICT)
                                .body("Upload Failed")
                    }
                }
    }

}
// end::class[]
