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
package io.micronaut.python.compiler

import io.micronaut.context.ApplicationContext
import io.micronaut.context.python.PythonContextRuntime
import io.micronaut.python.processing.PythonAnnotationProcessor
import spock.lang.Shared
import spock.lang.Specification

/**
 * The generated shim package of a Java package imports its subpackages on first access, not with
 * the package, and every import form of a subpackage keeps working.
 */
class LazyShimSubpackageSpec extends Specification {

    private static final String PROBE = '''
import importlib
import sys

from jakarta.inject import Singleton
from micronaut.context.annotation import Executable

# the shim modules of micronaut.core loaded when the application modules are imported
LOADED_AT_STARTUP = sorted(name for name in sys.modules if name == 'micronaut.core' or name.startswith('micronaut.core.'))


def _shims():
    # never called: the imports make the compiler generate the shims the probe resolves at run time
    from micronaut.core.async_.annotation import SingleResult
    from micronaut.core.async_.propagation import ReactorPropagation
    from micronaut.core.convert import ConversionService
    from micronaut.core.convert.value import ConvertibleValues
    from micronaut.core.naming import NameUtils
    from micronaut.core.util import StringUtils
    return SingleResult, ReactorPropagation, ConversionService, ConvertibleValues, NameUtils, StringUtils


def _loaded(name):
    return name in sys.modules


@Singleton
class Probe:
    @Executable
    def report(self) -> str:
        # the checks run in this order, each on subpackages no earlier check imported
        result = {'startup': ','.join(LOADED_AT_STARTUP)}

        convert = importlib.import_module('micronaut.core.convert')
        result['package_import_loads_subpackage'] = _loaded('micronaut.core.convert.value')
        result['all'] = ','.join(convert.__all__)
        result['dir_lists_subpackage'] = 'value' in dir(convert)
        result['dir_loads_subpackage'] = _loaded('micronaut.core.convert.value')
        result['attribute'] = convert.value.ConvertibleValues.empty().isEmpty()
        result['attribute_binds_subpackage'] = 'value' in vars(convert)

        namespace = {}
        exec('from micronaut.core.async_ import *', namespace)
        result['star'] = ','.join(sorted(name for name in namespace if not name.startswith('__')))
        result['star_module'] = namespace['annotation'].__name__
        result['star_imports_subpackage'] = _loaded('micronaut.core.async_.propagation')

        namespace = {}
        exec('import micronaut.core.naming\\nvalue = micronaut.core.naming.NameUtils.hyphenate("fooBar")', namespace)
        result['import_dotted'] = namespace['value']

        namespace = {}
        exec('from micronaut.core import util\\nvalue = util.StringUtils.isEmpty("")', namespace)
        result['from_import'] = namespace['value']

        namespace = {}
        exec('import micronaut\\nvalue = micronaut.core.util.StringUtils.isNotEmpty("x")', namespace)
        result['bare_import_attribute'] = namespace['value']

        core = sys.modules['micronaut.core']
        result['missing'] = hasattr(core, 'no_such_subpackage')
        try:
            exec('from micronaut.core import no_such_subpackage', {})
            result['missing_import'] = 'imported'
        except ImportError:
            result['missing_import'] = 'ImportError'
        return ';'.join(f'{key}={value}' for key, value in result.items())
'''

    @Shared
    File srcDir
    @Shared
    File targetDir

    def setupSpec() {
        srcDir = File.createTempDir("python-lazy-subpackage-src", "")
        targetDir = File.createTempDir("python-lazy-subpackage-target", "")
        def probe = new File(srcDir, "app/probe.py")
        probe.parentFile.mkdirs()
        probe.text = PROBE
        PythonContextRuntime.resetContext()
        PyronautCompiler.builder()
            .pythonSrc(srcDir.absolutePath)
            .targetDir(targetDir)
            .build()
            .compile()
    }

    def cleanupSpec() {
        PythonContextRuntime.resetContext()
        srcDir.deleteDir()
        targetDir.deleteDir()
    }

    def "a shim members module names its subpackages instead of importing them"() {
        when:
        def core = packageMembers("micronaut/core")
        def convert = packageMembers("micronaut/core/convert")

        then:
        !core.contains("from . import")
        core.contains("__micronaut_subpackages__ = [\"async_\",\"convert\",\"naming\",\"util\"]")
        core.contains("__all__ = []")

        and: "the members stay members, the subpackages are named apart from them"
        convert.contains("ConversionService = _micronaut_java_type('io.micronaut.core.convert.ConversionService', True)")
        convert.contains("__micronaut_subpackages__ = [\"value\"]")
        convert.contains("__all__ = [\"ConversionService\"]")
    }

    def "subpackages load on first access and every import form resolves them"() {
        given:
        def classLoader = new URLClassLoader(targetDir.toURI().toURL())
        def context = ApplicationContext.builder().classLoader(classLoader).build().start()

        when:
        def probe = context.getBean(classLoader.loadClass("app.Probe"))
        Map<String, String> report = probe.report().split(';').collectEntries { it.split('=', 2) as List }

        then: "starting the application imports no subpackage of micronaut.core"
        report.startup == ""

        and: "importing a package leaves its subpackages to their first access"
        report.package_import_loads_subpackage == "False"

        and: "__all__ lists the members and then the subpackages, and dir() lists a subpackage not loaded yet"
        report.all == "ConversionService,value"
        report.dir_lists_subpackage == "True"
        report.dir_loads_subpackage == "False"

        and: "an attribute access imports the subpackage and binds it on the package"
        report.attribute == "True"
        report.attribute_binds_subpackage == "True"

        and: "a star import binds and imports the direct subpackages"
        report.star == "annotation,propagation"
        report.star_module == "micronaut.core.async_.annotation"
        report.star_imports_subpackage == "True"

        and: "import pkg.sub, from pkg import sub and attributes after a bare import pkg"
        report.import_dotted == "foo-bar"
        report.from_import == "True"
        report.bare_import_attribute == "True"

        and: "a name that is no subpackage stays missing"
        report.missing == "False"
        report.missing_import == "ImportError"

        cleanup:
        context?.close()
        classLoader?.close()
    }

    private String packageMembers(String packagePath) {
        def directory = new File(targetDir, "META-INF/${PythonAnnotationProcessor.APPLICATION_SRC_PATH}${packagePath}")
        directory.listFiles()
            .findAll { it.name.startsWith(PythonAnnotationProcessor.PACKAGE_MEMBERS_MODULE_PREFIX) }
            .sort { it.name }*.text.join('\n')
    }
}
