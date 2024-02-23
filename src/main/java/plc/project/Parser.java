package plc.project;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.ArrayList;
import java.util.Optional;

/**
 * The parser takes the sequence of tokens emitted by the lexer and turns that
 * into a structured representation of the program, called the Abstract Syntax
 * Tree (AST).
 *
 * The parser has a similar architecture to the lexer, just with {@link Token}s
 * instead of characters. As before, {@link #peek(Object...)} and {@link
 * #match(Object...)} are helpers to make the implementation easier.
 *
 * This type of parser is called <em>recursive descent</em>. Each rule in our
 * grammar will have it's own function, and reference to other rules correspond
 * to calling that functions.
 */
public final class Parser {

    private final TokenStream tokens;

    public Parser(List<Token> tokens) {
        this.tokens = new TokenStream(tokens);
    }

    /**
     * Parses the {@code source} rule.
     */
    public Ast.Source parseSource() throws ParseException {
        List< Ast.Global> globals = new ArrayList<>();
        List<Ast.Function> functions = new ArrayList<>();

        // Have to parse all globals first before functions
        boolean flag = false;
        while (tokens.has(1)) {
            if (peek("LIST") || peek("VAR") || peek("VAL")) {
                if (!flag) {
                    Ast.Global gl = parseGlobal();
                    globals.add(gl);
                } else {
                    throw new ParseException("Function Before Global", getErrIndex());
                }
            }
            if (match("FUN")) {
                Ast.Function fn = parseFunction();
                functions.add(fn);
                flag = true;
            }
        }

        return new Ast.Source(globals, functions);
    }

    /**
     * Parses the {@code global} rule. This method should only be called if the
     * next tokens start a global, aka {@code LIST|VAL|VAR}.
     */
    public Ast.Global parseGlobal() throws ParseException {
        Ast.Global gl;
        if (match("LIST")) {
            gl =  parseList();
        } else if (match("VAR")) {
            gl = parseMutable();
        } else {
            match("VAL");
            gl = parseImmutable();
        }
        if (!match(";")) {
            throw new ParseException("Missing semicolon", getErrIndex());
        }
        return gl;
    }

    /**
     * Parses the {@code list} rule. This method should only be called if the
     * next token declares a list, aka {@code LIST}.
     */
    public Ast.Global parseList() throws ParseException {
        String name = getIdentifier();
        checkNotEquals();
        if (!match("[")) {
            throw new ParseException("Missing [", getErrIndex());
        }

        List<Ast.Expression> exprs = new ArrayList<>();
        Ast.Expression expr = parseExpression();
        exprs.add(expr);
        while (!match("]")) {
            expr = parseExpression();
            exprs.add(expr);
            if (peek(",", "]")) {
                Token lastExp = tokens.get(0);
                int errIndex = lastExp.getIndex()+1;
                throw new ParseException("Trailing comma", errIndex);
            }
            match(",");
        }

        Ast.Expression.PlcList list = new Ast.Expression.PlcList(exprs);
        return new Ast.Global(name, true, Optional.of(list));
    }

    /**
     * Parses the {@code mutable} rule. This method should only be called if the
     * next token declares a mutable global variable, aka {@code VAR}.
     */
    public Ast.Global parseMutable() throws ParseException {
        String name = getIdentifier();
        if (match("=")) {
            return new Ast.Global(name, true, Optional.of(parseExpression()));
        }

        return new Ast.Global(name, true, Optional.empty());
    }

    /**
     * Parses the {@code immutable} rule. This method should only be called if the
     * next token declares an immutable global variable, aka {@code VAL}.
     */
    public Ast.Global parseImmutable() throws ParseException {
        String name = getIdentifier();
        checkNotEquals();

        return new Ast.Global(name, false, Optional.of(parseExpression()));
    }

