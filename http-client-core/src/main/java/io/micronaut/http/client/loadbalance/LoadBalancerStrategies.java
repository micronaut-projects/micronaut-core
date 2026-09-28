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
package io.micronaut.http.client.loadbalance;

import io.micronaut.core.annotation.Internal;
import io.micronaut.discovery.ServiceInstance;
import io.micronaut.http.client.LoadBalancer;
import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The built-in {@link LoadBalancerStrategy strategies}.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class LoadBalancerStrategies {

    private LoadBalancerStrategies() {
    }

    /**
     * @param instance An instance
     * @return The key of the instance: its URI, since instances of a service may share an id
     */
    static String key(ServiceInstance instance) {
        return instance.getURI().toString();
    }

    /**
     * Each instance in turn.
     */
    static final class RoundRobin implements LoadBalancerStrategy {
        private final AtomicInteger index = new AtomicInteger();

        @Override
        public ServiceInstance select(List<ServiceInstance> available, @Nullable Object discriminator) {
            int i = index.getAndUpdate(current -> current == Integer.MAX_VALUE ? 0 : current + 1);
            return available.get(i % available.size());
        }
    }

    /**
     * A random instance.
     */
    static final class Random implements LoadBalancerStrategy {
        @Override
        public ServiceInstance select(List<ServiceInstance> available, @Nullable Object discriminator) {
            return available.get(ThreadLocalRandom.current().nextInt(available.size()));
        }
    }

    /**
     * Of two random instances, the one with fewer exchanges in flight: an exchange counts from its
     * selection to its reported outcome.
     */
    static final class PowerOfTwoChoices implements LoadBalancerStrategy {
        private final Map<String, AtomicInteger> inFlight = new ConcurrentHashMap<>();

        @Override
        public ServiceInstance select(List<ServiceInstance> available, @Nullable Object discriminator) {
            ServiceInstance selected;
            int size = available.size();
            if (size == 1) {
                selected = available.get(0);
            } else {
                ThreadLocalRandom random = ThreadLocalRandom.current();
                int a = random.nextInt(size);
                int b = random.nextInt(size - 1);
                if (b >= a) {
                    b++;
                }
                ServiceInstance first = available.get(a);
                ServiceInstance second = available.get(b);
                selected = inFlight(first) <= inFlight(second) ? first : second;
            }
            inFlight.computeIfAbsent(key(selected), k -> new AtomicInteger()).incrementAndGet();
            return selected;
        }

        @Override
        public void report(ServiceInstance instance, LoadBalancer.Outcome outcome) {
            AtomicInteger count = inFlight.get(key(instance));
            if (count != null) {
                count.updateAndGet(n -> Math.max(0, n - 1));
            }
        }

        int inFlight(ServiceInstance instance) {
            AtomicInteger count = inFlight.get(key(instance));
            return count == null ? 0 : count.get();
        }
    }

    /**
     * Smooth weighted round robin, as nginx does it: each selection adds the weight of every
     * instance to its current weight, picks the highest, and takes the total weight from it.
     */
    static final class Weighted implements LoadBalancerStrategy {
        private final Map<String, Long> current = new HashMap<>();

        @Override
        public synchronized ServiceInstance select(List<ServiceInstance> available, @Nullable Object discriminator) {
            long total = 0;
            boolean anyPositive = false;
            for (ServiceInstance instance : available) {
                if (weight(instance) > 0) {
                    anyPositive = true;
                    break;
                }
            }
            ServiceInstance best = null;
            long bestWeight = Long.MIN_VALUE;
            for (ServiceInstance instance : available) {
                int weight = anyPositive ? weight(instance) : 1;
                if (weight <= 0) {
                    continue;
                }
                total += weight;
                long w = current.merge(key(instance), (long) weight, Long::sum);
                if (w > bestWeight) {
                    bestWeight = w;
                    best = instance;
                }
            }
            // every available instance has an entry: the others are gone and forgotten
            current.keySet().retainAll(available.stream().map(LoadBalancerStrategies::key).toList());
            if (best == null) {
                best = available.get(0);
            } else {
                current.merge(key(best), -total, Long::sum);
            }
            return best;
        }

        /**
         * @param instance An instance
         * @return Its weight, the {@code weight} metadata, {@code 1} when absent or invalid
         */
        static int weight(ServiceInstance instance) {
            String value = instance.getMetadata().get("weight", String.class).orElse(null);
            if (value == null) {
                return 1;
            }
            try {
                return Math.max(0, Integer.parseInt(value.strip()));
            } catch (NumberFormatException e) {
                return 1;
            }
        }
    }

    /**
     * The same instance for the same key (rendezvous hashing): when an instance goes away, only
     * its keys move. Round robin without a key.
     */
    static final class Sticky implements LoadBalancerStrategy {
        private final RoundRobin fallback = new RoundRobin();

        @Override
        public ServiceInstance select(List<ServiceInstance> available, @Nullable Object discriminator) {
            // a request is never hashed: its key is the load balancer key, if any
            Object keyObject = LoadBalancerKey.of(discriminator);
            if (keyObject == null) {
                return fallback.select(available, null);
            }
            String key = keyObject.toString();
            ServiceInstance best = available.get(0);
            long bestScore = hash(key, key(best));
            for (ServiceInstance instance : available) {
                long score = hash(key, key(instance));
                if (score > bestScore) {
                    best = instance;
                    bestScore = score;
                }
            }
            return best;
        }

        /**
         * A 64-bit FNV-1a hash of the discriminator and the instance, mixed, stable across
         * restarts.
         */
        static long hash(String discriminator, String instance) {
            long h = 0xcbf29ce484222325L;
            for (byte b : (discriminator + '\u0000' + instance).getBytes(StandardCharsets.UTF_8)) {
                h ^= b & 0xff;
                h *= 0x100000001b3L;
            }
            // finalizer of SplitMix64: spreads the close hashes of similar keys
            h ^= h >>> 30;
            h *= 0xbf58476d1ce4e5b9L;
            h ^= h >>> 27;
            h *= 0x94d049bb133111ebL;
            h ^= h >>> 31;
            return h;
        }
    }
}
