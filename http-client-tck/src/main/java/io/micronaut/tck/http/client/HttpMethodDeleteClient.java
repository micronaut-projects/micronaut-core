package io.micronaut.tck.http.client;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.client.annotation.Client;

@Requires(property = "spec.name", value = "HttpMethodDeleteTest")
@Client("/delete")
public interface HttpMethodDeleteClient {

    HttpResponse<Void> index();

    @Delete("/string-response")
    String response();

    @Delete("/object-response")
    HttpMethodDeleteTestController.Person person();
}
