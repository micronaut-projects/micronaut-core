from micronaut.context.annotation import Requires
from micronaut.http import MediaType
from micronaut.http.annotation import Controller, Get, Produces
from micronaut.scheduling import TaskExecutors
from micronaut.scheduling.annotation import ExecuteOn


@Requires(property="spec.name", value="TaskExecutorsBlockingTest")
#tag::clazz[]
@Controller("/hello")
class HelloWorldController:

    @ExecuteOn(TaskExecutors.BLOCKING)
    @Produces(MediaType.TEXT_PLAIN)
    @Get("/world")
    def index(self) -> str:
        return "Hello World"
#end::clazz[]
