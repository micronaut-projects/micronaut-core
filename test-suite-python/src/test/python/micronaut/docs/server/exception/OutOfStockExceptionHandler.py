import java
from micronaut.context.annotation import Requires
# tag::clazz[]
from jakarta.inject import Singleton
from micronaut.http import HttpRequest, HttpResponse
from micronaut.http.annotation import Produces
from micronaut.http.server.exceptions.response import ErrorContext, ErrorResponseProcessor

from .OutOfStockException import OutOfStockException

ExceptionHandler = java.type("io.micronaut.http.server.exceptions.ExceptionHandler")


# TODO: Re-enable this direct port when Python classes can extend Java Throwable types.
# @Produces
# @Singleton
# @Requires(property="spec.name", value="ExceptionHandlerSpec")
# @Requires(classes=[OutOfStockException, ExceptionHandler])
# class OutOfStockExceptionHandler(ExceptionHandler[OutOfStockException, HttpResponse]):
#
#     def __init__(self, errorResponseProcessor: ErrorResponseProcessor):
#         self.errorResponseProcessor = errorResponseProcessor
#
#     def handle(self, request: HttpRequest, e: OutOfStockException) -> HttpResponse:
#         return self.errorResponseProcessor.processResponse(
#             ErrorContext.builder(request)
#                 .cause(e)
#                 .errorMessage("No stock available")
#                 .build(),
#             HttpResponse.badRequest(),
#         )  # <1>
# end::clazz[]
