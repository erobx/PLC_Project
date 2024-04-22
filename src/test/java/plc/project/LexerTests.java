package plc.project;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public class LexerTests {

    @ParameterizedTest
    @MethodSource
    void testIdentifier(String test, String input, boolean success) {
        test(input, Token.Type.IDENTIFIER, success);
    }

    private static Stream<Arguments> testIdentifier() {
        return Stream.of(
                Arguments.of("Alphabetic", "getName", true),
                Arguments.of("Alphanumeric", "thelegend27", true),
                Arguments.of("Leading @", "@what3ver", true),
                Arguments.of("Leading Hyphen", "-five", false),
                Arguments.of("Leading Digit", "1fish2fish3fishbluefish", false),
                Arguments.of("Non Leading @", "some@words", false)
        );
    }

    @ParameterizedTest
    @MethodSource
    void testInteger(String test, String input, boolean success) {
        test(input, Token.Type.INTEGER, success);
    }

    private static Stream<Arguments> testInteger() {
        return Stream.of(
                Arguments.of("Single Digit", "1", true),
                Arguments.of("Multiple Digits", "12345", true),
                Arguments.of("Negative", "-1", true),
                Arguments.of("Trailing Zeros", "100", true),
                Arguments.of("Multiple Negative", "-50912", true),
                Arguments.of("Only Zero", "0", true),
                Arguments.of("Above Long Max", "123456789123456789123456789", false), // TODO: Look into this further
                Arguments.of("Leading Zero", "01", false),
                Arguments.of("Many Zeros", "000001000", false)
        );
    }

    @ParameterizedTest
    @MethodSource
    void testDecimal(String test, String input, boolean success) {
        test(input, Token.Type.DECIMAL, success);
    }

    private static Stream<Arguments> testDecimal() {
        return Stream.of(
                Arguments.of("Multiple Digits", "123.456", true),
                Arguments.of("Negative Decimal", "-1.0", true),
                Arguments.of("Zero Decimal", "0.0", true),
                Arguments.of("Negative Zero Decimal", "-0.0", true),
                Arguments.of("Single Digits", "1.0", true),
                Arguments.of("Trailing Decimal", "1.", false),
                Arguments.of("Leading Decimal", ".5", false),
                Arguments.of("Above Integer Precision", "9007199254740993.0", false ), //TODO: Look into this further
                Arguments.of("Trailing Zeros", "111.000", false) //TODO: Look into this further
        );
    }

    @ParameterizedTest
    @MethodSource
    void testCharacter(String test, String input, boolean success) {
        test(input, Token.Type.CHARACTER, success);
    }

    private static Stream<Arguments> testCharacter() {
        return Stream.of(
                Arguments.of("Alphabetic", "\'c\'", true),
                Arguments.of("Newline Escape", "\'\\n\'", true),
                Arguments.of("Backspace Escape", "'\\b'", true),
                Arguments.of("Carriage Escape", "'\\r'", true),
                Arguments.of("Tab Escape", "'\\t'", true),
                Arguments.of("Single Quote Escape", "'\\''", true),
                Arguments.of("Double Quote Escape", "'\\\"'", true),
                Arguments.of("Backslash Escape", "'\\\\'", true),
                Arguments.of("Empty", "\'\'", false),
                Arguments.of("Newline Char", "'\n'", false),
                Arguments.of("Multiple", "\'abc\'", false),
                Arguments.of("Unterminated", "'a", false),
                Arguments.of("Uninitialized", "a'", false),
                Arguments.of("Double Apostrophe", "'''", false),
                Arguments.of("Carriage Return", "'\r'", false),
                Arguments.of("Backslash", "'\\'", false),
                Arguments.of("Invalid Escape", "'\\a'", false),
                Arguments.of("Digit", "'1'", false), //TODO: Look into this further
                Arguments.of("Unicode", "'\\u0000'", false),
                Arguments.of("Space", "' '", false), //TODO: Look into this further
                Arguments.of("Unterminated Newline", "'\n", false),
                Arguments.of("Unterminated Empty", "''", false)
        );
    }

    @ParameterizedTest
    @MethodSource
    void testString(String test, String input, boolean success) {
        test(input, Token.Type.STRING, success);
    }

    private static Stream<Arguments> testString() {
        return Stream.of(
                Arguments.of("Empty", "\"\"", true),
                Arguments.of("Alphabetic", "\"abc\"", true),
                Arguments.of("Newline Escape", "\"Hello,\\nWorld\"", true),
                Arguments.of("String Escape", "\"\\\"\"", true),
                Arguments.of("Uninitialized String", "world\"", false),
                Arguments.of("Unterminated", "\"unterminated", false),
                Arguments.of("Invalid Escape", "\"invalid\\escape\"", false),
                Arguments.of("Newline Character", "\"\n\"", false)
        );
    }

    @ParameterizedTest
    @MethodSource
    void testOperator(String test, String input, boolean success) {
        //this test requires our lex() method, since that's where whitespace is handled.
        test(input, Arrays.asList(new Token(Token.Type.OPERATOR, input, 0)), success);
    }

    private static Stream<Arguments> testOperator() {
        return Stream.of(
                Arguments.of("Character", "(", true),
                Arguments.of("Comparison", "!=", true),
                Arguments.of("Money Symbol", "$", true),
                Arguments.of("Addition", "+", true),
                Arguments.of("Subtraction", "-", true),
                Arguments.of("Multiplication", "*", true),
                Arguments.of("Division", "\\", true),
                Arguments.of("And", "&&", true),
                Arguments.of("Or", "||", true),
                Arguments.of("Space", " ", false),
                Arguments.of("Tab", "\t", false)
        );
    }

    @ParameterizedTest
    @MethodSource
    void testExamples(String test, String input, List<Token> expected) {
        test(input, expected, true);
    }

    private static Stream<Arguments> testExamples() {
        String fizzBuzz = new String("LET i = 1;\nWHILE i != 100 DO\n    IF rem(i, 3) == 0 && rem(i, 5) == 0 DO");
        String source = new String("VAR i = -1 : Integer;\nVAL inc = 2 : Integer;\nFUN foo() DO\n    WHILE i != 1 DO\n        IF i > 0 DO\n            print(\"bar\");\n        END\n        i = i + inc;\n    END\nEND");
        List<Token> fbTokens = Arrays.asList(
                //LET i = 1;
                new Token(Token.Type.IDENTIFIER, "LET", 0),
                new Token(Token.Type.IDENTIFIER, "i", 4),
                new Token(Token.Type.OPERATOR, "=", 6),
                new Token(Token.Type.INTEGER, "1", 8),
                new Token(Token.Type.OPERATOR, ";", 9),

                //WHILE i != 100 DO
                new Token(Token.Type.IDENTIFIER, "WHILE", 11),
                new Token(Token.Type.IDENTIFIER, "i", 17),
                new Token(Token.Type.OPERATOR, "!=", 19),
                new Token(Token.Type.INTEGER, "100", 22),
                new Token(Token.Type.IDENTIFIER, "DO", 26),

                //    IF rem(i, 3) == 0 && rem(i, 5) == 0 DO
                new Token(Token.Type.IDENTIFIER, "IF", 33),
                new Token(Token.Type.IDENTIFIER, "rem", 36),
                new Token(Token.Type.OPERATOR, "(", 39),
                new Token(Token.Type.IDENTIFIER, "i", 40),
                new Token(Token.Type.OPERATOR, ",", 41),
                new Token(Token.Type.INTEGER, "3", 43),
                new Token(Token.Type.OPERATOR, ")", 44),
                new Token(Token.Type.OPERATOR, "==", 46),
                new Token(Token.Type.INTEGER, "0", 49),
                new Token(Token.Type.OPERATOR, "&&", 51),
                new Token(Token.Type.IDENTIFIER, "rem", 54),
                new Token(Token.Type.OPERATOR, "(", 57),
                new Token(Token.Type.IDENTIFIER, "i", 58),
                new Token(Token.Type.OPERATOR, ",", 59),
                new Token(Token.Type.INTEGER, "5", 61),
                new Token(Token.Type.OPERATOR, ")", 62),
                new Token(Token.Type.OPERATOR, "==", 64),
                new Token(Token.Type.INTEGER, "0", 67),
                new Token(Token.Type.IDENTIFIER, "DO", 69)
        );
        List<Token> input = Arrays.asList(
                //VAR i = -1 : Integer;
                new Token(Token.Type.IDENTIFIER, "VAR", 0),
                new Token(Token.Type.IDENTIFIER, "i", 4),
                new Token(Token.Type.OPERATOR, "=", 6),
                new Token(Token.Type.INTEGER, "-1", 8),
                new Token(Token.Type.OPERATOR, ":", 11),
                new Token(Token.Type.IDENTIFIER, "Integer", 13),
                new Token(Token.Type.OPERATOR, ";", 20),

                //VAL inc = 2 : Integer;
                new Token(Token.Type.IDENTIFIER, "VAL", 22),
                new Token(Token.Type.IDENTIFIER, "inc", 26),
                new Token(Token.Type.OPERATOR, "=", 30),
                new Token(Token.Type.INTEGER, "2", 32),
                new Token(Token.Type.OPERATOR, ":", 34),
                new Token(Token.Type.IDENTIFIER, "Integer", 36),
                new Token(Token.Type.OPERATOR, ";", 43),

                //DEF foo() DO
                new Token(Token.Type.IDENTIFIER, "FUN", 45),
                new Token(Token.Type.IDENTIFIER, "foo", 49),
                new Token(Token.Type.OPERATOR, "(", 52),
                new Token(Token.Type.OPERATOR, ")", 53),
                new Token(Token.Type.IDENTIFIER, "DO", 55),

                //    WHILE i != 1 DO
                new Token(Token.Type.IDENTIFIER, "WHILE", 62),
                new Token(Token.Type.IDENTIFIER, "i", 68),
                new Token(Token.Type.OPERATOR, "!=", 70),
                new Token(Token.Type.INTEGER, "1", 73),
                new Token(Token.Type.IDENTIFIER, "DO", 75),

                //        IF i > 0 DO
                new Token(Token.Type.IDENTIFIER, "IF", 86),
                new Token(Token.Type.IDENTIFIER, "i", 89),
                new Token(Token.Type.OPERATOR, ">", 91),
                new Token(Token.Type.INTEGER, "0", 93),
                new Token(Token.Type.IDENTIFIER, "DO", 95),

                //            print(\"bar\");
                new Token(Token.Type.IDENTIFIER, "print", 110),
                new Token(Token.Type.OPERATOR, "(", 115),
                new Token(Token.Type.STRING, "\"bar\"", 116),
                new Token(Token.Type.OPERATOR, ")", 121),
                new Token(Token.Type.OPERATOR, ";", 122),

                //        END
                new Token(Token.Type.IDENTIFIER, "END", 132),

                //        i = i + inc;
                new Token(Token.Type.IDENTIFIER, "i",144),
                new Token(Token.Type.OPERATOR, "=", 146),
                new Token(Token.Type.IDENTIFIER, "i", 148),
                new Token(Token.Type.OPERATOR, "+", 150),
                new Token(Token.Type.IDENTIFIER, "inc", 152),
                new Token(Token.Type.OPERATOR, ";", 155),

                //    END
                new Token(Token.Type.IDENTIFIER, "END", 161),

                //END
                new Token(Token.Type.IDENTIFIER, "END", 165)
        );
        return Stream.of(
                Arguments.of("Program", source, input),
                Arguments.of("FizzBuzz", fizzBuzz, fbTokens),
                Arguments.of("Example 1", "LET x = 5;", Arrays.asList(
                        new Token(Token.Type.IDENTIFIER, "LET", 0),
                        new Token(Token.Type.IDENTIFIER, "x", 4),
                        new Token(Token.Type.OPERATOR, "=", 6),
                        new Token(Token.Type.INTEGER, "5", 8),
                        new Token(Token.Type.OPERATOR, ";", 9)
                )),
                Arguments.of("Example 2", "print(\"Hello, World!\");", Arrays.asList(
                        new Token(Token.Type.IDENTIFIER, "print", 0),
                        new Token(Token.Type.OPERATOR, "(", 5),
                        new Token(Token.Type.STRING, "\"Hello, World!\"", 6),
                        new Token(Token.Type.OPERATOR, ")", 21),
                        new Token(Token.Type.OPERATOR, ";", 22)
                )),
                Arguments.of("Weird Case", "-0.foo", Arrays.asList(
                        new Token(Token.Type.OPERATOR, "-", 0),
                        new Token(Token.Type.INTEGER, "0", 1),
                        new Token(Token.Type.OPERATOR, ".", 2),
                        new Token(Token.Type.IDENTIFIER, "foo", 3)
                )),
                Arguments.of("Subtraction", "5-b", Arrays.asList(
                        new Token(Token.Type.INTEGER, "5", 0),
                        new Token(Token.Type.OPERATOR, "-", 1),
                        new Token(Token.Type.IDENTIFIER, "b", 2)
                )),
                Arguments.of("Integer Identifier", "1fish2fish", Arrays.asList(
                        new Token(Token.Type.INTEGER, "1", 0),
                        new Token(Token.Type.IDENTIFIER, "fish2fish", 1)
                )),
                Arguments.of("Negative Zero", "-0.0 -0 -1",Arrays.asList(
                        new Token(Token.Type.DECIMAL, "-0.0", 0),
                        new Token(Token.Type.OPERATOR, "-", 5),
                        new Token(Token.Type.INTEGER, "0", 6),
                        new Token(Token.Type.INTEGER, "-1", 8)
                )),
                Arguments.of("Decimals", ".51.00.2.", Arrays.asList(
                        new Token(Token.Type.OPERATOR, ".", 0),
                        new Token(Token.Type.DECIMAL, "51.00", 1),
                        new Token(Token.Type.OPERATOR, ".", 6),
                        new Token(Token.Type.INTEGER, "2", 7),
                        new Token(Token.Type.OPERATOR, ".", 8)
                )),
                Arguments.of("Negative Zero", "-0", Arrays.asList(
                        new Token(Token.Type.OPERATOR, "-", 0),
                        new Token(Token.Type.INTEGER, "0", 1)
                )),
                Arguments.of("Alternating Decimal", "1.2.3", Arrays.asList(
                        new Token(Token.Type.DECIMAL, "1.2", 0),
                        new Token(Token.Type.OPERATOR, ".", 3),
                        new Token(Token.Type.INTEGER, "3", 4)
                )),
                Arguments.of("Separate Zeroes", "000100", Arrays.asList(
                        new Token(Token.Type.INTEGER, "0", 0),
                        new Token(Token.Type.INTEGER, "0", 1),
                        new Token(Token.Type.INTEGER, "0", 2),
                        new Token(Token.Type.INTEGER, "100", 3)
                )),
                Arguments.of("Combination", "5-32'1'.your3mother@6h (\"Test\")", Arrays.asList(
                        new Token(Token.Type.INTEGER, "5", 0),
                        new Token(Token.Type.INTEGER, "-32", 1),
                        new Token(Token.Type.CHARACTER, "'1'", 4),
                        new Token(Token.Type.OPERATOR, ".", 7),
                        new Token(Token.Type.IDENTIFIER, "your3mother", 8),
                        new Token(Token.Type.IDENTIFIER, "@6h", 19),
                        new Token(Token.Type.OPERATOR, "(", 23),
                        new Token(Token.Type.STRING, "\"Test\"", 24),
                        new Token(Token.Type.OPERATOR, ")", 30)
                ))
        );
    }

    @Test
    void testException() {
        ParseException exception = Assertions.assertThrows(ParseException.class,
                () -> new Lexer("\"unterminated").lex());
        Assertions.assertEquals(13, exception.getIndex());

        exception = Assertions.assertThrows(ParseException.class,
                () -> new Lexer("\"invalid\\escape\"").lex());
        Assertions.assertEquals(9, exception.getIndex());

        exception = Assertions.assertThrows(ParseException.class,
                () -> new Lexer("\"a\\u0000b\\u12ABc\"").lex());
        Assertions.assertEquals(3, exception.getIndex());

        exception = Assertions.assertThrows(ParseException.class,
                () -> new Lexer("w\"s").lex());
        Assertions.assertEquals(3, exception.getIndex());

        exception = Assertions.assertThrows(ParseException.class,
                () -> new Lexer("\"\"\"").lex());
        Assertions.assertEquals(3, exception.getIndex());

        exception = Assertions.assertThrows(ParseException.class,
                () -> new Lexer("''").lex());
        Assertions.assertEquals(1, exception.getIndex());

        exception = Assertions.assertThrows(ParseException.class,
                () -> new Lexer("'abc'").lex());
        Assertions.assertEquals(2, exception.getIndex());

        exception = Assertions.assertThrows(ParseException.class,
                () -> new Lexer("'\n'").lex());
        Assertions.assertEquals(1, exception.getIndex());

        exception = Assertions.assertThrows(ParseException.class,
                () -> new Lexer("'c").lex());
        Assertions.assertEquals(1, exception.getIndex());
    }

    /**
     * Tests that lexing the input through {@link Lexer#lexToken()} produces a
     * single token with the expected type and literal matching the input.
     */
    private static void test(String input, Token.Type expected, boolean success) {
        try {
            if (success) {
                Assertions.assertEquals(new Token(expected, input, 0), new Lexer(input).lexToken());
            } else {
                Assertions.assertNotEquals(new Token(expected, input, 0), new Lexer(input).lexToken());
            }
        } catch (ParseException e) {
            Assertions.assertFalse(success, e.getMessage());
        }
    }

    /**
     * Tests that lexing the input through {@link Lexer#lex()} matches the
     * expected token list.
     */
    private static void test(String input, List<Token> expected, boolean success) {
        try {
            if (success) {
                Assertions.assertEquals(expected, new Lexer(input).lex());
            } else {
                Assertions.assertNotEquals(expected, new Lexer(input).lex());
            }
        } catch (ParseException e) {
            Assertions.assertFalse(success, e.getMessage());
        }
    }

}
