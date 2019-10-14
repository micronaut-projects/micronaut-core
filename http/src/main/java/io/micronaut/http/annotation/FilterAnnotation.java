/*
 *  Copyright 2017-2019 original authors
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package io.micronaut.http.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 *  Stereotype annotation that helps to link together instance of the filter and http client that filter should be applied to.
 *
 *  In order to use you will need to create new annotation and apply {@link FilterAnnotation} on it. After that apply newly
 *  created annotation on both instance of the http filter and instance of a http client.
 *
 *  <pre>{@code
 *  Example:
 *
 *  @FilterAnnotation
 *  public @interface Metered {
 *      ...
 *  }
 *
 *  @Metered
 *  public class MeteredHttpFilter implements HttpClientFilter {
 *      ....
 *  }
 *
 *  @Metered
 *  private HttpClient httpClient;
 *  }</pre>
 *
 *  In the example above only clients annotated with {@code @Metered} annotations are going to be filtered by MeteredHttpFilter
 */
@Documented
@Target({ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface FilterAnnotation {
}
