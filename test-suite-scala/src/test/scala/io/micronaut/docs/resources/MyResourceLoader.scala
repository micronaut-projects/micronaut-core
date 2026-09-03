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
package io.micronaut.docs.resources

import io.micronaut.context.annotation.Requires
import io.micronaut.core.io.IOUtils
import io.micronaut.core.io.ResourceResolver
import jakarta.inject.Singleton

import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Optional

@Requires(property = "spec.name", value = "ResourceLoaderTest")
// tag::class[]
@Singleton
class MyResourceLoader(private val resourceResolver: ResourceResolver): // <1>

  @throws[Exception]
  def getClasspathResourceAsText(path: String): Optional[String] =
    val url = resourceResolver.getResource("classpath:" + path) // <2>
    if url.isPresent then
      Optional.of(IOUtils.readText(new BufferedReader(new InputStreamReader(url.get().openStream())))) // <3>
    else
      Optional.empty()
// end::class[]
