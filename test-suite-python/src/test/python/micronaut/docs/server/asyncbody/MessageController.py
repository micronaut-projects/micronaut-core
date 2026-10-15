from typing import Annotated

from micronaut.context.annotation import Requires

# tag::imports[]
from micronaut.http import MediaType
from micronaut.http.annotation import Body, Controller, Post
# end::imports[]


@Requires(property="spec.name", value="PeopleControllerSpec")
# tag::class[]
@Controller("/messages")
class MessageController:

    @Post(consumes=MediaType.TEXT_PLAIN)
    def receive(self, message: Annotated[str, Body]) -> str:  # <1>
        return "Received " + message
# end::class[]
