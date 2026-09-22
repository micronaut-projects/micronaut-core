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
package io.micronaut.python.processing.staticcompile;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.sourcegen.model.ClassTypeDef;
import io.micronaut.sourcegen.model.ExpressionDef;
import io.micronaut.sourcegen.model.StatementDef;
import io.micronaut.sourcegen.model.TypeDef;
import io.micronaut.sourcegen.model.VariableDef;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns the {@link Ir} of a compiled body into SourceGen statements inside a stub method. The
 * generator is total: every node has a rendering, and only the lowering that produced the IR can
 * refuse a body.
 *
 * @author Graeme Rocher
 * @since 5.3.0
 */
@Experimental
public final class StaticBodyGenerator {

    private static final ClassTypeDef PYTHON_STATIC = ClassTypeDef.of("io.micronaut.context.python.PythonStatic");
    private static final ClassTypeDef PYTHON_CONVERSION = ClassTypeDef.of("io.micronaut.context.python.PythonConversion");
    private static final ClassTypeDef MATH = ClassTypeDef.of(Math.class);
    private static final TypeDef LONG = TypeDef.Primitive.LONG;
    private static final TypeDef DOUBLE = TypeDef.Primitive.DOUBLE;
    private static final TypeDef BOOLEAN = TypeDef.Primitive.BOOLEAN;

    private static final ClassTypeDef ITERATOR = ClassTypeDef.of(Iterator.class);
    private static final ClassTypeDef VALUE_COERCIBLE_TYPE = ClassTypeDef.of("io.micronaut.context.python.ValueCoercible");
    private static final ClassTypeDef POLYGLOT_VALUE_TYPE = ClassTypeDef.of("org.graalvm.polyglot.Value");

    private final SelfAccess self;
    private final Map<String, VariableDef.MethodParameter> parameters = new HashMap<>();
    private final Map<String, VariableDef> locals = new LinkedHashMap<>();
    // the loops enclosing the statement being generated, innermost last: a break or continue sets the
    // flag of its loop, and the statements following it in the body are guarded by the flags
    private final Deque<Loop> loops = new ArrayDeque<>();
    // every name the body uses: a temporary of the generator takes a name none of them has
    private final java.util.Set<String> names = new java.util.HashSet<>();
    private int temporaries;

    private StaticBodyGenerator(SelfAccess self, List<VariableDef.MethodParameter> methodParameters) {
        this.self = self;
        for (VariableDef.MethodParameter parameter : methodParameters) {
            parameters.put(parameter.name(), parameter);
        }
    }

    /**
     * Generates the statements of a compiled body.
     *
     * @param body             The body
     * @param methodParameters The parameters of the stub method, in order
     * @param self             How the properties of {@code self} are reached
     * @return The statements
     */
    public static StatementDef generate(Ir.CompiledBody body, List<VariableDef.MethodParameter> methodParameters, SelfAccess self) {
        return generate(body, methodParameters, self, false);
    }

    /**
     * Generates the statements of a compiled body, counting its entries when tracing.
     *
     * @param body             The body
     * @param methodParameters The parameters of the stub method, in order
     * @param self             How the properties of {@code self} are reached
     * @param trace            Whether the body counts its entries in {@code PythonStatic.entries()}
     * @return The statements
     */
    public static StatementDef generate(Ir.CompiledBody body, List<VariableDef.MethodParameter> methodParameters, SelfAccess self, boolean trace) {
        StaticBodyGenerator generator = new StaticBodyGenerator(self, methodParameters);
        generator.names.addAll(body.parameterNames());
        collectNames(body.body(), generator.names);
        StatementDef statements = generator.statements(body.body());
        if (body.wrapsCheckedExceptions()) {
            // a checked exception of a Java call reaches the caller unchecked, as it would through the bridge
            statements = new StatementDef.Try(statements).doCatch(Exception.class, exception ->
                PYTHON_STATIC.invokeStatic("unchecked", List.of(ClassTypeDef.of(Exception.class)), ClassTypeDef.of(RuntimeException.class), exception).doThrow());
        }
        if (!trace) {
            return statements;
        }
        return StatementDef.multi(
            (StatementDef) PYTHON_STATIC.invokeStatic("entered", List.of(ClassTypeDef.STRING), TypeDef.VOID, ExpressionDef.constant(body.key())),
            statements
        );
    }

