package io.micronaut.web.router.processor;

import io.micronaut.http.uri.RouteTemplate;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.writer.ByteCodeWriterUtils;
import io.micronaut.web.router.spi.RouteCandidateSink;
import io.micronaut.web.router.spi.RoutePlan;
import io.micronaut.web.router.spi.RouteSlot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates the class of a plan the way the route compiler writes it, and loads it.
 */
final class GeneratedPlans {

    private GeneratedPlans() {
    }

    static RouteDescription route(String key, String method, RouteTemplate template) {
        return new RouteDescription(key, method, template, null, ClassElement.of(Object.class));
    }

    static RoutePlan load(CompiledRoutePlan plan) {
        byte[] bytes = ByteCodeWriterUtils.writeByteCode(RoutePlanWriter.write(plan), null);
        ClassLoader loader = new ClassLoader(GeneratedPlans.class.getClassLoader()) {
            @Override
            protected Class<?> findClass(String name) throws ClassNotFoundException {
                if (name.equals(plan.className())) {
                    return defineClass(name, bytes, 0, bytes.length);
                }
                return super.findClass(name);
            }
        };
        try {
            return (RoutePlan) loader.loadClass(plan.className()).getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * @return The slots the parser matches for a normalised path, with the captured values
     */
    static Map<String, List<String>> match(RoutePlan plan, String path) {
        RouteSlot[] slots = plan.slots();
        Map<String, List<String>> matched = new LinkedHashMap<>();
        plan.match(path, (slot, p, spans) -> {
            List<String> values = new ArrayList<>();
            for (int i = 0; i < slots[slot].captures().length; i++) {
                values.add(p.substring(spans[2 * i], spans[2 * i + 1]));
            }
            if (matched.put(slots[slot].key(), values) != null) {
                throw new AssertionError("The slot " + slots[slot].key() + " was reported twice for " + path);
            }
        });
        return matched;
    }

    /**
     * A plan that counts the requests of the router, and records the slots its parser reported:
     * it proves which path the router took.
     */
    static final class ObservedPlan implements RoutePlan {
        final RoutePlan plan;
        final List<String> reported = new ArrayList<>();
        int matches;
        private final String fingerprint;

        ObservedPlan(RoutePlan plan) {
            this(plan, plan.fingerprint());
        }

        ObservedPlan(RoutePlan plan, String fingerprint) {
            this.plan = plan;
            this.fingerprint = fingerprint;
        }

        @Override
        public String id() {
            return plan.id();
        }

        @Override
        public int abiVersion() {
            return plan.abiVersion();
        }

        @Override
        public String inputProfile() {
            return plan.inputProfile();
        }

        @Override
        public String selectionPolicy() {
            return plan.selectionPolicy();
        }

        @Override
        public String fingerprint() {
            return fingerprint;
        }

        @Override
        public String[] owners() {
            return plan.owners();
        }

        @Override
        public RouteSlot[] slots() {
            return plan.slots();
        }

        @Override
        public String commonPrefix() {
            return plan.commonPrefix();
        }

        @Override
        public int maxCaptures() {
            return plan.maxCaptures();
        }

        @Override
        public void match(String path, RouteCandidateSink sink) {
            matches++;
            RouteSlot[] slots = plan.slots();
            plan.match(path, (slot, p, spans) -> {
                reported.add(slots[slot].key());
                sink.candidate(slot, p, spans);
            });
        }
    }
}
