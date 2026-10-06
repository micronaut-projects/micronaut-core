from micronaut.context.annotation import Requires

# tag::imports[]
from typing import Annotated

import java
from micronaut.http import MediaType
from micronaut.http.annotation import Controller, Part, Post
from micronaut.http.form import FileUpload, FormData, FormPart, FormParts

CompletionStage = java.type("java.util.concurrent.CompletionStage")
Files = java.type("java.nio.file.Files")
UUID = java.type("java.util.UUID")
# end::imports[]


@Requires(property="spec.name", value="ProfileControllerSpec")
# tag::class[]
@Controller(value="/profile", consumes=MediaType.MULTIPART_FORM_DATA)
class ProfileController:
    def __init__(self):
        self.uploads = Files.createTempDirectory("uploads")
# end::class[]

    # tag::formData[]
    @Post("/form")
    def form(self, form: FormData) -> CompletionStage:  # <1>
        name = form.getString("name")  # <2>
        age = form.getInt("age", 18)  # <3>
        avatar = form.getFile("avatar")  # <4>
        return (avatar.bytes(64 * 1024)  # <5>
                .thenApply(lambda content: f"{name} ({age}) sent {avatar.fileName()} of {len(content)} bytes"))
    # end::formData[]

    # tag::arguments[]
    @Post("/arguments")
    def arguments(self,
                  name: str,  # <1>
                  picture: Annotated[FileUpload, Part("avatar")],  # <2>
                  cover: FileUpload | None,  # <3>
                  documents: list[FileUpload]) -> CompletionStage:  # <4>
        destination = self.uploads.resolve(UUID.randomUUID().toString())
        cover_text = " without a cover" if cover is None else f" with {cover.fileName()}"
        return (picture.transferTo(destination)  # <5>
                .thenApply(lambda done: f"{name} sent {picture.fileName()}{cover_text} and {len(documents)} documents"))
    # end::arguments[]

    # tag::formPart[]
    @Post("/video")
    def video(self,
              title: str,  # <1>
              video: FormPart) -> CompletionStage:  # <2>
        destination = self.uploads.resolve(UUID.randomUUID().toString())
        return (video.transferTo(destination)  # <3>
                .thenApply(lambda done: f"{title} stored {video.fileName()}"))
    # end::formPart[]

    # tag::formParts[]
    @Post("/parts")
    def parts(self, parts: FormParts) -> CompletionStage:  # <1>
        summary = []

        def read(part):  # <2>
            if part.isFile():
                destination = self.uploads.resolve(UUID.randomUUID().toString())
                return (part.transferTo(destination)  # <3>
                        .thenRun(lambda: summary.append(f"{part.name()} stored, ")))
            return (part.text(1024)  # <4>
                    .thenAccept(lambda value: summary.append(f"{part.name()}={value}, ")))

        return parts.forEach(read).thenApply(lambda done: "".join(summary))  # <5>
    # end::formParts[]

# tag::endclass[]
# end::endclass[]