    /**
     * The names the statements declare or read, recursively.
     */
    private static void collectNames(Ir node, java.util.Set<String> names) {
        switch (node) {
            case Ir.Body body -> body.statements().forEach(s -> collectNames(s, names));
            case Ir.Local local -> {
                names.add(local.name());
                collectNames(local.value(), names);
            }
            case Ir.Assign assign -> {
                names.add(assign.name());
                collectNames(assign.value(), names);
            }
            case Ir.PutSelf put -> collectNames(put.value(), names);
            case Ir.If branch -> {
                collectNames(branch.test(), names);
                collectNames(branch.then(), names);
                if (branch.orElse() != null) {
                    collectNames(branch.orElse(), names);
                }
            }
            case Ir.Return ret -> {
                if (ret.value() != null) {
                    collectNames(ret.value(), names);
                }
            }
            case Ir.Eval eval -> collectNames(eval.expression(), names);
            case Ir.While loop -> {
                collectNames(loop.test(), names);
                collectNames(loop.body(), names);
            }
            case Ir.ForRange loop -> {
                names.add(loop.variable());
                collectNames(loop.start(), names);
                collectNames(loop.stop(), names);
                collectNames(loop.step(), names);
                collectNames(loop.body(), names);
            }
            case Ir.ForEach loop -> {
                names.add(loop.variable());
                collectNames(loop.iterable(), names);
                collectNames(loop.body(), names);
            }
            case Ir.Throw t -> collectNames(t.exception(), names);
            case Ir.Try t -> {
                collectNames(t.body(), names);
                for (Ir.Catch handler : t.catches()) {
                    if (handler.variable() != null) {
                        names.add(handler.variable());
                    }
                    collectNames(handler.body(), names);
                }
                if (t.finallyBody() != null) {
                    collectNames(t.finallyBody(), names);
                }
            }
            case Ir.Param param -> names.add(param.name());
            case Ir.LocalRef ref -> names.add(ref.name());
            case Ir.InvokeJava call -> {
                if (call.receiver() != null) {
                    collectNames(call.receiver(), names);
                }
                call.arguments().forEach(a -> collectNames(a, names));
            }
            case Ir.InvokeSibling call -> call.arguments().forEach(a -> collectNames(a, names));
            case Ir.InvokePython call -> {
                collectNames(call.receiver(), names);
                call.arguments().forEach(a -> collectNames(a, names));
            }
            case Ir.PythonMember member -> collectNames(member.receiver(), names);
            case Ir.NewJava construction -> construction.arguments().forEach(a -> collectNames(a, names));
            case Ir.Field field -> collectNames(field.receiver(), names);
            case Ir.Binary binary -> {
                collectNames(binary.left(), names);
                collectNames(binary.right(), names);
            }
            case Ir.Unary unary -> collectNames(unary.operand(), names);
            case Ir.Compare compare -> {
                collectNames(compare.left(), names);
                if (compare.right() != null) {
                    collectNames(compare.right(), names);
                }
            }
            case Ir.And and -> {
                collectNames(and.left(), names);
                collectNames(and.right(), names);
            }
            case Ir.Or or -> {
                collectNames(or.left(), names);
                collectNames(or.right(), names);
            }
            case Ir.Conditional conditional -> {
                collectNames(conditional.test(), names);
                collectNames(conditional.then(), names);
                collectNames(conditional.orElse(), names);
            }
            case Ir.Truthy truthy -> collectNames(truthy.operand(), names);
            case Ir.StrJoin join -> join.parts().forEach(p -> collectNames(p, names));
            case Ir.Helper helper -> helper.arguments().forEach(a -> collectNames(a, names));
            case Ir.Cast cast -> collectNames(cast.operand(), names);
            default -> {
            }
        }
    }

    /**
     * A name for a temporary of the generator that no local, parameter or earlier temporary has.
     */
    private String fresh(String base) {
        String name = base;
        while (!names.add(name)) {
            name = name + "_";
        }
        return name;
    }

    /**
     * @param name A Java type name as the IR spells it
     * @return The SourceGen type
     */
    public static TypeDef type(String name) {
        // a nested type is spelled with a dot in Java source; the type arguments the IR carries
        // for the lowering are not part of the generated declarations
        return Ir.NONE.equals(name) ? TypeDef.OBJECT : TypeDef.of(erased(name).replace('$', '.'));
    }

    /**
     * @param name A Java class name as the IR spells it, a nested class as {@code Outer$Inner}
     * @return The SourceGen class type
     */
    public static ClassTypeDef classType(String name) {
        return ClassTypeDef.of(erased(name).replace('$', '.'));
    }

    /**
     * @param name A Java type name, possibly with type arguments
     * @return The name without its type arguments
     */
    public static String erased(String name) {
        int generic = name.indexOf('<');
        return generic < 0 ? name : name.substring(0, generic);
    }

    // ---------------------------------------------------------------- statements

    private StatementDef statements(Ir.Body body) {
        return statements(body.statements());
    }

