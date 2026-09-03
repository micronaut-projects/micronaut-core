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
package io.micronaut.docs.client.resolver

import io.micronaut.context.ApplicationContext
import io.micronaut.inject.qualifiers.Qualifiers
import io.netty.resolver.AddressResolverGroup
import io.netty.resolver.DefaultAddressResolverGroup
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class AddressResolverClientConfigurationSpec:

  @Test
  def customResolverConfigurationIsAvailable(): Unit =
    val properties = AddressResolverClientConfiguration.serviceConfiguration()
    assertEquals("https://api.example.com", properties.get("micronaut.http.services.foo.urls[0]"))
    assertEquals("custom", properties.get("micronaut.http.services.foo.address-resolver-group-name"))

    val context = ApplicationContext.run()
    try
      val resolver = context.getBean(
        classOf[AddressResolverGroup[?]],
        Qualifiers.byName("custom")
      )
      assertSame(DefaultAddressResolverGroup.INSTANCE, resolver)
    finally
      context.close()
