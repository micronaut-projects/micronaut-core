from typing import Annotated

import java

# tag::class[]
from micronaut.core.async_.annotation import SingleResult
from micronaut.http import MediaType
from micronaut.http.annotation import Body, Controller, Post
from micronaut.http.multipart import CompletedFileUpload
from micronaut.http.server.multipart import MultipartBody

Flux = java.type("reactor.core.publisher.Flux")
Mono = java.type("reactor.core.publisher.Mono")
Publisher = java.type("org.reactivestreams.Publisher")
Schedulers = java.type("reactor.core.scheduler.Schedulers")


@Controller("/upload")
class WholeBodyUploadController:

    @Post(value="/whole-body", consumes=MediaType.MULTIPART_FORM_DATA, produces=MediaType.TEXT_PLAIN)  # <1>
    @SingleResult
    def uploadBytes(self, body: Annotated[MultipartBody, Body]) -> Publisher:  # <2>
        def close_part(completedPart):
            partName = completedPart.getName()
            if isinstance(completedPart, CompletedFileUpload):
                originalFileName = completedPart.getFilename()
            completedPart.close()

        return Flux.from_(body).publishOn(
            Schedulers.boundedElastic()
        ).doOnNext(close_part).then(Mono.just("Uploaded"))
# end::class[]
