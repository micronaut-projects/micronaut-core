package io.micronaut.redirect;

import io.micronaut.http.client.annotation.Client;

@Client(value = "/", definitionType = Client.DefinitionType.SERVER)
public interface HttpClientApi extends Api {
}
