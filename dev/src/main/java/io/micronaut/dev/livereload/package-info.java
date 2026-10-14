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
 * The LiveReload contract: browsers connected to the server reload the page, or swap a stylesheet,
 * when the application restarted or a static file or template changed. The server itself is the
 * {@code micronaut-dev-livereload} module, present on the development runtime classpath when
 * LiveReload is wanted.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Experimental
@NullMarked
package io.micronaut.dev.livereload;

import io.micronaut.core.annotation.Experimental;
import org.jspecify.annotations.NullMarked;
