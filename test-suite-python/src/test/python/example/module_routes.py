# tag::module-controller[]
from micronaut.http.annotation import Controller, Get

Controller("/module")

@Get("/")
def module_root() -> dict:
    return {"Hello": "World"}
# end::module-controller[]
