import java

# tag::class[]
from micronaut.core.async_.annotation import SingleResult
from micronaut.http import HttpResponse, HttpStatus, MediaType
from micronaut.http.annotation import Controller, Post
from micronaut.http.multipart import StreamingFileUpload

File = java.type("java.io.File")
ByteArrayOutputStream = java.type("java.io.ByteArrayOutputStream")
Mono = java.type("reactor.core.publisher.Mono")
Publisher = java.type("org.reactivestreams.Publisher")


@Controller("/upload")
class UploadController:
# end::class[]

    # tag::file[]
    @Post(value="/", consumes=MediaType.MULTIPART_FORM_DATA, produces=MediaType.TEXT_PLAIN)  # <1>
    @SingleResult
    def upload(self, file: StreamingFileUpload) -> Publisher:  # <2>
        tempFile = File.createTempFile(file.getFilename(), "temp")
        uploadPublisher = file.transferTo(tempFile)  # <3>

        return getattr(Mono, "from")(uploadPublisher).thenReturn(  # <4>
            HttpResponse.ok("Uploaded")
        ).onErrorReturn(
            HttpResponse.status(HttpStatus.CONFLICT).body("Upload Failed")
        )
    # end::file[]

    # tag::outputStream[]
    @Post(value="/outputStream", consumes=MediaType.MULTIPART_FORM_DATA, produces=MediaType.TEXT_PLAIN)  # <1>
    @SingleResult
    def uploadOutputStream(self, file: StreamingFileUpload) -> Publisher:  # <2>
        outputStream = ByteArrayOutputStream()  # <3>
        uploadPublisher = file.transferTo(outputStream)  # <4>

        return getattr(Mono, "from")(uploadPublisher).thenReturn(  # <5>
            HttpResponse.ok("Uploaded")
        ).onErrorReturn(
            HttpResponse.status(HttpStatus.CONFLICT).body("Upload Failed")
        )
    # end::outputStream[]

# tag::endclass[]
# end::endclass[]
