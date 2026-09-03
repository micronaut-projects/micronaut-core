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

// tag::imports[]
import io.micronaut.context.annotation.ConfigurationBuilder
import io.micronaut.context.annotation.ConfigurationProperties
import scala.annotation.meta.field
// end::imports[]

// tag::class[]
@ConfigurationProperties("my.engine") // <1>
class EngineConfig:

  @(ConfigurationBuilder @field)(prefixes = Array("with")) // <2>
  private[builder] var builder: EngineImpl.Builder = EngineImpl.builder()

  @(ConfigurationBuilder @field)(prefixes = Array("with"), configurationPrefix = "crank-shaft") // <3>
  private[builder] var crankShaft: CrankShaft.Builder = CrankShaft.builder()

  @(ConfigurationBuilder @field)(prefixes = Array("with"), configurationPrefix = "spark-plug") // <4>
  private[builder] var sparkPlugBuilder: SparkPlug.Builder = SparkPlug.builder()

  def getSparkPlug(): SparkPlug.Builder = sparkPlugBuilder

  def setSparkPlug(sparkPlug: SparkPlug.Builder): Unit =
    sparkPlugBuilder = sparkPlug
// end::class[]
