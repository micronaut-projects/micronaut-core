/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.docs.netty;

// tag::imports[]
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.http.netty.channel.ChannelPipelineCustomizer;
import org.zalando.logbook.Logbook;
import org.zalando.logbook.netty.LogbookClientHandler;
import org.zalando.logbook.netty.LogbookServerHandler;

import javax.inject.Singleton;
// end::imports[]

// tag::class[]
@Requires(beans = Logbook.class)
@Singleton
public class LogbookPipelineCustomizer
        implements BeanCreatedEventListener<ChannelPipelineCustomizer> { // <1>

    private final Logbook logbook;

    public LogbookPipelineCustomizer(Logbook logbook) {
        this.logbook = logbook;
    }

    @Override
    public ChannelPipelineCustomizer onCreated(BeanCreatedEvent<ChannelPipelineCustomizer> event) {
        ChannelPipelineCustomizer customizer = event.getBean();

        if (customizer.isServerChannel()) { // <2>
            customizer.doOnConnect(pipeline -> {
                pipeline.addAfter(
                    ChannelPipelineCustomizer.HANDLER_HTTP_SERVER_CODEC,
                    "logbook",
                    new LogbookServerHandler(logbook)
                );
                return pipeline;
            });
        } else { // <3>
            customizer.doOnConnect(pipeline -> {
                pipeline.addAfter(
                        ChannelPipelineCustomizer.HANDLER_HTTP_CLIENT_CODEC,
                        "logbook",
                        new LogbookClientHandler(logbook)
                );
                return pipeline;
            });
        }
        return customizer;
    }
}
// end::class[]
