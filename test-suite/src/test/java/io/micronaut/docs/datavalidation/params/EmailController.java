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
package io.micronaut.docs.datavalidation.params;

import io.micronaut.context.annotation.Requires;
//tag::imports[]
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.validation.Validated;

import javax.validation.constraints.NotBlank;
import java.util.Collections;
//end::imports[]

@Requires(property = "spec.name", value = "datavalidationparams")
//tag::clazz[]
@Validated // <1>
@Controller("/email")
public class EmailController {

    @Get("/send")
    public HttpResponse send(@NotBlank String recipient, // <2>
                             @NotBlank String subject) { // <2>
        return HttpResponse.ok(Collections.singletonMap("msg", "OK"));
    }
}
//end::clazz[]
