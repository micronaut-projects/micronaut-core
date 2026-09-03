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

import java.util.Optional

final class CrankShaft(val rodLength: Optional[java.lang.Double])

object CrankShaft:
  def builder(): Builder = new Builder()

  final class Builder:
    private var rodLength: Optional[java.lang.Double] = Optional.empty()

    def withRodLength(rodLength: java.lang.Double): Builder =
      this.rodLength = Optional.ofNullable(rodLength)
      this

    def build(): CrankShaft = new CrankShaft(rodLength)
