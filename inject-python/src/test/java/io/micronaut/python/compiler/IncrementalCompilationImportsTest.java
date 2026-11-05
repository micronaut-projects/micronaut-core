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
package io.micronaut.python.compiler;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How the planner of an incremental compilation reads the imports of Python sources, without compiling them: which
 * ones it can prove isolated, and that those it does are dependencies whatever way they are written.
 */
class IncrementalCompilationImportsTest {

    private static final Map<String, String> MODULES = Map.of(
        "pkg/models.py", """
            from dataclasses import dataclass as dc

            LIMIT = 1
            NAME: str = "name"

            class Model:
                def method(self):
                    in_method = 1

            def helper():
                in_function = 1

            async def async_helper():
                pass

            try:
                from .fallback import conditional
            except ImportError:
                conditional = None
            """,
        "pkg/fallback.py", "conditional = 1\n",
        "pkg/sub/deep.py", "class Deep:\n    pass\n"
    );

    @Test
    void aRelativeImportOfWhatTheSourcesShowIsResolved() {
        for (String imports : List.of(
            "from .models import Model",
            "from .models import LIMIT",
            "from .models import NAME",
            "from .models import helper, async_helper",
            "from .models import conditional",
            "from .models import dc",
            "from .models import Model as Renamed",
            "from .models import (\n    Model as Renamed,  # continued\n    helper,\n)",
            "from .models import Model; from .fallback import conditional",
            "from . import models",
            "from . import models as renamed",
            "from .import models",
            "from.models import Model",
            "from .models import(Model)",
            "from . models import Model",
            "from .sub.deep import Deep",
            "from .sub import deep",
            // a class of the package's modules, which the initialiser the compiler generates serves
            "from .sub import Deep",
            "def method():\n    from .models import Model",
            "try: from .models import Model\nexcept ImportError: pass"
        )) {
            assertFalse(unresolved("pkg/consumer.py", imports), imports);
        }
    }

    @Test
    void aNameInsideABracketSpreadOverLinesIsNotABindingOfTheModule() {
        Map<String, String> sources = Map.of(
            "pkg/calls.py", "result = dict(\n    named=1,\n)\n",
            "pkg/consumer.py", "from .calls import named\n"
        );
        assertTrue(IncrementalCompilation.hasDynamicOrUnresolvedPythonRelationship(sources));
    }

    @Test
    void aRelativeImportFromAParentIsResolved() {
        assertFalse(unresolved("pkg/nested/consumer.py", "from ..models import Model"));
        assertFalse(unresolved("pkg/nested/consumer.py", "from .. import models"));
        assertFalse(unresolved("pkg/nested/inner/consumer.py", "from ...models import Model"));
    }

    @Test
    void aReExportOfASourceInitialiserIsResolved() {
        // pyronaut process refuses a source initialiser, yet the planner runs ahead of that
        Map<String, String> sources = Map.of(
            "pkg/__init__.py", "from .models import Model\n",
            "pkg/models.py", "class Model:\n    pass\n",
            "pkg/consumer.py", "from . import Model\n"
        );
        assertFalse(IncrementalCompilation.hasDynamicOrUnresolvedPythonRelationship(sources));
    }

    @Test
    void anUnresolvedRelativeImportForcesEverySource() {
        for (String imports : List.of(
            "from .models import Missing",
            "from .models import Model, Missing",
            "from .models import (\n    Model,\n    Missing as Renamed,\n)",
            "from .models import in_method",
            "from .models import in_function",
            "from .missing import Name",
            "from .sub.missing import Name",
            "from . import missing",
            "from .sub import Missing",
            "from .models.missing import Name",
            "from .models import *",
            "from .models import*",
            "from.models import *",
            "try: from .models import *\nexcept ImportError: pass",
            "import os; from .models import *",
            "from . import *"
        )) {
            assertTrue(unresolved("pkg/consumer.py", imports), imports);
        }
    }

