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
package io.micronaut.web.router;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.io.service.SoftServiceLoader;
import io.micronaut.http.uri.RouteTemplate;
import io.micronaut.http.uri.spi.RouteTemplateEngines;
import io.micronaut.web.router.spi.RoutePlan;
import io.micronaut.web.router.spi.RouteSlot;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Loads the {@link RoutePlan}s of controllers and checks that a plan agrees with the runtime
 * before the router uses it.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class RoutePlans {

    private static final Logger LOG = LoggerFactory.getLogger(RoutePlans.class);

    private RoutePlans() {
    }

    /**
     * @param classLoader The class loader
     * @return The plans registered as services
     */
    static List<RoutePlan> load(ClassLoader classLoader) {
        return SoftServiceLoader.load(RoutePlan.class, classLoader).collectAll();
    }

    /**
     * Why the router cannot use a plan: an optional plan that does not agree with the runtime is
     * not used, and the routes of its slots are matched as ordinary routes.
     *
     * @param plan  The plan
     * @param slots Its slots
     * @return The reason, or {@code null} if the router can use the plan
     */
    static @Nullable String incompatibility(RoutePlan plan, RouteSlot[] slots) {
        if (plan.abiVersion() != RoutePlan.ABI_VERSION) {
            return "it was generated for the ABI version " + plan.abiVersion() + ", the router has " + RoutePlan.ABI_VERSION;
        }
        if (!RoutePlan.INPUT_PROFILE.equals(plan.inputProfile())) {
            return "it was generated for the input profile " + plan.inputProfile() + ", the router has " + RoutePlan.INPUT_PROFILE;
        }
        if (!RoutePlan.SELECTION_POLICY.equals(plan.selectionPolicy())) {
            return "it was generated for the selection policy " + plan.selectionPolicy() + ", the router has " + RoutePlan.SELECTION_POLICY;
        }
        if (!RoutePlan.fingerprint(slots).equals(plan.fingerprint())) {
            return "its slots do not have the fingerprint its parser was generated for";
        }
        RouteTemplateEngines engines = RouteTemplateEngines.defaults();
        for (RouteSlot slot : slots) {
            RouteTemplate template = slot.template();
            if (!engines.contains(template.engineId())) {
                return "the route template engine '" + template.engineId() + "' of " + slot + " is not registered";
            }
            String version = engines.engine(template.engineId()).version();
            if (!version.equals(slot.engineVersion())) {
                return "the route template engine '" + template.engineId() + "' of " + slot + " has the version " + version
                    + ", the plan was generated with " + slot.engineVersion();
            }
        }
        return null;
    }

    /**
     * Check a plan and log why it is not used.
     *
     * @param plan  The plan
     * @param slots Its slots
     * @return Whether the router can use the plan
     */
    static boolean usable(RoutePlan plan, RouteSlot[] slots) {
        String reason = incompatibility(plan, slots);
        if (reason != null) {
            LOG.warn("The route plan {} is not used and its routes are matched at runtime: {}", plan.id(), reason);
            return false;
        }
        return true;
    }
}
