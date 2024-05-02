package plc.project;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import javax.swing.text.html.Option;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class GeneratorTests {

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void testSource(String test, Ast.Source ast, String expected) {
        test(ast, expected);
    }

    private static Stream<Arguments> testSource() {
        return Stream.of(
                Arguments.of("Hello, World!",
                        // FUN main(): Integer DO
                        //     print("Hello, World!");
                        //     RETURN 0;
                        // END
                        new Ast.Source(
                                Arrays.asList(),
                                Arrays.asList(init(new Ast.Function("main", Arrays.asList(), Arrays.asList(), Optional.of("Integer"), Arrays.asList(
                                        new Ast.Statement.Expression(init(new Ast.Expression.Function("print", Arrays.asList(
                                                init(new Ast.Expression.Literal("Hello, World!"), ast -> ast.setType(Environment.Type.STRING))
                                        )), ast -> ast.setFunction(new Environment.Function("print", "System.out.println", Arrays.asList(Environment.Type.ANY), Environment.Type.NIL, args -> Environment.NIL)))),
                                        new Ast.Statement.Return(init(new Ast.Expression.Literal(BigInteger.ZERO), ast -> ast.setType(Environment.Type.INTEGER)))
                                )), ast -> ast.setFunction(new Environment.Function("main", "main", Arrays.asList(), Environment.Type.INTEGER, args -> Environment.NIL))))
                        ),
                        String.join(System.lineSeparator(),
                                "public class Main {",
                                "",
                                "    public static void main(String[] args) {",
                                "        System.exit(new Main().main());",
                                "    }",
                                "",
                                "    int main() {",
                                "        System.out.println(\"Hello, World!\");",
                                "        return 0;",
                                "    }",
                                "",
                                "}"
                        )
                ),
                Arguments.of("Sum",
                        // VAR x: Integer;
                        // VAR y: Integer = 10;
                        // FUN main(): Integer DO
                        //     RETURN x + y;
                        // END
                        new Ast.Source(
                                Arrays.asList(
                                        init(new Ast.Global("x", "Integer", true, Optional.empty()),
                                                ast -> ast.setVariable(new Environment.Variable("x", "x", Environment.Type.INTEGER, true, Environment.NIL))
                                        ),
                                        init(new Ast.Global("y", "Integer", true, Optional.of(init(new Ast.Expression.Literal(BigInteger.TEN), ast -> ast.setType(Environment.Type.INTEGER)))),
                                                ast -> ast.setVariable(new Environment.Variable("y", "y", Environment.Type.INTEGER, true, Environment.NIL))
                                        )
                                ),
                                Arrays.asList(
                                        init(new Ast.Function("main", Arrays.asList(), Arrays.asList(), Optional.of("Integer"), Arrays.asList(
                                                new Ast.Statement.Return(
                                                        init(new Ast.Expression.Binary("+",
                                                            init(new Ast.Expression.Access(Optional.empty(), "x"),
                                                                    ast -> ast.setVariable(new Environment.Variable("x", "x", Environment.Type.INTEGER, true, Environment.NIL))),
                                                            init(new Ast.Expression.Access(Optional.empty(), "y"),
                                                                    ast -> ast.setVariable(new Environment.Variable("y", "y", Environment.Type.INTEGER, true, Environment.NIL)))
                                                        ), ast -> ast.setType(Environment.Type.BOOLEAN))
                                                ))), ast -> ast.setFunction(new Environment.Function("main", "main", Arrays.asList(), Environment.Type.INTEGER, args -> Environment.NIL))
                                        )
                                )
                        ),
                        String.join(System.lineSeparator(),
                                "public class Main {",
                                "",
                                "    int x;",
                                "    int y = 10;",
                                "",
                                "    public static void main(String[] args) {",
                                "        System.exit(new Main().main());",
                                "    }",
                                "",
                                "    int main() {",
                                "        return x + y;",
                                "    }",
                                "",
                                "}"
                        )
                ),
                Arguments.of("Multiple Functions",
                        // FUN test() DO
                        //     print("Hello, World!");
                        // END
                        // FUN main(): Integer DO
                        //     print("Hello, World!");
                        // END
                        new Ast.Source(
                                Arrays.asList(),
                                Arrays.asList(
                                        init(new Ast.Function("test", Arrays.asList(), Arrays.asList(), Optional.empty(), Arrays.asList(
                                                new Ast.Statement.Expression(init(new Ast.Expression.Function("print", Arrays.asList(
                                                        init(new Ast.Expression.Literal("Hello, World!"), ast -> ast.setType(Environment.Type.STRING))
                                                )), ast -> ast.setFunction(new Environment.Function("print", "System.out.println", Arrays.asList(Environment.Type.NIL), Environment.Type.NIL, args -> Environment.NIL))))
                                        )), ast -> ast.setFunction(new Environment.Function("test", "test", Arrays.asList(), Environment.Type.NIL, args -> Environment.NIL))),
                                        init(new Ast.Function("main", Arrays.asList(), Arrays.asList(), Optional.of("Integer"), Arrays.asList(
                                        new Ast.Statement.Expression(init(new Ast.Expression.Function("print", Arrays.asList(
                                                init(new Ast.Expression.Literal("Hello, World!"), ast -> ast.setType(Environment.Type.STRING))
                                        )), ast -> ast.setFunction(new Environment.Function("print", "System.out.println", Arrays.asList(Environment.Type.ANY), Environment.Type.NIL, args -> Environment.NIL)))),
                                        new Ast.Statement.Return(init(new Ast.Expression.Literal(BigInteger.ZERO), ast -> ast.setType(Environment.Type.INTEGER)))
                                )), ast -> ast.setFunction(new Environment.Function("main", "main", Arrays.asList(), Environment.Type.INTEGER, args -> Environment.NIL))))
                        ),
                        String.join(System.lineSeparator(),
                                "public class Main {",
                                "",
                                "    public static void main(String[] args) {",
                                "        System.exit(new Main().main());",
                                "    }",
                                "",
                                "    Void test() {",
                                "        System.out.println(\"Hello, World!\");",
                                "    }",
                                "",
                                "    int main() {",
                                "        System.out.println(\"Hello, World!\");",
                                "        return 0;",
                                "    }",
                                "",
                                "}"
                        )
                ),
                Arguments.of("Nested",
                        /**
                         * FUN main(): Integer DO
                         *     LET i = 1;
                         *     WHILE i != 100 DO
                         *         SWITCH 3
                         *             CASE 3:
                         *                 print("Fizz");
                         *             DEFAULT
                         *         END
                         *         SWITCH 5
                         *             CASE 0:
                         *                 print("Buzz");
                         *             DEFAULT
                         *         END
                         *         i = i + 1;
                         *     END
                         *     RETURN 0;
                         * END
                         */
                        new Ast.Source(
                                Arrays.asList(),
                                Arrays.asList(
                                        init(new Ast.Function("main", Arrays.asList(), Arrays.asList(), Optional.of("Integer"), Arrays.asList(
                                                init(new Ast.Statement.Declaration("i", Optional.empty(), Optional.of(init(new Ast.Expression.Literal(BigInteger.ONE), ast -> ast.setType(Environment.Type.INTEGER)))),
                                                        ast -> ast.setVariable(new Environment.Variable("i", "i", Environment.Type.INTEGER, true, Environment.NIL))
                                                ),
                                                new Ast.Statement.While(
                                                        init(new Ast.Expression.Binary("!=",
                                                                init(new Ast.Expression.Access(Optional.empty(), "i"), ast -> ast.setVariable(new Environment.Variable("i", "i", Environment.Type.INTEGER, true, Environment.NIL))),
                                                                init(new Ast.Expression.Literal(BigInteger.valueOf(100)), ast -> ast.setType(Environment.Type.INTEGER))
                                                        ), ast -> ast.setType(Environment.Type.BOOLEAN)),
                                                        Arrays.asList(
                                                                new Ast.Statement.Switch(
                                                                        init(new Ast.Expression.Literal(BigInteger.valueOf(3)), ast -> ast.setType(Environment.Type.INTEGER)),
                                                                        Arrays.asList(
                                                                                new Ast.Statement.Case(
                                                                                        Optional.of(init(new Ast.Expression.Literal(BigInteger.valueOf(3)), ast -> ast.setType(Environment.Type.INTEGER))),
                                                                                        Arrays.asList(
                                                                                                new Ast.Statement.Expression(
                                                                                                        init(new Ast.Expression.Function("print", Arrays.asList(init(new Ast.Expression.Literal("Fizz"), ast -> ast.setType(Environment.Type.STRING)))),
                                                                                                                ast -> ast.setFunction(new Environment.Function("print", "System.out.println", Arrays.asList(Environment.Type.ANY), Environment.Type.NIL, args -> Environment.NIL))
                                                                                                        )
                                                                                                )
                                                                                        )
                                                                                ),
                                                                                new Ast.Statement.Case(
                                                                                        Optional.empty(),
                                                                                        Arrays.asList()
                                                                                )
                                                                        )
                                                                ),
                                                                new Ast.Statement.Switch(
                                                                        init(new Ast.Expression.Literal(BigInteger.valueOf(5)), ast -> ast.setType(Environment.Type.INTEGER)),
                                                                        Arrays.asList(
                                                                                new Ast.Statement.Case(
                                                                                        Optional.of(init(new Ast.Expression.Literal(BigInteger.valueOf(0)), ast -> ast.setType(Environment.Type.INTEGER))),
                                                                                        Arrays.asList(
                                                                                                new Ast.Statement.Expression(
                                                                                                        init(new Ast.Expression.Function("print", Arrays.asList(init(new Ast.Expression.Literal("Buzz"), ast -> ast.setType(Environment.Type.STRING)))),
                                                                                                                ast -> ast.setFunction(new Environment.Function("print", "System.out.println", Arrays.asList(Environment.Type.ANY), Environment.Type.NIL, args -> Environment.NIL))
                                                                                                        )
                                                                                                )
                                                                                        )
                                                                                ),
                                                                                new Ast.Statement.Case(
                                                                                        Optional.empty(),
                                                                                        Arrays.asList()
                                                                                )
                                                                        )
                                                                ),
                                                                new Ast.Statement.Assignment(
                                                                        init(new Ast.Expression.Access(Optional.empty(), "i"), ast -> ast.setVariable(new Environment.Variable("i", "i", Environment.Type.INTEGER, true, Environment.NIL))),
                                                                        init(new Ast.Expression.Binary("+",
                                                                                init(new Ast.Expression.Access(Optional.empty(), "i"), ast -> ast.setVariable(new Environment.Variable("i", "i", Environment.Type.INTEGER, true, Environment.NIL))),
                                                                                init(new Ast.Expression.Literal(BigInteger.ONE), ast -> ast.setType(Environment.Type.INTEGER))
                                                                        ), ast -> ast.setType(Environment.Type.INTEGER))
                                                                )
                                                        )
                                                ),
                                                new Ast.Statement.Return(init(new Ast.Expression.Literal(BigInteger.ZERO), ast -> ast.setType(Environment.Type.INTEGER)))
                                            )), ast -> ast.setFunction(new Environment.Function("main", "main", Arrays.asList(), Environment.Type.INTEGER, args -> Environment.NIL))
                                        )
                                )
                        ),
                        String.join(System.lineSeparator(),
                                "public class Main {",
                                "",
                                "    public static void main(String[] args) {",
                                "        System.exit(new Main().main());",
                                "    }",
                                "",
                                "    int main() {",
                                "        int i = 1;",
                                "        while (i != 100) {",
                                "            switch (3) {",
                                "                case 3:",
                                "                    System.out.println(\"Fizz\");",
                                "                    break;",
                                "                default:",
                                "            }",
                                "            switch (5) {",
                                "                case 0:",
                                "                    System.out.println(\"Buzz\");",
                                "                    break;",
                                "                default:",
                                "            }",
                                "            i = i + 1;",
                                "        }",
                                "        return 0;",
                                "    }",
                                "",
                                "}"
                        )
                )
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void testGlobal(String test, Ast.Global ast, String expected) { test(ast, expected); }

    private static Stream<Arguments> testGlobal() {
        return Stream.of(
                Arguments.of("Mutable String",
                        // VAR x: String;
                        init(new Ast.Global("x", "String", true, Optional.empty()),
                                ast -> ast.setVariable(new Environment.Variable("x", "x", Environment.Type.STRING, true, Environment.NIL))
                        ),
                        "String x;"
                ),
                Arguments.of("Mutable Integer",
                        // VAR x: Integer;
                        init(new Ast.Global("x", "Integer", true, Optional.empty()),
                                ast -> ast.setVariable(new Environment.Variable("x", "x", Environment.Type.INTEGER, true, Environment.NIL))
                        ),
                        "int x;"
                ),
                Arguments.of("Mutable With Value",
                        // VAR x: String = "test";
                        init(new Ast.Global("x", "String", true, Optional.of(
                                init(new Ast.Expression.Literal("test"), ast -> ast.setType(Environment.Type.STRING))
                            )),
                            ast -> ast.setVariable(new Environment.Variable("x", "x", Environment.Type.STRING, true, Environment.NIL))
                        ),
                        "String x = \"test\";"
                ),
                Arguments.of("Immutable",
                        // VAL y: Boolean = TRUE && FALSE;
                        init(new Ast.Global("y", "Boolean", false, Optional.of(
                                init(new Ast.Expression.Binary("&&",
                                        init(new Ast.Expression.Literal(Boolean.TRUE), ast -> ast.setType(Environment.Type.BOOLEAN)),
                                        init(new Ast.Expression.Literal(Boolean.FALSE), ast -> ast.setType(Environment.Type.BOOLEAN))
                                ), ast -> ast.setType(Environment.Type.BOOLEAN))
                        )), ast -> ast.setVariable(new Environment.Variable("y", "y", Environment.Type.BOOLEAN, false, Environment.NIL))),
                        "final boolean y = true && false;"
                ),
                Arguments.of("List",
                        // LIST list: Decimal = [1.0, 1.5, 2.0];
                        init(new Ast.Global("list", "Decimal", true, Optional.of(
                                init(new Ast.Expression.PlcList(Arrays.asList(
                                        init(new Ast.Expression.Literal(new BigDecimal("1.0")), ast -> ast.setType(Environment.Type.DECIMAL)),
                                        init(new Ast.Expression.Literal(new BigDecimal("1.5")), ast -> ast.setType(Environment.Type.DECIMAL)),
                                        init(new Ast.Expression.Literal(new BigDecimal("2.0")), ast -> ast.setType(Environment.Type.DECIMAL))
                                )), ast -> ast.setType(Environment.Type.DECIMAL)))),
                                ast -> ast.setVariable(new Environment.Variable("list", "list", Environment.Type.DECIMAL, true, Environment.NIL))
                        ),
                        "double[] list = {1.0, 1.5, 2.0};"
                )
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void testFunction(String test, Ast.Function ast, String expected) { test(ast, expected); }

    private static Stream<Arguments> testFunction() {
        return Stream.of(
                Arguments.of("Void No Arguments",
                        // FUN test() DO
                        //     print("Hello, World!")
                        // END
                        init(new Ast.Function("test", Arrays.asList(), Arrays.asList(), Optional.empty(), Arrays.asList(
                                new Ast.Statement.Expression(init(new Ast.Expression.Function("print", Arrays.asList(
                                        init(new Ast.Expression.Literal("Hello, World!"), ast -> ast.setType(Environment.Type.STRING))
                                )), ast -> ast.setFunction(new Environment.Function("print", "System.out.println", Arrays.asList(Environment.Type.NIL), Environment.Type.NIL, args -> Environment.NIL))))
                        )), ast -> ast.setFunction(new Environment.Function("test", "test", Arrays.asList(), Environment.Type.NIL, args -> Environment.NIL))),
                        String.join(System.lineSeparator(),
                                "Void test() {",
                                "    System.out.println(\"Hello, World!\");",
                                "}"
                        )
                ),
                Arguments.of("Empty Statements",
                        // FUN test() DO
                        // END
                        init(new Ast.Function("test", Arrays.asList(), Arrays.asList(), Optional.empty(), Arrays.asList()),
                                ast -> ast.setFunction(new Environment.Function("test", "test", Arrays.asList(), Environment.Type.NIL, args -> Environment.NIL))),
                        String.join(System.lineSeparator(),
                                "Void test() {}"
                        )
                ),
                Arguments.of("Return Type w/Arguments",
                        // FUN area(radius: Decimal): Decimal DO
                        //     RETURN 3.14 * radius * radius
                        // END
                        init(new Ast.Function("area", Arrays.asList("radius"), Arrays.asList("Decimal"), Optional.of("Decimal"), Arrays.asList(
                                        new Ast.Statement.Return(
                                                init(new Ast.Expression.Binary("*",
                                                        init(new Ast.Expression.Binary("*",
                                                                init(new Ast.Expression.Literal(new BigDecimal("3.14")), ast -> ast.setType(Environment.Type.DECIMAL)),
                                                                init(new Ast.Expression.Access(Optional.empty(), "radius"),
                                                                        ast -> ast.setVariable(new Environment.Variable("radius", "radius", Environment.Type.DECIMAL, true, Environment.NIL))
                                                                )), ast -> ast.setType(Environment.Type.BOOLEAN)
                                                        ),
                                                        init(new Ast.Expression.Access(Optional.empty(), "radius"),
                                                                ast -> ast.setVariable(new Environment.Variable("radius", "radius", Environment.Type.DECIMAL, true, Environment.NIL))
                                                        )
                                                ), ast -> ast.setType(Environment.Type.BOOLEAN))
                                        ))), ast -> ast.setFunction(new Environment.Function("area", "area", Arrays.asList(Environment.Type.DECIMAL), Environment.Type.DECIMAL, args -> Environment.NIL))),
                        String.join(System.lineSeparator(),
                                "double area(double radius) {",
                                "    return 3.14 * radius * radius;",
                                "}"
                        )
                )
        );
    }

    @Test
    void testList() {
        // LIST list: Decimal = [1.0, 1.5, 2.0];
        Ast.Expression.Literal expr1 = new Ast.Expression.Literal(new BigDecimal("1.0"));
        Ast.Expression.Literal expr2 = new Ast.Expression.Literal(new BigDecimal("1.5"));
        Ast.Expression.Literal expr3 = new Ast.Expression.Literal(new BigDecimal("2.0"));
        expr1.setType(Environment.Type.DECIMAL);
        expr2.setType(Environment.Type.DECIMAL);
        expr3.setType(Environment.Type.DECIMAL);

        Ast.Global global = new Ast.Global("list", "Decimal", true, Optional.of(new Ast.Expression.PlcList(Arrays.asList(expr1, expr2, expr3))));
        Ast.Global astList = init(global, ast -> ast.setVariable(new Environment.Variable("list", "list", Environment.Type.DECIMAL, true, Environment.create(Arrays.asList(new Double(1.0), new Double(1.5), new Double(2.0))))));

        String expected = new String("double[] list = {1.0, 1.5, 2.0};");
        test(astList, expected);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void testExpressionStatement(String test, Ast.Statement.Expression ast, String expected) {
        test(ast, expected);
    }

    private static Stream<Arguments> testExpressionStatement() {
        return Stream.of(
                Arguments.of("Function",
                        // log("Hello, World");
                        new Ast.Statement.Expression(
                                init(new Ast.Expression.Function("log", Arrays.asList(
                                    init(new Ast.Expression.Literal("Hello, World"), ast -> ast.setType(Environment.Type.STRING))
                                )), ast -> ast.setFunction(new Environment.Function("log", "log", Arrays.asList(Environment.Type.ANY), Environment.Type.NIL, args -> Environment.NIL)))
                        ),
                        "log(\"Hello, World\");"
                ),
                Arguments.of("Literal",
                        // 1;
                        new Ast.Statement.Expression(
                                init(new Ast.Expression.Literal(BigInteger.ONE), ast -> ast.setType(Environment.Type.INTEGER))
                        ),
                        "1;"
                )
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void testDeclarationStatement(String test, Ast.Statement.Declaration ast, String expected) {
        test(ast, expected);
    }

    private static Stream<Arguments> testDeclarationStatement() {
        return Stream.of(
                Arguments.of("Declaration",
                        // LET name: Integer;
                        init(new Ast.Statement.Declaration("name", Optional.of("Integer"), Optional.empty()), ast -> ast.setVariable(new Environment.Variable("name", "name", Environment.Type.INTEGER, true, Environment.NIL))),
                        "int name;"
                ),
                Arguments.of("Initialization",
                        // LET name = 1.0;
                        init(new Ast.Statement.Declaration("name", Optional.empty(), Optional.of(
                                init(new Ast.Expression.Literal(new BigDecimal("1.0")),ast -> ast.setType(Environment.Type.DECIMAL))
                        )), ast -> ast.setVariable(new Environment.Variable("name", "name", Environment.Type.DECIMAL, true, Environment.NIL))),
                        "double name = 1.0;"
                ),
                Arguments.of("Null",
                        // LET name = null;
                        init(new Ast.Statement.Declaration("name", Optional.empty(), Optional.of(
                                init(new Ast.Expression.Literal(null), ast -> ast.setType(Environment.Type.NIL))
                        )), ast -> ast.setVariable(new Environment.Variable("name", "name", Environment.Type.NIL, true, Environment.NIL))),
                        "Void name = null;"
                )
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void testAssignmentStatement(String test, Ast.Statement.Assignment ast, String expected) {
        test(ast, expected);
    }

    private static Stream<Arguments> testAssignmentStatement() {
        return Stream.of(
                Arguments.of("Variable",
                        // variable = "Hello World";
                        new Ast.Statement.Assignment(
                                init(new Ast.Expression.Access(Optional.empty(), "variable"),
                                        ast -> ast.setVariable(new Environment.Variable("variable", "variable", Environment.Type.ANY, true, Environment.NIL))
                                ),
                                init(new Ast.Expression.Literal("Hello World"), ast -> ast.setType(Environment.Type.STRING))
                        ),
                        "variable = \"Hello World\";"
                )
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void testIfStatement(String test, Ast.Statement.If ast, String expected) {
        test(ast, expected);
    }

    private static Stream<Arguments> testIfStatement() {
        return Stream.of(
                Arguments.of("If",
                        // IF expr DO
                        //     stmt;
                        // END
                        new Ast.Statement.If(
                                init(new Ast.Expression.Access(Optional.empty(), "expr"), ast -> ast.setVariable(new Environment.Variable("expr", "expr", Environment.Type.BOOLEAN, true, Environment.NIL))),
                                Arrays.asList(new Ast.Statement.Expression(init(new Ast.Expression.Access(Optional.empty(), "stmt"), ast -> ast.setVariable(new Environment.Variable("stmt", "stmt", Environment.Type.NIL, true, Environment.NIL))))),
                                Arrays.asList()
                        ),
                        String.join(System.lineSeparator(),
                                "if (expr) {",
                                "    stmt;",
                                "}"
                        )
                ),
                Arguments.of("Else",
                        // IF expr DO
                        //     stmt1;
                        // ELSE
                        //     stmt2;
                        // END
                        new Ast.Statement.If(
                                init(new Ast.Expression.Access(Optional.empty(), "expr"), ast -> ast.setVariable(new Environment.Variable("expr", "expr", Environment.Type.BOOLEAN, true, Environment.NIL))),
                                Arrays.asList(new Ast.Statement.Expression(init(new Ast.Expression.Access(Optional.empty(), "stmt1"), ast -> ast.setVariable(new Environment.Variable("stmt1", "stmt1", Environment.Type.NIL, true, Environment.NIL))))),
                                Arrays.asList(new Ast.Statement.Expression(init(new Ast.Expression.Access(Optional.empty(), "stmt2"), ast -> ast.setVariable(new Environment.Variable("stmt2", "stmt2", Environment.Type.NIL, true, Environment.NIL)))))
                        ),
                        String.join(System.lineSeparator(),
                                "if (expr) {",
                                "    stmt1;",
                                "} else {",
                                "    stmt2;",
                                "}"
                        )
                ),
                Arguments.of("Empty If",
                        // IF expr DO
                        // END
                        new Ast.Statement.If(
                                init(new Ast.Expression.Access(Optional.empty(), "expr"), ast -> ast.setVariable(new Environment.Variable("expr", "expr", Environment.Type.NIL, true, Environment.NIL))),
                                Arrays.asList(),
                                Arrays.asList()
                        ),
                        String.join(System.lineSeparator(),
                                "if (expr) {}"
                        )
                )
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void testSwitchStatement(String test, Ast.Statement.Switch ast, String expected) {
        test(ast, expected);
    }

    private static Stream<Arguments> testSwitchStatement() {
        return Stream.of(
                Arguments.of("Switch",
                        // SWITCH letter
                        //     CASE 'y':
                        //         print("yes");
                        //         letter = 'n';
                        //         break;
                        //     DEFAULT
                        //         print("no");
                        // END
                        new Ast.Statement.Switch(
                                init(new Ast.Expression.Access(Optional.empty(), "letter"), ast -> ast.setVariable(new Environment.Variable("letter", "letter", Environment.Type.CHARACTER, true, Environment.create('y')))),
                                Arrays.asList(
                                        new Ast.Statement.Case(
                                                Optional.of(init(new Ast.Expression.Literal('y'), ast -> ast.setType(Environment.Type.CHARACTER))),
                                                Arrays.asList(
                                                        new Ast.Statement.Expression(
                                                                init(new Ast.Expression.Function("print", Arrays.asList(init(new Ast.Expression.Literal("yes"), ast -> ast.setType(Environment.Type.STRING)))),
                                                                        ast -> ast.setFunction(new Environment.Function("print", "System.out.println", Arrays.asList(Environment.Type.ANY), Environment.Type.NIL, args -> Environment.NIL))
                                                                )
                                                        ),
                                                        new Ast.Statement.Assignment(
                                                                init(new Ast.Expression.Access(Optional.empty(), "letter"), ast -> ast.setVariable(new Environment.Variable("letter", "letter", Environment.Type.CHARACTER, true, Environment.create('y')))),
                                                                init(new Ast.Expression.Literal('n'), ast -> ast.setType(Environment.Type.CHARACTER))
                                                        )
                                                )
                                        ),
                                        new Ast.Statement.Case(
                                                Optional.empty(),
                                                Arrays.asList(
                                                        new Ast.Statement.Expression(
                                                                init(new Ast.Expression.Function("print", Arrays.asList(init(new Ast.Expression.Literal("no"), ast -> ast.setType(Environment.Type.STRING)))),
                                                                        ast -> ast.setFunction(new Environment.Function("print", "System.out.println", Arrays.asList(Environment.Type.ANY), Environment.Type.NIL, args -> Environment.NIL))
                                                                )
                                                        )
                                                )
                                        )
                                )
                        ),
                        String.join(System.lineSeparator(),
                                "switch (letter) {",
                                "    case 'y':",
                                "        System.out.println(\"yes\");",
                                "        letter = 'n';",
                                "        break;",
                                "    default:",
                                "        System.out.println(\"no\");",
                                "}"
                        )
                ),
                Arguments.of("Empty Case Statements",
                        // SWITCH letter
                        //     CASE 'y':
                        //         print("yes");
                        //         letter = 'n';
                        //         break;
                        //     DEFAULT
                        // END
                        new Ast.Statement.Switch(
                                init(new Ast.Expression.Access(Optional.empty(), "letter"), ast -> ast.setVariable(new Environment.Variable("letter", "letter", Environment.Type.CHARACTER, true, Environment.create('y')))),
                                Arrays.asList(
                                        new Ast.Statement.Case(
                                                Optional.of(init(new Ast.Expression.Literal('y'), ast -> ast.setType(Environment.Type.CHARACTER))),
                                                Arrays.asList()
                                        ),
                                        new Ast.Statement.Case(
                                                Optional.empty(),
                                                Arrays.asList()
                                        )
                                )
                        ),
                        String.join(System.lineSeparator(),
                                "switch (letter) {",
                                "    case 'y':",
                                "        break;",
                                "    default:",
                                "}"
                        )
                )
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void testWhileStatement(String test, Ast.Statement.While ast, String expected) { test(ast, expected); }

    private static Stream<Arguments> testWhileStatement() {
        return Stream.of(
                Arguments.of("Empty While",
                        // WHILE TRUE DO END
                        new Ast.Statement.While(
                                init(new Ast.Expression.Literal(Boolean.TRUE), ast -> ast.setType(Environment.Type.BOOLEAN)),
                                Arrays.asList()
                        ),
                        "while (true) {}"
                ),
                Arguments.of("Simple While",
                        // WHILE (0 < 1) DO
                        //     stmt1;
                        //     stmt2;
                        // END
                        new Ast.Statement.While(
                                init(new Ast.Expression.Binary("<",
                                        init(new Ast.Expression.Literal(BigInteger.ZERO), ast -> ast.setType(Environment.Type.INTEGER)),
                                        init(new Ast.Expression.Literal(BigInteger.ONE), ast -> ast.setType(Environment.Type.INTEGER))
                                ), ast -> ast.setType(Environment.Type.BOOLEAN)),
                                Arrays.asList(
                                        new Ast.Statement.Expression(
                                                init(new Ast.Expression.Access(Optional.empty(), "stmt1"),
                                                        ast -> ast.setVariable(new Environment.Variable("stmt1", "stmt1", Environment.Type.NIL, true, Environment.NIL))
                                                )
                                        ),
                                        new Ast.Statement.Expression(
                                                init(new Ast.Expression.Access(Optional.empty(), "stmt2"),
                                                        ast -> ast.setVariable(new Environment.Variable("stmt2", "stmt2", Environment.Type.NIL, true, Environment.NIL))
                                                )
                                        )
                                )
                        ),
                        String.join(System.lineSeparator(),
                            "while (0 < 1) {",
                            "    stmt1;",
                            "    stmt2;",
                            "}"
                        )
                ),
                Arguments.of("Nested Statements",
                        // WHILE condition DO
                        //     IF TRUE DO
                        //         stmt1;
                        //     END
                        //     stmt2;
                        // END
                        new Ast.Statement.While(
                                init(new Ast.Expression.Access(Optional.empty(), "condition"),
                                        ast -> ast.setVariable(new Environment.Variable("condition", "condition", Environment.Type.NIL, true, Environment.NIL))),
                                Arrays.asList(
                                        new Ast.Statement.If(
                                                init(new Ast.Expression.Literal(Boolean.TRUE), ast -> ast.setType(Environment.Type.BOOLEAN)),
                                                Arrays.asList(
                                                        new Ast.Statement.Expression(
                                                                init(new Ast.Expression.Access(Optional.empty(), "stmt1"),
                                                                        ast -> ast.setVariable(new Environment.Variable("stmt1", "stmt1", Environment.Type.NIL, true, Environment.NIL))
                                                                )
                                                        )
                                                ),
                                                Arrays.asList()
                                        ),
                                        new Ast.Statement.Expression(
                                                init(new Ast.Expression.Access(Optional.empty(), "stmt2"),
                                                        ast -> ast.setVariable(new Environment.Variable("stmt2", "stmt2", Environment.Type.NIL, true, Environment.NIL))
                                                )
                                        )
                                )
                        ),
                        String.join(System.lineSeparator(),
                                "while (condition) {",
                                "    if (true) {",
                                "        stmt1;",
                                "    }",
                                "    stmt2;",
                                "}"
                        )
                )
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void testReturnStatement(String test, Ast.Statement.Return ast, String expected) {
        test(ast, expected);
    }

    private static Stream<Arguments> testReturnStatement() {
        return Stream.of(
                Arguments.of("Return",
                        // RETURN 5 * 10
                        new Ast.Statement.Return(
                                init(new Ast.Expression.Binary("*",
                                        init(new Ast.Expression.Literal(BigInteger.valueOf(5)), ast -> ast.setType(Environment.Type.INTEGER)),
                                        init(new Ast.Expression.Literal(BigInteger.TEN), ast -> ast.setType(Environment.Type.INTEGER))
                                ), ast -> ast.setType(Environment.Type.INTEGER))
                        ),
                        "return 5 * 10;"
                )
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void testLiteralExpression(String test, Ast.Expression.Literal ast, String expected) {
        test(ast, expected);
    }

    private static Stream<Arguments> testLiteralExpression() {
        return Stream.of(
                Arguments.of("Integer",
                        // 1
                        init(new Ast.Expression.Literal(BigInteger.ONE), ast -> ast.setType(Environment.Type.INTEGER)),
                        "1"
                ),
                Arguments.of("Decimal",
                        // 1.23
                        init(new Ast.Expression.Literal(new BigDecimal("1.23")), ast -> ast.setType(Environment.Type.DECIMAL)),
                        "1.23"
                ),
                Arguments.of("String",
                        // "hello"
                        init(new Ast.Expression.Literal("hello"), ast -> ast.setType(Environment.Type.STRING)),
                        "\"hello\""
                ),
                Arguments.of("Character",
                        // 'a'
                        init(new Ast.Expression.Literal('a'), ast -> ast.setType(Environment.Type.CHARACTER)),
                        "'a'"
                ),
                Arguments.of("Boolean",
                        // true
                        init(new Ast.Expression.Literal(Boolean.TRUE), ast -> ast.setType(Environment.Type.BOOLEAN)),
                        "true"
                ),
                Arguments.of("Null",
                        // null
                        init(new Ast.Expression.Literal(null), ast -> ast.setType(Environment.Type.NIL)),
                        "null"
                )
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void testGroupExpression(String test, Ast.Expression.Group ast, String expected) {
        test(ast, expected);
    }

    private static Stream<Arguments> testGroupExpression() {
        return Stream.of(
                Arguments.of("Literal",
                        // (1)
                        init(new Ast.Expression.Group(
                                init(new Ast.Expression.Literal(BigInteger.ONE), ast -> ast.setType(Environment.Type.INTEGER))
                        ), ast -> ast.setType(Environment.Type.INTEGER)),
                        "(1)"
                ),
                Arguments.of("Binary",
                        // (1 + 10)
                        init(new Ast.Expression.Group(
                                init(new Ast.Expression.Binary("+",
                                        init(new Ast.Expression.Literal(BigInteger.ONE), ast -> ast.setType(Environment.Type.INTEGER)),
                                        init(new Ast.Expression.Literal(BigInteger.TEN), ast -> ast.setType(Environment.Type.INTEGER))
                                ), ast -> ast.setType(Environment.Type.INTEGER))
                        ), ast -> ast.setType(Environment.Type.INTEGER)),
                        "(1 + 10)"
                )
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void testBinaryExpression(String test, Ast.Expression.Binary ast, String expected) {
        test(ast, expected);
    }

    private static Stream<Arguments> testBinaryExpression() {
        return Stream.of(
                Arguments.of("And",
                        // TRUE && FALSE
                        init(new Ast.Expression.Binary("&&",
                                init(new Ast.Expression.Literal(true), ast -> ast.setType(Environment.Type.BOOLEAN)),
                                init(new Ast.Expression.Literal(false), ast -> ast.setType(Environment.Type.BOOLEAN))
                        ), ast -> ast.setType(Environment.Type.BOOLEAN)),
                        "true && false"
                ),
                Arguments.of("Concatenation",
                        // "Ben" + 10
                        init(new Ast.Expression.Binary("+",
                                init(new Ast.Expression.Literal("Ben"), ast -> ast.setType(Environment.Type.STRING)),
                                init(new Ast.Expression.Literal(BigInteger.TEN), ast -> ast.setType(Environment.Type.INTEGER))
                        ), ast -> ast.setType(Environment.Type.STRING)),
                        "\"Ben\" + 10"
                ),
                Arguments.of("Exp",
                        // 2 ^ 3
                        init(new Ast.Expression.Binary("^",
                                init(new Ast.Expression.Literal(BigInteger.valueOf(2)), ast -> ast.setType(Environment.Type.INTEGER)),
                                init(new Ast.Expression.Literal(BigInteger.valueOf(3)), ast -> ast.setType(Environment.Type.INTEGER))
                        ), ast -> ast.setType(Environment.Type.INTEGER)),
                        "Math.pow(2, 3)"
                ),
                Arguments.of("Exp With Addition",
                        // 2 ^ 3 + 1
                        init(new Ast.Expression.Binary("+",
                            init(new Ast.Expression.Binary("^",
                                    init(new Ast.Expression.Literal(BigInteger.valueOf(2)), ast -> ast.setType(Environment.Type.INTEGER)),
                                    init(new Ast.Expression.Literal(BigInteger.valueOf(3)), ast -> ast.setType(Environment.Type.INTEGER))
                            ), ast -> ast.setType(Environment.Type.INTEGER)),
                            init(new Ast.Expression.Literal(BigInteger.ONE), ast -> ast.setType(Environment.Type.INTEGER))
                        ), ast -> ast.setType(Environment.Type.INTEGER)),
                        "Math.pow(2, 3) + 1"
                )
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void testAccessExpression(String test, Ast.Expression.Access ast, String expected) {
        test(ast, expected);
    }

    private static Stream<Arguments> testAccessExpression() {
        return Stream.of(
                Arguments.of("Variable",
                        // variable
                        init(new Ast.Expression.Access(Optional.empty(), "variable"),
                                ast -> ast.setVariable(new Environment.Variable("variable", "variable", Environment.Type.NIL, true, Environment.NIL))
                        ),
                        "variable"
                ),
                Arguments.of("List Access",
                        // list[expr]
                        init(new Ast.Expression.Access(
                            Optional.of(init(new Ast.Expression.Access(Optional.empty(), "expr"),
                                ast -> ast.setVariable(new Environment.Variable("expr", "expr", Environment.Type.NIL, true, Environment.NIL))
                            )), "list"),
                                ast -> ast.setVariable(new Environment.Variable("list", "list", Environment.Type.INTEGER, true, Environment.create(
                                    init(new Ast.Expression.PlcList(Arrays.asList(init(new Ast.Expression.Literal(BigInteger.ONE), as -> as.setType(Environment.Type.INTEGER)))),
                                            as -> as.setType(Environment.Type.INTEGER)
                                    )
                                )))
                        ),
                        "list[expr]"
                )
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void testFunctionExpression(String test, Ast.Expression.Function ast, String expected) {
        test(ast, expected);
    }

    private static Stream<Arguments> testFunctionExpression() {
        return Stream.of(
                Arguments.of("Print",
                        // print("Hello, World!")
                        init(new Ast.Expression.Function("print", Arrays.asList(
                                init(new Ast.Expression.Literal("Hello, World!"), ast -> ast.setType(Environment.Type.STRING))
                        )), ast -> ast.setFunction(new Environment.Function("print", "System.out.println", Arrays.asList(Environment.Type.ANY), Environment.Type.NIL, args -> Environment.NIL))),
                        "System.out.println(\"Hello, World!\")"
                ),
                Arguments.of("No Arguments",
                        // function()
                        init(new Ast.Expression.Function("function", Arrays.asList()),
                                ast -> ast.setFunction(new Environment.Function("function", "function", Arrays.asList(), Environment.Type.NIL, args -> Environment.NIL))
                        ),
                        "function()"
                ),
                Arguments.of("Multiple Arguments",
                        // f(1, 2, 3)
                        init(new Ast.Expression.Function("f", Arrays.asList(
                                init(new Ast.Expression.Literal(BigInteger.ONE), ast -> ast.setType(Environment.Type.INTEGER)),
                                init(new Ast.Expression.Literal(BigInteger.valueOf(2)), ast -> ast.setType(Environment.Type.INTEGER)),
                                init(new Ast.Expression.Literal(BigInteger.valueOf(3)), ast -> ast.setType(Environment.Type.INTEGER))
                        )), ast -> ast.setFunction(new Environment.Function("f", "f", Arrays.asList(Environment.Type.INTEGER, Environment.Type.INTEGER, Environment.Type.INTEGER), Environment.Type.NIL, args -> Environment.NIL))),
                        "f(1, 2, 3)"
                )
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void testPlcListExpression(String test, Ast.Expression.PlcList ast, String expected) {
        test(ast, expected);
    }

    private static Stream<Arguments> testPlcListExpression() {
        return Stream.of(
                Arguments.of("Decimal List",
                        // [1.0, 1.5, 2.0]
                        init(new Ast.Expression.PlcList(Arrays.asList(
                                init(new Ast.Expression.Literal(new BigDecimal("1.0")), ast -> ast.setType(Environment.Type.DECIMAL)),
                                init(new Ast.Expression.Literal(new BigDecimal("1.5")), ast -> ast.setType(Environment.Type.DECIMAL)),
                                init(new Ast.Expression.Literal(new BigDecimal("2.0")), ast -> ast.setType(Environment.Type.DECIMAL))
                        )), ast -> ast.setType(Environment.Type.DECIMAL)),
                        "{1.0, 1.5, 2.0}"
                ),
                Arguments.of("Empty List",
                        // {}
                        init(new Ast.Expression.PlcList(Arrays.asList()), ast -> ast.setType(Environment.Type.NIL)),
                        "{}"
                ),
                Arguments.of("One Value",
                        // [10]
                        init(new Ast.Expression.PlcList(Arrays.asList(
                                init(new Ast.Expression.Literal(BigInteger.TEN), ast -> ast.setType(Environment.Type.INTEGER))
                        )), ast -> ast.setType(Environment.Type.INTEGER)),
                        "{10}"
                )
        );
    }

    /**
     * Helper function for tests, using a StringWriter as the output stream.
     */
    private static void test(Ast ast, String expected) {
        StringWriter writer = new StringWriter();
        new Generator(new PrintWriter(writer)).visit(ast);
        Assertions.assertEquals(expected, writer.toString());
    }

    /**
     * Runs a callback on the given value, used for inline initialization.
     */
    private static <T> T init(T value, Consumer<T> initializer) {
        initializer.accept(value);
        return value;
    }

}
