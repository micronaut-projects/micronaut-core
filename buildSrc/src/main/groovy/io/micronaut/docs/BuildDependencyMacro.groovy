package io.micronaut.docs

import org.asciidoctor.ast.AbstractBlock
import org.asciidoctor.extension.InlineMacroProcessor

/**
 * Inline macro which can be invoked in asciidoc with:
 *
 * dependency:micronaut-spring[version="1.0.1", groupId="io.micronaut"]
 *
 * For
 *
 * Gradle
 * compile 'io.micronaut:micronaut-spring:1.0.1'
 *
 * Maven
 * <dependency>
 *     <groupId>io.micronaut</groupId>
 *     <artifactId>micronaut-spring</artifactId>
 *     <version>1.0.1</version>
 * </dependency>
 *
 * invoke it with:
 *
 * dependency:micronaut-spring[version="1.0.1", groupId="io.micronaut", verbose="true"]
 *
 * for:
 *
 * Gradle
 * compile group: 'io.micronaut', name: 'micronaut-spring', version: '1.0.1'
 *
 * Maven
 * <dependency>
 *     <groupId>io.micronaut</groupId>
 *     <artifactId>micronaut-spring</artifactId>
 *     <version>1.0.1</version>
 * </dependency>
 *
 * or simply:
 *
 * Gradle
 * compile 'io.micronaut:micronaut-spring'
 *
 * Maven
 * <dependency>
 * <groupId>io.micronaut</groupId>
 * <artifactId>micronaut-spring</artifactId>
 * </dependency>
 *
 * By default compile scope is used
 *
 * You can use:
 *
 * dependency:micronaut-spring[scope="testCompile"]
 *
 * or specify a different scope for gradle or maven
 *
 * dependency:micronaut-spring[gradleScope="implementation"]
 *
 */
class BuildDependencyMacro extends InlineMacroProcessor implements ValueAtAttributes {
    static final String MICRONAUT_GROUPID = "io.micronaut."
    static final String DEPENDENCY_PREFIX = 'micronaut-'
    static final String GROUPID = 'io.micronaut'
    static final String MULTILANGUAGECSSCLASS = 'multi-language-sample'
    static final String BUILD_GRADLE = 'gradle'
    static final String BUILD_MAVEN = 'maven'
    public static final String SCOPE_COMPILE = 'compile'

    BuildDependencyMacro(String macroName, Map<String, Object> config) {
        super(macroName, config)
    }

    @Override
    protected Object process(AbstractBlock parent, String target, Map<String, Object> attributes) {

        String groupId = valueAtAttributes('groupId', attributes) ?: GROUPID
        String artifactId = target.startsWith(DEPENDENCY_PREFIX) ? target : groupId.startsWith(MICRONAUT_GROUPID) ? "${DEPENDENCY_PREFIX}${target}" : target
        String version = valueAtAttributes('version', attributes)
        boolean verbose = valueAtAttributes('verbose', attributes) as boolean

        String gradleScope = valueAtAttributes('gradleScope', attributes) ?: valueAtAttributes('scope', attributes) ?: SCOPE_COMPILE
        String mavenScope = valueAtAttributes('mavenScope', attributes) ?: toMavenScope(attributes) ?: SCOPE_COMPILE
        String content = gradleDepependency(BUILD_GRADLE, groupId, artifactId, version, gradleScope, MULTILANGUAGECSSCLASS, verbose)
        content += mavenDepependency(BUILD_MAVEN, groupId, artifactId, version, mavenScope, MULTILANGUAGECSSCLASS)
        createBlock(parent, "pass", [content], attributes, config).convert()
    }

    private String toMavenScope(Map<String, Object> attributes) {
        String s = valueAtAttributes('scope', attributes)
        switch (s) {
            case 'implementation':
                return 'compile'
            case 'testCompile':
            case 'testImplementation':
                return 'test'
            case 'compileOnly': return 'provided'
            case 'runtimeOnly': return 'runtime'
            default: return s
        }
    }



    String gradleDepependency(String build,
                             String groupId,
                             String artifactId,
                             String version,
                              String scope,
                             String multilanguageCssClass,
                             boolean  verbose) {
String html = """\
        <div class=\"listingblock ${multilanguageCssClass}\">
<div class=\"content\">
<pre class=\"highlightjs highlight\"><code class=\"language-groovy hljs" data-lang="${build}">"""
        if (verbose) {
            html += "${scope} <span class=\"hljs-string\">group:</span> <span class=\"hljs-string\">'${groupId}'</span>, <span class=\"hljs-string\">name:</span> <span class=\"hljs-string\">'${artifactId}'</span>"
            if (version) {
                html +=", <span class=\"hljs-string\">version:</span> <span class=\"hljs-string\">'${version}'</span>"
            }
        } else {
            html += "${scope} <span class=\"hljs-string\">'${groupId}:${artifactId}"
            if (version) {
                html += ":${version}"
            }
            html += "'</span>"
        }
        html += """</code></pre>
</div>
</div>
"""
        html
    }

    String mavenDepependency(String build,
                              String groupId,
                              String artifactId,
                              String version,
                              String scope,
                              String multilanguageCssClass
                             ) {
        String html = """\
<div class=\"listingblock ${multilanguageCssClass}\">
<div class=\"content\">
<pre class=\"highlightjs highlight\"><code class=\"language-xml hljs\" data-lang=\"${build}\">&lt;dependency&gt;
    &lt;groupId&gt;${groupId}&lt;/groupId&gt;
    &lt;artifactId&gt;${artifactId}&lt;/artifactId&gt;"""
        if (version) {
            html += "\n    &lt;version&gt;${version}&lt;/version&gt;"
        }
        if (scope != SCOPE_COMPILE) {
            html += "\n    &lt;scope&gt;${scope}&lt;/scope&gt;"
        }

        html += """
&lt;/dependency&gt;</code></pre>
</div>
</div>
"""
        html
    }
}

