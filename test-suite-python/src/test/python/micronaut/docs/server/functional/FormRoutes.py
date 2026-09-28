from micronaut.context.annotation import Requires
# tag::imports[]
import uuid
from typing import Annotated

from jakarta.inject import Singleton
from java.nio.file import Path
from micronaut.context.annotation import Value
from micronaut.http import HttpResponse, MediaType
from micronaut.web.router.builder import HttpRouteBuilder, HttpRoutes
# end::imports[]


@Requires(property="spec.name", value="FormRoutesTest")
# tag::clazz[]
@Singleton
class FormRoutes(HttpRoutes):

    def __init__(self, uploads: Annotated[Path, Value("${uploads.directory}")]):
        self.uploads = uploads

    def routes(self, routes: HttpRouteBuilder) -> None:
        routes.POST("/forms/signup").form().handle(lambda request, path_variables, form:  # <1>
                    HttpResponse.ok(f"Welcome {form.getString('name')}, {form.getInt('age', 18)}")
                    .contentType(MediaType.TEXT_PLAIN_TYPE))

        def profile(request, path_variables, body):
            form_stage = body.form()  # <2>

            def read_avatar(form):
                avatar = form.getFile("avatar")  # <3>
                return avatar.bytes(1024 * 1024).thenApply(lambda bytes:
                    HttpResponse.ok(f"{form.getString('name')} sent {avatar.fileName()}, {len(bytes)} bytes")
                    .contentType(MediaType.TEXT_PLAIN_TYPE))

            return form_stage.thenCompose(read_avatar)

        routes.POST("/forms/profile").consumes(MediaType.MULTIPART_FORM_DATA_TYPE).body().handleAsync(profile)

        def upload(request, path_variables, body):
            destination = self.uploads.resolve(f"{uuid.uuid4()}.upload")
            return (body.parts()  # <4>
                    .part("file", lambda part: part.file().transferTo(destination))  # <5>
                    .thenApply(lambda found:
                               HttpResponse.created(str(destination.getFileName())).contentType(MediaType.TEXT_PLAIN_TYPE)
                               if found else HttpResponse.badRequest("no file")))

        routes.POST("/forms/upload").consumes(MediaType.MULTIPART_FORM_DATA_TYPE).body().handleAsync(upload)
# end::clazz[]
