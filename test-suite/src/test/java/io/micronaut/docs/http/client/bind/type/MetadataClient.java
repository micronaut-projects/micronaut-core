package io.micronaut.docs.http.client.bind.type;

import io.micronaut.docs.http.client.bind.type.Metadata;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.client.annotation.Client;

//tag::clazz[]
@Client("/")
public interface MetadataClient {

    @Get("/client/bind")
    String get(Metadata metadata);
}
//end::clazz[]
