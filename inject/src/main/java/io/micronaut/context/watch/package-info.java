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
/**
 * Watches over the bean definitions, beans, executable methods, configuration, resources and classes of a
 * context: a watcher describes what it derives state from with a fluent request, such as
 * {@link DefinitionWatchRequest}, and receives one batch whenever it changes, the state when the watch was
 * registered being the first batch, or one change at a time through the per-change handlers of the request.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Experimental
@NullMarked
package io.micronaut.context.watch;

import io.micronaut.core.annotation.Experimental;
import org.jspecify.annotations.NullMarked;
