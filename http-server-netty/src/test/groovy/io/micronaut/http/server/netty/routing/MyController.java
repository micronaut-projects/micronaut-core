package io.micronaut.http.server.netty.routing;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;

import java.util.List;

@Requires(property = "spec.name", value = "RootRoutingTest")
@Controller
@ExecuteOn(TaskExecutors.BLOCKING)
public class MyController {

    @Post
    public KeyValue createRoot(@Body KeyValue body) {
        return body;
    }

    @Get
    public KeyValue root() {
        return KeyValue.of("hello", "world");
    }

    @Get("/{id}")
    public KeyValue id(String id) {
        return KeyValue.of("hello", id);
    }

    @Get("/{id}/items")
    public List<KeyValue> items(String id) {
        return List.of(KeyValue.of("hello", id), KeyValue.of("foo", "bar"));
    }
}
