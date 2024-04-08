package plc.project;

import java.io.PrintWriter;

public final class Generator implements Ast.Visitor<Void> {

    private final PrintWriter writer;
    private int indent = 0;

    public Generator(PrintWriter writer) {
        this.writer = writer;
    }

    private void print(Object... objects) {
        for (Object object : objects) {
            if (object instanceof Ast) {
                visit((Ast) object);
            } else {
                writer.write(object.toString());
            }
        }
    }

    private void newline(int indent) {
        writer.println();
        for (int i = 0; i < indent; i++) {
            writer.write("    ");
        }
    }

    @Override
    public Void visit(Ast.Source ast) {
        return null;
    }

    @Override
    public Void visit(Ast.Global ast) {
        throw new UnsupportedOperationException(); //TODO
    }

    @Override
    public Void visit(Ast.Function ast) {
        throw new UnsupportedOperationException(); //TODO
    }

    @Override
    public Void visit(Ast.Statement.Expression ast) {
        visit(ast.getExpression());
        print(";");
        return null;
    }

    @Override
    public Void visit(Ast.Statement.Declaration ast) {
        throw new UnsupportedOperationException(); //TODO
    }

    @Override
    public Void visit(Ast.Statement.Assignment ast) {
        throw new UnsupportedOperationException(); //TODO
    }

    @Override
    public Void visit(Ast.Statement.If ast) {
        print("if ");
        wrapParens(ast.getCondition());
        print(" {");

        if (!ast.getThenStatements().isEmpty()) {
            newline(1);
            int[] index = {0};
            ast.getThenStatements().forEach(stmt -> {
                index[0]++;
                visit(stmt);
                if (index[0] != ast.getThenStatements().size()) {
                    newline(1);
                } else {
                    newline(0);
                }
            });
            print("}");
        } else {
            newline(0);
            print("}");
        }

        if (!ast.getElseStatements().isEmpty()) {
            print(" else {");
            newline(1);
            int[] index = {0};
            ast.getElseStatements().forEach(stmt -> {
                index[0]++;
                visit(stmt);
                if (index[0] != ast.getThenStatements().size()) {
                    newline(1);
                } else {
                    newline(0);
                }
            });
            print("}");
        }
        return null;
    }

    @Override
    public Void visit(Ast.Statement.Switch ast) {
        throw new UnsupportedOperationException(); //TODO
    }

    @Override
    public Void visit(Ast.Statement.Case ast) {
        throw new UnsupportedOperationException(); //TODO
    }

    @Override
    public Void visit(Ast.Statement.While ast) {
        throw new UnsupportedOperationException(); //TODO
    }

    @Override
    public Void visit(Ast.Statement.Return ast) {
        print("return ");
        visit(ast.getValue());
        print(";");
        return null;
    }

    @Override
    public Void visit(Ast.Expression.Literal ast) {
        // For strings and characters have to print literal ' & "
        if (ast.getLiteral() instanceof String) {
            print("\"");
            print(ast.getLiteral());
            print("\"");
        } else if (ast.getLiteral() instanceof Character) {
            print("'");
            print(ast.getLiteral());
            print("'");
        } else if (ast.getLiteral() != null) {
            print(ast.getLiteral());
        } else {
            print("null");
        }
        return null;
    }

    @Override
    public Void visit(Ast.Expression.Group ast) {
        wrapParens(ast.getExpression());
        return null;
    }

    @Override
    public Void visit(Ast.Expression.Binary ast) {
        if (ast.getOperator().equals("^")) {
            // Math.pow(left, right)
            print("Math.pow(");
            visit(ast.getLeft());
            print(", ");
            visit(ast.getRight());
            print(")");
            return null;
        }
        visit(ast.getLeft());
        print(" " + ast.getOperator() + " ");
        visit(ast.getRight());
        return null;
    }

    @Override
    public Void visit(Ast.Expression.Access ast) {
        print(ast.getVariable().getJvmName());
        if (ast.getOffset().isPresent()) {
            print("[");
            visit(ast.getOffset().get());
            print("]");
        }
        return null;
    }

    @Override
    public Void visit(Ast.Expression.Function ast) {
        print(ast.getFunction().getJvmName());
        print("(");
        int[] index = {0};
        if (!ast.getArguments().isEmpty()) {
            ast.getArguments().forEach(exp -> {
                index[0]++;
                visit(exp);
                if (index[0] != ast.getArguments().size()) {
                    print(", ");
                }
            });
        }
        print(")");
        return null;
    }

    @Override
    public Void visit(Ast.Expression.PlcList ast) {
        print("{");
        int[] index = {0};
        if (!ast.getValues().isEmpty()) {
            ast.getValues().forEach(exp -> {
                index[0]++;
                visit(exp);
                if (index[0] != ast.getValues().size()) {
                    print(", ");
                }
            });
        }
        print("}");
        return null;
    }

    private Void wrapParens(Ast.Expression exp) {
        print("(");
        visit(exp);
        print(")");
        return null;
    }
}
