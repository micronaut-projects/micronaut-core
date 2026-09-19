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
 * The Java packages, types and annotations the Python sources import are served at run time by the
 * import finder of the runtime module from the manifest the compiler writes, without generated modules,
 * and every import form of them keeps working.
 */
class JavaImportFinderSpec extends Specification {

    private static final String PROBE = '''
import importlib
import sys

from jakarta.inject import Singleton
from micronaut.context.annotation import Executable

# the modules of micronaut.core loaded when the application modules are imported
LOADED_AT_STARTUP = sorted(name for name in sys.modules if name == 'micronaut.core' or name.startswith('micronaut.core.'))
GENERATED_AT_STARTUP = sorted(name for name, module in sys.modules.items() if (getattr(module, '__file__', None) or '').startswith('/graalpy_vfs/src/'))


def _shims():
    # never called: the imports make the compiler record the Java imports the probe resolves at run time
    from micronaut.core.async_.annotation import SingleResult
    from micronaut.core.async_.propagation import ReactorPropagation
    from micronaut.core.convert import ConversionService
    from micronaut.core.convert.value import ConvertibleValues
    from micronaut.core.naming import NameUtils
    from micronaut.core.util import StringUtils
    from micronaut.context.annotation import Mapper, Requires
    from micronaut.http import HttpResponse
    from micronaut.http.HttpResponse import HttpResponse as HttpResponseType
    from micronaut.http.client.exceptions import HttpClientResponseException
    from micronaut.core.type import Argument
    from micronaut.http.annotation import Error
    return SingleResult, ReactorPropagation, ConversionService, ConvertibleValues, NameUtils, StringUtils, Mapper, Requires, HttpResponse, HttpResponseType, HttpClientResponseException, Argument, Error


def _loaded(name):
    return name in sys.modules


@Singleton
class Probe:
    @Executable
    def report(self) -> str:
        # the checks run in this order, each on packages no earlier check imported
        result = {'startup': ','.join(LOADED_AT_STARTUP), 'generated': ','.join(GENERATED_AT_STARTUP)}

        convert = importlib.import_module('micronaut.core.convert')
        result['package_file'] = getattr(convert, '__file__', None)
        result['package_path'] = repr(list(convert.__path__))
        result['package_import_loads_subpackage'] = _loaded('micronaut.core.convert.value')
        result['all'] = ','.join(convert.__all__)
        result['dir_lists_subpackage'] = 'value' in dir(convert)
        result['dir_loads_subpackage'] = _loaded('micronaut.core.convert.value')
        result['attribute'] = convert.value.ConvertibleValues.empty().isEmpty()
        result['attribute_binds_subpackage'] = 'value' in vars(convert)
        result['class_is_host_class'] = convert.ConversionService.SHARED is not None

        namespace = {}
        exec('from micronaut.core.async_ import *', namespace)
        result['star'] = ','.join(sorted(name for name in namespace if not name.startswith('__')))
        result['star_module'] = namespace['annotation'].__name__
        result['star_imports_subpackage'] = _loaded('micronaut.core.async_.propagation')

        namespace = {}
        exec('from micronaut.context.annotation import *', namespace)
        result['star_annotations'] = all(name in namespace for name in ('Executable', 'Mapper', 'Requires'))

        namespace = {}
        exec('import micronaut.core.naming\\nvalue = micronaut.core.naming.NameUtils.hyphenate("fooBar")', namespace)
        result['import_dotted'] = namespace['value']

        namespace = {}
        exec('from micronaut.core import util\\nvalue = util.StringUtils.isEmpty("")', namespace)
        result['from_import'] = namespace['value']

        namespace = {}
        exec('import micronaut\\nvalue = micronaut.core.util.StringUtils.isNotEmpty("x")', namespace)
        result['bare_import_attribute'] = namespace['value']

        namespace = {}
        exec('import micronaut.context.annotation as a\\n@a.Requires(property="x")\\nclass Marked:\\n    pass\\nvalue = Marked.__name__', namespace)
        result['package_alias_decorator'] = namespace['value']

        from micronaut.context.annotation import Requires, Mapper
        result['decorator_identity'] = Requires is sys.modules['micronaut.context.annotation'].Requires
        result['decorator_class_name'] = Requires.java_class_name
        result['decorator_java_class'] = Requires.java_class.getName()
        result['decorator_returns_target'] = Requires(property='x')(Probe) is Probe
        result['decorator_nested_enum'] = Requires.Sdk.MICRONAUT.name()
        result['decorator_nested_annotation'] = Mapper.Mapping.java_class_name
        result['decorator_nested_returns_target'] = Mapper.Mapping(to='x')(Probe) is Probe
        try:
            Requires(Probe)
            result['bare_guard'] = 'no error'
        except TypeError as e:
            result['bare_guard'] = 'TypeError'
        # an annotation whose value member holds a class takes a class as its argument
        from micronaut.http.annotation import Error
        result['class_value_bare'] = callable(Error(Probe))
        # the compiler records that, so an annotation absent at run time still takes the class
        import micronaut_java_imports
        result['class_value_recorded'] = callable(micronaut_java_imports._MicronautJavaAnnotation('missing.ClassValued', None, True)(Probe))
        try:
            micronaut_java_imports._MicronautJavaAnnotation('missing.Plain', None, None)(Probe)
            result['class_value_unrecorded'] = 'no error'
        except TypeError:
            result['class_value_unrecorded'] = 'TypeError'

        from micronaut.http import HttpResponse
        from micronaut.http.HttpResponse import HttpResponse as HttpResponseType
        result['type_module'] = HttpResponse is HttpResponseType and HttpResponse.ok().status().getCode() == 200
        import micronaut.http.HttpResponse
        result['type_module_import_keeps_class'] = micronaut.http.HttpResponse is HttpResponseType

        from micronaut.http.client.exceptions import HttpClientResponseException
        result['class_instantiation'] = HttpClientResponseException("boom", HttpResponse.badRequest()).getMessage()

        # a package imported as a module without a class import of its own resolves its classes on the class path
        import micronaut.core.beans as core_beans
        result['package_only_import'] = core_beans.BeanIntrospector.SHARED is not None

        core = sys.modules['micronaut.core']
        result['missing'] = hasattr(core, 'no_such_subpackage')
        try:
            exec('from micronaut.core import no_such_subpackage', {})
            result['missing_import'] = 'imported'
        except ImportError:
            result['missing_import'] = 'ImportError'
        try:
            exec('import micronaut.nowhere', {})
            result['missing_package'] = 'imported'
        except ImportError:
            result['missing_package'] = 'ImportError'
        return ';'.join(f'{key}={value}' for key, value in result.items())
'''

