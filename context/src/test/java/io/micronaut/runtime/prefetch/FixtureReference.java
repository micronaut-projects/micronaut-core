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
package io.micronaut.runtime.prefetch;

import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanDefinitionReference;

/**
 * A bean definition reference that only a test registers, in a {@code META-INF/micronaut} entry of
 * its own. It is never enabled, so a context that reads it never loads it. Its subclasses report
 * their static initializer and their constructor to {@link PrefetchProbe}.
 */
public abstract class FixtureReference implements BeanDefinitionReference<Object> {

    protected FixtureReference() {
        PrefetchProbe.constructed(getClass().getName());
    }

    @Override
    public String getBeanDefinitionName() {
        return getClass().getName();
    }

    @Override
    public BeanDefinition<Object> load() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isPresent() {
        return true;
    }

    @Override
    public Class<Object> getBeanType() {
        return Object.class;
    }

    @Override
    public boolean isEnabled(BeanContext context, BeanResolutionContext resolutionContext) {
        return false;
    }

    public static final class First extends FixtureReference {
        static {
            PrefetchProbe.initialized(First.class.getName());
        }
    }

    public static final class Second extends FixtureReference {
        static {
            PrefetchProbe.initialized(Second.class.getName());
        }
    }

    public static final class FailsAtRuntime extends FixtureReference {
        static {
            PrefetchProbe.failRuntime(FailsAtRuntime.class.getName());
        }
    }

    public static final class FailsWithNoSuchField extends FixtureReference {
        static {
            PrefetchProbe.failField(FailsWithNoSuchField.class.getName());
        }
    }

    public static final class FailsToLink extends FixtureReference {
        static {
            PrefetchProbe.failLinkage(FailsToLink.class.getName());
        }
    }

    public static final class Blocks extends FixtureReference {
        static {
            PrefetchProbe.block(Blocks.class.getName());
        }
    }
}
