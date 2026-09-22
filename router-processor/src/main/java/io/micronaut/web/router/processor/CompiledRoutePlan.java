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
package io.micronaut.web.router.processor;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.web.router.spi.RouteSlot;

import java.util.List;

/**
 * A route plan the {@link RoutePlanCompiler} planned: the slots with their lowered templates, in
 * slot order, and the name of the class generated for it.
 *
 * @param className The name of the generated class
 * @param id        The identity of the plan
 * @param owners    The controller types whose routes are the slots, empty for a plan of declarations
 * @param slots     The slot descriptors, by slot number
 * @param lowered   The lowered template of each slot, by slot number
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public record CompiledRoutePlan(String className,
                                String id,
                                List<String> owners,
                                List<RouteSlot> slots,
                                List<LoweredTemplate> lowered) {

    /**
     * @return The fingerprint of the slots, see {@link io.micronaut.web.router.spi.RoutePlan#fingerprint(RouteSlot[])}
     */
    public String fingerprint() {
        return io.micronaut.web.router.spi.RoutePlan.fingerprint(slots.toArray(RouteSlot[]::new));
    }

    /**
     * @return The largest number of captured variables of a compiled slot
     */
    public int maxCaptures() {
        int max = 0;
        for (RouteSlot slot : slots) {
            if (slot.compiled()) {
                max = Math.max(max, slot.captures().length);
            }
        }
        return max;
    }

    /**
     * @return A literal that every path of a compiled slot starts with, or an empty string
     */
    public String commonPrefix() {
        String common = null;
        for (int i = 0; i < slots.size(); i++) {
            if (!slots.get(i).compiled()) {
                continue;
            }
            String prefix = prefix(lowered.get(i));
            if (common == null) {
                common = prefix;
            } else {
                int n = 0;
                int max = Math.min(common.length(), prefix.length());
                while (n < max && common.charAt(n) == prefix.charAt(n)) {
                    n++;
                }
                common = common.substring(0, n);
            }
        }
        return common == null ? "" : common;
    }

    /**
     * The literal that every path of a lowered template starts with: the leading literal
     * segments, and the slash of the first variable segment; nothing for the root template, which
     * matches the empty path.
     */
    private static String prefix(LoweredTemplate template) {
        StringBuilder prefix = new StringBuilder();
        for (LoweredTemplate.Segment segment : template.segments()) {
            prefix.append('/');
            if (segment.isVariable()) {
                break;
            }
            prefix.append(segment.literal());
        }
        return prefix.toString();
    }
}
