package io.micronaut.docs

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction

class JavaDocAtValueReplacementTask extends DefaultTask {

    @InputFile
    private File adocFile

    private String rootProjectDir

    String getRootProjectDir() {
        return rootProjectDir
    }

    void setAdocFile(File adocFile) {
        this.adocFile = adocFile
    }

    void setRootProjectDir(String rootProjectDir) {
        this.rootProjectDir = rootProjectDir
    }

    public String getConfigurationPropertiesFolder() { return configurationPropertiesFolder }
    public void setConfigurationPropertiesFolder(String configurationPropertiesFolder) { this.configurationPropertiesFolder = configurationPropertiesFolder }

    @Override
    String getGroup() {
        'documentation'
    }

    @TaskAction
    void replaceAtValue() {
        if (this.adocFile.exists()) {

            String configurationPropertiesClassName
            List<String> accumulator = []
            List<String> allLines = adocFile.readLines()
            for ( int i = 0; i < allLines.size(); i++) {
                String line = allLines[i]
                if (line == '++++' && allLines[i+1].startsWith("<a id=\"")) {
                    String subline = allLines[i+1].substring("<a id=\"".length())
                    configurationPropertiesClassName = subline.substring(0, subline.indexOf("\""))
                }

                String proccessedLine = line

                int attempts = 5

                while(proccessedLine.contains('{@value') && attempts > 0) {
                    AtValue atValueReplacement = JavaDocAtValueReplacement.atValueField(configurationPropertiesClassName, proccessedLine)

                    if (atValueReplacement != null && atValueReplacement.type != null && atValueReplacement.fieldName != null) {
                        String resolvedValue = calculateResolvedValue(atValueReplacement.type, atValueReplacement.fieldName)
                        if (resolvedValue) {
                            String result = proccessedLine.substring(0, proccessedLine.indexOf("{@value "))
                            result += resolvedValue
                            String sub = proccessedLine.substring(proccessedLine.indexOf("{@value ") + "{@value ".size())
                            sub = sub.substring(sub.indexOf('}') + '}'.length())
                            result += sub
                            proccessedLine = result
                        } else {
                            println "no resolved value for type: ${atValueReplacement?.type} fieldname: ${atValueReplacement?.fieldName}"
                        }
                    }
                    attempts--
                }

                accumulator << proccessedLine

            }
            adocFile.text = accumulator.join('\n')
        }
    }

    String calculateResolvedValue(String className, String fieldName) {

        List<String> targetClassNames = []
        targetClassNames.addAll((className.split('\\.') as List<String>).findAll { it -> Character.isUpperCase(it.charAt(0)) }.collect { it.split('\\$') }.flatten() )

        def classFiles
        for ( String targetClassName : targetClassNames) {
            classFiles = project.fileTree(rootProjectDir).filter { it.isFile() && it.name == (targetClassName+".java") }.files
            if (classFiles) {
                break
            }
        }

        if (classFiles) {
            File f = classFiles.first()
            List<String> lines = f.readLines()

            for (String targetClassName : targetClassNames.reverse()) {
                String classEvaluated
                String interfaceEvaluated
                for (int i = 0; i < lines.size(); i++) {
                    String line = lines[i]
                    if (line.contains("class ")) {
                        String subLine = line.substring(line.indexOf("class ") + "class ".length())
                        classEvaluated = subLine.substring(0, subLine.indexOf(' '))
                    }
                    if (line.contains("interface ")) {
                        String subLine = line.substring(line.indexOf("interface ") + "interface ".length())
                        interfaceEvaluated = subLine.substring(0, subLine.indexOf(' '))
                    }
                    if (
                    (classEvaluated && (classEvaluated == targetClassName)) ||
                            (interfaceEvaluated && (interfaceEvaluated == targetClassName))
                    ) {
                        if (line.contains(" ${fieldName} = ".toString()) && line.contains(';')) {
                            return line.substring(line.indexOf("${fieldName} = ".toString()) + "${fieldName} = ".toString().length(), line.indexOf(';'))
                        }
                    }
                }
            }
        }
        null
    }
}
