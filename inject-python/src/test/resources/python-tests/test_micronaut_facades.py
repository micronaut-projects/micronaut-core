"""
Unit tests for micronaut_facades.py, run inside GraalPy by PythonSourceUnitTest.
"""
import ast
import unittest

from micronaut_facades import Facade, FacadeMember, FacadeImportRewriter, FacadeRegistry


def http_facade():
    members = {
        'Get': FacadeMember('Get', 'ANNOTATION', 'io.micronaut.http.annotation.Get', None),
        'HttpResponse': FacadeMember('HttpResponse', 'INTERFACE', 'io.micronaut.http.HttpResponse', None),
        'ok': FacadeMember('ok', 'STATIC_METHOD', 'io.micronaut.http.HttpResponse', 'ok'),
        'status': FacadeMember('status', 'STATIC_METHOD', 'io.micronaut.http.HttpResponse', 'status'),
        'CREATED': FacadeMember('CREATED', 'CONSTANT', 'io.micronaut.http.HttpStatus', 'CREATED'),
    }
    return Facade('pyronaut.http', True, None, members)


def rewrite(source):
    registry = FacadeRegistry(lambda name: None, ['pyronaut.http'])
    registry._cache['pyronaut.http'] = http_facade()
    rewriter = FacadeImportRewriter(registry)
    tree = rewriter.rewrite(ast.parse(source))
    return ast.unparse(tree), rewriter


class FacadeScopeTest(unittest.TestCase):

    def test_parameters_shadow_star_imported_members(self):
        code, _ = rewrite(
            "from pyronaut.http import *\n"
            "class C:\n"
            "    def check(self, status: int, ok: bool):\n"
            "        return status if ok else 0\n")
        self.assertIn("return status if ok else 0", code)

    def test_members_outside_the_shadowing_scope_are_rewritten(self):
        code, _ = rewrite(
            "from pyronaut.http import *\n"
            "def check(ok: bool):\n"
            "    return ok\n"
            "def respond():\n"
            "    return ok('x')\n")
        self.assertIn("return ok\n", code)
        self.assertIn("return HttpResponse.ok('x')", code)

    def test_locals_lambdas_and_comprehensions_shadow(self):
        code, _ = rewrite(
            "from pyronaut.http import *\n"
            "def f():\n"
            "    status = 1\n"
            "    g = lambda ok: ok\n"
            "    values = [CREATED for CREATED in range(3)]\n"
            "    return status, g, values, CREATED\n")
        self.assertIn("status = 1", code)
        self.assertIn("lambda ok: ok", code)
        self.assertIn("[CREATED for CREATED in range(3)]", code)
        self.assertIn("return (status, g, values, HttpStatus.CREATED)", code)

    def test_class_body_bindings_do_not_reach_methods(self):
        code, _ = rewrite(
            "from pyronaut.http import *\n"
            "class C:\n"
            "    ok = True\n"
            "    flag = ok\n"
            "    def m(self):\n"
            "        return ok('x')\n")
        self.assertIn("flag = ok\n", code)
        self.assertIn("return HttpResponse.ok('x')", code)

    def test_a_module_binding_shadows_a_star_imported_member(self):
        code, rewriter = rewrite(
            "from pyronaut.http import *\n"
            "def status(code):\n"
            "    return code\n"
            "x = status(1)\n")
        self.assertIn("x = status(1)", code)
        self.assertIn("HttpResponse", code)

    def test_a_parameter_shadows_a_facade_module_binding(self):
        code, _ = rewrite(
            "from pyronaut import http\n"
            "@http.Get('/')\n"
            "def f(http):\n"
            "    return http.ok\n")
        self.assertIn("@Get('/')", code)
        self.assertIn("return http.ok", code)

    def test_decorators_defaults_and_annotations_use_the_enclosing_scope(self):
        code, _ = rewrite(
            "from pyronaut.http import *\n"
            "def f(status=CREATED, ok: HttpResponse = None):\n"
            "    return ok\n")
        self.assertIn("status=HttpStatus.CREATED", code)
        self.assertIn("ok: HttpResponse=None", code)
        self.assertTrue(code.endswith("return ok"), code)


if __name__ == '__main__':
    unittest.main()
