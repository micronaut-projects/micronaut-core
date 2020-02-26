/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.discovery.consul.client.v1;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.convert.ConversionService;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * A TTL check.
 *
 * @author graemerocher
 * @since 1.0
 */
public class TTLCheck extends NewCheck {
    private Duration ttl;

    /**
     * @return The optional TTL
     */
    @JsonProperty("TTL")
    public Optional<String> getTtl() {
        if (ttl != null) {
            return Optional.of(ttl.getSeconds() + "s");
        }
        return Optional.empty();
    }

    /**
     * @return The interval as a {@link Duration}
     */
    public Optional<Duration> ttl() {
        return Optional.ofNullable(this.ttl);
    }

    /**
     * @param ttl The TTL
     */
    @JsonProperty("TTL")
    void setTtl(String ttl) {
        this.ttl = ConversionService.SHARED.convert(ttl, Duration.class).orElseThrow(() -> new IllegalArgumentException("Invalid TTL Returned"));
    }

    /**
     * @param interval The interval as a {@link Duration}
     * @return The {@link NewCheck} instance
     */
    public NewCheck ttl(Duration interval) {
        if (interval != null) {
            this.ttl = interval;
        }
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        TTLCheck ttlCheck = (TTLCheck) o;
        return Objects.equals(ttl, ttlCheck.ttl);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), ttl);
    }
}
