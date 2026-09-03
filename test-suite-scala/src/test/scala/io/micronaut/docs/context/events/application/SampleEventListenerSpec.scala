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
package io.micronaut.docs.context.events.application

// tag::imports[]
import io.micronaut.context.ApplicationContext
import io.micronaut.docs.context.events.SampleEventEmitterBean
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
// end::imports[]

// tag::class[]
class SampleEventListenerSpec:

  @Test
  def testEventListenerIsNotified(): Unit =
    val context = ApplicationContext.run()
    try
      val emitter = context.getBean(classOf[SampleEventEmitterBean])
      val listener = context.getBean(classOf[SampleEventListener])
      assertEquals(0, listener.getInvocationCounter())
      emitter.publishSampleEvent()
      assertEquals(1, listener.getInvocationCounter())
    finally context.close()
// end::class[]