    @Test
    void aRelativeImportBeyondTheTopLevelPackageIsUnresolved() {
        assertTrue(unresolved("pkg/consumer.py", "from ..top import Name"));
        assertTrue(unresolved("pkg/consumer.py", "from .. import top"));
        // a module of the root has no package: python/ is the Java package its stub goes to
        assertTrue(unresolved("consumer.py", "from .top import Name"));
        assertTrue(unresolved("consumer.py", "from .python.deep import Name", Map.of("python/deep.py", "Name = 1\n")));
    }

    @Test
    void aNameBehindAModuleGetattrIsUnresolved() {
        Map<String, String> sources = Map.of(
            "pkg/lazy.py", "def __getattr__(name):\n    return name\n",
            "pkg/consumer.py", "from .lazy import computed\n"
        );
        assertTrue(IncrementalCompilation.hasDynamicOrUnresolvedPythonRelationship(sources));
    }

    @Test
    void anImportInsideAFunctionOrClassIsNotABindingOfTheModule() {
        for (String body : List.of(
            "def other():\n    from .fallback import conditional\n",
            "async def other(\n    value,\n) -> int:\n    from .fallback import conditional\n",
            "def other():\n    value = [\n        1,\n]\n    from .fallback import conditional\n",
            "class Other:\n    from .fallback import conditional\n"
        )) {
            Map<String, String> sources = Map.of(
                "pkg/fallback.py", "conditional = 1\n",
                "pkg/lazy.py", "def __getattr__(name):\n    return name\n\n" + body,
                "pkg/consumer.py", "from .lazy import conditional\n"
            );
            assertTrue(IncrementalCompilation.hasDynamicOrUnresolvedPythonRelationship(sources), body);
        }
    }

    @Test
    void aDynamicImportForcesEverySourceBesideResolvedRelativeImports() {
        assertTrue(unresolved("pkg/consumer.py", "from .models import Model\nimport importlib\nimportlib.import_module(\"pkg.models\")"));
    }

    @Test
    void anImportInsideAStringIsNotAnImport() {
        assertFalse(unresolved("pkg/consumer.py", "\"\"\"\nfrom .missing import *\n\"\"\"\nfrom .models import Model"));
    }

    @Test
    void aRelativeImportIsADependencyOnItsModuleHoweverItIsWritten() {
        for (String imports : List.of(
            "from .models import helper",
            "from.models import helper",
            "from . models import helper",
            "from .models import(helper)",
            "from .models import helper as renamed",
            "try: from .models import helper\nexcept ImportError: pass",
            "if True: from .models import helper",
            "import os; from .models import helper"
        )) {
            assertTrue(IncrementalCompilation.pythonImports(imports).contains(".models"), imports);
        }
    }

    @Test
    void aNameOutsideAsciiIsADependencyLikeAnyOther() {
        assertTrue(IncrementalCompilation.pythonImports("from . import \u03b1").contains(".\u03b1"));
        assertTrue(IncrementalCompilation.pythonImports("from .x import \u03b1 as a").contains(".x.\u03b1"));
        assertTrue(IncrementalCompilation.pythonImports("import pkg.\u03b1").contains("pkg.\u03b1"));
        // a name the module binds in a way the scan does not read is not taken for resolved
        assertTrue(unresolved("pkg/consumer.py", "from .models import \u03b1"));
    }

    @Test
    void anImportAfterTheColonOfACompoundStatementIsADependency() {
        assertTrue(IncrementalCompilation.pythonImports("try: import pkg.models\nexcept ImportError: pass").contains("pkg.models"));
    }

    private static boolean unresolved(String path, String source) {
        return unresolved(path, source, Map.of());
    }

    private static boolean unresolved(String path, String source, Map<String, String> others) {
        Map<String, String> sources = new HashMap<>(MODULES);
        sources.putAll(others);
        sources.put(path, source + "\n");
        return IncrementalCompilation.hasDynamicOrUnresolvedPythonRelationship(sources);
    }
}
