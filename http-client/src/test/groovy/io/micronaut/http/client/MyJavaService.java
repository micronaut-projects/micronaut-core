package io.micronaut.http.client;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class MyJavaService {

    @Inject
    @Client("/")
    HttpClient client;

    @Inject @Client("/")
    RxHttpClient rxHttpClient;

    public HttpClient getClient() {
        return client;
    }

    public RxHttpClient getRxHttpClient() {
        return rxHttpClient;
    }
}
