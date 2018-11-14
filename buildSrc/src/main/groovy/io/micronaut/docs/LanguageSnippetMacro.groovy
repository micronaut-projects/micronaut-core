package io.micronaut.docs

import org.asciidoctor.Asciidoctor
import org.asciidoctor.SafeMode
import org.asciidoctor.ast.AbstractBlock
import org.asciidoctor.extension.BlockMacroProcessor

class LanguageSnippetMacro extends BlockMacroProcessor {
    final Asciidoctor asciidoctor

    LanguageSnippetMacro(String macroName, Map<String, Object> config, Asciidoctor asciidoctor) {
        super(macroName, config)
        this.asciidoctor = asciidoctor
    }

    @Override
    protected Object process(AbstractBlock parent, String target, Map<String, Object> attributes) {
        String baseName = target.replace(".", File.separator)
        String project = attributes.get("project")
        String[] tags = attributes.get("tags")?.toString()?.split(",")

        StringBuilder content = new StringBuilder(".${attributes.title}")
        for(lang in ["java", "kotlin", "groovy"]) {
            String projectDir
            String ext = lang == 'kotlin' ? 'kt' : lang
            if (project == null) {
                projectDir = lang == 'kotlin' ? 'test-suite-kotlin' :  "test-suite"
            } else {
                projectDir = project
            }
            File file = new File("$projectDir/src/test/$lang/${baseName}.$ext")
            if (!file.exists()) {
                file = new File("$projectDir/src/test/groovy/${baseName}.$ext")
            }
            String includes
            if (tags) {
                includes =  tags.collect() { "include::$file.absolutePath[tag=$it]" }.join("\n")
            } else {
                includes = "include::$file.absolutePath[]"
            }

            if (file.exists()) {
                content << """
[source.multi-language-sample,$lang]
.${Character.toUpperCase(lang.charAt(0))}${lang.substring(1)}
----
$includes
----"""

            }
        }

        if (content) {
            println content
            String result = asciidoctor.render(content.toString(), [
                    'safe': SafeMode.UNSAFE.level,
                    'source-highlighter':'highlightjs',
            ])
            createBlock(parent, "pass", result, attributes, config)
        }
    }

}
