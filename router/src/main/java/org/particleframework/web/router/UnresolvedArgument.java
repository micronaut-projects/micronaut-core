/*
 * Copyright 2017 original authors
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License. 
 */
package org.particleframework.web.router;

import org.particleframework.core.bind.ArgumentBinder;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Represents an unresolved argument to a {@link org.particleframework.web.router.Route}
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@FunctionalInterface
public interface UnresolvedArgument<T> extends Supplier<ArgumentBinder.BindingResult<T>> {
}
