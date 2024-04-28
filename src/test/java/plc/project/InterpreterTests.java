package plc.project;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

final class InterpreterTests {

    @ParameterizedTest
    @MethodSource
    void testSource(String test, Ast.Source ast, Object expected) {
        test(ast, expected, new Scope(null));
    }

    private static Stream<Arguments> testSource() {
        return Stream.of(
                // FUN main() DO RETURN 0; END
                Arguments.of("Main", new Ast.Source(
                        Arrays.asList(),
                        Arrays.asList(new Ast.Function("main", Arrays.asList(), Arrays.asList(
                                new Ast.Statement.Return(new Ast.Expression.Literal(BigInteger.ZERO)))
                        ))
                ), BigInteger.ZERO),
                // VAR x = 1; VAR y = 10; FUN main() DO x + y; END
                Arguments.of("Globals & No Return", new Ast.Source(
                        Arrays.asList(
                                new Ast.Global("x", true, Optional.of(new Ast.Expression.Literal(BigInteger.ONE))),
                                new Ast.Global("y", true, Optional.of(new Ast.Expression.Literal(BigInteger.TEN)))
                        ),
                        Arrays.asList(new Ast.Function("main", Arrays.asList(), Arrays.asList(
                                new Ast.Statement.Expression(new Ast.Expression.Binary("+",
                                        new Ast.Expression.Access(Optional.empty(), "x"),
                                        new Ast.Expression.Access(Optional.empty(), "y")))
                        )))
                ), Environment.NIL.getValue()
                ),
//              Defining log as => FUN log(z) DO print(z) END
//              VAR x = 1; FUN main() DO log(x); END
                Arguments.of("Global access", new Ast.Source(
                        Arrays.asList(
                                new Ast.Global("x", true, Optional.of(new Ast.Expression.Literal(BigInteger.ONE)))
                        ),
                        Arrays.asList(new Ast.Function("main", Arrays.asList(), Arrays.asList(
                                new Ast.Statement.Expression(
                                        new Ast.Expression.Function("log", Arrays.asList(
                                                new Ast.Expression.Access(Optional.empty(), "x"))
                                        )))
                                ),
                                new Ast.Function("log", Arrays.asList("x"), Arrays.asList(
                                        new Ast.Statement.Expression(
                                                new Ast.Expression.Function("print", Arrays.asList(
                                                        new Ast.Expression.Access(Optional.empty(), "x"))
                                                ))
                                    )
                                )
                        )
                ), Environment.NIL.getValue()
                )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testGlobal(String test, Ast.Global ast, Object expected) {
        Scope scope = test(ast, Environment.NIL.getValue(), new Scope(null));
        Assertions.assertEquals(expected, scope.lookupVariable(ast.getName()).getValue().getValue());
    }

    private static Stream<Arguments> testGlobal() {
        return Stream.of(
                // VAR name;
                // Failing Canvas test. Declaration: Unexpected java.lang.NullPointerException (null)
                Arguments.of("Mutable", new Ast.Global("name", true, Optional.empty()), Environment.NIL.getValue()),
                // VAL name = 1;
                // Failing Canvas test. Declaration: Unexpected java.lang.NullPointerException (null)
                Arguments.of("Immutable", new Ast.Global("name", false, Optional.of(new Ast.Expression.Literal(BigInteger.ONE))), BigInteger.ONE)
                // List initialization. List Initialization: LIST list = [2, 4, 8];

        );
    }

    @Test
    void testList() {
        // LIST list = [1, 5, 10];
        List<Object> expected = Arrays.asList(BigInteger.ONE, BigInteger.valueOf(5), BigInteger.TEN);

        List<Ast.Expression> values = Arrays.asList(new Ast.Expression.Literal(BigInteger.ONE),
                                                    new Ast.Expression.Literal(BigInteger.valueOf(5)),
                                                    new Ast.Expression.Literal(BigInteger.TEN));

        Optional<Ast.Expression> value = Optional.of(new Ast.Expression.PlcList(values));
        Ast.Global ast = new Ast.Global("list", true, value);

        Scope scope = test(ast, Environment.NIL.getValue(), new Scope(null));
        Assertions.assertEquals(expected, scope.lookupVariable(ast.getName()).getValue().getValue());
    }

    @ParameterizedTest
    @MethodSource
    void testFunction(String test, Ast.Function ast, List<Environment.PlcObject> args, Object expected) {
        Scope scope = test(ast, Environment.NIL.getValue(), new Scope(null));
        scope.defineVariable("x", true, Environment.create(BigInteger.valueOf(10)));
        Assertions.assertEquals(expected, scope.lookupFunction(ast.getName(), args.size()).invoke(args).getValue());
    }

    private static Stream<Arguments> testFunction() {
        return Stream.of(
                // FUN main() DO RETURN 0; END
                Arguments.of("Main",
                        new Ast.Function("main", Arrays.asList(), Arrays.asList(
                                new Ast.Statement.Return(new Ast.Expression.Literal(BigInteger.ZERO)))
                        ),
                        Arrays.asList(),
                        BigInteger.ZERO
                ),
                // FUN square(x) DO RETURN x * x; END
                Arguments.of("Arguments",
                        new Ast.Function("square", Arrays.asList("x"), Arrays.asList(
                                new Ast.Statement.Return(new Ast.Expression.Binary("*",
                                        new Ast.Expression.Access(Optional.empty(), "x"),
                                        new Ast.Expression.Access(Optional.empty(), "x")
                                ))
                        )),
                        Arrays.asList(Environment.create(BigInteger.TEN)),
                        BigInteger.valueOf(100)
                )
        );
    }

    @Test
    void testExpressionStatement() {
        // print("Hello, World!");
        PrintStream sysout = System.out;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out));
        try {
            test(new Ast.Statement.Expression(
                    new Ast.Expression.Function("print", Arrays.asList(new Ast.Expression.Literal("Hello, World!")))
            ), Environment.NIL.getValue(), new Scope(null));
            Assertions.assertEquals("Hello, World!" + System.lineSeparator(), out.toString());
        } finally {
            System.setOut(sysout);
        }
    }

