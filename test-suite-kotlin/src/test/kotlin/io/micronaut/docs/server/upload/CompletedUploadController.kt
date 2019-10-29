package io.micronaut.docs.server.upload

// tag::class[]
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.http.multipart.CompletedFileUpload

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

@Controller("/upload")
class CompletedUploadController {

    @Post(value = "/completed", consumes = [MediaType.MULTIPART_FORM_DATA]) // <1>
    fun uploadCompleted(file: CompletedFileUpload): HttpResponse<String> { // <2>
        return try {
            val tempFile = File.createTempFile(file.filename, "temp") //<3>
            val path = Paths.get(tempFile.absolutePath)
            Files.write(path, file.bytes) //<3>
            HttpResponse.ok("Uploaded")
        } catch (exception: IOException) {
            HttpResponse.badRequest("Upload Failed")
        }

    }
}
// end::class[]
