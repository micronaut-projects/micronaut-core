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
package io.micronaut.docs.config.builder

// tag::class[]
final class EngineImpl private (
    val manufacturer: String,
    override val cylinders: Int,
    val crankShaft: CrankShaft,
    val sparkPlug: SparkPlug
) extends Engine:

  override def start(): String =
    s"$manufacturer Engine Starting V$cylinders [rodLength=${crankShaft.rodLength.orElse(6.0d)}, sparkPlug=$sparkPlug]"

object EngineImpl:
  def builder(): Builder = new Builder()

  final class Builder:
    private var manufacturer: String = "Ford"
    private var cylinders: Int = 0

    def withManufacturer(manufacturer: String): Builder =
      this.manufacturer = manufacturer
      this

    def withCylinders(cylinders: Int): Builder =
      this.cylinders = cylinders
      this

    def build(crankShaft: CrankShaft.Builder, sparkPlug: SparkPlug.Builder): EngineImpl =
      new EngineImpl(manufacturer, cylinders, crankShaft.build(), sparkPlug.build())
// end::class[]
