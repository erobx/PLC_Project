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
        String[] operators = {"[!=]=?|&&||||\\."};

        try {
            if (peek(identifiers)) {
                return lexIdentifier();
            } else if (peek(decimals)) {
                return lexNumber();
            } else if (peek(numbers)) {
                return lexNumber();
            } else if (peek(zeroOrNeg) || peek(integer) || peek("0")) {
                return lexNumber();
            } else if (peek(character)) {
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
        String[] integers = {"-?[1-9]"};
        String beginDecimals = "(0|[1-9])";
        String[] digits = {"[0-9]*"};
        String[] decimal = {"\\."};
        String[] onesPlace = {"-?(0|[1-9])", "\\."};
        String[] afterDecimal = {"[0-9]+"};

        // Separate leading zeroes into multiple tokens
        if (peek("0", "[0-9]")) {
            match("0");
            return chars.emit(Token.Type.INTEGER);
        }

        if (peek("0", "\\.", "[^0-9]+")) {
            match("0");
            return chars.emit(Token.Type.INTEGER);
        }

        if (match(beginDecimals)) {
            if (peek(digits)) {
                while (match(digits)) {
                    if (peek(decimal)) {
                        match(decimal);
                        break;
                    }
                }
            } else if (peek(decimal)) {
                match(decimal);
            }
            while (match(afterDecimal)) continue;
            return chars.emit(Token.Type.DECIMAL);
        } else if (match("-?", beginDecimals)) {
            if (peek(digits)) {
                while (match(digits)) {
                    if (peek(decimal)) {
                        match(decimal);
                        break;
                    }
                }
            } else if (peek(decimal)) {
                match(decimal);
            }
            while (match(afterDecimal)) continue;
            return chars.emit(Token.Type.DECIMAL);
        }
        else {
            throw new ParseException("Invalid digit", chars.index);
        }

//        if (peek(onesPlace)) {
//            match(onesPlace);
//            while (match(afterDecimal)) continue;
//            return chars.emit(Token.Type.DECIMAL);
//        } else if (peek(decimals)) {
//            match(decimals);
//            return chars.emit(Token.Type.DECIMAL);
//        } else if (peek(integers)) {
//            while (peek("[0-9]*")) chars.advance();
//            return chars.emit(Token.Type.INTEGER);
//        } else {
//        }
    }

    public Token lexCharacter() {
        String[] characters = {"'", "([^'\\n\\r\\\\]|\\\\[bnrt'\"\\\\])", "'"};
        String[] checkBackslash = {"'", "\\\\"};

        if (peek(checkBackslash)) {
            chars.advance();
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
        String[] strings = {"\"", "", "\""};
        String[] checkBackslash = {"\"", "\\\\"};
        //System.out.println("Char: " + chars.get(chars.index));
        if (peek("\"")) { // Found a valid string start
            chars.advance();
        }
        while(!peek("[\"\n\r]+")) { // Go through string, stopping at \n, \", or \r
            if (peek("\\\\")) { // If escape sequence is found
                chars.advance(); // Skip \
                // After skipping backslash, check if the next character is part of a valid escape sequence
                if (!peek("[\"\\\\bnrt]")) { // Escape character found, throw an error :(
                    throw new ParseException("Invalid escape sequence", chars.index);
                }
                chars.advance(); // Skip the character after backslash
            } else if (peek(".")) { // Not technically needed but good sanity check
                chars.advance();
            }else {
                // if a valid string character isnt found, then this while loop ends and the string ending " is checked
                break;
            }
        }
        if (peek("\"")) { // Hopefully this string terminates with a "
            chars.advance();
        } else {
            throw new ParseException(errorMsg, chars.index); // Otherwise the string is unterminated (or a mistake was made)
        }

        return chars.emit(Token.Type.STRING); // Return the string token
        /* Old code
        if (peek("\"")) {
            chars.advance();
        } else if (peek(strings)) {
            chars.advance();
        } else {
            throw new ParseException(errorMsg, chars.index);
        }
        */
    }

    public void lexEscape() {
        String errorMsg = "Invalid escape character";
        match("\\\\");
        if (peek("[bnrt'\"\\\\]", "'")) {
            match("[bnrt'\"\\\\]", "'");
        } else {
            throw new ParseException(errorMsg, chars.index);
        }
    }

    public Token lexOperator() {
        // TODO != && == operators

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