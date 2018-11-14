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
 *
 */
class BuildDependencyMacro extends InlineMacroProcessor {
    static final String DEPENDENCY_PREFIX = 'micronaut-'
    static final String GROUPID = 'io.micronaut'
    static final String MULTILANGUAGECSSCLASS = 'multi-language-sample'
    static final String BUILD_GRADLE = 'gradle'
    static final String BUILD_MAVEN = 'maven'

    BuildDependencyMacro(String macroName, Map<String, Object> config) {
        super(macroName, config)
    }

    @Override
    protected Object process(AbstractBlock parent, String target, Map<String, Object> attributes) {

        String artifactId = target.startsWith(DEPENDENCY_PREFIX) ? target : "${DEPENDENCY_PREFIX}${target}"
        String groupId = valueAtAttributes('groupId', attributes) ?: GROUPID
        String version = valueAtAttributes('version', attributes)
        boolean verbose = valueAtAttributes('verbose', attributes) as boolean
        String content = gradleDepependency(BUILD_GRADLE, groupId, artifactId, version, MULTILANGUAGECSSCLASS, verbose)
        content += mavenDepependency(BUILD_MAVEN, groupId, artifactId, version, MULTILANGUAGECSSCLASS)
        createBlock(parent, "pass", [content], attributes, config).convert()
    }

    /**
     * Given a map such as  ['text':'version="1.0.1", groupId="io.micronaut"']
     * for name = 'version' it returns '1.0.1'
     */
    String valueAtAttributes(String name, Map<String, Object> attributes) {
        if (attributes.containsKey('text')) {
            String text = attributes['text']
            if (text.contains("${name}=\"")) {
                String partial = text.substring(text.indexOf("${name}=\"") + "${name}=\"".length())
                if ( partial.contains('"')) {
                    return partial.substring(0, partial.indexOf('"'))
                }
                return partial
            }
        }
        null
    }

    String gradleDepependency(String build,
                             String groupId,
                             String artifactId,
                             String version,
                             String multilanguageCssClass,
                             boolean  verbose) {
String html = """\
        <div class=\"listingblock ${multilanguageCssClass}\">
<div class=\"content\">
<pre class=\"highlightjs highlight\"><code class=\"language-groovy hljs" data-lang="${build}">"""
        if (verbose) {
            html += "compile <span class=\"hljs-string\">group:</span> <span class=\"hljs-string\">'${groupId}'</span>, <span class=\"hljs-string\">name:</span> <span class=\"hljs-string\">'${artifactId}'</span>"
            if (version) {
                html +=", <span class=\"hljs-string\">version:</span> <span class=\"hljs-string\">'${version}'</span>"
            }
        } else {
            html += "compile <span class=\"hljs-string\">'${groupId}:${artifactId}"
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
                              String multilanguageCssClass) {
        String html = """\
<div class=\"listingblock ${multilanguageCssClass}\">
<div class=\"content\">
<pre class=\"highlightjs highlight\"><code class=\"language-xml hljs\" data-lang=\"${build}\">&lt;dependency&gt;
    &lt;groupId&gt;${groupId}&lt;/groupId&gt;
    &lt;artifactId&gt;${artifactId}&lt;/artifactId&gt;"""
        if (version) {
            html += "\n    &lt;version&gt;${version}&lt;/version&gt;"
        }
        html += """
&lt;/dependency&gt;</code></pre>
        </div>
</div>
"""
        html
    }
}
