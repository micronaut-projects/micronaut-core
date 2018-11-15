package io.micronaut.docs

import org.asciidoctor.Asciidoctor
import org.asciidoctor.SafeMode
import org.asciidoctor.ast.AbstractBlock
import org.asciidoctor.extension.BlockMacroProcessor

class LanguageSnippetMacro extends BlockMacroProcessor implements ValueAtAttributes {
    final Asciidoctor asciidoctor

    private static final String LANG_JAVA = 'java'
    private static final String LANG_GROOVY = 'groovy'
    private static final String LANG_KOTLIN = 'kotlin'
    private static final List<String> LANGS = [LANG_JAVA, LANG_GROOVY, LANG_KOTLIN]
    private static final String DEFAULT_KOTLIN_PROJECT = 'test-suite-kotlin'
    private static final String DEFAULT_JAVA_PROJECT = 'test-suite'
    private static final String DEFAULT_GROOVY_PROJECT = 'test-suite-groovy'
    private static final String ATTR_PROJECT = 'project'

    LanguageSnippetMacro(String macroName, Map<String, Object> config, Asciidoctor asciidoctor) {
        super(macroName, config)
        this.asciidoctor = asciidoctor
    }

    private String projectDir(String lang, Map<String, Object> attributes) {
        if (lang == LANG_KOTLIN) {
            return DEFAULT_KOTLIN_PROJECT
        }
        if (lang == LANG_GROOVY) {
            return DEFAULT_GROOVY_PROJECT
        }
        String project = valueAtAttributes(ATTR_PROJECT, attributes)
        if (!project) {
            return DEFAULT_JAVA_PROJECT
        }
    }

    @Override
    protected Object process(AbstractBlock parent, String target, Map<String, Object> attributes) {
        String baseName = target.replace(".", File.separator)
        String[] tags = valueAtAttributes("tags", attributes)?.toString()?.split(",")

        StringBuilder content = new StringBuilder()
        for(lang in LANGS) {
            String projectDir = projectDir(lang, attributes)
            String ext = lang == LANG_KOTLIN ? 'kt' : lang
            String testFolder = lang == LANG_JAVA ? 'groovy' : lang
            File file = new File("$projectDir/src/test/$testFolder/${baseName}.$ext")
            if (!file.exists()) {
                continue
            }
            String includes
            if (tags) {
                includes =  tags.collect() { "include::$file.absolutePath[tag=$it]" }.join("\n\n")
            } else {
                includes = "include::$file.absolutePath[]"
            }

            if (file.exists()) {
                content << """
[source.multi-language-sample,$lang]
----
$includes
----"""
            }
        }
        if (content) {
            String result = asciidoctor.render(content.toString(), [
                    'safe': SafeMode.UNSAFE.level,
                    'source-highlighter':'highlightjs',
            ])
            createBlock(parent, "pass", result, attributes, config)
        }
    }

}
