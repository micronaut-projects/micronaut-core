package io.micronaut.aop.util;

/**
 * This class only exists as a mean to make the package "io.micronaut.aop.util" exist to JPMS since
 * java is compiled before kotlin it seems and java "can't find" the package since the kotlin
 * classes are not yet compiled in it.
 * <p>This is a workaround and can be removed once https://youtrack.jetbrains.com/issue/KT-55389/Gradle-plugin-should-expose-an-extension-method-to-automatically-enable-JPMS-for-Kotlin-compiled-output is closed/resolved</p>
 */
final class Placeholder {
}
