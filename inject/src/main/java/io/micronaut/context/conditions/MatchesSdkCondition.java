/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.context.conditions;

import groovy.lang.GroovySystem;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.condition.Condition;
import io.micronaut.context.condition.ConditionContext;
import io.micronaut.context.env.CachedEnvironment;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.UsedByGeneratedCode;
import io.micronaut.core.version.SemanticVersion;
import io.micronaut.core.version.VersionUtils;
import kotlin.KotlinVersion;

import java.util.Objects;

/**
 * Matches SDK condition.
 *
 * @param sdk     The SDK
 * @param version The version
 * @author Denis Stepanov
 * @since 4.6
 */
@UsedByGeneratedCode
@Internal
public record MatchesSdkCondition(Requires.Sdk sdk, String version) implements Condition {
    @Override
    public boolean matches(ConditionContext context) {
        switch (sdk) {
            case GROOVY:
                String groovyVersion = GroovySystem.getVersion();
                boolean versionMatch = SemanticVersion.isAtLeast(groovyVersion, version);
                if (!versionMatch) {
                    context.fail("Groovy version [" + groovyVersion + "] must be at least " + version);
                }
                return versionMatch;
            case KOTLIN:
                String kotlinVersion = KotlinVersion.CURRENT.toString();
                boolean isSupported = SemanticVersion.isAtLeast(kotlinVersion, version);
                if (!isSupported) {
                    context.fail("Kotlin version [" + kotlinVersion + "] must be at least " + version);
                }
                return isSupported;
            case JAVA:
                String javaVersion = CachedEnvironment.getProperty("java.version");
                try {
                    boolean result = SemanticVersion.isAtLeast(javaVersion, version);
                    if (!result) {
                        context.fail("Java version [" + javaVersion + "] must be at least " + version);
                    }
                    return result;
                } catch (Exception e) {
                    if (javaVersion != null) {

                        // non-semantic versioning in play
                        int majorVersion = resolveJavaMajorVersion(javaVersion);
                        @SuppressWarnings("java:S2259") // false positive
                        int requiredVersion = resolveJavaMajorVersion(version);

                        if (majorVersion >= requiredVersion) {
                            return true;
                        } else {
                            context.fail("Java major version [" + majorVersion + "] must be at least " + requiredVersion);
                        }
                    } else {
                        int requiredVersion = resolveJavaMajorVersion(version);
                        context.fail("Java major version must be at least " + requiredVersion);
                    }
                }
                return !context.isFailing();
            case MICRONAUT:
                boolean versionCheck = VersionUtils.isAtLeastMicronautVersion(version);
                if (!versionCheck) {
                    context.fail("Micronaut version [" + VersionUtils.MICRONAUT_VERSION + "] must be at least " + version);
                }
                return versionCheck;
            default:
                throw new IllegalStateException("Unexpected value: " + sdk);
        }
    }

    private int resolveJavaMajorVersion(String javaVersion) {
        int majorVersion = 0;
        if (javaVersion.indexOf('.') > -1) {
            String[] tokens = javaVersion.split("\\.");
            String first = tokens[0];
            if (first.length() == 1) {
                majorVersion = first.charAt(0);
                if (Character.isDigit(majorVersion)) {
                    if (majorVersion == '1' && tokens.length > 1) {
                        majorVersion = tokens[1].charAt(0);
                    }
                }
            } else {
                try {
                    majorVersion = Integer.parseInt(first);
                } catch (NumberFormatException e) {
                    // ignore
                }
            }
        } else {
            if (javaVersion.length() == 1) {
                char ch = javaVersion.charAt(0);
                if (Character.isDigit(ch)) {
                    majorVersion = ch;
                }
            } else {
                try {
                    majorVersion = Integer.parseInt(javaVersion);
                } catch (NumberFormatException e) {
                    // ignore
                }
            }
        }
        return majorVersion;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        MatchesSdkCondition that = (MatchesSdkCondition) o;
        return Objects.equals(version, that.version) && sdk == that.sdk;
    }

    @Override
    public int hashCode() {
        return Objects.hash(sdk, version);
    }

    @Override
    public String toString() {
        return "MatchesSdkCondition{" +
            "sdk=" + sdk +
            ", version='" + version + '\'' +
            '}';
    }
}