    @Test
    void testExpressionStatementAddition() {
        // log(1) + log(2);
        Scope scope = new Scope(null);
        StringWriter writer = new StringWriter();
        scope.defineFunction("log", 1, args -> {
            writer.write(String.valueOf(args.get(0).getValue()));
            return args.get(0);
        });

        test(new Ast.Statement.Expression(
                new Ast.Expression.Binary("+",
                        new Ast.Expression.Function("log", Arrays.asList(new Ast.Expression.Literal(BigInteger.ONE))),
                        new Ast.Expression.Function("log", Arrays.asList(new Ast.Expression.Literal(BigInteger.valueOf(2)))
        ))), Environment.NIL.getValue(), scope);
        Assertions.assertEquals("12", writer.toString());
    }

    @Test
    void testExpressionStatementSubtraction() {
        // log(1) - log(2);
        Scope scope = new Scope(null);
        StringWriter writer = new StringWriter();
        scope.defineFunction("log", 1, args -> {
            writer.write(String.valueOf(args.get(0).getValue()));
            return args.get(0);
        });

        test(new Ast.Statement.Expression(
                new Ast.Expression.Binary("-",
                        new Ast.Expression.Function("log", Arrays.asList(new Ast.Expression.Literal(BigInteger.ONE))),
                        new Ast.Expression.Function("log", Arrays.asList(new Ast.Expression.Literal(BigInteger.valueOf(2)))
                        ))), Environment.NIL.getValue(), scope);
        Assertions.assertEquals("12", writer.toString());
    }

    @Test
    void testExpressionStatementMultiplication() {
        // log(1) * log(2);
        Scope scope = new Scope(null);
        StringWriter writer = new StringWriter();
        scope.defineFunction("log", 1, args -> {
            writer.write(String.valueOf(args.get(0).getValue()));
            return args.get(0);
        });

        test(new Ast.Statement.Expression(
                new Ast.Expression.Binary("*",
                        new Ast.Expression.Function("log", Arrays.asList(new Ast.Expression.Literal(BigInteger.ONE))),
                        new Ast.Expression.Function("log", Arrays.asList(new Ast.Expression.Literal(BigInteger.valueOf(2)))
                        ))), Environment.NIL.getValue(), scope);
        Assertions.assertEquals("12", writer.toString());
    }

    @Test
    void testExpressionStatementDivision() {
        // log(1) / log(2);
        Scope scope = new Scope(null);
        StringWriter writer = new StringWriter();
        scope.defineFunction("log", 1, args -> {
            writer.write(String.valueOf(args.get(0).getValue()));
            return args.get(0);
        });

        test(new Ast.Statement.Expression(
                new Ast.Expression.Binary("/",
                        new Ast.Expression.Function("log", Arrays.asList(new Ast.Expression.Literal(BigInteger.ONE))),
                        new Ast.Expression.Function("log", Arrays.asList(new Ast.Expression.Literal(BigInteger.valueOf(2)))
                        ))), Environment.NIL.getValue(), scope);
        Assertions.assertEquals("12", writer.toString());
    }

    @Test
    void testExpressionStatementExp() {
        // log(1) ^ log(2);
        Scope scope = new Scope(null);
        StringWriter writer = new StringWriter();
        scope.defineFunction("log", 1, args -> {
            writer.write(String.valueOf(args.get(0).getValue()));
            return args.get(0);
        });

        test(new Ast.Statement.Expression(
                new Ast.Expression.Binary("^",
                        new Ast.Expression.Function("log", Arrays.asList(new Ast.Expression.Literal(BigInteger.ONE))),
                        new Ast.Expression.Function("log", Arrays.asList(new Ast.Expression.Literal(BigInteger.valueOf(2)))
                        ))), Environment.NIL.getValue(), scope);
        Assertions.assertEquals("12", writer.toString());
    }

    @Test
    void testExpressionStatementGT() {
        // log(1) > log(2);
        Scope scope = new Scope(null);
        StringWriter writer = new StringWriter();
        scope.defineFunction("log", 1, args -> {
            writer.write(String.valueOf(args.get(0).getValue()));
            return args.get(0);
        });

        test(new Ast.Statement.Expression(
                new Ast.Expression.Binary(">",
                        new Ast.Expression.Function("log", Arrays.asList(new Ast.Expression.Literal(BigInteger.ONE))),
                        new Ast.Expression.Function("log", Arrays.asList(new Ast.Expression.Literal(BigInteger.valueOf(2)))
                        ))), Environment.NIL.getValue(), scope);
        Assertions.assertEquals("12", writer.toString());
    }

