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
package io.micronaut.context.reload;

import io.micronaut.context.event.ApplicationEvent;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.inject.BeanDefinition;
import org.jspecify.annotations.NullMarked;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * The new generation of the application's classes is in place: its bean definitions are registered,
 * the bean definition and executable method processors ran over them, and its eager beans started.
 *
 * <p>Listeners rebuild what they derive from the set of beans, such as a route table or a registry
 * of consumers, and re-run startup work that depends on the shape of application classes, such as
 * schema generation for changed entities. The event is published in the context that is now
 * current: the same context after a {@link ReloadStrategy#RELOAD reload}, the new one after a
 * {@link ReloadStrategy#RESTART restart}.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Experimental
@NullMarked
public final class ReloadCompletedEvent extends ApplicationEvent {

    private final ClassChangeEvent change;
    private final Collection<BeanDefinition<?>> addedDefinitions;
    private final Collection<BeanDefinition<?>> removedDefinitions;
    private final Duration elapsed;

    /**
     * Creates the event.
     *
     * @param source The launcher or component publishing the event
     * @param change The change that was applied
     * @param addedDefinitions The definitions registered from the new generation
     * @param removedDefinitions The definitions of the retired generation that were removed
     * @param elapsed How long the reload took, from the change being detected
     */
    public ReloadCompletedEvent(Object source,
                                ClassChangeEvent change,
                                Collection<BeanDefinition<?>> addedDefinitions,
                                Collection<BeanDefinition<?>> removedDefinitions,
                                Duration elapsed) {
        super(source);
        this.change = Objects.requireNonNull(change, "change");
        this.addedDefinitions = List.copyOf(Objects.requireNonNull(addedDefinitions, "addedDefinitions"));
        this.removedDefinitions = List.copyOf(Objects.requireNonNull(removedDefinitions, "removedDefinitions"));
        this.elapsed = Objects.requireNonNull(elapsed, "elapsed");
    }

    /**
     * @return The change that was applied
     */
    public ClassChangeEvent change() {
        return change;
    }

    /**
     * @return The definitions registered from the new generation
     */
    public Collection<BeanDefinition<?>> addedDefinitions() {
        return addedDefinitions;
    }

    /**
     * @return The definitions of the retired generation that were removed
     */
    public Collection<BeanDefinition<?>> removedDefinitions() {
        return removedDefinitions;
    }

    /**
     * @return How long the reload took
     */
    public Duration elapsed() {
        return elapsed;
    }

    /**
     * @return The strategy that was applied
     */
    public ReloadStrategy strategy() {
        return change.strategy();
    }

    @Override
    public String toString() {
        return "ReloadCompletedEvent{strategy=" + strategy() + ", added=" + addedDefinitions.size() + ", removed=" + removedDefinitions.size() + ", elapsed=" + elapsed + '}';
    }
}
