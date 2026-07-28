from typing import Annotated

import java

# tag::endpointImport[]
from micronaut.management.endpoint.annotation import Endpoint
# end::endpointImport[]

# tag::readImport[]
from micronaut.management.endpoint.annotation import Read
# end::readImport[]

# tag::mediaTypeImport[]
from micronaut.http import MediaType
from micronaut.management.endpoint.annotation import Selector
# end::mediaTypeImport[]

# tag::writeImport[]
from micronaut.management.endpoint.annotation import Write
# end::writeImport[]

from jakarta.annotation import PostConstruct

Date = java.type("java.util.Date")


# tag::endpointClassBegin[]
@Endpoint(id="date",
          prefix="custom",
          defaultEnabled=True,
          defaultSensitive=False)
class CurrentDateEndpoint:
# end::endpointClassBegin[]

    # tag::methodSummary[]
    # .. endpoint methods
    # end::methodSummary[]

    # tag::currentDate[]
    currentDate: Date
    # end::currentDate[]

    @PostConstruct
    def init(self) -> None:
        self.currentDate = Date()

    # tag::simpleRead[]
    @Read
    def currentDate(self) -> Date:
        return self.currentDate
    # end::simpleRead[]

    # tag::readArg[]
    @Read(produces=MediaType.TEXT_PLAIN)  # <1>
    def currentDatePrefix(self, prefix: Annotated[str, Selector]) -> str:
        return prefix + ": " + str(self.currentDate)
    # end::readArg[]

    # tag::simpleWrite[]
    @Write
    def reset(self) -> str:
        self.currentDate = Date()

        return "Current date reset"
    # end::simpleWrite[]
# tag::endpointClassEnd[]
# end::endpointClassEnd[]