    /**
     * The statements of a block. Inside a loop, the statements following one that may break or
     * continue the loop run only when it did not: SourceGen has no break or continue node, so both
     * are flags the loop condition and the rest of the body consult.
     */
    private StatementDef statements(List<Ir.Statement> body) {
        Loop loop = loops.peekLast();
        List<StatementDef> statements = new ArrayList<>(body.size());
        for (int i = 0; i < body.size(); i++) {
            Ir.Statement statement = body.get(i);
            statements.add(statement(statement));
            if (loop != null && i < body.size() - 1 && exits(statement, loop.id())) {
                statements.add(loop.notExited().doIf(statements(body.subList(i + 1, body.size()))));
                break;
            }
        }
        return StatementDef.multi(statements);
    }

    /**
     * Whether a statement breaks or continues the given loop, anywhere inside it.
     */
    private static boolean exits(Ir.Statement statement, int loop) {
        return switch (statement) {
            case Ir.Break b -> b.loop() == loop;
            case Ir.Continue c -> c.loop() == loop;
            case Ir.Body body -> body.statements().stream().anyMatch(s -> exits(s, loop));
            case Ir.If branch -> exits(branch.then(), loop) || (branch.orElse() != null && exits(branch.orElse(), loop));
            case Ir.While w -> exits(w.body(), loop);
            case Ir.ForRange f -> exits(f.body(), loop);
            case Ir.ForEach f -> exits(f.body(), loop);
            case Ir.Try t -> exits(t.body(), loop) || t.catches().stream().anyMatch(c -> exits(c.body(), loop)) || (t.finallyBody() != null && exits(t.finallyBody(), loop));
            default -> false;
        };
    }

    private Loop enterLoop(int id, boolean hasBreak, boolean hasContinue, List<StatementDef> before) {
        VariableDef.Local broken = null;
        if (hasBreak) {
            broken = new VariableDef.Local(fresh("broken" + id), BOOLEAN);
            before.add(new StatementDef.DefineAndAssign(broken, ExpressionDef.falseValue()));
        }
        VariableDef.Local continued = hasContinue ? new VariableDef.Local(fresh("continued" + id), BOOLEAN) : null;
        Loop loop = new Loop(id, broken, continued);
        loops.addLast(loop);
        return loop;
    }

    /**
     * The body of a loop: the continue flag reset per iteration, the statements guarded, then the
     * statements run at the end of every iteration that is not broken (the step of a range).
     */
    private StatementDef loopBody(Loop loop, Ir.Body body, List<StatementDef> perIteration) {
        List<StatementDef> statements = new ArrayList<>();
        if (loop.continued() != null) {
            statements.add(new StatementDef.DefineAndAssign(loop.continued(), ExpressionDef.falseValue()));
        }
        statements.add(statements(body));
        statements.addAll(perIteration);
        loops.removeLast();
        return StatementDef.multi(statements);
    }

    private ExpressionDef.ConditionExpressionDef loopCondition(Loop loop, ExpressionDef.ConditionExpressionDef test) {
        return loop.broken() == null ? test : new ExpressionDef.And(loop.broken().isFalse(), test);
    }

