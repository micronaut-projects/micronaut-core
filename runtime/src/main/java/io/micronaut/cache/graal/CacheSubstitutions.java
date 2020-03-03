/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.cache.graal;

//CHECKSTYLE:OFF

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.TargetClass;

/**
 * Substitutions for Caffeine UnsafeRefArrayAccess.
 */
@TargetClass(className = "io.micronaut.caffeine.cache.UnsafeRefArrayAccess")
final class Target_io_micronaut_caffeine_cache_UnsafeRefArrayAccess {
    @Alias
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.ArrayIndexShift, declClass = Object[].class)
    public static int REF_ELEMENT_SHIFT;
}
//CHECKSTYLE:ON
