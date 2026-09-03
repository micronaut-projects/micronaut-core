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
package io.micronaut.docs.ioc.mappers

// tag::class[]
import io.micronaut.context.annotation.Mapper
import io.micronaut.context.annotation.Mapper.Mapping
import jakarta.inject.Singleton

@Singleton
abstract class ProductMappers:
  @Mapper(
    value = Array(
      new Mapping(
        to = "price",
        from = "#{product.price * 2}",
        format = "$#.00"
      ),
      new Mapping(
        to = "distributor",
        from = "#{this.getDistributor()}"
      )
    )
  )
  def toProductDTO(product: Product): ProductDTO

  def getDistributor(): String = "Great Product Company"
// tag::end[]
