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
package io.micronaut.docs.netty

// tag::imports[]

import io.micronaut.context.annotation.Requires
import io.micronaut.context.event.BeanCreatedEvent
import io.micronaut.context.event.BeanCreatedEventListener
import io.micronaut.http.netty.channel.ChannelPipelineCustomizer
import io.micronaut.http.server.netty.NettyServerCustomizer
import io.micronaut.http.server.netty.NettyServerCustomizer.ChannelRole
import io.netty.channel.Channel
import jakarta.inject.Singleton
import org.zalando.logbook.Logbook
import org.zalando.logbook.netty.LogbookServerHandler
// end::imports[]

// tag::class[]
@Requires(beans = Array(classOf[Logbook]))
@Singleton
class LogbookNettyServerCustomizer(logbook: Logbook)
    extends BeanCreatedEventListener[NettyServerCustomizer.Registry]: // <1>

  override def onCreated(
      event: BeanCreatedEvent[NettyServerCustomizer.Registry]
  ): NettyServerCustomizer.Registry =
    val registry = event.getBean
    registry.register(Customizer(null)) // <2>
    registry

  private class Customizer(channel: Channel | Null) extends NettyServerCustomizer: // <3>

    override def specializeForChannel(channel: Channel, role: ChannelRole): NettyServerCustomizer =
      Customizer(channel) // <4>

    override def onStreamPipelineBuilt(): Unit =
      channel.asInstanceOf[Channel].pipeline().addBefore( // <5>
        ChannelPipelineCustomizer.HANDLER_MICRONAUT_INBOUND,
        "logbook",
        LogbookServerHandler(logbook)
      )
// end::class[]
