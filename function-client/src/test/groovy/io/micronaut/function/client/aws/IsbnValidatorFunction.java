package io.micronaut.function.client.aws;

import io.micronaut.context.annotation.Requires;

//tag::imports[]
import io.micronaut.function.FunctionBean;
import java.util.function.Function;
//end::imports[]

@Requires(property = "spec.name", value = "IsbnValidationSpec")
//tag::clazz[]
@FunctionBean("isbn-validator")
public class IsbnValidatorFunction implements Function<IsbnValidationRequest, IsbnValidationResponse> {

    @Override
    public IsbnValidationResponse apply(IsbnValidationRequest request) {
        return new IsbnValidationResponse();
    }
}
//end::clazz[]