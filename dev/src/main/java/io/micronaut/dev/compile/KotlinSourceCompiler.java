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
package io.micronaut.dev.compile;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.reflect.ClassUtils;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The embedded Kotlin compiler: kotlinc through the Kotlin Build Tools API, preceded by KSP when the
 * project has symbol processors, run incrementally by {@link StagedSourceCompiler}. Available when
 * the Build Tools API and its implementation are on the launch classpath at Kotlin 2.4 or later; the
 * module does not depend on them, and the build plugin puts them there at the project's Kotlin and KSP
 * versions.
 *
 * <p>An older Kotlin is refused here, with a warning, rather than left to fail: the API below 2.4 lacks
 * the types of the compilation this compiler builds, which do not link, and an implementation below 2.4
 * under a newer API reports no compiler message. The launcher treats the compiler as absent, so the
 * Kotlin sources are the build tool's.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Experimental
public final class KotlinSourceCompiler extends StagedSourceCompiler {

    private static final Logger LOG = LoggerFactory.getLogger(KotlinSourceCompiler.class);
    private static final String TOOLCHAINS = "org.jetbrains.kotlin.buildtools.api.KotlinToolchains";
    // present in the versions of the API before KotlinToolchains too, so that they can be named
    private static final String COMPILATION_SERVICE = "org.jetbrains.kotlin.buildtools.api.CompilationService";
    private static final int MINIMUM_MAJOR = 2;
    private static final int MINIMUM_MINOR = 4;
    private static final String MINIMUM = MINIMUM_MAJOR + "." + MINIMUM_MINOR;
    private static final Pattern VERSION = Pattern.compile("^(\\d+)\\.(\\d+)");
    // the manifest of a Kotlin jar says 2.4.10-release-377
    private static final Pattern BUILD_SUFFIX = Pattern.compile("-release-\\d+$");
    // isAvailable is asked again with each compilation, and of every instance
    private static final AtomicBoolean WARNED = new AtomicBoolean();

    @Override
    public Set<SourceKind> kinds() {
        return Set.of(SourceKind.KOTLIN);
    }

    @Override
    public boolean isAvailable() {
        String unsupported = unsupportedVersion();
        if (unsupported != null) {
            if (WARNED.compareAndSet(false, true)) {
                LOG.warn(unsupportedMessage(unsupported));
            }
            return false;
        }
        return ClassUtils.isPresent(TOOLCHAINS, KotlinSourceCompiler.class.getClassLoader()) && KotlinCompilation.isAvailable();
    }

    @Override
    @Nullable
    protected Produced runCompilation(CompilationRequest request, Set<Path> toCompile, Set<String> hiddenClasses, Path staging, Path generatedStaging, List<CompileDiagnostic> diagnostics) throws IOException {
        if (!isAvailable()) {
            String unsupported = unsupportedVersion();
            throw new IllegalStateException(unsupported != null ? unsupportedMessage(unsupported)
                : "The Kotlin Build Tools API is not on the classpath: development mode compiles Kotlin sources with kotlin-build-tools-impl at the project's Kotlin version");
        }
        // the Kotlin types are referenced by that class alone, so this one loads without Kotlin present
        return KotlinCompilation.run(request, toCompile, hiddenClasses, staging, generatedStaging, diagnostics);
    }

    @Override
    protected StagedSourceCompiler.@Nullable Produced runWithoutProcessors(CompilationRequest request, Set<Path> toCompile, Set<String> hiddenClasses, Path staging, List<CompileDiagnostic> diagnostics) throws IOException {
        return isAvailable() ? KotlinCompilation.runWithoutProcessors(request, toCompile, hiddenClasses, staging, diagnostics) : null;
    }

    @Override
    protected @Nullable String declarationText(String source) {
        // KSP is what reads declarations: without it there is nothing to keep, and kotlinc compiles alone anyway
        return isAvailable() && KotlinCompilation.kspPresent() ? KotlinDeclarationText.of(source) : null;
    }

