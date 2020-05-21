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
package io.micronaut.http.netty.channel;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.netty.channel.ChannelPipeline;

/**
 * An interface that allows users to configure the Netty pipeline used by Micronaut.
 *
 * @author graemerocher
 * @since 2.0.0
 */
@FunctionalInterface
public interface ChannelPipelineListener {

    /**
     * A method called every time the pipeline is initialized.
     * @param pipeline The pipeline
     * @return The pipeline
     */
    @NonNull ChannelPipeline onConnect(@NonNull ChannelPipeline pipeline);
}
