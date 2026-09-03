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
package io.micronaut.docs.http.server.secondary

// tag::imports[]
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.context.env.Environment
import io.micronaut.core.util.StringUtils
import io.micronaut.discovery.ServiceInstanceList
import io.micronaut.discovery.StaticServiceInstanceList
import io.micronaut.http.server.netty.NettyEmbeddedServer
import io.micronaut.http.server.netty.NettyEmbeddedServerFactory
import io.micronaut.http.server.netty.configuration.NettyHttpServerConfiguration
import io.micronaut.http.ssl.ServerSslConfiguration
import jakarta.inject.Named
// end::imports[]

import java.util.Collections

object SecondaryNettyServer:
  final val SERVER_ID = "another" // <1>

@Requires(property = "secondary.enabled", value = StringUtils.TRUE)
// tag::class[]
@Factory
class SecondaryNettyServer:

  @Named(SecondaryNettyServer.SERVER_ID)
  @Context
  @Bean // <2>
  @Requires(beans = Array(classOf[Environment]))
  def nettyEmbeddedServer(
      serverFactory: NettyEmbeddedServerFactory // <3>
  ): NettyEmbeddedServer =
    val configuration = NettyHttpServerConfiguration() // <4>
    val sslConfiguration = ServerSslConfiguration() // <5>
    sslConfiguration.setBuildSelfSigned(true)
    sslConfiguration.setEnabled(true)
    sslConfiguration.setPort(-1) // random port

    val embeddedServer = serverFactory.build(configuration, sslConfiguration) // <6>
    embeddedServer.start() // <7>
    embeddedServer // <8>

  @Bean
  def serviceInstanceList( // <9>
      @Named(SecondaryNettyServer.SERVER_ID) nettyEmbeddedServer: NettyEmbeddedServer
  ): ServiceInstanceList =
    StaticServiceInstanceList(
      SecondaryNettyServer.SERVER_ID,
      Collections.singleton(nettyEmbeddedServer.getURI)
    )
// end::class[]
