package io.micronaut.docs.function.client.aws

import io.micronaut.context.annotation.Requires
//tag::imports[]
import io.micronaut.function.client.FunctionClient
import io.micronaut.http.annotation.Body
import io.reactivex.Single
import javax.inject.Named

//end::imports[]

@Requires(property = "spec.name", value = "IsbnValidationSpec")
//tag::clazz[]
@FunctionClient
interface IsbnValidatorClient {

    @Named("isbn-validator")
    fun validate(@Body request: IsbnValidationRequest): Single<IsbnValidationResponse>  // <1>
}
//end::clazz[]