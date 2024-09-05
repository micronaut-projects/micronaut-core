package io.micronaut.http.server.netty.routing;

import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.client.annotation.Client;

import java.util.List;

@Client("/")
public interface MyClient {
    @Post
    KeyValue setRoot(@Body KeyValue body);

    @Get
    KeyValue getRoot();

    @Get("/{id}")
    KeyValue getId(String id);

    @Get("/{id}/items")
    List<KeyValue> getRelations(String id);
}