    @Shared
    File srcDir
    @Shared
    File targetDir

    def setupSpec() {
        srcDir = File.createTempDir("python-java-import-finder-src", "")
        targetDir = File.createTempDir("python-java-import-finder-target", "")
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

    def "the compiler writes one manifest of the Java imports and no module for them"() {
        when:
        def srcPath = new File(targetDir, "META-INF/${PythonAnnotationProcessor.APPLICATION_SRC_PATH}")
        def manifest = JavaImportsManifest.read(targetDir)

        then: "the generated sources are the application package and the manifest"
        JavaImportsManifest.files(targetDir).size() == 1
        srcPath.listFiles()*.name.findAll { !it.startsWith('__') }.sort() == ['app']
        !new File(srcPath, "micronaut").exists()
        !new File(srcPath, "jakarta").exists()

        and: "the manifest names every package on the way to an imported type, the imported types and their kinds"
        manifest.packages["micronaut"] == "io.micronaut"
        manifest.packages["micronaut.core"] == "io.micronaut.core"
        manifest.packages["micronaut.core.async_"] == "io.micronaut.core.async"
        manifest.packages["jakarta.inject"] == "jakarta.inject"
        manifest.packages["micronaut.core.beans"] == "io.micronaut.core.beans"
        manifest.members["micronaut.core.beans"] == null
        manifest.types["micronaut.http.HttpResponse"] == "io.micronaut.http.HttpResponse"
        manifest.member("micronaut.core.convert", "ConversionService") == ["io.micronaut.core.convert.ConversionService", "interface"]
        manifest.member("micronaut.core.naming", "NameUtils") == ["io.micronaut.core.naming.NameUtils", "class"]
        manifest.member("micronaut.context.annotation", "Executable") == ["io.micronaut.context.annotation.Executable", "annotation"]
        manifest.member("micronaut.http", "HttpResponse") == ["io.micronaut.http.HttpResponse", "interface"]
        manifest.member("micronaut.http.annotation", "Error") == ["io.micronaut.http.annotation.Error", "annotation", "class-value"]
        manifest.members["micronaut.core"] == null
    }

    def "Java packages, types and annotations resolve through the finder in every import form"() {
        given:
        def classLoader = new URLClassLoader(targetDir.toURI().toURL())
        def context = ApplicationContext.builder().classLoader(classLoader).build().start()

        when:
        def probe = context.getBean(classLoader.loadClass("app.Probe"))
        Map<String, String> report = probe.report().split(';').collectEntries { it.split('=', 2) as List }

        then: "starting the application imports no module of micronaut.core, and the only generated modules are the application's"
        report.startup == ""
        report.generated.split(',').every { !it.startsWith('micronaut.') && !it.startsWith('jakarta') }

        and: "a Java package is a module without a file that leaves its subpackages to their first access"
        report.package_file == "None"
        report.package_path == "[]"
        report.package_import_loads_subpackage == "False"

        and: "__all__ lists the members and then the subpackages, and dir() lists a subpackage not loaded yet"
        report.all == "ConversionService,value"
        report.dir_lists_subpackage == "True"
        report.dir_loads_subpackage == "False"

        and: "an attribute access imports the subpackage and binds it on the package, a class is the host class"
        report.attribute == "True"
        report.attribute_binds_subpackage == "True"
        report.class_is_host_class == "True"

        and: "a star import binds and imports the direct subpackages, and the annotations of a package"
        report.star == "annotation,propagation"
        report.star_module == "micronaut.core.async_.annotation"
        report.star_imports_subpackage == "True"
        report.star_annotations == "True"

        and: "import pkg.sub, from pkg import sub, attributes after a bare import pkg and a package alias"
        report.import_dotted == "foo-bar"
        report.from_import == "True"
        report.bare_import_attribute == "True"
        report.package_alias_decorator == "Marked"

        and: "an annotation is one decorator carrying its type, returning its target, with its nested types"
        report.decorator_identity == "True"
        report.decorator_class_name == "io.micronaut.context.annotation.Requires"
        report.decorator_java_class == "io.micronaut.context.annotation.Requires"
        report.decorator_returns_target == "True"
        report.decorator_nested_enum == "MICRONAUT"
        report.decorator_nested_annotation == "io.micronaut.context.annotation.Mapper\$Mapping"
        report.decorator_nested_returns_target == "True"
        report.bare_guard == "TypeError"
        report.class_value_bare == "True"
        report.class_value_recorded == "True"
        report.class_value_unrecorded == "TypeError"

        and: "the module of a type exports the type, and importing it keeps the type on the package"
        report.type_module == "True"
        report.type_module_import_keeps_class == "True"
        report.class_instantiation == "boom"

        and: "a package imported as a module without a class import resolves its classes"
        report.package_only_import == "True"

        and: "a name that is no member, and a package the sources do not import, stay missing"
        report.missing == "False"
        report.missing_import == "ImportError"
        report.missing_package == "ImportError"

        cleanup:
        context?.close()
        classLoader?.close()
    }
}
