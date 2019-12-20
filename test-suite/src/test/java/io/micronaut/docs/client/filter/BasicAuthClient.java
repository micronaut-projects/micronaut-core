package io.micronaut.docs.client.filter;

//tag::class[]
import io.micronaut.http.annotation.Get;
import io.micronaut.http.client.annotation.Client;

@BasicAuth // <1>
@Client("/message")
public interface BasicAuthClient {

    @Get
    String getMessage();
}
//end::class[]
