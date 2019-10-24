package io.micronaut.docs.function.client.aws

import io.micronaut.context.annotation.Requires

//tag::imports[]
import io.micronaut.function.FunctionBean
import java.util.function.Function

//end::imports[]

@Requires(property = "spec.name", value = "IsbnValidationSpec")
//tag::clazz[]
@FunctionBean("isbn-validator")
class IsbnValidatorFunction : Function<IsbnValidationRequest, IsbnValidationResponse> {

    override fun apply(request: IsbnValidationRequest): IsbnValidationResponse {
        return IsbnValidationResponse()
    }
}
//end::clazz[]