    /**
     * Parses the {@code function} rule. This method should only be called if the
     * next tokens start a method, aka {@code FUN}.
     */
    public Ast.Function parseFunction() throws ParseException {
        String name = getIdentifier();
        if (!match("(")) {
            throw new ParseException("Missing left paren", getErrIndex());
        }
        List<String> params = new ArrayList<>();
        List<Ast.Statement> statements = new ArrayList<>();

        while (!match(")")) {
            if (!match(Token.Type.IDENTIFIER)) {
                throw new ParseException("Invalid argument", getErrIndex());
            }
            String arg = tokens.get(-1).getLiteral();
            params.add(arg);
            if (peek(",", ")")) {
                Token lastExp = tokens.get(0);
                int errIndex = lastExp.getIndex()+1;
                throw new ParseException("Trailing comma", errIndex);
            }
            match(",");
        }

        if (!match("DO")) {
            throw new ParseException("Missing DO", getErrIndex());
        }
        statements = parseBlock();

        return new Ast.Function(name, params, statements);
    }

    /**
     * Parses the {@code block} rule. This method should only be called if the
     * preceding token indicates the opening a block of statements.
     */
    public List<Ast.Statement> parseBlock() throws ParseException {
        List<Ast.Statement> stmts = new ArrayList<>();
        while (!match("END")) {
            stmts.add(parseStatement());
        }
        return stmts;
    }

    private void checkNotEquals() throws ParseException {
        if (!match("=")) {
            throw new ParseException("Missing =", getErrIndex());
        }
    }

    private String getIdentifier() throws ParseException {
        if (!match(Token.Type.IDENTIFIER)) {
            throw new ParseException("Invalid identifier", getErrIndex());
        }
        return tokens.get(-1).getLiteral();
    }

    /**
     * Parses the {@code statement} rule and delegates to the necessary method.
     * If the next tokens do not start a declaration, if, while, or return
     * statement, then it is an expression/assignment statement.
     */
    public Ast.Statement parseStatement() throws ParseException {
        if (match("LET")) {
            return parseDeclarationStatement();
        } else if (match("SWITCH")) {
            return parseSwitchStatement();
        } else if (match("IF")) {
            return parseIfStatement();
        } else if (match("WHILE")) {
            return parseWhileStatement();
        } else if (match("RETURN")) {
            return parseReturnStatement();
        } else {
            Ast.Expression expr = parseExpression();
            if (match("=")) {
                try {
                    Ast.Expression right = parseExpression();
                    if (match(";")) {
                        return new Ast.Statement.Assignment(expr, right);
                    }
                } catch (ParseException ex) {
                    throw new ParseException("Missing Assign Value", getErrIndex());
                }
            }
            if (peek(";")) {
                match(";");
            } else {
                throw new ParseException("Missing semicolon", getErrIndex());
            }
            return new Ast.Statement.Expression(expr);
        }
    }

    /**
     * Parses a declaration statement from the {@code statement} rule. This
     * method should only be called if the next tokens start a declaration
     * statement, aka {@code LET}.
     */
    public Ast.Statement.Declaration parseDeclarationStatement() throws ParseException {
        if (!match(Token.Type.IDENTIFIER)) {
            throw new ParseException("Missing identifier", getErrIndex());
        }
        String name = tokens.get(-1).getLiteral();
        Ast.Statement.Declaration dec = new Ast.Statement.Declaration(name, Optional.empty());
        if (match("=")) {
            Ast.Expression right = parseExpression();
            dec =  new Ast.Statement.Declaration(name, Optional.of(right));
        }
        // Matched ID and not = check for ;
        if (!match(";")) {
            throw new ParseException("Missing semicolon", getErrIndex());
        }
        return dec;
    }

    /**
     * Parses an if statement from the {@code statement} rule. This method
     * should only be called if the next tokens start an if statement, aka
     * {@code IF}.
     */
    public Ast.Statement.If parseIfStatement() throws ParseException {
        Ast.Expression expr = parseExpression();
        List<Ast.Statement> thenStatements = new ArrayList<>();
        List<Ast.Statement> elseStatements = new ArrayList<>();

        if (!match("DO")) {
            if (tokens.has(0)) {
                throw new ParseException("Invalid DO", getErrIndex()+1);
            }
            throw new ParseException("Missing DO", getErrIndex());
        } else {
            // Parse statements
            while (!peek("ELSE") && !match("END")) {
                Ast.Statement stmt = parseStatement();
                thenStatements.add(stmt);
            }
        }

        if (match("ELSE")) {
            while (!match("END")) {
                Ast.Statement stmt = parseStatement();
                elseStatements.add(stmt);
            }
        }

        return new Ast.Statement.If(expr, thenStatements, elseStatements);
    }

