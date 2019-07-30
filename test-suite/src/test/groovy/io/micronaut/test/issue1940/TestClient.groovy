package io.micronaut.test.issue1940


import io.micronaut.http.annotation.Get
import io.micronaut.http.client.annotation.Client

@Client
interface TestClient extends TestApi {

    @Get("/greeting")
    @Override
    String greeting(String name);
}