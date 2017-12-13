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
package org.particleframework.http.annotation;

import org.particleframework.context.annotation.AliasFor;
import org.particleframework.context.annotation.Executable;
import org.particleframework.http.HttpMethod;

import javax.inject.Singleton;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * <p>An annotation that can be applied to classes that implement {@link org.particleframework.http.filter.HttpFilter} to specify the patterns</p>
 *
 * <p>Used as an alternative to applying filters manually via the {code Router} API</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
@Documented
@Retention(RUNTIME)
@Target(ElementType.TYPE)
@Executable
public @interface Filter {

    /**
     * @return The patterns this filter should match
     */
    String[] value() default {};

    /**
     * Same as {@link #value()}
     * @return The patterns
     */
    @AliasFor(member = "value")
    String[] patterns() default {};

    /**
     * @return The methods to match. Defaults to all
     */
    HttpMethod[] methods() default {};
}
