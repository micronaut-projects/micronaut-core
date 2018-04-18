/*
 * Copyright 2017-2018 original authors
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
package io.micronaut.security.config;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.util.Toggleable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Stores configuration for JWT
 *
 * @author Sergio del Amo
 * @since 1.0
 */
@ConfigurationProperties(SecurityConfiguration.PREFIX)
public class SecurityConfiguration implements Toggleable {

    protected InterceptUrlMapConverter interceptUrlMapConverter;

    public static final String PREFIX = "micronaut.security";

    private boolean enabled = false;

    private SecurityConfigType securityConfigType = SecurityConfigType.INTERCEPT_URL_MAP;

    public SecurityConfigType getSecurityConfigType() {
        return this.securityConfigType;
    }

    public void setSecurityConfigType(SecurityConfigType securityConfigType) {
        this.securityConfigType = securityConfigType;
    }

    public SecurityConfiguration(InterceptUrlMapConverter interceptUrlMapConverter) {
        this.interceptUrlMapConverter = interceptUrlMapConverter;
    }

    @Override
    public boolean isEnabled() {
        return this.enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    private List interceptUrlMap;

    public List<InterceptUrlMapPattern> getInterceptUrlMap() {
        return interceptUrlMap;
    }

    public void setInterceptUrlMap(List interceptUrlMap) {
        if ( interceptUrlMap != null ) {
            this.interceptUrlMap = new ArrayList();
            for ( Object obj : interceptUrlMap ) {
                if ( obj instanceof Map) {
                    Map m = (Map) obj;
                    Optional<InterceptUrlMapPattern> i = interceptUrlMapConverter.convert(m,
                            InterceptUrlMapPattern.class, null);
                    if ( i.isPresent() ) {
                        this.interceptUrlMap.add(i.get());
                    } else {
                        throw new IllegalArgumentException(invalidInterceptUrlMapPatternMessage(m));
                    }
                }
            }
        }
    }


    private static String invalidInterceptUrlMapPatternMessage(Map m) {
        StringBuilder sb = new StringBuilder();
        sb.append("Invalid intercept Url Map configuration");
        sb.append(m.toString());
        return sb.toString();
    }
}
