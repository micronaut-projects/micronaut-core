/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