    private StatementDef statement(Ir.Statement statement) {
        return switch (statement) {
            case Ir.Body body -> statements(body);
            case Ir.Local local -> {
                VariableDef.Local variable = new VariableDef.Local(local.name(), type(local.type()));
                locals.put(local.name(), variable);
                yield new StatementDef.DefineAndAssign(variable, expression(local.value()));
            }
            case Ir.Assign assign -> local(assign.name()).assign(expression(assign.value()));
            case Ir.PutSelf put -> self.write(put.property(), type(put.type()), expression(put.value()), put.accessor());
            case Ir.If branch -> {
                ExpressionDef.ConditionExpressionDef condition = condition(branch.test());
                yield branch.orElse() == null
                    ? condition.doIf(statements(branch.then()))
                    : condition.doIfElse(statements(branch.then()), statements(branch.orElse()));
            }
            case Ir.Return ret -> ret.value() == null ? new StatementDef.Return(null) : expression(ret.value()).returning();
            case Ir.While loop -> {
                List<StatementDef> out = new ArrayList<>();
                Loop context = enterLoop(loop.loop(), loop.hasBreak(), loop.hasContinue(), out);
                ExpressionDef.ConditionExpressionDef test = loopCondition(context, condition(loop.test()));
                out.add(test.whileLoop(loopBody(context, loop.body(), List.of())));
                yield StatementDef.multi(out);
            }
            case Ir.ForRange loop -> {
                List<StatementDef> out = new ArrayList<>();
                // declared in the enclosing block: a fresh Java name, so a later loop over the same
                // Python name, or a local of that name assigned after the loop, declares its own
                VariableDef.Local variable = new VariableDef.Local(fresh(loop.variable()), LONG);
                VariableDef.Local stop = new VariableDef.Local(fresh("stop" + loop.loop()), LONG);
                VariableDef.Local step = new VariableDef.Local(fresh("step" + loop.loop()), LONG);
                locals.put(loop.variable(), variable);
                out.add(new StatementDef.DefineAndAssign(variable, expression(loop.start())));
                out.add(new StatementDef.DefineAndAssign(stop, expression(loop.stop())));
                out.add(new StatementDef.DefineAndAssign(step, PYTHON_STATIC.invokeStatic("step", List.of(LONG), LONG, expression(loop.step()))));
                Loop context = enterLoop(loop.loop(), loop.hasBreak(), loop.hasContinue(), out);
                ExpressionDef.ConditionExpressionDef test = loopCondition(context, PYTHON_STATIC.invokeStatic("inRange", List.of(LONG, LONG, LONG), BOOLEAN, variable, stop, step).isTrue());
                StatementDef advance = variable.assign(PYTHON_STATIC.invokeStatic("advance", List.of(LONG, LONG, LONG), LONG, variable, stop, step));
                if (context.broken() != null) {
                    advance = context.broken().isFalse().doIf(advance);
                }
                out.add(test.whileLoop(loopBody(context, loop.body(), List.of(advance))));
                yield StatementDef.multi(out);
            }
            case Ir.ForEach loop -> {
                List<StatementDef> out = new ArrayList<>();
                VariableDef.Local iterator = new VariableDef.Local(fresh("iterator" + loop.loop()), ITERATOR);
                out.add(new StatementDef.DefineAndAssign(iterator, expression(loop.iterable()).invoke("iterator", ITERATOR)));
                Loop context = enterLoop(loop.loop(), loop.hasBreak(), loop.hasContinue(), out);
                ExpressionDef.ConditionExpressionDef test = loopCondition(context, iterator.invoke("hasNext", BOOLEAN).isTrue());
                VariableDef.Local variable = new VariableDef.Local(loop.variable(), type(loop.type()));
                locals.put(loop.variable(), variable);
                ExpressionDef element = cast(iterator.invoke("next", TypeDef.OBJECT), "java.lang.Object", loop.elementType());
                StatementDef bind = new StatementDef.DefineAndAssign(variable, cast(element, loop.elementType(), loop.type()));
                StatementDef body = loopBody(context, loop.body(), List.of());
                out.add(test.whileLoop(StatementDef.multi(bind, body)));
                yield StatementDef.multi(out);
            }
            case Ir.Break b -> flag(b.loop(), true).assign(ExpressionDef.trueValue());
            case Ir.Continue c -> flag(c.loop(), false).assign(ExpressionDef.trueValue());
            case Ir.Throw t -> expression(t.exception()).doThrow();
            case Ir.Try t -> {
                StatementDef.Try tried = new StatementDef.Try(statements(t.body()));
                for (Ir.Catch handler : t.catches()) {
                    tried = tried.doCatch(classType(handler.type()), exception -> {
                        if (handler.variable() != null) {
                            locals.put(handler.variable(), exception);
                        }
                        return statements(handler.body());
                    });
                }
                yield t.finallyBody() == null ? tried : tried.doFinally(statements(t.finallyBody()));
            }
            case Ir.Eval eval -> {
                ExpressionDef value = expression(eval.expression());
                if (value instanceof StatementDef asStatement) {
                    yield asStatement;
                }
                yield new StatementDef.DefineAndAssign(new VariableDef.Local(fresh("unused" + temporaries++), type(eval.expression().type())), value);
            }
        };
    }

    private VariableDef local(String name) {
        VariableDef variable = locals.get(name);
        if (variable == null) {
            throw new IllegalStateException("Local [" + name + "] is read before it is declared");
        }
        return variable;
    }

    private VariableDef.Local flag(int loop, boolean broken) {
        for (Iterator<Loop> it = loops.descendingIterator(); it.hasNext();) {
            Loop candidate = it.next();
            if (candidate.id() == loop) {
                VariableDef.Local variable = broken ? candidate.broken() : candidate.continued();
                if (variable == null) {
                    throw new IllegalStateException("Loop " + loop + " declares no " + (broken ? "break" : "continue"));
                }
                return variable;
            }
        }
        throw new IllegalStateException("No enclosing loop " + loop);
    }

    // ---------------------------------------------------------------- expressions