    /**
     * Parses a switch statement from the {@code statement} rule. This method
     * should only be called if the next tokens start a switch statement, aka
     * {@code SWITCH}.
     */
    public Ast.Statement.Switch parseSwitchStatement() throws ParseException {
        Ast.Expression condition = parseExpression();
        // Enter CASE and DEFAULT loop
        List<Ast.Statement.Case> cases = caseLoop();
        return new Ast.Statement.Switch(condition, cases);
    }

    // ('CASE' expression ':' block)*
    // new Ast.Statement.Case(Expressions, List<Statement>)
    // SWITCH expr CASE expr : LET name; LET x = 0; CASE exp2 : LET v; DEFAULT LET v; END
    private List<Ast.Statement.Case> caseLoop() throws ParseException {
        List<Ast.Statement.Case> cases = new ArrayList<>();
        while (match("CASE")) {
            cases.add(parseCaseStatement());
        }
        cases.add(parseDefault());
        return cases;
    }

    // DEFAULT found
    private Ast.Statement.Case parseDefault() throws ParseException {
        List<Ast.Statement> statements = new ArrayList<>();
        if (!match("DEFAULT")) {
            throw new ParseException("Missing Default Case", getErrIndex());
        }
        while (!match("END")) {
            try {
                Ast.Statement stmt = parseStatement();
                statements.add(stmt);
            } catch (ParseException ex) {
                throw new ParseException("Missing END", getErrIndex());
            }

        }

        return new Ast.Statement.Case(Optional.empty(), statements);
    }

    /**
     * Parses a case or default statement block from the {@code switch} rule. 
     * This method should only be called if the next tokens start the case or 
     * default block of a switch statement, aka {@code CASE} or {@code DEFAULT}.
     */
    public Ast.Statement.Case parseCaseStatement() throws ParseException {
        Ast.Expression expr = parseExpression();
        if (!match(":")) {
            throw new ParseException("Missing colon", getErrIndex());
        }
        // Parse statements
        List<Ast.Statement> statements = new ArrayList<>();
        while (!peek("DEFAULT") && !peek("CASE") && !peek("END")) {
            Ast.Statement stmt = parseStatement();
            statements.add(stmt);
        }
        return new Ast.Statement.Case(Optional.of(expr), statements);
    }

    /**
     * Parses a while statement from the {@code statement} rule. This method
     * should only be called if the next tokens start a while statement, aka
     * {@code WHILE}.
     */
    public Ast.Statement.While parseWhileStatement() throws ParseException {
        Ast.Expression condition = parseExpression();
        if (!match("DO")) {
            throw new ParseException("Missing DO", getErrIndex());
        }
        List<Ast.Statement> statements = new ArrayList<>();
        while (!match("END")) {
            try {
                Ast.Statement stmt = parseStatement();
                statements.add(stmt);
            } catch (ParseException ex) {
                throw new ParseException("Missing END", getErrIndex());
            }
        }

        return new Ast.Statement.While(condition, statements);
    }

    /**
     * Parses a return statement from the {@code statement} rule. This method
     * should only be called if the next tokens start a return statement, aka
     * {@code RETURN}.
     */
    public Ast.Statement.Return parseReturnStatement() throws ParseException {
        Ast.Expression expr = null;
        try {
            expr = parseExpression();
            if (!match(";")) {
                throw new ParseException("Missing semicolon", getErrIndex());
            }
        } catch (ParseException ex) {
            throw new ParseException("Missing Value", getErrIndex());
        }
        return new Ast.Statement.Return(expr);
    }

    /**
     * Parses the {@code expression} rule.
     */
    public Ast.Expression parseExpression() throws ParseException {
        return parseLogicalExpression();
    }

    /**
     * Parses the {@code logical-expression} rule.
     */
    public Ast.Expression parseLogicalExpression() throws ParseException {
        Ast.Expression expr = parseComparisonExpression();

        while (match("&&") || match("||")) {
            // Binary takes type String as operator
            String operator = tokens.get(-1).getLiteral();
            try {
                Ast.Expression right = parseComparisonExpression();
                expr = new Ast.Expression.Binary(operator, expr, right);
            } catch (ParseException ex) {
                int errIndex = tokens.get(-1).getIndex() + 1;
                throw new ParseException("Invalid logic", errIndex);
            }
        }

        return expr;
    }

