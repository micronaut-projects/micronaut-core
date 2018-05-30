package io.micronaut.cli.util

import io.micronaut.cli.MicronautCli

class VersionInfo {

    static String getVersion(Class clazz) {
        def version = clazz.getPackage()?.getImplementationVersion()

        if(!version) {
            Properties prop = new Properties()
            prop.load(MicronautCli.getClassLoader().getResourceAsStream("META-INF/MANIFEST.MF"))

            version = prop.getProperty("Implementation-Version")
        }

        version
    }

}
