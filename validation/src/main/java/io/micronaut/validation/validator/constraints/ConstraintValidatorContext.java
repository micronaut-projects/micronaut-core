/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.validation.validator.constraints;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

import javax.validation.ClockProvider;

/**
 * Subset of the {@link javax.validation.ConstraintValidatorContext} interface without the unnecessary parts.
 *
 * @author graemerocher
 * @since 1.2
 */
public interface ConstraintValidatorContext {

    /**
     * Returns the provider for obtaining the current time in the form of a {@link java.time.Clock},
     * e.g. when validating the {@code Future} and {@code Past} constraints.
     *
     * @return the provider for obtaining the current time, never {@code null}. If no
     * specific provider has been configured during bootstrap, a default implementation using
     * the current system time and the current default time zone as returned by
     * {@link java.time.Clock#systemDefaultZone()} will be returned.
     *
     * @since 2.0
     */
    @NonNull ClockProvider getClockProvider();

    /**
     * In case of using this constraint validator with {@code javax.validation.ConstraintValidator} returns null, because JRS-303 doesn't
     * support passing a root bean in their validation context.
     *
     * @return The root bean under validation.
     */
    @Nullable Object getRootBean();
    
}
