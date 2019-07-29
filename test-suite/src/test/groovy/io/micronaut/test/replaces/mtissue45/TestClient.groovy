package io.micronaut.test.replaces.mtissue45


import io.micronaut.http.annotation.Get
import io.micronaut.http.client.annotation.Client

@Client
interface TestClient extends TestApi {

    @Get("/greeting")
    @Override
    String greeting(String name);
}