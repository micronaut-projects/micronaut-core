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
package io.micronaut.cache.jcache;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.util.Toggleable;

/**
 * Configuration for the Java cache manager.
 *
 * @author James Kleeh
 * @since 1.3.5
 */
@ConfigurationProperties("micronaut.jcache")
public class JCacheConfiguration implements Toggleable {

    /**
     * The default enable value.
     */
    @SuppressWarnings("WeakerAccess")
    public static final boolean DEFAULT_ENABLED = false;


    /**
     * The default convert value.
     */
    @SuppressWarnings("WeakerAccess")
    public static final boolean DEFAULT_CONVERT = true;

    private boolean enabled = DEFAULT_ENABLED;
    private boolean convert = DEFAULT_CONVERT;

    /**
     * @return True if the Java cache manager should be enabled
     */
    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Default value ({@value #DEFAULT_ENABLED}).
     * @param enabled Enable the distributed configuration
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * @return True if cache values should be converted to the required type. They will
     * be cast to the type otherwise
     */
    public boolean isConvert() {
        return convert;
    }

    /**
     * @param convert Sets whether retrieved cache values should be converted (true), or cast (false)
     *                to the required type. Default value ({@value #DEFAULT_CONVERT})
     */
    public void setConvert(boolean convert) {
        this.convert = convert;
    }
}
