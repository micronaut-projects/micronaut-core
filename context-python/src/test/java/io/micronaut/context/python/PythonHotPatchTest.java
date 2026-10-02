package io.micronaut.context.python;

import io.micronaut.context.reload.InPlaceResourceReloader;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static io.micronaut.context.python.PythonContextRuntime.PYTHON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The merge rules of {@code __micronaut_hot_patch}: an edited module is patched into the objects that
 * already exist, which the generated classes and the runtime's caches hold, and what cannot be patched
 * is refused with the module left as it was.
 */
final class PythonHotPatchTest {

    private static final String HARNESS = """
        import importlib.machinery
        import importlib.util
        import sys
        import types

        _SOURCES = {}

        class _FakeLoader:
            def get_data(self, path):
                try:
                    return _SOURCES[path]
                except KeyError:
                    raise OSError(path)

        def _path(name):
            return '/graalpy_vfs/src/' + name.replace('.', '/') + '.py'

        def define(name, source):
            path = _path(name)
            _SOURCES[path] = source.encode()
            module = types.ModuleType(name)
            module.__file__ = path
            loader = _FakeLoader()
            module.__loader__ = loader
            module.__spec__ = importlib.machinery.ModuleSpec(name, loader, origin=path)
            sys.modules[name] = module
            exec(compile(source, path, 'exec'), module.__dict__)
            return module

        def edit(name, source):
            _SOURCES[_path(name)] = source.encode()

        def bytecode(name, compiled_source, checked_source):
            import marshal
            header = importlib.util.MAGIC_NUMBER + (3).to_bytes(4, 'little') + importlib.util.source_hash(checked_source.encode())
            _SOURCES[importlib.util.cache_from_source(_path(name))] = header + marshal.dumps(compile(compiled_source, _path(name), 'exec'))
        """;

    private Context context;
    private Value patch;

    @BeforeEach
    void setUp() {
        context = Context.newBuilder(PYTHON).allowAllAccess(true).build();
        PythonContextRegistry.registerContext(context);
        context.eval(PYTHON, HARNESS);
        patch = PythonContextRuntime.helper(context, "__micronaut_hot_patch");
    }

    @AfterEach
    void tearDown() {
        PythonContextRegistry.unregisterContext(context);
        context.close();
    }

    private Value hotPatch(String... paths) {
        Value result = patch.execute((Object) paths);
        assertTrue(result.getArrayElement(1).isNull(), () -> "refused: " + result.getArrayElement(1));
        return result.getArrayElement(0);
    }

    private void run(String python) {
        context.eval(PYTHON, python);
    }

    @Test
    void theReloaderOfTheInstalledRuntimePatchesItsContexts() throws Exception {
        run("""
            m = define('hot.reused', '''
            def answer():
                return 1
            ''')
            captured = m.answer
            """);
        run("""
            edit('hot.reused', '''
            def answer():
                return 2
            ''')
            """);
        InPlaceResourceReloader reloader = PythonContextRuntime.inPlaceReloader();
        String module = GraalPyContextFactory.APPLICATION_SRC_PATH + "hot/reused.py";
        assertTrue(reloader.canReload(Set.of(module), Set.of()));
        assertFalse(reloader.canReload(Set.of(GraalPyContextFactory.APPLICATION_SRC_PATH + "hot/__init__.py"), Set.of()));
        assertFalse(reloader.canReload(Set.of(module), Set.of(GraalPyContextFactory.APPLICATION_SRC_PATH + "hot/gone.py")));
        // the runtime installed when it reloads, as a test runner's reused context
        PythonApplicationRuntime runtime = PythonContextRuntime.setContext(context, getClass().getClassLoader());
        try {
            assertEquals(1, reloader.reload(Set.of(module)).count());
        } finally {
            PythonApplicationRuntime.uninstall(runtime);
        }
        run("""
            assert m.answer is captured
            assert captured() == 2, captured()
            """);
    }

    @Test
    void aFunctionKeepsItsIdentityAndRunsTheNewBody() {
        run("""
            m = define('hot.funcs', '''
            def greet(name='world'):
                return 'hello ' + name
            ''')
            captured = m.greet
            """);
        run("""
            edit('hot.funcs', '''
            def greet(name='there'):
                \"\"\"Greets.\"\"\"
                return 'hi ' + name
            ''')
            """);
        Value patched = hotPatch("hot/funcs.py");
        assertEquals(List.of("hot.funcs"), patched.as(List.class));
        run("""
            assert m.greet is captured
            assert captured() == 'hi there', captured()
            assert captured.__doc__ == 'Greets.'
            """);
    }

