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
package io.micronaut.core.io;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Resource loading strategy.
 *
 * @param type             The strategy type. Defaults to {@link ResourceLoadStrategyType#FAIL_ON_DUPLICATE}.
 * @param warnOnDuplicates Whether to warn when duplicates are found. Applies only to {@link ResourceLoadStrategyType#FIRST_MATCH}.
 * @param mergeOrder       Artifact name regex patterns used to order resources before merging. Applies only to {@link ResourceLoadStrategyType#MERGE_ALL}.
 * @since 5.0.0
 */
@NullMarked
public record ResourceLoadStrategy(
    ResourceLoadStrategyType type,
    boolean warnOnDuplicates,
    List<String> mergeOrder
) {

    public ResourceLoadStrategy {
        if (type == null) {
            type = ResourceLoadStrategyType.FAIL_ON_DUPLICATE;
        }
        if (mergeOrder == null) {
            mergeOrder = List.of();
        } else {
            mergeOrder = new ArrayList<>(mergeOrder);
        }

        if (!mergeOrder.isEmpty() && type != ResourceLoadStrategyType.MERGE_ALL) {
            throw new IllegalArgumentException("mergeOrder is only supported when resource loading strategy type is MERGE_ALL");
        }

        mergeOrder = Collections.unmodifiableList(mergeOrder);
    }

    /**
     * Returns the default resource load strategy.
     *
     * @return The default resource load strategy.
     * @since 5.0.0
     */
    public static ResourceLoadStrategy defaultStrategy() {
        return builder().build();
    }

    /**
     * Creates a new builder.
     *
     * @return A new builder.
     * @since 5.0.0
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link ResourceLoadStrategy}.
     *
     * @since 5.0.0
     */
    @NullMarked
    public static final class Builder {
        private ResourceLoadStrategyType type = ResourceLoadStrategyType.FAIL_ON_DUPLICATE;
        private boolean warnOnDuplicates = true;
        private List<String> mergeOrder = List.of();

        /**
         * Sets the strategy type.
         *
         * @param type The strategy type
         * @return This builder
         * @since 5.0.0
         */
        public Builder type(@Nullable ResourceLoadStrategyType type) {
            this.type = type == null ? ResourceLoadStrategyType.FAIL_ON_DUPLICATE : type;
            return this;
        }

        /**
         * Applies only to {@link ResourceLoadStrategyType#FIRST_MATCH}.
         *
         * @param warnOnDuplicates Whether to warn when duplicates are found
         * @return This builder
         * @since 5.0.0
         */
        public Builder warnOnDuplicates(boolean warnOnDuplicates) {
            this.warnOnDuplicates = warnOnDuplicates;
            return this;
        }

        /**
         * Applies only to {@link ResourceLoadStrategyType#MERGE_ALL}.
         *
         * @param mergeOrder Resource ordering patterns
         * @return This builder
         * @since 5.0.0
         */
        public Builder mergeOrder(@Nullable List<String> mergeOrder) {
            this.mergeOrder = mergeOrder == null ? List.of() : mergeOrder;
            return this;
        }

        /**
         * Applies only to {@link ResourceLoadStrategyType#MERGE_ALL}.
         *
         * @param mergeOrder Resource ordering patterns
         * @return This builder
         * @since 5.0.0
         */
        public Builder mergeOrder(@Nullable String... mergeOrder) {
            if (mergeOrder == null || mergeOrder.length == 0) {
                this.mergeOrder = List.of();
            } else {
                this.mergeOrder = List.of(mergeOrder);
            }
            return this;
        }

        /**
         * Builds a new {@link ResourceLoadStrategy}.
         *
         * @return A new {@link ResourceLoadStrategy}.
         * @since 5.0.0
         */
        public ResourceLoadStrategy build() {
            return new ResourceLoadStrategy(type, warnOnDuplicates, mergeOrder);
        }
    }
}