    private ExpressionDef expression(Ir.Expression expression) {
        return switch (expression) {
            case Ir.Const constant -> constant(constant);
            case Ir.Param param -> parameter(param);
            case Ir.LocalRef ref -> local(ref.name());
            case Ir.SelfProperty property -> self.read(property.property(), property.type(), type(property.type()), property.accessor());
            case Ir.InvokeJava call -> invoke(call);
            case Ir.InvokeSibling call -> self.invoke(call.name(), types(call.parameterTypes()), expressions(call.arguments()), call.type(), Ir.VOID.equals(call.type()) ? TypeDef.VOID : type(call.type()), "java".equals(call.dispatch()));
            case Ir.InvokePython call -> self.invokeOn(pythonObject(call.receiver()), call.name(), expressions(call.arguments()), call.type(), Ir.VOID.equals(call.type()) ? TypeDef.VOID : type(call.type()));
            case Ir.PythonMember member -> self.readOf(pythonObject(member.receiver()), member.name(), member.type(), type(member.type()));
            case Ir.NewJava construction -> classType(construction.type()).instantiate(types(construction.parameterTypes()), expressions(construction.arguments()));
            case Ir.StaticField field -> classType(field.owner()).getStaticField(field.name(), type(field.type()));
            case Ir.Field field -> expression(field.receiver()).field(field.name(), type(field.type()));
            case Ir.Binary binary -> binary(binary);
            case Ir.Unary unary -> negation(unary);
            case Ir.Compare compare -> compare(compare);
            case Ir.And and -> new ExpressionDef.And(condition(and.left()), condition(and.right()));
            case Ir.Or or -> new ExpressionDef.Or(condition(or.left()), condition(or.right()));
            case Ir.Conditional conditional -> new ExpressionDef.IfElse(condition(conditional.test()), expression(conditional.then()), expression(conditional.orElse()), type(conditional.type()));
            case Ir.Truthy truthy -> truthy(truthy.operand());
            case Ir.StrJoin join -> join(join);
            case Ir.Helper helper -> PYTHON_STATIC.invokeStatic(helper.name(), argumentTypes(helper.arguments()), type(helper.type()), expressions(helper.arguments()));
            case Ir.Cast cast -> cast(expression(cast.operand()), cast.operand().type(), cast.type());
        };
    }

    private static ExpressionDef constant(Ir.Const constant) {
        Object value = constant.value();
        if (value == null) {
            return ExpressionDef.nullValue();
        }
        return switch (constant.type()) {
            case Ir.LONG -> ExpressionDef.constant(((Number) value).longValue());
            case Ir.DOUBLE -> ExpressionDef.constant(((Number) value).doubleValue());
            case Ir.BOOLEAN -> ExpressionDef.constant(Boolean.TRUE.equals(value));
            default -> ExpressionDef.constant(String.valueOf(value));
        };
    }

    private ExpressionDef parameter(Ir.Param param) {
        VariableDef.MethodParameter parameter = parameters.get(param.name());
        if (parameter == null) {
            throw new IllegalStateException("The stub method has no parameter [" + param.name() + "]");
        }
        return cast(parameter, param.parameterType(), param.type());
    }

    private ExpressionDef invoke(Ir.InvokeJava call) {
        List<TypeDef> parameterTypes = types(call.parameterTypes());
        TypeDef returnType = Ir.VOID.equals(call.type()) ? TypeDef.VOID : type(call.type());
        List<ExpressionDef> arguments = expressions(call.arguments());
        if (call.receiver() == null) {
            return classType(call.owner()).invokeStatic(call.name(), parameterTypes, returnType, arguments);
        }
        return expression(call.receiver()).invoke(call.name(), parameterTypes, returnType, arguments);
    }

    /**
     * An operand of an operator. A conditional expression is grouped through {@code PythonStatic.group}:
     * the source generator renders it without parentheses, and Java's conditional binds weaker than
     * every operator around it, so {@code (a ? b : c) + d} would otherwise come out as {@code a ? b : c + d}.
     */
    private static ExpressionDef operand(ExpressionDef value) {
        if (value instanceof ExpressionDef.IfElse) {
            TypeDef type = value.type();
            return PYTHON_STATIC.invokeStatic("group", List.of(type), type, value);
        }
        return value;
    }

    private ExpressionDef negation(Ir.Unary unary) {
        if ("not".equals(unary.op())) {
            return condition(unary.operand()).isFalse();
        }
        ExpressionDef operand = operand(expression(unary.operand()));
        // exact: negating Long.MIN_VALUE raises where Python would promote
        return Ir.LONG.equals(unary.type())
            ? MATH.invokeStatic("negateExact", List.of(LONG), LONG, operand)
            : operand.math(ExpressionDef.MathUnaryOperation.OpType.NEGATE);
    }

