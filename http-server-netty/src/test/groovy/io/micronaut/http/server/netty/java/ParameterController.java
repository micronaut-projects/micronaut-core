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
package io.micronaut.http.server.netty.java;

import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.HttpParameters;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;

import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * @author Graeme Rocher
 * @since 1.0
 */
@Controller("/java/parameter")
public class ParameterController {
    @Get
    String index(Integer max) {
        return "Parameter Value: " + max;
    }

    @Get("/simple")
    String simple(@QueryValue Integer max) {
        return "Parameter Value: " + max;
    }

    @Get("/named")
    String named(@QueryValue("maximum") Integer max) {
        return "Parameter Value: " + max;
    }

    @Get("/optional")
    String optional(@QueryValue Optional<Integer> max) {
        return "Parameter Value: " + max.orElse(10);
    }

    @Get("/nullable")
    String nullable(@Nullable Integer max) {
        return "Parameter Value: " + (max != null ? max : "null");
    }

    @Post(value = "/nullable-body", consumes = MediaType.APPLICATION_JSON)
    String nullableBody(@Nullable Integer max) {
        return "Body Value: " + (max != null ? max : "null");
    }

    @Post(value = "/requires-body", consumes = MediaType.APPLICATION_JSON)
    String requiresBody(Integer max) {
        return "Body Value: " + (max != null ? max : "null");
    }

    @Get("/all")
    String all(HttpParameters parameters) {
        return "Parameter Value: " + parameters.get("max", Integer.class, 10);
    }

    @Get("/map")
    String map(Map<String, Integer> values) {
        return "Parameter Value: " + values.get("max") + values.get("offset");
    }

    @Get("/list")
    String list(List<Integer> values) {
        assert values.stream().allMatch(val -> val instanceof Integer);
        return "Parameter Value: " + values;
    }

    @Get("/optional-list")
    String optionalList(Optional<List<Integer>> values) {
        if (values.isPresent()) {
            assert values.get().stream().allMatch(val -> val instanceof Integer);
            return "Parameter Value: " + values.get();
        } else {
            return "Parameter Value: none";
        }
    }
}