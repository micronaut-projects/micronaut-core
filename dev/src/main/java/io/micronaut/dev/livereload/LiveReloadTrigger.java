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
package io.micronaut.dev.livereload;

import io.micronaut.core.annotation.Experimental;

import java.nio.file.Path;

/**
 * Asks the browsers connected to the LiveReload server to reload. A bean in every development
 * context, a no-op when the server is disabled, so a module or an application can call it without
 * checking; outside development mode there is no bean.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Experimental
public interface LiveReloadTrigger {

    /**
     * @return Whether a server runs and browsers may be connected
     */
    boolean isEnabled();

    /**
     * Reloads the page in every connected browser.
     */
    void reload();

    /**
     * Reloads because the given file changed; the browser decides what to do with the path.
     *
     * @param path The changed file
     */
    void reload(Path path);

    /**
     * Swaps a stylesheet in place, without reloading the page, in every connected browser.
     *
     * @param stylesheet The changed stylesheet
     */
    void reloadCss(Path stylesheet);
}
