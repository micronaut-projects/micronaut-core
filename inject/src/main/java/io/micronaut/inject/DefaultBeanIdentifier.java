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
package io.micronaut.inject;

import io.micronaut.core.annotation.Internal;

import java.util.Objects;

/**
 * Default impl for the {@link BeanIdentifier} interface.
 *
 * @author graemerocher
 * @since 1.0
 */
@Internal
class DefaultBeanIdentifier implements BeanIdentifier {

    private final String id;

    /**
     * The constructor.
     *
     * @param id The id
     */
    DefaultBeanIdentifier(String id) {
        this.id = id;
    }

    @Override
    public String toString() {
        return id;
    }

    @Override
    public String getName() {
        return id;
    }

    @Override
    public int length() {
        return id.length();
    }

    @Override
    public char charAt(int index) {
        return id.charAt(index);
    }

    @Override
    public CharSequence subSequence(int start, int end) {
        return id.subSequence(start, end);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DefaultBeanIdentifier that = (DefaultBeanIdentifier) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {

        return Objects.hash(id);
    }
}
