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
package io.micronaut.docs.propagation.reactor

// tag::imports[]
import io.micronaut.core.async.propagation.ReactorPropagation
import io.micronaut.core.propagation.PropagatedContext
import io.micronaut.core.propagation.PropagatedContextElement
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.QueryValue
import reactor.core.publisher.Mono
// end::imports[]
import io.micronaut.context.annotation.Requires

@Requires(property = "spec.name", value = "PropagatedContextSpec")
// tag::example[]
@Controller
class HelloController:

  @Get("/hello")
  def hello(@QueryValue("name") name: String): Mono[String] =
    val propagatedContext = PropagatedContext.get().plus(MyContextElement(name)) // <1>
    Mono.just(s"Hello, $name")
      .contextWrite(ctx => ReactorPropagation.addPropagatedContext(ctx, propagatedContext)) // <2>

case class MyContextElement(value: String) extends PropagatedContextElement
// end::example[]
