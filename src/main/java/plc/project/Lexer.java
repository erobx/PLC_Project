package plc.project;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The lexer works through three main functions:
 *
 *  - {@link #lex()}, which repeatedly calls lexToken() and skips whitespace
 *  - {@link #lexToken()}, which lexes the next token
 *  - {@link CharStream}, which manages the state of the lexer and literals
 *
 * If the lexer fails to parse something (such as an unterminated string) you
 * should throw a {@link ParseException} with an index at the character which is
 * invalid.
 *
 * The {@link #peek(String...)} and {@link #match(String...)} functions are * helpers you need to use, they will make the implementation a lot easier. */
public final class Lexer {

    private final CharStream chars;

    public Lexer(String input) {
        chars = new CharStream(input);
    }

    /**
     * Repeatedly lexes the input using {@link #lexToken()}, also skipping over
     * whitespace where appropriate.
     */
    public List<Token> lex() {
        List<Token> tokens = new ArrayList<>();
        int offset = 0;
        while (chars.index < chars.input.length()) {
            switch (chars.get(offset)) {
                case ' ', '\b', '\n', '\r', '\t': chars.reset(); break;
                default: Token token = lexToken(); tokens.add(token); break;
            }
        }
        return tokens;
    }

    /**
     * This method determines the type of the next token, delegating to the
     * appropriate lex method. As such, it is best for this method to not change
     * the state of the char stream (thus, use peek not match).
     *
     * The next character should start a valid token since whitespace is handled
     * by {@link #lex()}
     */
    public Token lexToken() {
        String[] identifiers = {"(@|[A-Za-z])"};
        String[] decimals = {"-?", "(0|[1-9])", "[0-9]*", "\\.", "[0-9]+"};
        String[] numbers = {"-?", "(0|[1-9])", "\\.", "[0-9]+"};
        String[] zeroOrNeg = {"0|-?"};
        String[] integer = {"[1-9]"};
        String[] character = {"'"};
        String[] string = {"\""};
        String[] operators = {"[!=]=?|&&||||.|\\(|\\)|;|-"};

        try {
            if (peek(identifiers)) {
                return lexIdentifier();
            } else if (peek(decimals)) {
                System.out.println("Sending " + chars.get(chars.index) + " to lexNumber");
                return lexNumber();
            } else if (peek(numbers)) {
                return lexNumber();
            } else if (peek(zeroOrNeg) || peek(integer) || peek("0")) {
                return lexNumber();
            } else if (peek(character) && (!peek("'", "['\\n\\r\\\\]", "'") || peek("'", "\\\\", "[bnrt'\"\\\\]", "'"))) {
                return lexCharacter();
            } else if (peek(string)) {
                return lexString();
            } else if (peek(operators)) {
                return lexOperator();
            } else {
                throw new ParseException("Could not match token", chars.index);
            }
        } catch (ParseException ex) {
            System.out.println(ex.getMessage() + " at index: " + ex.getIndex());
            // Stop lexing???
            // Skip over invalid character after failing to parse
            chars.reset();
        }
        // Not sure what is required to return on failing to parse
        return null;
    }

    public Token lexIdentifier() {
        String identifier = "[A-Za-z0-9_-]*";
        match("@");
        while (peek(identifier)) chars.advance();
        return chars.emit(Token.Type.IDENTIFIER);
    }

    // Combination of Integer and Decimal grammar
    public Token lexNumber() {
        // Handle negatives
        if (peek("-")) {
            int indexTemp = chars.index;
            int lengthTemp = chars.length;
            chars.advance();

            // Handle special case of "-0.foo" TODO: Possibly make it handle longer leading numbers for -0.foo type cases
            if (peek("0")) { // May need to modify for all numbers?
                chars.advance();
                if (peek("\\.")) {
                    chars.advance();
                    if (peek("[0-9]")) { // Normal decimal, let the normal logic handle this
                        chars.index = indexTemp;
                        chars.length = lengthTemp;
                        match("-");
                    } else { // Special case of -0.foo
                        chars.index = indexTemp;
                        chars.length = lengthTemp;
                        match("-");
                        return chars.emit(Token.Type.OPERATOR); // return the - as an operator
                    }
                }
            }
        }

        // Separate leading zeroes into multiple tokens
        if (peek("0", "[0-9]")) {
            match("0");
            return chars.emit(Token.Type.INTEGER);
        }

        // Check if it's an integer first

        while (peek("[0-9]")) {
            match("[0-9]"); // We hit an integer
            if (peek("\\.", "[0-9]")) { // Decimal hit

                match("\\.");
                if (peek("[0-9]")) { // Integers after decimal
                    while (peek("[0-9]")) {
                        match("[0-9]");
                    } // We have run out of integers after the decimal
                    return chars.emit(Token.Type.DECIMAL);
                } else if (peek("[A-Za-z]")) { // Handles . as an operator. We hit a letter after the decimal
                    chars.index--; // this is important to handle 0.foo.
                    chars.length--; // basically rewinds to right before the . and returns that.
                    return chars.emit(Token.Type.INTEGER);
                }else { // We hit a decimal but no integers after
                    throw new ParseException("Invalid digit", chars.index);
                }
            } else if (peek("[0-9]")) { // We didn't hit a decimal, but we hit another integer
                // let the while loop handle this
            } else { // We didn't hit a decimal or another integer after this one
                return chars.emit(Token.Type.INTEGER);
            }
        }

        throw new ParseException("Something went wrong", chars.index); // If we made it this far, something went wrong.
    }

    public Token lexCharacter() {
        String[] characters = {"'", "([^'\\n\\r\\\\]|\\\\[bnrt'\"\\\\])", "'"};
        String[] checkEscape = {"'", "\\\\", "[bnrt'\"\\\\]", "'"};

        if (peek(checkEscape)) {
            match("'");
            lexEscape();
        } else if (peek("'", "['\\n\\r\\\\]", "'")) {
            throw new ParseException("Invalid escape sequence", chars.index);
        } else if (peek(characters)) {
            match(characters);
        } else {
            return lexOperator();
        }

        return chars.emit(Token.Type.CHARACTER);
    }

    // Very similar to lexCharacter()
    public Token lexString() {
        String errorMsg = "Invalid string";

        if (!peek("\"", "([^'\\n\\r\\\\]|\\\\[bnrt'\"\\\\])*")) {
            return lexOperator();
        }
        // Can just match since we peeked in lexToken()
        match("\"");

        while(!peek("[\"\\n\\r]")) { // Go through string, stopping at \n, \", or \r
            if (peek("\\\\")) { // If escape sequence is found
                lexEscape();
            } else if (peek(".")) {
                match(".");
            } else {
                break;
            }
        }
        if (peek("\"")) {
            match("\"");
        } else {
            throw new ParseException(errorMsg, chars.index-1);
        }

        return chars.emit(Token.Type.STRING); // Return the string token
    }

    public void lexEscape() {
        String errorMsg = "Invalid escape character";
        String escape = "[bnrt'\"\\\\]";
        match("\\\\");
        if (peek(escape, "'")) {
            match(escape, "'");
        } else if (peek(escape)) {
            match(escape);
        } else {
            throw new ParseException(errorMsg, chars.index-2);
        }
    }

    public Token lexOperator() {
        // Handles !=
        if (peek("!")) {
            match("!");
            if (peek("=")) {
                match("="); // Yeah, we could probably do something more advanced, but this works for now
                return chars.emit(Token.Type.OPERATOR);
            }
        }
        if (peek("=")) {
            match("=");
            if (peek("=")) {
                match("=");
                return chars.emit(Token.Type.OPERATOR);
            }else { // Added this to handle just the operator = on its own
                return chars.emit(Token.Type.OPERATOR);
            }
        }

        chars.advance();
        return chars.emit(Token.Type.OPERATOR);
    }

    /**
     * Returns true if the next sequence of characters match the given patterns,
     * which should be a regex. For example, {@code peek("a", "b", "c")} would
     * return true if the next characters are {@code 'a', 'b', 'c'}.
     */
    public boolean peek(String... patterns) {
        for (int i = 0; i < patterns.length; i++) {
            if (!chars.has(i) || !String.valueOf(chars.get(i)).matches(patterns[i])) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns true in the same way as {@link #peek(String...)}, but also
     * advances the character stream past all matched characters if peek returns
     * true. Hint - it's easiest to have this method simply call peek.
     */
    public boolean match(String... patterns) {
        boolean peek = peek(patterns);
        if (peek) {
            for (int i = 0; i < patterns.length; i++) {
                chars.advance();
            }
        }
        return peek;
    }

    /**
     * A helper class maintaining the input string, current index of the char
     * stream, and the current length of the token being matched.
     *
     * You should rely on peek/match for state management in nearly all cases.
     * The only field you need to access is {@link #index} for any {@link
     * ParseException} which is thrown.
     */
    public static final class CharStream {

        private final String input;
        private int index = 0;
        private int length = 0;

        public CharStream(String input) {
            this.input = input;
        }

        public boolean has(int offset) {
            return index + offset < input.length();
        }

        public char get(int offset) {
            return input.charAt(index + offset);
        }

        // Used to advance CharStream when matching a Token
        public void advance() {
            index++;
            length++;
        }

        public void moveTwo() {
            for (int i = 0; i < 2; i++) {
                advance();
            }
        }

        // Used to reset CharStream for the start of a new Token
        public void skip() {
            length = 0;
        }

        // Using to skip whitespace and manage CharStream state
        public void reset() {
            advance();
            skip();
        }

        public Token emit(Token.Type type) {
            int start = index - length;
            skip();
            return new Token(type, input.substring(start, index), start);
        }

    }

}