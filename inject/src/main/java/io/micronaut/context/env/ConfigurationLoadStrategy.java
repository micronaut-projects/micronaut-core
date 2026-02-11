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
package io.micronaut.context.env;

import io.micronaut.context.exceptions.ConfigurationException;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Configuration resource loading strategy.
 *
 * @param type             The strategy type. Defaults to {@link ConfigurationLoadStrategyType#FAIL_ON_DUPLICATE}.
 * @param warnOnDuplicates Whether to warn when duplicates are found. Applies only to {@link ConfigurationLoadStrategyType#FIRST_MATCH}.
 * @param mergeOrder       Artifact name regex patterns used to order resources before merging. Applies only to {@link ConfigurationLoadStrategyType#MERGE_ALL}.
 * @since 5.0.0
 */
@NullMarked
public record ConfigurationLoadStrategy(
    ConfigurationLoadStrategyType type,
    boolean warnOnDuplicates,
    List<String> mergeOrder
) {
    /**
     * Default strategy.
     */
    public static ConfigurationLoadStrategy defaultStrategy() {
        return builder().build();
    }

    /**
     * @return A new {@link Builder}.
     */
    public static Builder builder() {
        return new Builder();
    }

    public ConfigurationLoadStrategy {
        if (type == null) {
            type = ConfigurationLoadStrategyType.FAIL_ON_DUPLICATE;
        }
        if (mergeOrder == null) {
            mergeOrder = List.of();
        } else {
            // Always create a defensive copy to prevent external mutation
            mergeOrder = new ArrayList<>(mergeOrder);
        }

        if (!mergeOrder.isEmpty() && type != ConfigurationLoadStrategyType.MERGE_ALL) {
            throw new ConfigurationException("mergeOrder is only supported when configuration loading strategy type is MERGE_ALL");
        }

        mergeOrder = Collections.unmodifiableList(mergeOrder);
    }

    /**
     * Mutable builder for {@link ConfigurationLoadStrategy}.
     */
    @NullMarked
    public static final class Builder {
        private ConfigurationLoadStrategyType type = ConfigurationLoadStrategyType.FAIL_ON_DUPLICATE;
        private boolean warnOnDuplicates = true;
        private List<String> mergeOrder = List.of();

        public Builder type(@Nullable ConfigurationLoadStrategyType type) {
            this.type = type == null ? ConfigurationLoadStrategyType.FAIL_ON_DUPLICATE : type;
            return this;
        }

        public Builder warnOnDuplicates(boolean warnOnDuplicates) {
            this.warnOnDuplicates = warnOnDuplicates;
            return this;
        }

        public Builder mergeOrder(@Nullable List<String> mergeOrder) {
            this.mergeOrder = mergeOrder == null ? List.of() : mergeOrder;
            return this;
        }

        public Builder mergeOrder(@Nullable String... mergeOrder) {
            if (mergeOrder == null || mergeOrder.length == 0) {
                this.mergeOrder = List.of();
            } else {
                this.mergeOrder = List.of(mergeOrder);
            }
            return this;
        }

        public ConfigurationLoadStrategy build() {
            return new ConfigurationLoadStrategy(type, warnOnDuplicates, mergeOrder);
        }
    }
}
