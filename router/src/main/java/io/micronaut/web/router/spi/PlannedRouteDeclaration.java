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

import io.micronaut.core.annotation.Experimental;

/**
 * A declaration generated at compile time together with a {@link RoutePlan}: the declaration of
 * the slot with its {@link #key() key} in its {@link #plan() plan}. A handler bound to it is
 * matched by the parser of the plan: the router resolves the key to the slot once, when it takes
 * the route, and never looks the declaration up again for a request.
 *
 * <p>The declaration is a contract for the authors of annotation processors, which generate
 * declarations in any form, e.g. the constants of an enum or of a class; the plan identifies its
 * slots by their keys, not by the ordinal of a constant.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public interface PlannedRouteDeclaration extends IndexedRouteDeclaration {

    /**
     * @return The plan the slot of the declaration belongs to
     */
    RoutePlan plan();

    /**
     * @return The logical key of the declaration, the {@link RouteSlot#key() key} of its slot
     */
    String key();

    /**
     * The declaration of a slot of a plan, with the facts of its descriptor.
     *
     * @param plan The plan
     * @param key  The key of the slot
     * @return The declaration
     * @throws IllegalArgumentException if the plan has no slot with the key
     */
    static PlannedRouteDeclaration of(RoutePlan plan, String key) {
        for (RouteSlot slot : plan.slots()) {
            if (slot.key().equals(key)) {
                return new DefaultPlannedRouteDeclaration(plan, slot);
            }
        }
        throw new IllegalArgumentException("The route plan " + plan.id() + " has no slot with the key " + key);
    }
}