    private ExpressionDef binary(Ir.Binary binary) {
        ExpressionDef left = operand(expression(binary.left()));
        ExpressionDef right = operand(expression(binary.right()));
        if ("concat".equals(binary.op())) {
            return left.stringConcat(right);
        }
        if (Ir.LONG.equals(binary.type())) {
            return switch (binary.op()) {
                case "+" -> MATH.invokeStatic("addExact", List.of(LONG, LONG), LONG, left, right);
                case "-" -> MATH.invokeStatic("subtractExact", List.of(LONG, LONG), LONG, left, right);
                case "*" -> MATH.invokeStatic("multiplyExact", List.of(LONG, LONG), LONG, left, right);
                case "//" -> PYTHON_STATIC.invokeStatic("floorDiv", List.of(LONG, LONG), LONG, left, right);
                case "%" -> PYTHON_STATIC.invokeStatic("floorMod", List.of(LONG, LONG), LONG, left, right);
                case "**" -> PYTHON_STATIC.invokeStatic("power", List.of(LONG, LONG), LONG, left, right);
                default -> throw new IllegalStateException("No integer lowering of the operator " + binary.op());
            };
        }
        return switch (binary.op()) {
            case "+" -> left.math(ExpressionDef.MathBinaryOperation.OpType.ADDITION, right);
            case "-" -> left.math(ExpressionDef.MathBinaryOperation.OpType.SUBTRACTION, right);
            case "*" -> left.math(ExpressionDef.MathBinaryOperation.OpType.MULTIPLICATION, right);
            case "/" -> PYTHON_STATIC.invokeStatic("divide", List.of(DOUBLE, DOUBLE), DOUBLE, left, right);
            case "//" -> PYTHON_STATIC.invokeStatic("floorDiv", List.of(DOUBLE, DOUBLE), DOUBLE, left, right);
            case "%" -> PYTHON_STATIC.invokeStatic("floorMod", List.of(DOUBLE, DOUBLE), DOUBLE, left, right);
            case "**" -> MATH.invokeStatic("pow", List.of(DOUBLE, DOUBLE), DOUBLE, left, right);
            default -> throw new IllegalStateException("No float lowering of the operator " + binary.op());
        };
    }

    private ExpressionDef compare(Ir.Compare compare) {
        ExpressionDef left = operand(expression(compare.left()));
        if ("is None".equals(compare.op())) {
            return left.isNull();
        }
        if ("is not None".equals(compare.op())) {
            return left.isNonNull();
        }
        Ir.Expression rightOperand = compare.right();
        if (rightOperand == null) {
            throw new IllegalStateException("The comparison " + compare.op() + " needs a right operand");
        }
        ExpressionDef right = operand(expression(rightOperand));
        return switch (compare.op()) {
            case "equals" -> left.equalsStructurally(right);
            case "!equals" -> left.notEqualsStructurally(right);
            case "same" -> left.equalsReferentially(right);
            case "!same" -> left.notEqualsReferentially(right);
            case "<" -> left.compare(ExpressionDef.ComparisonOperation.OpType.LESS_THAN, right);
            case "<=" -> left.compare(ExpressionDef.ComparisonOperation.OpType.LESS_THAN_OR_EQUAL, right);
            case ">" -> left.compare(ExpressionDef.ComparisonOperation.OpType.GREATER_THAN, right);
            case ">=" -> left.compare(ExpressionDef.ComparisonOperation.OpType.GREATER_THAN_OR_EQUAL, right);
            case "==" -> left.compare(ExpressionDef.ComparisonOperation.OpType.EQUAL_TO, right);
            case "!=" -> left.compare(ExpressionDef.ComparisonOperation.OpType.NOT_EQUAL_TO, right);
            default -> throw new IllegalStateException("No lowering of the comparison " + compare.op());
        };
    }

    private ExpressionDef truthy(Ir.Expression operand) {
        ExpressionDef value = operand(expression(operand));
        return switch (operand.type()) {
            case Ir.BOOLEAN -> value;
            case Ir.LONG -> value.compare(ExpressionDef.ComparisonOperation.OpType.NOT_EQUAL_TO, ExpressionDef.constant(0L));
            case Ir.DOUBLE -> value.compare(ExpressionDef.ComparisonOperation.OpType.NOT_EQUAL_TO, ExpressionDef.constant(0.0d));
            case Ir.STRING -> PYTHON_STATIC.invokeStatic("truthy", List.of(ClassTypeDef.STRING), BOOLEAN, value);
            default -> PYTHON_STATIC.invokeStatic("truthy", List.of(TypeDef.OBJECT), BOOLEAN, value);
        };
    }

    private ExpressionDef join(Ir.StrJoin join) {
        ExpressionDef result = null;
        for (Ir.Expression part : join.parts()) {
            ExpressionDef rendered = operand(str(part));
            result = result == null ? rendered : result.stringConcat(rendered);
        }
        return result == null ? ExpressionDef.constant("") : result;
    }

