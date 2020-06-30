/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.docs.function.client.aws;

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