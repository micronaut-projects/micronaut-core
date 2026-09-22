/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.web.router.spi;

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.uri.RouteTemplate;

/**
 * The declaration of a slot of a plan.
 *
 * @param plan The plan
 * @param slot The slot
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
record DefaultPlannedRouteDeclaration(RoutePlan plan, RouteSlot slot) implements PlannedRouteDeclaration {

    @Override
    public String key() {
        return slot.key();
    }

    @Override
    public HttpMethod httpMethod() {
        return slot.httpMethod();
    }

    @Override
    public String httpMethodName() {
        return slot.httpMethodName();
    }

    @Override
    public String uriTemplate() {
        return slot.template().expression();
    }

    @Override
    public RouteTemplate template() {
        return slot.template();
    }

    @Override
    public String requiredPathPrefix() {
        return slot.requiredPrefix();
    }

    @Override
    public int rawLength() {
        return slot.rawLength();
    }

    @Override
    public int pathVariableCount() {
        return slot.pathVariableCount();
    }

    @Override
    public String toString() {
        return slot.httpMethodName() + ' ' + slot.template() + " [" + slot.key() + ']';
    }
}
