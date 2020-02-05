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
package io.micronaut.http.simple.cookies;

import io.micronaut.http.cookie.Cookie;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;

/**
 * Simple {@link Cookie} implementation.
 *
 * @author Vladimir Orany
 * @since 1.0
 */
public class SimpleCookie implements Cookie {

    private final String name;
    private String value;
    private String domain;
    private String path;
    private boolean httpOnly;
    private boolean secure;
    private long maxAge;

    /**
     * Constructor.
     *
     * @param name The name
     * @param value The value
     */
    public SimpleCookie(String name, String value) {
        this.name = name;
        this.value = value;
    }

    @Override
    public @NonNull String getName() {
        return name;
    }

    @Override
    public @NonNull String getValue() {
        return value;
    }

    @Override
    public String getDomain() {
        return domain;
    }

    @Override
    public String getPath() {
        return path;
    }

    @Override
    public boolean isHttpOnly() {
        return httpOnly;
    }

    @Override
    public boolean isSecure() {
        return secure;
    }

    @Override
    public long getMaxAge() {
        return maxAge;
    }

    @Override
    public @NonNull Cookie maxAge(long maxAge) {
        this.maxAge = maxAge;
        return this;
    }

    @Override
    public @NonNull Cookie value(@NonNull String value) {
        this.value = value;
        return this;
    }

    @Override
    public @NonNull Cookie domain(String domain) {
        this.domain = domain;
        return this;
    }

    @Override
    public @NonNull Cookie path(String path) {
        this.path = path;
        return this;
    }

    @Override
    public @NonNull Cookie secure(boolean secure) {
        this.secure = secure;
        return this;
    }

    @Override
    public @NonNull Cookie httpOnly(boolean httpOnly) {
        this.httpOnly = httpOnly;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof Cookie)) {
            return false;
        }

        Cookie that = (Cookie) o;
        if (!getName().equals(that.getName())) {
            return false;
        }

        if (getPath() == null) {
            if (that.getPath() != null) {
                return false;
            }
        } else if (that.getPath() == null) {
            return false;
        } else if (!getPath().equals(that.getPath())) {
            return false;
        }

        if (getDomain() == null) {
            return that.getDomain() == null;
        } else {
            return getDomain().equalsIgnoreCase(that.getDomain());
        }

    }

    @Override
    public int compareTo(Cookie c) {
        int v = getName().compareTo(c.getName());
        if (v != 0) {
            return v;
        }

        if (getPath() == null) {
            if (c.getPath() != null) {
                return -1;
            }
        } else if (c.getPath() == null) {
            return 1;
        } else {
            v = getPath().compareTo(c.getPath());
            if (v != 0) {
                return v;
            }
        }

        if (getDomain() == null) {
            if (c.getDomain() != null) {
                return -1;
            }
        } else if (c.getDomain() == null) {
            return 1;
        } else {
            v = getDomain().compareToIgnoreCase(c.getDomain());
            return v;
        }

        return 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, domain, path);
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder()
                .append(getName())
                .append('=')
                .append(getValue());
        if (getDomain() != null) {
            buf.append(", domain=")
                    .append(getDomain());
        }
        if (getPath() != null) {
            buf.append(", path=")
                    .append(getPath());
        }
        if (getMaxAge() >= 0) {
            buf.append(", maxAge=")
                    .append(getMaxAge())
                    .append('s');
        }
        if (isSecure()) {
            buf.append(", secure");
        }
        if (isHttpOnly()) {
            buf.append(", HTTPOnly");
        }
        return buf.toString();
    }
}
