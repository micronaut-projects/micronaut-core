/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.docs.filters

//tag::import[]
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Consumes
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
//end::import[]

//tag::classBegin[]
@Controller("/")
class CspReportController {
//end::classBegin[]

    //tag::type[]
    public static final String APPLICATION_CSP_REPORT = "application/csp-report"
    //end::type[]

    //tag::report[]
    @Consumes(APPLICATION_CSP_REPORT) //<1>
    @Post("/report")
    HttpStatus report(@Body String violation) {
        // record violation
        return HttpStatus.OK
    }
    //end::report[]

//tag::classEnd[]
}
//end::classEnd[]