    @Test
    void aClassKeepsItsIdentityAndItsInstancesAndMembersSeeTheNewCode() {
        run("""
            m = define('hot.classes', '''
            class Base:
                def kind(self):
                    return 'base'

            class Book(Base):
                shelf = 'a'

                def __init__(self, title):
                    self.title = title

                def describe(self):
                    return 'book ' + self.title

                @staticmethod
                def tag():
                    return 'old'

                @classmethod
                def make(cls):
                    return cls('made')

                @property
                def upper(self):
                    return self.title.upper()

                def removed(self):
                    return 'gone'

                def __len__(self):
                    return 1

                class Nested:
                    def value(self):
                        return 1

            def gone():
                return 1

            default_book = Book('default')
            ''')
            Book = m.Book
            book = Book('dune')
            describe = Book.describe
            Book.__micronaut_introduction__ = True
            def java_default(self):
                return 'java'
            java_default.__module__ = 'micronaut_runtime'
            Book.from_java = java_default
            """);
        run("""
            edit('hot.classes', '''
            class Base:
                def kind(self):
                    return 'base!'

            class Book(Base):
                shelf = 'b'

                def __init__(self, title):
                    self.title = title

                def describe(self):
                    return 'BOOK ' + self.title + ' ' + self.added()

                def added(self):
                    return 'added ' + super().kind()

                @staticmethod
                def tag():
                    return 'new'

                @classmethod
                def make(cls):
                    return cls('made again')

                @property
                def upper(self):
                    return self.title.upper() + '!'

                class Nested:
                    def value(self):
                        return 2

            default_book = Book('other default')
            ''')
            """);
        hotPatch("hot/classes.py");
        run("""
            assert m.Book is Book
            assert Book.describe is describe
            assert book.describe() == 'BOOK dune added base!', book.describe()
            assert Book.tag() == 'new'
            assert Book.make().title == 'made again'
            assert book.upper == 'DUNE!'
            assert Book.shelf == 'b'
            assert not hasattr(Book, 'removed')
            assert not hasattr(Book, '__len__')
            assert not hasattr(m, 'gone')
            assert Book.Nested().value() == 2
            assert Book.__micronaut_introduction__
            assert book.from_java() == 'java'
            assert type(m.default_book) is Book and m.default_book.title == 'other default'
            """);
    }

    @Test
    void injectedGlobalsKeepTheirValuesAndOtherGlobalsAreRebound() {
        run("""
            m = define('hot.globals', '''
            from typing import Annotated
            service: Annotated[object, 'Inject'] = None
            limit = 1

            def use():
                return (service, limit)
            ''')
            m.service = 'injected'
            """);
        run("""
            edit('hot.globals', '''
            from typing import Annotated
            service: Annotated[object, 'Inject'] = None
            limit = 2

            def use():
                return (service, limit, 'new')
            ''')
            """);
        hotPatch("hot/globals.py");
        run("assert m.use() == ('injected', 2, 'new'), m.use()");
    }

    @Test
    void slotsAndDefaultsKeepPointingAtTheOldClasses() {
        run("""
            m = define('hot.slots', '''
            class Point:
                __slots__ = ('x',)

                def __init__(self, x):
                    self.x = x

                def show(self):
                    return 'p' + str(self.x)

            class Line:
                start: Point

            first: Point = Point(5)

            def origin(point: Point = Point(0), kind=Point):
                return (point, kind)
            ''')
            point = m.Point(1)
            """);
        run("""
            edit('hot.slots', '''
            class Point:
                __slots__ = ('x',)

                def __init__(self, x):
                    self.x = x

                def show(self):
                    return 'P' + str(self.x)

            class Line:
                start: Point

            first: Point = Point(5)

            def origin(point: Point = Point(0), kind=Point):
                return (point, kind)
            ''')
            """);
        hotPatch("hot/slots.py");
        run("""
            assert point.x == 1 and point.show() == 'P1', point.show()
            default, kind = m.origin()
            assert kind is m.Point and type(default) is m.Point and default.x == 0
            assert m.origin.__annotations__['point'] is m.Point
            assert m.Line.__annotations__['start'] is m.Point
            assert m.__annotations__['first'] is m.Point and type(m.first) is m.Point
            """);
    }

    @Test
    void decoratorAttributesNestedAnnotationsAndImportedValuesFollowTheEdit() {
        run("""
            m = define('hot.meta', '''
            def tagged(function):
                function.tag = 'old'
                return function

            class Item:
                pass

            LIMIT = [1]

            @tagged
            def handle(items: list[Item]) -> Item | None:
                return None
            ''')
            Item = m.Item
            other = define('hot.importer', '''
            from hot.meta import LIMIT
            ''')
            """);
        run("""
            edit('hot.meta', '''
            def tagged(function):
                function.tag = 'old'
                return function

            class Item:
                pass

            LIMIT = [1]

            def handle(items: list[Item]) -> Item | None:
                return items
            ''')
            """);
        // the value another module imported changed: only a restart gives it the new one
        assertRefused("hot/meta.py", "imported 'LIMIT'");
        run("""
            del other.LIMIT
            """);
        hotPatch("hot/meta.py");
        run("""
            assert not hasattr(m.handle, 'tag')
            annotations = m.handle.__annotations__
            assert annotations['items'].__args__[0] is Item, annotations
            assert Item in annotations['return'].__args__, annotations
            """);
    }

