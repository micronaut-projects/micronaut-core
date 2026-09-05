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
package example.micronaut.synthesis;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.ReflectionConfig;
import io.micronaut.core.annotation.TypeHint;

/**
 * The type whose annotation metadata the test synthesizes annotations from.
 *
 * <p>The {@link ReflectionConfig} hints declare the annotation types as dynamic proxies, which is the
 * only mechanism Micronaut offers for making {@code synthesize(..)} work in a native image.</p>
 */
@Guarded(value = "secret", max = 3)
@Role("admin")
@Role("auditor")
@Introspected
@ReflectionConfig(type = Guarded.class, accessType = TypeHint.AccessType.DYNAMIC_PROXY)
@ReflectionConfig(type = Role.class, accessType = TypeHint.AccessType.DYNAMIC_PROXY)
@ReflectionConfig(type = Roles.class, accessType = TypeHint.AccessType.DYNAMIC_PROXY)
public class Protected {
}
