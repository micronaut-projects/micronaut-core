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
package io.micronaut.http.server.netty;

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

@Internal
final class CompositeNettyServerCustomizer implements NettyServerCustomizer {
    private static final Logger LOG = LoggerFactory.getLogger(CompositeNettyServerCustomizer.class);

    private final List<NettyServerCustomizer> members;

    private CompositeNettyServerCustomizer(List<NettyServerCustomizer> members) {
        this.members = members;
    }

    public CompositeNettyServerCustomizer() {
        this(new CopyOnWriteArrayList<>());
    }

    public synchronized void add(NettyServerCustomizer customizer) {
        assert members instanceof CopyOnWriteArrayList : "only allow adding to root customizer";
        // do the insertion in one operation, so that concurrent readers don't see an inconsistent
        // (unsorted) state
        int insertionIndex = Collections.binarySearch(members, customizer, OrderUtil.COMPARATOR);
        if (insertionIndex < 0) {
            insertionIndex = ~insertionIndex;
        }
        members.add(insertionIndex, customizer);
    }

    @NonNull
    @Override
    public NettyServerCustomizer specializeForChannel(@NonNull Channel channel, @NonNull ChannelRole role) {
        List<NettyServerCustomizer> specialized = null;
        for (int i = 0; i < this.members.size(); i++) {
            NettyServerCustomizer old = this.members.get(i);
            NettyServerCustomizer nev;
            try {
                nev = old.specializeForChannel(channel, role);
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
            return this;
        } else {
            return new CompositeNettyServerCustomizer(specialized);
        }
    }

    private void forEach(Consumer<NettyServerCustomizer> consumer) {
        for (NettyServerCustomizer member : members) {
            try {
                consumer.accept(member);
            } catch (Exception e) {
                LOG.error("Failed to trigger customizer event", e);
            }
        }
    }

    @Override
    public void onInitialPipelineBuilt() {
        forEach(NettyServerCustomizer::onInitialPipelineBuilt);
    }

    @Override
    public void onStreamPipelineBuilt() {
        forEach(NettyServerCustomizer::onStreamPipelineBuilt);
    }
}