    @Test
    void aWrappedFunctionIsPatchedThroughItsDecorator() {
        run("""
            m = define('hot.wrapped', '''
            import functools

            def logged(function):
                @functools.wraps(function)
                def wrapper(*args):
                    return 'logged ' + function(*args)
                return wrapper

            @logged
            def work():
                return 'old'
            ''')
            wrapper = m.work
            inner = m.work.__wrapped__
            """);
        run("""
            edit('hot.wrapped', '''
            import functools

            def logged(function):
                @functools.wraps(function)
                def wrapper(*args):
                    return 'logged ' + function(*args)
                return wrapper

            @logged
            def work():
                return 'new'
            ''')
            """);
        hotPatch("hot/wrapped.py");
        run("""
            assert m.work is wrapper and m.work.__wrapped__ is inner
            assert wrapper() == 'logged new', wrapper()
            """);
    }

    @Test
    void theCheckedBytecodeOfAModuleIsPreferredToItsSource() {
        run("""
            m = define('hot.transformed', '''
            def value():
                return 'source'
            ''')
            source = '''
            def value():
                return 'source again'
            '''
            edit('hot.transformed', source)
            bytecode('hot.transformed', '''
            def value():
                return 'transformed'
            ''', source)
            """);
        hotPatch("hot/__pycache__/transformed." + implementationTag() + ".pyc");
        run("assert m.value() == 'transformed', m.value()");
    }

    @Test
    void bytecodeOfAnotherVersionOfTheSourceIsRefused() {
        run("""
            m = define('hot.stale', '''
            def value():
                return 'old'
            ''')
            edit('hot.stale', '''
            def value():
                return 'new'
            ''')
            bytecode('hot.stale', 'def value():\\n    return 1\\n', 'something else')
            """);
        assertRefused("hot/stale.py", "another version of the source");
        run("assert m.value() == 'old'");
    }

    @Test
    void structuralChangesAreRefusedAndTheModuleIsLeftAsItWas() {
        run("""
            m = define('hot.refused', '''
            class A:
                pass

            class B:
                pass

            class Thing(A):
                def run(self):
                    return 'old'

            def helper():
                return 'old'

            def items():
                return [1]
            ''')
            Thing = m.Thing
            """);
        // the bases changed
        run("""
            edit('hot.refused', '''
            class A:
                pass

            class B:
                pass

            class Thing(B):
                def run(self):
                    return 'new'

            def helper():
                return 'new'

            def items():
                return [1]
            ''')
            """);
        assertRefused("hot/refused.py", "bases");
        run("assert m.Thing is Thing and Thing().run() == 'old' and m.helper() == 'old'");
        // a function became a class
        run("""
            edit('hot.refused', '''
            class A:
                pass

            class B:
                pass

            class Thing(A):
                def run(self):
                    return 'new'

            class helper:
                pass

            def items():
                return [1]
            ''')
            """);
        assertRefused("hot/refused.py", "changed from function to class");
        run("assert Thing().run() == 'old' and m.helper() == 'old'");
        // a function became a generator
        run("""
            edit('hot.refused', '''
            class A:
                pass

            class B:
                pass

            class Thing(A):
                def run(self):
                    return 'new'

            def helper():
                return 'new'

            def items():
                yield 1
            ''')
            """);
        assertRefused("hot/refused.py", "generator");
        run("assert Thing().run() == 'old' and m.items() == [1]");
    }

    @Test
    void aClosureThatChangedIsRefused() {
        run("""
            m2 = define('hot.closure2', '''
            def deco(f):
                def wrapper():
                    return f()
                return wrapper

            @deco
            def work():
                return 1
            ''')
            edit('hot.closure2', '''
            def deco(f):
                extra = 1
                def wrapper():
                    return f() + extra
                return wrapper

            @deco
            def work():
                return 1
            ''')
            """);
        assertRefused("hot/closure2.py", "closes over changed");
        run("assert m2.work() == 1");
    }

    @Test
    void aModuleTheContextNeverImportedIsLeftToItsFirstImport() {
        Value patched = hotPatch("hot/never.py");
        assertEquals(0, patched.getArraySize());
    }

    private void assertRefused(String path, String reason) {
        Value result = patch.execute((Object) new String[] {path});
        assertTrue(result.getArrayElement(0).isNull(), "patched although refused");
        String message = result.getArrayElement(1).asString();
        assertTrue(message.contains(reason), message);
    }

    private String implementationTag() {
        return context.eval(PYTHON, "import sys; sys.implementation.cache_tag").asString();
    }
}
