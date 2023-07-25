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
package io.micronaut.web.router;

import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/**
 * A {@link RouteMatch} for a status code.
 *
 * @param <T> The type
 * @param <R> The return type
 * @author Graeme Rocher
 * @since 1.0
 */
class StatusRouteMatch<T, R> extends AbstractRouteMatch<T, R> {

    private final ArrayList<Argument<?>> requiredArguments;

    /**
     * @param routeInfo         The route info
     * @param conversionService The conversion service
     */
    StatusRouteMatch(StatusRouteInfo<T, R> routeInfo, ConversionService conversionService) {
        super(routeInfo, conversionService);
        this.requiredArguments = new ArrayList<>(Arrays.asList(getArguments()));
    }

    @Override
    public Map<String, Object> getVariableValues() {
        return Collections.emptyMap();
    }

    @Override
    public Collection<Argument<?>> getRequiredArguments() {
        return requiredArguments;
    }

}
