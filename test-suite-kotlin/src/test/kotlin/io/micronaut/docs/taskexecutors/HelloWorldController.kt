package io.micronaut.docs.taskexecutors
import io.micronaut.context.annotation.Requires
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Produces
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn

@Requires(property = "spec.name", value = "TaskExecutorsBlockingTest")
//tag::clazz[]
@Controller("/hello")
class HelloWorldController {
    @ExecuteOn(TaskExecutors.BLOCKING)
    @Produces(MediaType.TEXT_PLAIN)
    @Get("/world")
    fun index() = "Hello World"
}
//end::clazz[]