    @Override
    protected boolean compilesEmptyBatches() {
        // a deletion reaches the symbol processors, which regenerate what depended on the deleted source
        return isAvailable() && KotlinCompilation.kspPresent();
    }

    @Override
    protected Set<String> keepSharedOutputs(CompilationRequest request, SiblingOutput siblings, Set<String> hiddenClasses, Path staging, Path siblingStaging, Set<String> siblingClasses) throws IOException {
        // the module metadata lists the top-level declarations: each output lists those it holds
        KotlinModuleMapping.splitInto(KotlinCompilation.moduleName(request.options()), staging, siblingStaging, siblingClasses::contains);
        if (!request.isFull()) {
            KotlinModuleMapping.mergeInto(KotlinCompilation.moduleName(request.options()), request.classOutput(), staging, hiddenClasses::contains);
            KotlinModuleMapping.mergeInto(KotlinCompilation.moduleName(request.options()), siblings.classes(), siblingStaging, hiddenClasses::contains);
        }
        return Set.of(KotlinModuleMapping.relativeFile(KotlinCompilation.moduleName(request.options())).toString());
    }

    /**
     * Where KSP keeps its outputs and caches between runs: beside the class output.
     *
     * @param request The request
     * @return The directory, which may not exist
     */
    static Path kspWork(CompilationRequest request) {
        return request.classOutput().resolveSibling(request.classOutput().getFileName() + "-ksp");
    }

    /**
     * Whether the embedded compilation runs on a Kotlin version. A version that cannot be read is
     * supported as far as this check goes.
     *
     * @param version The version, as a compiler or a jar's manifest gives it
     * @return True if it is 2.4 or later
     */
    static boolean isSupported(String version) {
        Matcher matcher = VERSION.matcher(version);
        if (!matcher.find()) {
            return true;
        }
        int major = Integer.parseInt(matcher.group(1));
        return major > MINIMUM_MAJOR || major == MINIMUM_MAJOR && Integer.parseInt(matcher.group(2)) >= MINIMUM_MINOR;
    }

    /**
     * What a developer is told about a Kotlin too old for the embedded compilation.
     *
     * @param version The version found
     * @return The message
     */
    static String unsupportedMessage(String version) {
        return "Kotlin " + version + " is older than " + MINIMUM + ", which the embedded compiler of development mode needs: "
            + "changes to Kotlin sources reach the application only through the class output the build tool writes. Upgrade to Kotlin " + MINIMUM + " or later to have them compiled embedded";
    }

    /**
     * @return The version of the Kotlin on the classpath when it is too old, the API's or the implementation's, null otherwise
     */
    @Nullable
    private static String unsupportedVersion() {
        ClassLoader loader = KotlinSourceCompiler.class.getClassLoader();
        boolean toolchains = ClassUtils.isPresent(TOOLCHAINS, loader);
        String api = apiVersion(toolchains ? TOOLCHAINS : COMPILATION_SERVICE, loader);
        if (api != null && !isSupported(api)) {
            return api;
        }
        return tooOld(api, toolchains ? KotlinCompilation.compilerVersion() : null);
    }

    /**
     * @param api The version of the Build Tools API, if known
     * @param compiler The version of its implementation, if known
     * @return The version that is too old, the API's first, null if neither is
     */
    @Nullable
    static String tooOld(@Nullable String api, @Nullable String compiler) {
        if (api != null && !isSupported(api)) {
            return api;
        }
        return compiler != null && !isSupported(compiler) ? compiler : null;
    }

    @Nullable
    private static String apiVersion(String className, ClassLoader loader) {
        return ClassUtils.forName(className, loader)
            .map(type -> type.getPackage().getImplementationVersion())
            .map(version -> BUILD_SUFFIX.matcher(version).replaceFirst(""))
            .orElse(null);
    }
}
