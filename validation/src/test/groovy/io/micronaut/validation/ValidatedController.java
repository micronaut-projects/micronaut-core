/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.validation;

import io.micronaut.http.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.Digits;
import javax.validation.constraints.Min;
import java.util.Optional;

/**
 * @author Graeme Rocher
 * @since 1.0
 */
@Controller("/validated")
public class ValidatedController {

    @Post("/args")
    public String args(@Digits(integer = 3, fraction = 2) String amount) {
        return "$" + amount;
    }

    @Post("/pojo")
    public Pojo pojo(@Body @Valid Pojo pojo) {
        return pojo;
    }

    @Post("/no-introspection")
    public PojoNoIntrospection pojo(@Body @Valid PojoNoIntrospection pojo) {
        return pojo;
    }

    @Get("/optional")
    public boolean optional(@QueryValue @Min(1) Optional<Integer> limit) {
        return limit.map(l -> l >= 1).orElse(true);
    }
}