    /**
     * Parses the {@code comparison-expression} rule.
     */
    public Ast.Expression parseComparisonExpression() throws ParseException {
        Ast.Expression expr = parseAdditiveExpression();

        while (match("<") || match(">") || match("==") || match("!=")) {
            // Same as Logical and so forth
            String operator = tokens.get(-1).getLiteral();
            try {
                Ast.Expression right = parseAdditiveExpression();
                expr = new Ast.Expression.Binary(operator, expr, right);
            } catch (ParseException ex) {
                int errIndex = tokens.get(-1).getIndex() + 1;
                throw new ParseException("Invalid comparison", errIndex);
            }
        }

        return expr;
    }

    /**
     * Parses the {@code additive-expression} rule.
     */
    public Ast.Expression parseAdditiveExpression() throws ParseException {
        Ast.Expression expr = parseMultiplicativeExpression();

        while (match("+") || match("-")) {
            String operator = tokens.get(-1).getLiteral();
            try {
                Ast.Expression right = parseMultiplicativeExpression();
                expr = new Ast.Expression.Binary(operator, expr, right);
            } catch (ParseException ex) {
                int errIndex = tokens.get(-1).getIndex() + 1;
                throw new ParseException("Invalid operand", errIndex);
            }
        }

        return expr;
    }

    /**
     * Parses the {@code multiplicative-expression} rule.
     */
    public Ast.Expression parseMultiplicativeExpression() throws ParseException {
        Ast.Expression expr = parsePrimaryExpression();

        while (match("*") || match("/") || match("^")) {
            String operator = tokens.get(-1).getLiteral();
            try {
                Ast.Expression right = parsePrimaryExpression();
                expr = new Ast.Expression.Binary(operator, expr, right);
            } catch (ParseException ex) {
                int errIndex = tokens.get(-1).getIndex() + 1;
                throw new ParseException("Invalid operand", errIndex);
            }

        }

        return expr;
    }

    /**
     * Parses the {@code primary-expression} rule. This is the top-level rule
     * for expressions and includes literal values, grouping, variables, and
     * functions. It may be helpful to break these up into other methods but is
     * not strictly necessary.
     */
    public Ast.Expression parsePrimaryExpression() throws ParseException {
        // NIL, TRUE, FALSE, BigInteger, BigDecimal, Char, String,
        // (expression), ID, ID(), ID(expression), ID(expression, expression...)
        // ID[expression]

        // LITERALS
        if (match("NIL")) return new Ast.Expression.Literal(null);
        if (match("TRUE")) return new Ast.Expression.Literal(Boolean.TRUE);
        if (match("FALSE")) return new Ast.Expression.Literal(Boolean.FALSE);
        if (match(Token.Type.INTEGER)) {
            BigInteger obj = new BigInteger(tokens.get(-1).getLiteral());
            return new Ast.Expression.Literal(obj);
        }
        if (match(Token.Type.DECIMAL)) {
            BigDecimal obj = new BigDecimal(tokens.get(-1).getLiteral());
            return new Ast.Expression.Literal(obj);
        }
        // Chars
        if (match(Token.Type.CHARACTER)) {
            String literal = tokens.get(-1).getLiteral();
            // Remove first and second '
            for (int i = 0; i < 2; i++) {
                literal = literal.replace("'", "");
            }
            // Check for escapes
            char ch = literal.charAt(0);
            if (ch == '\\') {
                char nextChar = literal.charAt(1);
                switch (nextChar) {
                    case 'n':
                        ch = '\n';
                        break;
                    case 'b':
                        ch = '\b';
                        break;
                    case 'r':
                        ch = '\r';
                        break;
                    case 't':
                        ch = '\t';
                        break;
                    case 'f':
                        ch = '\f';
                        break;
                    default:
                        break;
                }
            }

            return new Ast.Expression.Literal(ch);
        }
        // Strings
        if (match(Token.Type.STRING)) {
            String literal = tokens.get(-1).getLiteral();
            // Check for escapes
            literal = replaceEscapes(literal);
            if (literal.contains("\\\"")) {
                literal = literal.replace("\\\"", "\"");
                for (int i = 0; i < 2; i++) {
                    literal = literal.replaceFirst("\"", "");
                }
            } else {
                literal = literal.replace("\"", "");
            }

            // Weird characters
            literal = literal.replace("\\u0000b", "\u0000b");
            literal = literal.replace("\\f", "\f");
            return new Ast.Expression.Literal(literal);
        }
        // GROUP
        if (match("(")) {
            Ast.Expression expr = parseExpression();
            if (peek(")")) {
                match(")");
                return new Ast.Expression.Group(expr);
            } else {
                Token token = tokens.get(-1);
                throw new ParseException("Invalid group", token.getIndex()+token.getLiteral().length());
            }
        }
        // ID(), ID(expr), ID(expr, expr...)
        if (match(Token.Type.IDENTIFIER, "(")) {
            String literal = tokens.get(-2).getLiteral();
            List<Ast.Expression> args = new ArrayList<>();
            while (!match(")")) {
                Ast.Expression expr = parseExpression();
                args.add(expr);
                if (peek(",", ")")) {
                    Token lastExp = tokens.get(0);
                    int errIndex = lastExp.getIndex()+1;
                    throw new ParseException("Trailing comma", errIndex);
                }
                match(",");
            }
            return new Ast.Expression.Function(literal, args);
        }

        // Access with offset ID[expr]
        if (match(Token.Type.IDENTIFIER, "[")) {
            String literal = tokens.get(-2).getLiteral();
            Ast.Expression expr = parseExpression();
            if (peek("]")) {
                match("]");
                return new Ast.Expression.Access(Optional.of(expr), literal);
            }
        }

        if (match(Token.Type.IDENTIFIER)) {
            return new Ast.Expression.Access(Optional.empty(), tokens.get(-1).getLiteral());
        }
        throw new ParseException("Invalid expression", tokens.index);
    }

