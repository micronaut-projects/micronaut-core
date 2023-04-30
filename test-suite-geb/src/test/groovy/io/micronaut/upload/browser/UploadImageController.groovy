package io.micronaut.upload.browser

import io.micronaut.context.annotation.Requires
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Consumes
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.Produces
import io.micronaut.http.multipart.CompletedFileUpload

@Requires(property = "spec.name", value = "UploadBrowserSpec")
@Controller("/image")
class UploadImageController {

    @Produces(MediaType.TEXT_HTML)
    @Get("/create")
    String create() {
        '<html><head><title>Create Image</title></head><body><form method="post" action="/image/save" enctype="multipart/form-data"><label for="file">File:</label><input type="file" name="file" id="file"><input type="submit" value="Upload"/></form></body></html>'
    }


    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.TEXT_HTML)
    @Post("/save")
    String save(CompletedFileUpload file) {
        String title = file.getSize() == 0 ? 'File is Empty' : 'File Saved'
        "<html><head><title>${title}</title></head><body></body></html>"
    }
}
