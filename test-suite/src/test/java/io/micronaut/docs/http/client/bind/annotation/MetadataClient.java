package io.micronaut.docs.http.client.bind.annotation;

import io.micronaut.http.annotation.Get;
import io.micronaut.http.client.annotation.Client;

import java.util.Map;

//tag::clazz[]
@Client("/")
public interface MetadataClient {

    @Get("/client/bind")
    String get(@Metadata Map<String, Object> metadata);
}
//end::clazz[]
