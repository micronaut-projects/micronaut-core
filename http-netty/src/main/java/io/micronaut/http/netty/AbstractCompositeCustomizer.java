/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.http.netty;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.order.OrderUtil;
import io.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * Base class for the composite customizers for the client and server. The APIs are structured
 * quite similarly, but it doesn't make sense to merge them because writing generic code for them
 * does not make sense, so this class exists to at least merge the composition behavior (ordering
 * and such).
 *
 * @param <C> The actual customizer class. Must also be implemented by the
 * {@link AbstractCompositeCustomizer} subclass.
 * @param <R> The ChannelRole type.
 */
@Internal
public abstract class AbstractCompositeCustomizer<C, R> {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractCompositeCustomizer.class);

    private final List<C> members;

    protected AbstractCompositeCustomizer(List<C> members) {
        this.members = members;
    }

    protected AbstractCompositeCustomizer() {
        this(new CopyOnWriteArrayList<>());
    }

    /**
     * Add customizer.
     *
     * @param customizer Customizer
     */
    public synchronized void add(C customizer) {
        assert members instanceof CopyOnWriteArrayList : "only allow adding to root customizer";
        // do the insertion in one operation, so that concurrent readers don't see an inconsistent
        // (unsorted) state
        int insertionIndex = Collections.binarySearch(members, customizer, OrderUtil.COMPARATOR_ZERO);
        if (insertionIndex < 0) {
            insertionIndex = ~insertionIndex;
        }
        members.add(insertionIndex, customizer);
    }

    protected abstract C specializeForChannel(C member, Channel channel, R role);

    protected abstract C makeNewComposite(List<C> members);

    @NonNull
    public final C specializeForChannel(@NonNull Channel channel, @NonNull R role) {
        return specialize(c -> specializeForChannel(c, channel, role));
    }

    /**
     * Specialize all members with the given action.
     *
     * @param specializeAction The specialization action. Input is the old member customizer, output
     *                         is the new member customizer.
     * @return The specialized composite customizer
     */
    protected final C specialize(UnaryOperator<C> specializeAction) {
        List<C> specialized = null;
        for (int i = 0; i < this.members.size(); i++) {
            C old = this.members.get(i);
            C nev;
            try {
                nev = specializeAction.apply(old);
                Objects.requireNonNull(nev, "specializeForChannel must not return null");
            } catch (Exception e) {
                LOG.error("Failed to specialize customizer", e);
                nev = old;
            }
            if (specialized == null) {
                if (nev == old) {
                    continue;
                }
                specialized = new ArrayList<>(this.members.size());
                specialized.addAll(this.members.subList(0, i));
            }
            specialized.add(nev);
        }
        if (specialized == null) {
            //noinspection unchecked
            return (C) this;
        } else {
            return makeNewComposite(specialized);
        }
    }

    protected final void forEach(Consumer<C> consumer) {
        for (C member : members) {
            try {
                consumer.accept(member);
            } catch (Exception e) {
                LOG.error("Failed to trigger customizer event", e);
            }
        }
    }
}
