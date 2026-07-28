import java

# tag::class[]
from micronaut.http import HttpResponse, MediaType
from micronaut.http.annotation import Controller, Post

File = java.type("java.io.File")
Files = java.type("java.nio.file.Files")
Paths = java.type("java.nio.file.Paths")


@Controller("/upload")
class BytesUploadController:

    @Post(value="/bytes", consumes=MediaType.MULTIPART_FORM_DATA, produces=MediaType.TEXT_PLAIN)  # <1>
    def uploadBytes(self, file: bytes, fileName: str) -> HttpResponse:  # <2>
        try:
            tempFile = File.createTempFile(fileName, "temp")
            path = Paths.get(tempFile.getAbsolutePath())
            Files.write(path, file)  # <3>
            return HttpResponse.ok("Uploaded")
        except Exception:
            return HttpResponse.badRequest("Upload Failed")
# end::class[]
