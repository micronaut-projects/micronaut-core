# tag::endpointImport[]
from micronaut.context.annotation import Requires
from micronaut.management.endpoint.annotation import Endpoint
# end::endpointImport[]

# tag::mediaTypeImport[]
from micronaut.http import MediaType
# end::mediaTypeImport[]

# tag::writeImport[]
from micronaut.management.endpoint.annotation import Write
# end::writeImport[]

# tag::deleteImport[]
from micronaut.management.endpoint.annotation import Delete
# end::deleteImport[]

from jakarta.annotation import PostConstruct
from micronaut.management.endpoint.annotation import Read


@Requires(property="spec.name", value="MessageEndpointSpec")
# tag::endpointClassBegin[]
@Endpoint(id="message", defaultSensitive=False)
class MessageEndpoint:
# end::endpointClassBegin[]

    # tag::message[]
    message: str | None
    # end::message[]

    @PostConstruct
    def init(self) -> None:
        self.message = "default message"

    @Read
    def readMessage(self) -> str | None:
        return self.message

    # tag::writeArg[]
    @Write(consumes=MediaType.APPLICATION_FORM_URLENCODED, produces=MediaType.TEXT_PLAIN)
    def updateMessage(self, newMessage: str) -> str:
        self.message = newMessage

        return "Message updated"
    # end::writeArg[]

    # tag::simpleDelete[]
    @Delete
    def deleteMessage(self) -> str:
        self.message = None

        return "Message deleted"
    # end::simpleDelete[]

# tag::endpointClassEnd[]
# end::endpointClassEnd[]
