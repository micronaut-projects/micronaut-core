import java
from micronaut.http.annotation import Get, Post

InvocationCounter = java.type("example.InvocationCounter")


@Post("/invocation-count/increment")
def increment() -> str:
    return str(InvocationCounter.increment())


@Get("/invocation-count/none")
def none_value() -> str:
    InvocationCounter.increment()
    return None
