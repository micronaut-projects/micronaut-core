/*
 * Copyright 2017 original authors
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
package org.particleframework.configuration.lettuce.session;

import org.particleframework.core.convert.ArgumentConversionContext;
import org.particleframework.core.convert.value.MutableConvertibleValues;
import org.particleframework.session.Session;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;

/**
 * @author Graeme Rocher
 * @since 1.0
 */
public class RedisSession implements Session {
    @Override
    public MutableConvertibleValues<Object> put(CharSequence key, Object value) {
        return null;
    }

    @Override
    public MutableConvertibleValues<Object> remove(CharSequence key) {
        return null;
    }

    @Override
    public MutableConvertibleValues<Object> clear() {
        return null;
    }

    @Override
    public Instant getCreationTime() {
        return null;
    }

    @Override
    public String getId() {
        return null;
    }

    @Override
    public Instant getLastAccessedTime() {
        return null;
    }

    @Override
    public void setMaxInactiveInterval(Duration duration) {

    }

    @Override
    public Duration getMaxInactiveInterval() {
        return null;
    }

    @Override
    public boolean isExpired() {
        return false;
    }

    @Override
    public Set<String> getNames() {
        return null;
    }

    @Override
    public Collection<Object> values() {
        return null;
    }

    @Override
    public <T> Optional<T> get(CharSequence name, ArgumentConversionContext<T> conversionContext) {
        return null;
    }
}