    @ParameterizedTest
    @MethodSource
    void testDeclarationStatement(String test, Ast.Statement.Declaration ast, Object expected) {
        Scope scope = test(ast, Environment.NIL.getValue(), new Scope(null));
        Assertions.assertEquals(expected, scope.lookupVariable(ast.getName()).getValue().getValue());
    }

    private static Stream<Arguments> testDeclarationStatement() {
        return Stream.of(
                // LET name;
                Arguments.of("Declaration",
                        new Ast.Statement.Declaration("name", Optional.empty()),
                        Environment.NIL.getValue()
                ),
                // LET name = 1;
                Arguments.of("Initialization",
                        new Ast.Statement.Declaration("name", Optional.of(new Ast.Expression.Literal(BigInteger.ONE))),
                        BigInteger.ONE
                )
        );
    }

    @Test
    void testVariableAssignmentStatement() {
        // variable = 1;
        Scope scope = new Scope(null);
        scope.defineVariable("variable", true, Environment.create("variable"));
        test(new Ast.Statement.Assignment(
                new Ast.Expression.Access(Optional.empty(),"variable"),
                new Ast.Expression.Literal(BigInteger.ONE)
        ), Environment.NIL.getValue(), scope);
        Assertions.assertEquals(BigInteger.ONE, scope.lookupVariable("variable").getValue().getValue());
    }

    @Test
    void testImmutableAssignment() {
        // immutable = 10;
        Scope scope = new Scope(null);
        scope.defineVariable("immutable", false, Environment.NIL);
        RuntimeException ex = Assertions.assertThrows(RuntimeException.class, () ->
            test(new Ast.Statement.Assignment(
                    new Ast.Expression.Access(Optional.empty(), "immutable"),
                    new Ast.Expression.Literal(BigInteger.TEN)
            ), Environment.NIL.getValue(), scope)
        );
        Assertions.assertEquals("java.lang.RuntimeException: Immutable variable", ex.getMessage());
    }

    @Test
    void testListAssignmentStatement() {
        // list[2] = 3;
        List<Object> expected = Arrays.asList(BigInteger.ONE, BigInteger.valueOf(5), BigInteger.valueOf(3));
        List<Object> list = Arrays.asList(BigInteger.ONE, BigInteger.valueOf(5), BigInteger.TEN);
        Scope scope = new Scope(null);
        scope.defineVariable("list", true, Environment.create(list));
        test(new Ast.Statement.Assignment(
                new Ast.Expression.Access(Optional.of(new Ast.Expression.Literal(BigInteger.valueOf(2))), "list"),
                new Ast.Expression.Literal(BigInteger.valueOf(3))
        ), Environment.NIL.getValue(), scope);

        Assertions.assertEquals(expected, scope.lookupVariable("list").getValue().getValue());
    }

    @ParameterizedTest
    @MethodSource
    void testIfStatement(String test, Ast.Statement.If ast, Object expected) {
        Scope scope = new Scope(null);
        scope.defineVariable("num", true, Environment.NIL);
        test(ast, Environment.NIL.getValue(), scope);
        Assertions.assertEquals(expected, scope.lookupVariable("num").getValue().getValue());
    }

    private static Stream<Arguments> testIfStatement() {
        return Stream.of(
                // IF TRUE DO num = 1; END
                Arguments.of("True Condition",
                        new Ast.Statement.If(
                                new Ast.Expression.Literal(true),
                                Arrays.asList(new Ast.Statement.Assignment(new Ast.Expression.Access(Optional.empty(),"num"), new Ast.Expression.Literal(BigInteger.ONE))),
                                Arrays.asList()
                        ),
                        BigInteger.ONE
                ),
                // IF FALSE DO ELSE num = 10; END
                Arguments.of("False Condition",
                        new Ast.Statement.If(
                                new Ast.Expression.Literal(false),
                                Arrays.asList(),
                                Arrays.asList(new Ast.Statement.Assignment(new Ast.Expression.Access(Optional.empty(),"num"), new Ast.Expression.Literal(BigInteger.TEN)))
                        ),
                        BigInteger.TEN
                ),
                // IF 1 == 1 DO num = 10; END
                Arguments.of("Binary Condition",
                        new Ast.Statement.If(
                                new Ast.Expression.Binary("==", new Ast.Expression.Literal(BigInteger.ONE), new Ast.Expression.Literal(BigInteger.ONE)),
                                Arrays.asList(new Ast.Statement.Assignment(new Ast.Expression.Access(Optional.empty(), "num"), new Ast.Expression.Literal(BigInteger.TEN))),
                                Arrays.asList()
                        ),
                        BigInteger.TEN
                ),
                // IF (0 < 1) DO num = 10; END
                Arguments.of("Binary Condition",
                        new Ast.Statement.If(
                                new Ast.Expression.Group(
                                new Ast.Expression.Binary("==", new Ast.Expression.Literal(BigInteger.ONE), new Ast.Expression.Literal(BigInteger.ONE))),
                                Arrays.asList(new Ast.Statement.Assignment(new Ast.Expression.Access(Optional.empty(), "num"), new Ast.Expression.Literal(BigInteger.TEN))),
                                Arrays.asList()
                        ),
                        BigInteger.TEN
                )
        );
    }

