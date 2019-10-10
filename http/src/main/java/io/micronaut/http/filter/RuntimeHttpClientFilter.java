/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.http.filter;

/**
 * The implementation of this class can define at runtime what clients, paths, http methods this filter will be applied to.
 *
 * Unlike with {@link HttpClientFilter} where {@link io.micronaut.http.annotation.Filter} fully controls what request or client
 * the filter should be applied to that is static configuration, by implementing this interface you can
 * configure filter at runtime.
 */
public interface RuntimeHttpClientFilter extends HttpClientFilter {

    /**
     * The method returns {@link FilterProperties} which controls what request filter is applied to.
     * This method does not replace configuration defined in {@link io.micronaut.http.annotation.Filter} annotation but
     * complement it.
     *
     * @return {@link FilterProperties} which controls what request filter is applied to
     */
    FilterProperties getFilterProperties();
}
