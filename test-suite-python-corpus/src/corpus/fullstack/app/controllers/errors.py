"""The API error contract, in one place.

The upstream FastAPI template returns ``{"detail": "..."}`` for ordinary errors
but ``{"detail": [...]}`` for validation failures, so its generated TypeScript
client has to branch on the type of ``detail``. We return one shape from every
failure — ``{"message": ..., "errors": {...}}`` — and this handler is the only
place that decides it.

Because validation is declarative (``@Valid`` on the body), controllers never
call a validator or assemble an error response themselves.
"""

from jakarta.inject import Singleton
from jakarta.validation import ConstraintViolationException
from micronaut.context.annotation import Replaces
from micronaut.http import HttpRequest, HttpResponse, HttpStatus
from micronaut.http.server.exceptions import ExceptionHandler
from micronaut.validation.exceptions import ConstraintExceptionHandler

from ..dto import ApiError


def _field_of(violation) -> str:
    """Last segment of a constraint violation's property path.

    GraalPy exposes the property path as a foreign object, so use its Java
    string form rather than relying on an unexported API.
    """
    path = str(violation.getPropertyPath().toString())
    return path.rsplit(".", 1)[-1] if path else ""


@Singleton
@Replaces(ConstraintExceptionHandler)
class ValidationExceptionHandler(ExceptionHandler[ConstraintViolationException, HttpResponse]):
    """Turns bean-validation failures into 422 with a field-keyed error map.

    Replaces Micronaut's default handler so that validation failures use the
    same body shape as every other error.
    """

    def handle(self, request: HttpRequest, exception: ConstraintViolationException) -> HttpResponse:
        errors = {}
        for violation in exception.getConstraintViolations():
            field = _field_of(violation)
            if field:
                errors[field] = str(violation.getMessage())
        return HttpResponse.status(HttpStatus.UNPROCESSABLE_ENTITY).body(
            ApiError(message="Validation failed", errors=errors)
        )
