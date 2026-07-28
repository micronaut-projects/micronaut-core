import java

# tag::class[]
from micronaut.http import HttpResponse, MediaType
from micronaut.http.annotation import Controller, Post
from micronaut.http.multipart import CompletedFileUpload
from micronaut.scheduling import TaskExecutors
from micronaut.scheduling.annotation import ExecuteOn

File = java.type("java.io.File")
Files = java.type("java.nio.file.Files")
Paths = java.type("java.nio.file.Paths")


@Controller("/upload")
class CompletedUploadController:

    @Post(value="/completed", consumes=MediaType.MULTIPART_FORM_DATA, produces=MediaType.TEXT_PLAIN)  # <1>
    @ExecuteOn(TaskExecutors.BLOCKING)
    def uploadCompleted(self, file: CompletedFileUpload) -> HttpResponse:  # <2>
        try:
            tempFile = File.createTempFile(file.getFilename(), "temp")  # <3>
            path = Paths.get(tempFile.getAbsolutePath())
            Files.write(path, file.getBytes())  # <3>
            return HttpResponse.ok("Uploaded")
        except Exception:
            return HttpResponse.badRequest("Upload Failed")
        finally:
            file.close()
# end::class[]
