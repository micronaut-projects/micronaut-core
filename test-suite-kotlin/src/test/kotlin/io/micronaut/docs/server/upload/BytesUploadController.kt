package io.micronaut.docs.server.upload

// tag::class[]
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

@Controller("/upload")
class BytesUploadController {

    @Post(value = "/bytes", consumes = [MediaType.MULTIPART_FORM_DATA]) // <1>
    fun uploadBytes(file: ByteArray, fileName: String): HttpResponse<String> { // <2>
        return try {
            val tempFile = File.createTempFile(fileName, "temp")
            val path = Paths.get(tempFile.absolutePath)
            Files.write(path, file) // <3>
            HttpResponse.ok("Uploaded")
        } catch (exception: IOException) {
            HttpResponse.badRequest("Upload Failed")
        }

    }
}
// end::class[]