    /**
     * The Python string form of a value: a string is itself, a number or a boolean is rendered as
     * Python renders it, anything else through the helper that knows None.
     */
    private ExpressionDef str(Ir.Expression part) {
        ExpressionDef value = expression(part);
        if (part instanceof Ir.Const constant && Ir.STRING.equals(constant.type())) {
            return value;
        }
        return switch (part.type()) {
            case Ir.LONG -> PYTHON_STATIC.invokeStatic("str", List.of(LONG), ClassTypeDef.STRING, value);
            case Ir.DOUBLE -> PYTHON_STATIC.invokeStatic("str", List.of(DOUBLE), ClassTypeDef.STRING, value);
            case Ir.BOOLEAN -> PYTHON_STATIC.invokeStatic("str", List.of(BOOLEAN), ClassTypeDef.STRING, value);
            default -> PYTHON_STATIC.invokeStatic("str", List.of(TypeDef.OBJECT), ClassTypeDef.STRING, value);
        };
    }

    /**
     * The Python object behind a value of a generated class: every generated class is a
     * {@code ValueCoercible}.
     */
    private ExpressionDef pythonObject(Ir.Expression receiver) {
        return expression(receiver).cast(VALUE_COERCIBLE_TYPE).invoke("asPolyglotValue", POLYGLOT_VALUE_TYPE);
    }

    private ExpressionDef.ConditionExpressionDef condition(Ir.Expression test) {
        ExpressionDef value = expression(test);
        return value instanceof ExpressionDef.ConditionExpressionDef condition ? condition : operand(value).isTrue();
    }

    /**
     * A value of one Java type at another: integers widen to long and narrow back exactly,
     * integers widen to double, references are cast.
     */
    private static ExpressionDef cast(ExpressionDef value, String from, String to) {
        String declared = to;
        String source = from;
        from = erased(from);
        to = erased(to);
        if (from.equals(to)) {
            if (!declared.equals(source) && declared.indexOf('<') >= 0 && source.indexOf('<') >= 0) {
                // Java generics are invariant: a value of the erased type with other type arguments
                // reaches the declared type through Object and the raw type, as an unchecked
                // assignment (a cast to the expression's own erased type would be elided)
                return PYTHON_CONVERSION.invokeStatic("asObject", List.of(TypeDef.OBJECT), TypeDef.OBJECT, value).cast(type(to));
            }
            return value;
        }
        if ("java.lang.Object".equals(from)) {
            // an element of a collection or an iterator: unboxed as the host boundary would convert it
            return switch (to) {
                case Ir.LONG -> PYTHON_STATIC.invokeStatic("toLong", List.of(TypeDef.OBJECT), LONG, value);
                case Ir.DOUBLE -> PYTHON_STATIC.invokeStatic("toDouble", List.of(TypeDef.OBJECT), DOUBLE, value);
                case Ir.BOOLEAN -> value.cast(ClassTypeDef.of(Boolean.class));
                default -> value.cast(type(to));
            };
        }
        return switch (to) {
            case Ir.LONG -> value.cast(LONG);
            case Ir.DOUBLE -> value.cast(DOUBLE);
            case "int" -> Ir.LONG.equals(from) ? MATH.invokeStatic("toIntExact", List.of(LONG), TypeDef.Primitive.INT, value) : value.cast(TypeDef.Primitive.INT);
            case "short" -> Ir.LONG.equals(from) ? PYTHON_STATIC.invokeStatic("toShortExact", List.of(LONG), TypeDef.Primitive.SHORT, value) : value.cast(TypeDef.Primitive.SHORT);
            case "byte" -> Ir.LONG.equals(from) ? PYTHON_STATIC.invokeStatic("toByteExact", List.of(LONG), TypeDef.Primitive.BYTE, value) : value.cast(TypeDef.Primitive.BYTE);
            case "float" -> value.cast(TypeDef.Primitive.FLOAT);
            // a boxed number is boxed from its primitive, which javac converts from the narrowed value
            case "java.lang.Integer" -> ClassTypeDef.of(Integer.class).invokeStatic("valueOf", List.of(TypeDef.Primitive.INT), ClassTypeDef.of(Integer.class), cast(value, from, "int"));
            case "java.lang.Long" -> ClassTypeDef.of(Long.class).invokeStatic("valueOf", List.of(LONG), ClassTypeDef.of(Long.class), cast(value, from, Ir.LONG));
            case "java.lang.Short" -> ClassTypeDef.of(Short.class).invokeStatic("valueOf", List.of(TypeDef.Primitive.SHORT), ClassTypeDef.of(Short.class), cast(value, from, "short"));
            case "java.lang.Byte" -> ClassTypeDef.of(Byte.class).invokeStatic("valueOf", List.of(TypeDef.Primitive.BYTE), ClassTypeDef.of(Byte.class), cast(value, from, "byte"));
            case "java.lang.Double" -> ClassTypeDef.of(Double.class).invokeStatic("valueOf", List.of(DOUBLE), ClassTypeDef.of(Double.class), cast(value, from, Ir.DOUBLE));
            case "java.lang.Float" -> ClassTypeDef.of(Float.class).invokeStatic("valueOf", List.of(TypeDef.Primitive.FLOAT), ClassTypeDef.of(Float.class), cast(value, from, "float"));
            case "java.lang.Boolean" -> ClassTypeDef.of(Boolean.class).invokeStatic("valueOf", List.of(BOOLEAN), ClassTypeDef.of(Boolean.class), value);
            default -> value.cast(type(to));
        };
    }

