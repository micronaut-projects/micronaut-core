from micronaut.http.annotation import Get
from .another import AnotherService
from typing import Annotated
from jakarta.inject import Inject

example_service : Annotated[AnotherService, Inject]

@Get("/route-from-script2")
def get_message() -> str:
    return example_service.say_hello()
