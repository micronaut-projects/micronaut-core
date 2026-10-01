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
package io.micronaut.dev.test;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.order.Ordered;
import org.jspecify.annotations.NullMarked;

/**
 * A report of the runs, registered as a service: an HTML renderer, an IDE bridge, a terminal UI. It receives
 * the same events as the JUnit XML reports, in {@link #getOrder() order}.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Experimental
@NullMarked
public interface TestReportListener extends TestEventListener, Ordered {
}
