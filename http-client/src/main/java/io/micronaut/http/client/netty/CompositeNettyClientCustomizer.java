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
package io.micronaut.http.client.netty;

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.netty.AbstractCompositeCustomizer;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;

import java.util.Collections;
import java.util.List;

@Internal
final class CompositeNettyClientCustomizer
    extends AbstractCompositeCustomizer<NettyClientCustomizer, NettyClientCustomizer.ChannelRole>
    implements NettyClientCustomizer {
    static final NettyClientCustomizer EMPTY =
        new CompositeNettyClientCustomizer(Collections.emptyList());

    private CompositeNettyClientCustomizer(List<NettyClientCustomizer> members) {
        super(members);
    }

    CompositeNettyClientCustomizer() {
        super();
    }

    /**
     * Check whether the given customizer is known to do nothing, i.e. it is a composite without
     * any members. Must be checked at call time, since customizers can be registered later.
     *
     * @param customizer The customizer
     * @return {@code true} if the customizer is an empty composite
     */
    static boolean isEmpty(NettyClientCustomizer customizer) {
        return customizer instanceof CompositeNettyClientCustomizer composite && composite.isEmpty();
    }

    @Override
    protected NettyClientCustomizer specializeForChannel(NettyClientCustomizer member, Channel channel, ChannelRole role) {
        return member.specializeForChannel(channel, role);
    }

    @Override
    public NettyClientCustomizer specializeForBootstrap(Bootstrap bootstrap) {
        return specialize(ch -> ch.specializeForBootstrap(bootstrap));
    }

    @Override
    protected NettyClientCustomizer makeNewComposite(List<NettyClientCustomizer> members) {
        return new CompositeNettyClientCustomizer(members);
    }

    @Override
    public void onInitialPipelineBuilt() {
        forEach(NettyClientCustomizer::onInitialPipelineBuilt);
    }

    @Override
    public void onStreamPipelineBuilt() {
        forEach(NettyClientCustomizer::onStreamPipelineBuilt);
    }

    @Override
    public void onRequestPipelineBuilt() {
        forEach(NettyClientCustomizer::onRequestPipelineBuilt);
    }
}