    private List<ExpressionDef> expressions(List<Ir.Expression> expressions) {
        List<ExpressionDef> result = new ArrayList<>(expressions.size());
        for (Ir.Expression expression : expressions) {
            result.add(expression(expression));
        }
        return result;
    }

    private static List<TypeDef> types(List<String> names) {
        List<TypeDef> result = new ArrayList<>(names.size());
        for (String name : names) {
            result.add(type(name));
        }
        return result;
    }

    private static List<TypeDef> argumentTypes(List<Ir.Expression> arguments) {
        List<TypeDef> result = new ArrayList<>(arguments.size());
        for (Ir.Expression argument : arguments) {
            result.add(type(argument.type()));
        }
        return result;
    }

    /**
     * A loop being generated: its flags, when its body breaks or continues it.
     *
     * @param id        The number of the loop
     * @param broken    The flag set by a break, or {@code null}
     * @param continued The flag set by a continue, or {@code null}
     */
    private record Loop(int id, VariableDef.@Nullable Local broken, VariableDef.@Nullable Local continued) {

        /**
         * The condition that the loop was neither broken nor continued.
         */
        ExpressionDef.ConditionExpressionDef notExited() {
            ExpressionDef.ConditionExpressionDef condition = null;
            if (broken != null) {
                condition = broken.isFalse();
            }
            if (continued != null) {
                condition = condition == null ? continued.isFalse() : new ExpressionDef.And(condition, continued.isFalse());
            }
            return condition == null ? ExpressionDef.trueValue().isTrue() : condition;
        }
    }

    /**
     * How a compiled body reaches the properties of {@code self}: a Java field of the stub, or the
     * member of the Python object behind it.
     */
    public interface SelfAccess {
        /**
         * @param property The property
         * @param typeName The Java type of the property, as the IR spells it
         * @param type     The Java type of the property
         * @param accessor Whether the property is a Python {@code @property}, whose getter runs on the
         *                 Python object: never a Java field of the stub
         * @return The expression reading it
         */
        ExpressionDef read(String property, String typeName, TypeDef type, boolean accessor);

        /**
         * @param name           The method of the class to call on {@code self}
         * @param parameterTypes The Java parameter types of the stub method, when dispatched to the stub
         * @param arguments      The arguments, at the parameter types when dispatched to the stub, else boxed
         * @param typeName       The Java return type as the IR spells it, {@code void} for none
         * @param type           The Java return type
         * @param direct         Whether the stub's Java method is called; else the method of the Python object
         * @return The expression calling it
         */
        ExpressionDef invoke(String name, List<TypeDef> parameterTypes, List<ExpressionDef> arguments, String typeName, TypeDef type, boolean direct);

        /**
         * @param value     The Python object of another object of the compilation
         * @param name      The method to invoke on it
         * @param arguments The arguments, boxed
         * @param typeName  The Java return type as the IR spells it, {@code void} for none
         * @param type      The Java return type
         * @return The expression invoking it and converting the result
         */
        ExpressionDef invokeOn(ExpressionDef value, String name, List<ExpressionDef> arguments, String typeName, TypeDef type);

        /**
         * @param value    The Python object of another object of the compilation
         * @param property The attribute to read
         * @param typeName The Java type of the value as the IR spells it
         * @param type     The Java type of the value
         * @return The expression reading and converting it
         */
        ExpressionDef readOf(ExpressionDef value, String property, String typeName, TypeDef type);

        /**
         * @param property The property
         * @param type     The Java type of the property
         * @param value    The value
         * @param accessor Whether the property is a Python {@code @property}, whose setter runs on the Python object
         * @return The statement writing it
         */
        StatementDef write(String property, TypeDef type, ExpressionDef value, boolean accessor);
    }
}
