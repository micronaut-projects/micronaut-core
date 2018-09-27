package io.micronaut.function.client.aws;

import io.micronaut.context.annotation.Requires;
//tag::imports[]
import io.micronaut.function.client.FunctionClient;
import io.micronaut.http.annotation.Body;
import io.reactivex.Single;
import javax.inject.Named;
//end::imports[]

@Requires(property = "spec.name", value = "IsbnValidationSpec")
//tag::clazz[]
@FunctionClient
public interface IsbnValidatorClient {

    @Named("isbn-validator")
    Single<IsbnValidationResponse> validate(@Body IsbnValidationRequest request); // <1>
}
//end::clazz[]