package io.micronaut.validation;

import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.client.annotation.Client;

import javax.validation.constraints.NotBlank;

@Client("/validated/tests")
interface JavaClient {

    @Get(value = "/validated")
    String test5(@NotBlank @Header String header);
}