    private int getErrIndex() {
        return tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length();
    }

    private String replaceEscapes(String literal) {
        // escape ::= '\' [bnrt'"\\]
        literal = literal.replace("\\n", "\n");
        literal = literal.replace("\\b", "\b");
        literal = literal.replace("\\r", "\r");
        literal = literal.replace("\\t", "\t");
        literal = literal.replace("\\'", "'");
        literal = literal.replace("\\\\", "\\");
        return literal;
    }

    /**
     * As in the lexer, returns {@code true} if the current sequence of tokens
     * matches the given patterns. Unlike the lexer, the pattern is not a regex;
     * instead it is either a {@link Token.Type}, which matches if the token's
     * type is the same, or a {@link String}, which matches if the token's
     * literal is the same.
     *
     * In other words, {@code Token(IDENTIFIER, "literal")} is matched by both
     * {@code peek(Token.Type.IDENTIFIER)} and {@code peek("literal")}.
     */
    private boolean peek(Object... patterns) {
        for (int i = 0; i < patterns.length; i++) {
            if (!tokens.has(i)) {
                return false;
            } else if (patterns[i] instanceof Token.Type) {
                if (patterns[i] != tokens.get(i).getType()) {
                    return false;
                }
            } else if (patterns[i] instanceof String) {
                if (!patterns[i].equals(tokens.get(i).getLiteral())) {
                    return false;
                }
            } else {
                throw new AssertionError("Invalid pattern object: " + patterns[i].getClass());
            }
        }
        return true;
    }

    /**
     * As in the lexer, returns {@code true} if {@link #peek(Object...)} is true
     * and advances the token stream.
     */
    private boolean match(Object... patterns) {
        boolean peek = peek(patterns);
        if (peek) {
            for (int i = 0; i < patterns.length; i++) {
                tokens.advance();
            }
        }
        return peek;
    }

    private static final class TokenStream {

        private final List<Token> tokens;
        private int index = 0;

        private TokenStream(List<Token> tokens) {
            this.tokens = tokens;
        }

        /**
         * Returns true if there is a token at index + offset.
         */
        public boolean has(int offset) {
            return index + offset < tokens.size();
        }

        /**
         * Gets the token at index + offset.
         */
        public Token get(int offset) {
            return tokens.get(index + offset);
        }

        /**
         * Advances to the next token, incrementing the index.
         */
        public void advance() {
            index++;
        }

    }

}
