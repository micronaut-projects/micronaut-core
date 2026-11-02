from typing import Annotated

from micronaut.context.annotation import Requires
from micronaut.http import MediaType
from micronaut.http.annotation import Body, Controller, Post


@Requires(property="spec.name", value="ChecksumFilterSpec")
# tag::clazz[]
@Controller("/checksum")
class ChecksumController:

    @Post(consumes=MediaType.TEXT_PLAIN, produces=MediaType.TEXT_PLAIN)
    def upload(self, body: Annotated[str, Body]) -> str:  # <1>
        return "Received " + str(len(body)) + " characters"
# end::clazz[]
