from micronaut.context.annotation import Requires
# tag::imports[]
import uuid
from typing import Annotated

import java
from jakarta.inject import Singleton
from java.nio.file import Path
from micronaut.context.annotation import Value
from micronaut.http import HttpResponse, HttpStatus, MediaType
from micronaut.web.router.builder import HttpRouteBuilder, HttpRoutes

from .Item import Item
from .ItemRepository import ItemRepository

CompletableFuture = java.type("java.util.concurrent.CompletableFuture")
# end::imports[]


@Requires(property="spec.name", value="BodyRoutesTest")
# tag::clazz[]
@Singleton
class BodyRoutes(HttpRoutes):

    def __init__(self, items: ItemRepository, uploads: Annotated[Path, Value("${uploads.directory}")]):
        self.items = items
        self.uploads = uploads

    def routes(self, routes: HttpRouteBuilder) -> None:
        routes.asyncPOST("/async/items", lambda request, path_variables, body:
                         body.body(Item)  # <1>
                         .thenCompose(self.items.save_async)
                         .thenApply(lambda item: HttpResponse.created(item)))
        routes.asyncPOST("/async/items/import", lambda request, path_variables, body:
                         body.elements(Item)  # <2>
                         .forEach(self.items.save_async)
                         .thenApply(lambda done=None: HttpResponse.accepted()))
        routes.asyncPOST("/async/notes", lambda request, path_variables, body:
                         body.text(1024)  # <3>
                         .thenApply(lambda text: HttpResponse.ok(f"received {len(text)} characters"))) \
            .consumes(MediaType.TEXT_PLAIN_TYPE)

        def transfer(request, path_variables, body):
            destination = self.uploads.resolve(f"{uuid.uuid4()}.bin")
            return (body.transferTo(destination)  # <4>
                    .thenApply(lambda done=None: HttpResponse.created(str(destination.getFileName()))))

        routes.asyncPUT("/async/files", transfer).consumes(MediaType.APPLICATION_OCTET_STREAM_TYPE)

        def guarded(request, path_variables, body):
            if not request.getHeaders().contains("X-Token"):
                return CompletableFuture.completedFuture(HttpResponse.status(HttpStatus.UNAUTHORIZED))  # <5>
            return (body.bytes(64 * 1024)
                    .thenApply(lambda bytes: HttpResponse.ok(f"accepted {len(bytes)} bytes")))

        routes.asyncPOST("/async/guarded", guarded).consumes(MediaType.APPLICATION_OCTET_STREAM_TYPE)
# end::clazz[]