    @Test
    void testSwitchStatement() {
        // SWITCH letter CASE 'y': print("yes"); letter = 'n'; DEFAULT: print("no"); END
        Scope scope = new Scope(null);
        scope.defineVariable("letter", true, Environment.create(new Character('y')));

        List<Ast.Statement> statements = Arrays.asList(
                new Ast.Statement.Expression(new Ast.Expression.Function("print", Arrays.asList(new Ast.Expression.Literal("yes")))),
                new Ast.Statement.Assignment(new Ast.Expression.Access(Optional.empty(), "letter"),
                                             new Ast.Expression.Literal(new Character('n')))
        );

        List<Ast.Statement.Case> cases = Arrays.asList(
                new Ast.Statement.Case(Optional.of(new Ast.Expression.Literal(new Character('y'))), statements),
                new Ast.Statement.Case(Optional.empty(), Arrays.asList(new Ast.Statement.Expression(new Ast.Expression.Function("print", Arrays.asList(new Ast.Expression.Literal("no"))))))
        );

        Ast.Statement.Switch ast = new Ast.Statement.Switch(new Ast.Expression.Access(Optional.empty(), "letter"), cases);

        PrintStream sysout = System.out;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out));
        try {
            test(ast, Environment.NIL.getValue(), scope);
            Assertions.assertEquals("yes" + System.lineSeparator(), out.toString());
        } finally {
            System.setOut(sysout);
        }

        Assertions.assertEquals(new Character('n'), scope.lookupVariable("letter").getValue().getValue());
    }

    @Test
    void testDefaultSwitchCase() {
        // SWITCH letter CASE 'y': print("yes"); DEFAULT print("no"); END
        Scope scope = new Scope(null);
        scope.defineVariable("letter", true, Environment.create(new Character('n')));

        List<Ast.Statement> statements = Arrays.asList(
                new Ast.Statement.Expression(new Ast.Expression.Function("print", Arrays.asList(new Ast.Expression.Literal("yes"))))
        );

        List<Ast.Statement.Case> cases = Arrays.asList(
                new Ast.Statement.Case(Optional.of(new Ast.Expression.Literal(new Character('y'))), statements),
                new Ast.Statement.Case(Optional.empty(), Arrays.asList(new Ast.Statement.Expression(new Ast.Expression.Function("print", Arrays.asList(new Ast.Expression.Literal("no"))))))
        );

        Ast.Statement.Switch ast = new Ast.Statement.Switch(new Ast.Expression.Access(Optional.empty(), "letter"), cases);

        PrintStream sysout = System.out;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out));
        try {
            test(ast, Environment.NIL.getValue(), scope);
            Assertions.assertEquals("no" + System.lineSeparator(), out.toString());
        } finally {
            System.setOut(sysout);
        }
    }

    @Test
    void testWhileStatement() {
        // WHILE num < 10 DO num = num + 1; END
        Scope scope = new Scope(null);
        scope.defineVariable("num", true, Environment.create(BigInteger.ZERO));
        test(new Ast.Statement.While(
                new Ast.Expression.Binary("<",
                        new Ast.Expression.Access(Optional.empty(),"num"),
                        new Ast.Expression.Literal(BigInteger.TEN)
                ),
                Arrays.asList(new Ast.Statement.Assignment(
                        new Ast.Expression.Access(Optional.empty(),"num"),
                        new Ast.Expression.Binary("+",
                                new Ast.Expression.Access(Optional.empty(),"num"),
                                new Ast.Expression.Literal(BigInteger.ONE)
                        )
                ))
        ),Environment.NIL.getValue(), scope);
        Assertions.assertEquals(BigInteger.TEN, scope.lookupVariable("num").getValue().getValue());
    }

    @ParameterizedTest
    @MethodSource
    void testLiteralExpression(String test, Ast ast, Object expected) {
        test(ast, expected, new Scope(null));
    }

    private static Stream<Arguments> testLiteralExpression() {
        return Stream.of(
                // NIL
                Arguments.of("Nil", new Ast.Expression.Literal(null), Environment.NIL.getValue()), //remember, special case
                // TRUE
                Arguments.of("Boolean", new Ast.Expression.Literal(true), true),
                // 1
                Arguments.of("Integer", new Ast.Expression.Literal(BigInteger.ONE), BigInteger.ONE),
                // 1.0
                Arguments.of("Decimal", new Ast.Expression.Literal(BigDecimal.ONE), BigDecimal.ONE),
                // 'c'
                Arguments.of("Character", new Ast.Expression.Literal('c'), 'c'),
                // "string"
                Arguments.of("String", new Ast.Expression.Literal("string"), "string")
        );
    }

    @ParameterizedTest
    @MethodSource
    void testGroupExpression(String test, Ast ast, Object expected) {
        test(ast, expected, new Scope(null));
    }

    private static Stream<Arguments> testGroupExpression() {
        return Stream.of(
                // (1)
                Arguments.of("Literal", new Ast.Expression.Group(new Ast.Expression.Literal(BigInteger.ONE)), BigInteger.ONE),
                // (1 + 10)
                Arguments.of("Binary",
                        new Ast.Expression.Group(new Ast.Expression.Binary("+",
                                new Ast.Expression.Literal(BigInteger.ONE),
                                new Ast.Expression.Literal(BigInteger.TEN)
                        )),
                        BigInteger.valueOf(11)
                )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testBinaryExpression(String test, Ast ast, Object expected) {
        test(ast, expected, new Scope(null));
    }

    private static Stream<Arguments> testBinaryExpression() {
        return Stream.of(
                // TRUE && FALSE
                Arguments.of("And",
                        new Ast.Expression.Binary("&&",
                                new Ast.Expression.Literal(true),
                                new Ast.Expression.Literal(false)
                        ),
                        false
                ),
                // TRUE || undefined
                Arguments.of("Or (Short Circuit)",
                        new Ast.Expression.Binary("||",
                                new Ast.Expression.Literal(true),
                                new Ast.Expression.Access(Optional.empty(), "undefined")
                        ),
                        true
                ),
                // 1 < 10
                Arguments.of("Less Than",
                        new Ast.Expression.Binary("<",
                                new Ast.Expression.Literal(BigInteger.ONE),
                                new Ast.Expression.Literal(BigInteger.TEN)
                        ),
                        true
                ),
                // 1 == 10
                Arguments.of("Equal",
                        new Ast.Expression.Binary("==",
                                new Ast.Expression.Literal(BigInteger.ONE),
                                new Ast.Expression.Literal(BigInteger.TEN)
                        ),
                        false
                ),
                // "a" + "b"
                Arguments.of("Concatenation",
                        new Ast.Expression.Binary("+",
                                new Ast.Expression.Literal("a"),
                                new Ast.Expression.Literal("b")
                        ),
                        "ab"
                ),
                // 1 + 10
                Arguments.of("Addition",
                        new Ast.Expression.Binary("+",
                                new Ast.Expression.Literal(BigInteger.ONE),
                                new Ast.Expression.Literal(BigInteger.TEN)
                        ),
                        BigInteger.valueOf(11)
                ),
                // 1.2 / 3.4
                Arguments.of("Division",
                        new Ast.Expression.Binary("/",
                                new Ast.Expression.Literal(new BigDecimal("1.2")),
                                new Ast.Expression.Literal(new BigDecimal("3.4"))
                        ),
                        new BigDecimal("0.4")
                ),
                // FALSE && undefined
                Arguments.of("And (Short Circuit)",
                        new Ast.Expression.Binary("&&",
                                new Ast.Expression.Literal(Boolean.FALSE),
                                new Ast.Expression.Access(Optional.empty(), "undefined")
                        ),
                        false
                ),
                // NIL == NIL
                Arguments.of("Nil Equals",
                        new Ast.Expression.Binary("==",
                                new Ast.Expression.Literal(null),
                                new Ast.Expression.Literal(null)
                        ),
                        true
                ),
                // 1 != 10
                Arguments.of("Not Equal",
                        new Ast.Expression.Binary("!=",
                                new Ast.Expression.Literal(BigInteger.ONE),
                                new Ast.Expression.Literal(BigInteger.TEN)
                        ),
                        true
                ),
                // 1 != "1"
                Arguments.of("Distinct Types",
                        new Ast.Expression.Binary("!=",
                                new Ast.Expression.Literal(BigInteger.ONE),
                                new Ast.Expression.Literal("1")
                        ),
                        true
                )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testAccessExpression(String test, Ast ast, Object expected) {
        Scope scope = new Scope(null);
        scope.defineVariable("variable", true, Environment.create("variable"));
        List<Object> list = Arrays.asList(BigInteger.ONE, BigInteger.valueOf(5), BigInteger.TEN);
        scope.defineVariable("list", true, Environment.create(list));
        test(ast, expected, scope);
    }

    private static Stream<Arguments> testAccessExpression() {
        return Stream.of(
                // variable
                Arguments.of("Variable",
                    new Ast.Expression.Access(Optional.empty(), "variable"),
                    "variable"
                ),
                // list[1], scope={list = [1, 5, 10]} => 5
                Arguments.of("Access", new Ast.Expression.Access(Optional.of(
                    new Ast.Expression.Literal(BigInteger.ONE)), "list"),
                    BigInteger.valueOf(5)
                )
        );
    }

    @Test
    void testListAccessExpression() {
        // list[1]
        List<Object> list = Arrays.asList(BigInteger.ONE, BigInteger.valueOf(5), BigInteger.TEN);

        Scope scope = new Scope(null);
        scope.defineVariable("list", true, Environment.create(list));
        test(new Ast.Expression.Access(Optional.of(new Ast.Expression.Literal(BigInteger.valueOf(1))), "list"), BigInteger.valueOf(5), scope);
    }

    @ParameterizedTest
    @MethodSource
    void testFunctionExpression(String test, Ast ast, Object expected) {
        Scope scope = new Scope(null);
        scope.defineFunction("function", 0, args -> Environment.create("function"));
        scope.defineFunction("print", 1, args -> Environment.create("Hello, World!"));
        scope.defineFunction("add", 2, args -> Environment.create(BigInteger.ONE.add(BigInteger.valueOf(2))));
        test(ast, expected, scope);
    }

    private static Stream<Arguments> testFunctionExpression() {
        return Stream.of(
                // function()
                Arguments.of("Function",
                        new Ast.Expression.Function("function", Arrays.asList()),
                        "function"
                ),
                // print("Hello, World!")
                Arguments.of("Print",
                        new Ast.Expression.Function("print", Arrays.asList(new Ast.Expression.Literal("Hello, World!"))),
                        Environment.NIL.getValue()
                ),
                // add(1, 2)
                Arguments.of("Add",
                        new Ast.Expression.Function("add", Arrays.asList(new Ast.Expression.Literal(BigInteger.ONE), new Ast.Expression.Literal(BigInteger.valueOf(2)))),
                        BigInteger.valueOf(3)
                )
        );
    }

    @Test
    void testFunctionCalls() {
        /*
        FUN f(x) DO log(x); END
        FUN g(y) DO log(y); f(y + 1); END
        FUN h(z) DO log(z); g(z + 1); END
        FUN main() DO f(0); g(1); h(2); END
         */
        Scope scope = new Scope(null);

        scope.defineVariable("x", true, Environment.create(BigInteger.ZERO));
        scope.defineVariable("y", true, Environment.create(BigInteger.ONE));
        scope.defineVariable("z", true, Environment.create(BigInteger.valueOf(2)));

        StringBuilder builder = new StringBuilder();
        scope.defineFunction("log", 1, args -> {
            builder.append(args.get(0).getValue());
            return args.get(0);
        });

        scope.defineFunction("f", 1, args -> scope.lookupFunction("log", 1).invoke(args));
        scope.defineFunction("g", 1, args -> {
            scope.lookupFunction("log", 1).invoke(args);
            BigInteger temp = (BigInteger) args.get(0).getValue();
            args.set(0, Environment.create(temp.add(BigInteger.ONE)));
            return scope.lookupFunction("f", 1).invoke(args);
        });
        scope.defineFunction("h", 1, args -> {
            scope.lookupFunction("log", 1).invoke(args);
            BigInteger temp = (BigInteger) args.get(0).getValue();
            args.set(0, Environment.create(temp.add(BigInteger.ONE)));
            return scope.lookupFunction("g", 1).invoke(args);
        });

        test(new Ast.Statement.Expression(new Ast.Expression.Function("f", Arrays.asList(
                new Ast.Expression.Access(Optional.empty(), "x")
        ))), Environment.NIL.getValue(), scope);

        Assertions.assertEquals("0", builder.toString());
        builder.delete(0, builder.length());

        test(new Ast.Expression.Function("g", Arrays.asList(
                new Ast.Expression.Access(Optional.empty(), "y")
        )), BigInteger.valueOf(2), scope);
        Assertions.assertEquals("12", builder.toString());
        builder.delete(0, builder.length());

        test(new Ast.Expression.Function("h", Arrays.asList(
                new Ast.Expression.Access(Optional.empty(), "z")
        )), BigInteger.valueOf(4), scope);
        Assertions.assertEquals("234", builder.toString());
    }

    @Test
    void testPlcList() {
        // [1, 5, 10]
        List<Object> expected = Arrays.asList(BigInteger.ONE, BigInteger.valueOf(5), BigInteger.TEN);

        List<Ast.Expression> values = Arrays.asList(new Ast.Expression.Literal(BigInteger.ONE),
                new Ast.Expression.Literal(BigInteger.valueOf(5)),
                new Ast.Expression.Literal(BigInteger.TEN));

        Ast ast = new Ast.Expression.PlcList(values);

        test(ast, expected, new Scope(null));
    }

    @Test
    void testIfScope() {
    /*
        FUN main() DO
            LET x = 1;
            LET y = 2;
            log(x);
            log(y);
            IF TRUE DO
                LET x = 3;
                y = 4;
                log(x);
                log(y);
            END
            log(x);
            log(y);
        END
     */
        Scope scope = new Scope(null);
        StringBuilder builder = new StringBuilder();
        scope.defineFunction("log", 1, args -> {
            builder.append(args.get(0).getValue());
            return args.get(0);
        });

        List<Ast.Statement> statements = Arrays.asList(
                new Ast.Statement.Declaration("x", Optional.of(new Ast.Expression.Literal(BigInteger.ONE))),
                new Ast.Statement.Declaration("y", Optional.of(new Ast.Expression.Literal(BigInteger.valueOf(2)))),
                new Ast.Statement.Expression(new Ast.Expression.Function("log", Arrays.asList(new Ast.Expression.Access(Optional.empty(), "x")))),
                new Ast.Statement.Expression(new Ast.Expression.Function("log", Arrays.asList(new Ast.Expression.Access(Optional.empty(), "y")))),
                new Ast.Statement.If(new Ast.Expression.Literal(true), Arrays.asList(
                        new Ast.Statement.Declaration("x", Optional.of(new Ast.Expression.Literal(BigInteger.valueOf(3)))),
                        new Ast.Statement.Assignment(new Ast.Expression.Access(Optional.empty(), "y"), new Ast.Expression.Literal(BigInteger.valueOf(4))),
                        new Ast.Statement.Expression(new Ast.Expression.Function("log", Arrays.asList(new Ast.Expression.Access(Optional.empty(), "x")))),
                        new Ast.Statement.Expression(new Ast.Expression.Function("log", Arrays.asList(new Ast.Expression.Access(Optional.empty(), "y"))))
                ), Arrays.asList()),
                new Ast.Statement.Expression(new Ast.Expression.Function("log", Arrays.asList(new Ast.Expression.Access(Optional.empty(), "x")))),
                new Ast.Statement.Expression(new Ast.Expression.Function("log", Arrays.asList(new Ast.Expression.Access(Optional.empty(), "y"))))
        );

        List<Ast.Function> functions = Arrays.asList(
                new Ast.Function("main", Arrays.asList(), statements)
        );

        Ast.Source src = new Ast.Source(Arrays.asList(), functions);
        Object expected = "123414";

        test(src, Environment.NIL.getValue(), scope);
        Assertions.assertEquals(expected, builder.toString());
    }

    @Test
    void testFunctionScope() {
    /*
    VAR x = 1;
    VAR y = 2;
    VAR z = 3;
    FUN f(z) DO
        RETURN x + y + z;
    END
    FUN main() DO
        LET y = 4;
        RETURN f(5);
    END
     */
        List<Ast.Global> globals = Arrays.asList(
                new Ast.Global("x", true, Optional.of(new Ast.Expression.Literal(BigInteger.ONE))),
                new Ast.Global("y", true, Optional.of(new Ast.Expression.Literal(BigInteger.valueOf(2)))),
                new Ast.Global("z", true, Optional.of(new Ast.Expression.Literal(BigInteger.valueOf(3))))
        );

        List<Ast.Statement> statements = Arrays.asList(
                new Ast.Statement.Declaration("y", Optional.of(new Ast.Expression.Literal(BigInteger.valueOf(4)))),
                new Ast.Statement.Return(new Ast.Expression.Function("f", Arrays.asList(new Ast.Expression.Literal(BigInteger.valueOf(5)))))
        );

        List<Ast.Function> functions = Arrays.asList(
                new Ast.Function("main", Arrays.asList(), statements),
                new Ast.Function("f", Arrays.asList("z"), Arrays.asList(new Ast.Statement.Return(
                        new Ast.Expression.Binary("+",
                                new Ast.Expression.Binary("+", new Ast.Expression.Access(Optional.empty(), "x"), new Ast.Expression.Access(Optional.empty(), "y")),
                                new Ast.Expression.Access(Optional.empty(), "z")))
                ))
        );

        Ast.Source src = new Ast.Source(globals, functions);
        Object expected = BigInteger.valueOf(8);

        test(src, expected, new Scope(null));
    }

    @Test
    void testSimpleArgument() {
    /*
    FUN func(x) DO
        log(x);
    END
    FUN main() DO
        func(10);
    END
     */

        List<Ast.Function> functions = Arrays.asList(
                new Ast.Function("main", Arrays.asList(), Arrays.asList(
                        new Ast.Statement.Expression(new Ast.Expression.Function("func", Arrays.asList(new Ast.Expression.Literal(BigInteger.TEN))))
                )),
                new Ast.Function("func", Arrays.asList("x"), Arrays.asList(
                        new Ast.Statement.Expression(new Ast.Expression.Function("log", Arrays.asList(
                                new Ast.Expression.Access(Optional.empty(), "x")
                        )))
                ))
        );

        Scope scope = new Scope(null);
        StringBuilder builder = new StringBuilder();
        scope.defineFunction("log", 1, args -> {
            builder.append(args.get(0).getValue());
            return args.get(0);
        });

        Ast.Source src = new Ast.Source(Arrays.asList(), functions);

        test(src, Environment.NIL.getValue(), scope);
        Assertions.assertEquals("10", builder.toString());
    }

    @Test
    void testMultipleArguments() {
    /*
    FUN func(x, y, z) DO
        log(x);
        log(y);
        log(z);
    END
    FUN main() DO
        func(0, 1, 10);
    END
     */

        List<Ast.Function> functions = Arrays.asList(
                new Ast.Function("main", Arrays.asList(), Arrays.asList(
                        new Ast.Statement.Expression(new Ast.Expression.Function("func", Arrays.asList(
                                new Ast.Expression.Literal(BigInteger.ZERO), new Ast.Expression.Literal(BigInteger.ONE),
                                new Ast.Expression.Literal(BigInteger.TEN)
                        )))
                )),
                new Ast.Function("func", Arrays.asList("x", "y", "z"), Arrays.asList(
                        new Ast.Statement.Expression(new Ast.Expression.Function("log", Arrays.asList(
                                new Ast.Expression.Access(Optional.empty(), "x")
                        ))),
                        new Ast.Statement.Expression(new Ast.Expression.Function("log", Arrays.asList(
                                new Ast.Expression.Access(Optional.empty(), "y")
                        ))),
                        new Ast.Statement.Expression(new Ast.Expression.Function("log", Arrays.asList(
                                new Ast.Expression.Access(Optional.empty(), "z")
                        )))
                ))
        );

        Scope scope = new Scope(null);
        StringBuilder builder = new StringBuilder();
        scope.defineFunction("log", 1, args -> {
            builder.append(args.get(0).getValue());
            return args.get(0);
        });

        Ast.Source src = new Ast.Source(Arrays.asList(), functions);

        test(src, Environment.NIL.getValue(), scope);
        Assertions.assertEquals("0110", builder.toString());
    }

    @Test
    void testSumList() {
    /*
    VAL len = 5;
    LIST list = [1, 2, 3, 4, 5];
    FUN main() DO
        LET total = 0;
        LET i = 0;
        WHILE i < len DO
            total = total + list[i];
            i = i + 1;
        END
        RETURN total;
    END
     */
        List<Ast.Expression> values = Arrays.asList(
                new Ast.Expression.Literal(BigInteger.ONE),
                new Ast.Expression.Literal(BigInteger.valueOf(2)),
                new Ast.Expression.Literal(BigInteger.valueOf(3)),
                new Ast.Expression.Literal(BigInteger.valueOf(4)),
                new Ast.Expression.Literal(BigInteger.valueOf(5))
        );
        Optional<Ast.Expression> value = Optional.of(new Ast.Expression.PlcList(values));

        List<Ast.Global> globals = Arrays.asList(
                new Ast.Global("len", false, Optional.of(new Ast.Expression.Literal(BigInteger.valueOf(5)))),
                new Ast.Global("list", true, value)
        );

        List<Ast.Statement> statements = Arrays.asList(
                new Ast.Statement.Declaration("total", Optional.of(new Ast.Expression.Literal(BigInteger.ZERO))),
                new Ast.Statement.Declaration("i", Optional.of(new Ast.Expression.Literal(BigInteger.ZERO))),
                new Ast.Statement.While(
                        new Ast.Expression.Binary("<",
                            new Ast.Expression.Access(Optional.empty(), "i"),
                            new Ast.Expression.Access(Optional.empty(), "len")
                        ),
                        Arrays.asList(
                                new Ast.Statement.Assignment(
                                        new Ast.Expression.Access(Optional.empty(), "total"),
                                        new Ast.Expression.Binary("+",
                                                new Ast.Expression.Access(Optional.empty(), "total"),
                                                new Ast.Expression.Access(Optional.of(new Ast.Expression.Access(Optional.empty(), "i")), "list")
                                        )
                                ),
                                new Ast.Statement.Assignment(
                                        new Ast.Expression.Access(Optional.empty(), "i"),
                                        new Ast.Expression.Binary("+",
                                                new Ast.Expression.Access(Optional.empty(), "i"),
                                                new Ast.Expression.Literal(BigInteger.ONE)
                                        )
                                )
                        )
                ),
                new Ast.Statement.Return(new Ast.Expression.Access(Optional.empty(), "total"))
        );

        List<Ast.Function> functions = Arrays.asList(
                new Ast.Function("main", Arrays.asList(), statements)
        );

        Ast.Source src = new Ast.Source(globals, functions);
        Object expected = BigInteger.valueOf(15);

        test(src, expected, new Scope(null));
    }

    @ParameterizedTest
    @MethodSource
    void testRuntimes(String test, Scope scope, List<Ast> asts, RuntimeException expected) {
        testRuntimeException(scope, expected, asts);
    }

    private static Stream<Arguments> testRuntimes() {
        return Stream.of(
                Arguments.of("Redefined global",
                    new Scope(null),
                    // VAR name; VAR name = 1;
                        Arrays.asList(
                            new Ast.Global("name", true, Optional.empty()),
                            new Ast.Global("name", true, Optional.of(new Ast.Expression.Literal(BigInteger.ONE)))
                        ),
                    new RuntimeException("The variable name is already defined in this scope.")
                ),
                Arguments.of("Whilw w/String",
                    new Scope(null),
                    // WHILE "false" DO END
                    Arrays.asList(
                            new Ast.Statement.While(new Ast.Expression.Literal("false"), Arrays.asList())),
                    new RuntimeException("Expected type java.lang.Boolean, received java.lang.String.")
                ),
                Arguments.of("Missing main",
                        new Scope(null),
                        Arrays.asList(
                                new Ast.Source(Arrays.asList(), Arrays.asList())
                        ),
                        new RuntimeException("The function main/0 is not defined in this scope.")
                ),
                Arguments.of("Invalid main arity",
                        new Scope(null),
                        Arrays.asList(
                                new Ast.Function("main", Arrays.asList("y"), Arrays.asList())
                        ),
                        new RuntimeException("Invalid main arity")
                ),
                // 1 / 0
                Arguments.of("Divide By Zero",
                        new Scope(null),
                        Arrays.asList(new Ast.Expression.Binary("/",
                                new Ast.Expression.Literal(BigInteger.ONE),
                                new Ast.Expression.Literal(BigInteger.ZERO)
                        )),
                        new RuntimeException("BigInteger divide by zero")
                ),
                Arguments.of("Undefined Function",
                        new Scope(null),
                        Arrays.asList(
                                new Ast.Expression.Function("undefined", Arrays.asList())
                        ),
                        new RuntimeException("The function undefined/0 is not defined in this scope.")
                )
        );
    }

    private static Scope test(Ast ast, Object expected, Scope scope) {
        Interpreter interpreter = new Interpreter(scope);
        if (expected != null) {
            Assertions.assertEquals(expected, interpreter.visit(ast).getValue());
        } else {
            Assertions.assertThrows(RuntimeException.class, () -> interpreter.visit(ast));
        }
        return interpreter.getScope();
    }

    private static void testRuntimeException(Scope scope, RuntimeException exception, List<Ast> asts) {
        Interpreter interpreter = new Interpreter(scope);
        RuntimeException rex = Assertions.assertThrows(RuntimeException.class, () -> asts.forEach(interpreter::visit));
        Assertions.assertEquals(exception.getMessage(), rex.getMessage());
    }

}
