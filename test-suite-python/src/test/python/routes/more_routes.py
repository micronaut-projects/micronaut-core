from micronaut.http.annotation import Get
from example import ExampleService
from typing import Annotated
from jakarta.inject import Inject

example_service : Annotated[ExampleService, Inject]

@Get("/another-route-from-script")
def get_message() -> str:
    return example_service.say_hello() + "!"
