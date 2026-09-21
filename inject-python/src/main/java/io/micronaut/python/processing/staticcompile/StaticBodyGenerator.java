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

import java.util.ArrayList;
import java.util.HashMap;
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
    private static final ClassTypeDef MATH = ClassTypeDef.of(Math.class);
    private static final TypeDef LONG = TypeDef.Primitive.LONG;
    private static final TypeDef DOUBLE = TypeDef.Primitive.DOUBLE;
    private static final TypeDef BOOLEAN = TypeDef.Primitive.BOOLEAN;

    private final SelfAccess self;
    private final Map<String, VariableDef.MethodParameter> parameters = new HashMap<>();
    private final Map<String, VariableDef.Local> locals = new LinkedHashMap<>();
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
        StatementDef statements = new StaticBodyGenerator(self, methodParameters).statements(body.body());
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
     * @param name A Java type name as the IR spells it
     * @return The SourceGen type
     */
    public static TypeDef type(String name) {
        // a nested type is spelled with a dot in Java source
        return Ir.NONE.equals(name) ? TypeDef.OBJECT : TypeDef.of(name.replace('$', '.'));
    }

    /**
     * @param name A Java class name as the IR spells it, a nested class as {@code Outer$Inner}
     * @return The SourceGen class type
     */
    public static ClassTypeDef classType(String name) {
        return ClassTypeDef.of(name.replace('$', '.'));
    }

    // ---------------------------------------------------------------- statements

    private StatementDef statements(Ir.Body body) {
        List<StatementDef> statements = new ArrayList<>(body.statements().size());
        for (Ir.Statement statement : body.statements()) {
            statements.add(statement(statement));
        }
        return StatementDef.multi(statements);
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
            case Ir.PutSelf put -> self.write(put.property(), type(put.type()), expression(put.value()));
            case Ir.If branch -> {
                ExpressionDef.ConditionExpressionDef condition = condition(branch.test());
                yield branch.orElse() == null
                    ? condition.doIf(statements(branch.then()))
                    : condition.doIfElse(statements(branch.then()), statements(branch.orElse()));
            }
            case Ir.Return ret -> ret.value() == null ? new StatementDef.Return(null) : expression(ret.value()).returning();
            case Ir.Eval eval -> {
                ExpressionDef value = expression(eval.expression());
                if (value instanceof StatementDef asStatement) {
                    yield asStatement;
                }
                yield new StatementDef.DefineAndAssign(new VariableDef.Local("unused" + temporaries++, type(eval.expression().type())), value);
            }
        };
    }

    private VariableDef.Local local(String name) {
        VariableDef.Local variable = locals.get(name);
        if (variable == null) {
            throw new IllegalStateException("Local [" + name + "] is read before it is declared");
        }
        return variable;
    }

    // ---------------------------------------------------------------- expressions

    private ExpressionDef expression(Ir.Expression expression) {
        return switch (expression) {
            case Ir.Const constant -> constant(constant);
            case Ir.Param param -> parameter(param);
            case Ir.LocalRef ref -> local(ref.name());
            case Ir.SelfProperty property -> self.read(property.property(), property.type(), type(property.type()));
            case Ir.InvokeJava call -> invoke(call);
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

    private ExpressionDef.ConditionExpressionDef condition(Ir.Expression test) {
        ExpressionDef value = expression(test);
        return value instanceof ExpressionDef.ConditionExpressionDef condition ? condition : operand(value).isTrue();
    }

    /**
     * A value of one Java type at another: integers widen to long and narrow back exactly,
     * integers widen to double, references are cast.
     */
    private static ExpressionDef cast(ExpressionDef value, String from, String to) {
        if (from.equals(to)) {
            return value;
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
     * How a compiled body reaches the properties of {@code self}: a Java field of the stub, or the
     * member of the Python object behind it.
     */
    public interface SelfAccess {
        /**
         * @param property The property
         * @param typeName The Java type of the property, as the IR spells it
         * @param type     The Java type of the property
         * @return The expression reading it
         */
        ExpressionDef read(String property, String typeName, TypeDef type);

        /**
         * @param property The property
         * @param type     The Java type of the property
         * @param value    The value
         * @return The statement writing it
         */
        StatementDef write(String property, TypeDef type, ExpressionDef value);
    }
}
