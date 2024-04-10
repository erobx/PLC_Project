package plc.project;

import java.io.PrintWriter;
import java.util.List;

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
        print(ast.getFunction().getReturnType().getJvmName() + " " + ast.getFunction().getName() + "(");

        int[] index = {0};
        if (!ast.getParameters().isEmpty()) {
            ast.getFunction().getParameterTypes().forEach(type -> {
                String name = ast.getParameters().get(index[0]);
                index[0]++;
                print(type.getJvmName() + " ");
                print(name);
                if (index[0] != ast.getParameters().size()) {
                    print(", ");
                }
            });
        }
        print(") {");
        if (ast.getStatements().isEmpty()) {
            print("}"); return null;
        }

        printStatements(ast.getStatements());
        print("}");

        return null;
    }

    @Override
    public Void visit(Ast.Statement.Expression ast) {
        visit(ast.getExpression());
        print(";");
        return null;
    }

    @Override
    public Void visit(Ast.Statement.Declaration ast) {
        print(ast.getVariable().getType().getJvmName());
        print(" " + ast.getVariable().getJvmName());

        if (ast.getValue().isPresent()) {
            print(" = ");
            visit(ast.getValue().get());
        }

        print(";");
        return null;
    }

    @Override
    public Void visit(Ast.Statement.Assignment ast) {
        visit(ast.getReceiver());
        print(" = ");
        visit(ast.getValue());
        print(";");
        return null;
    }

    @Override
    public Void visit(Ast.Statement.If ast) {
        print("if ");
        wrapParens(ast.getCondition());
        print(" {");

        if (ast.getThenStatements().isEmpty()) {
            print("}");
            return null;
        }

        printStatements(ast.getThenStatements());
        print("}");

        if (!ast.getElseStatements().isEmpty()) {
            print(" else {");
            printStatements(ast.getElseStatements());
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
        print("while ");
        wrapParens(ast.getCondition());
        print(" {");
        if (ast.getStatements().isEmpty()) {
            print("}");
            return null;
        }

        printStatements(ast.getStatements());
        print("}");

        return null;
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
        if (!ast.getArguments().isEmpty()) {
            printExpressions(ast.getArguments());
        }
        print(")");
        return null;
    }

    @Override
    public Void visit(Ast.Expression.PlcList ast) {
        print("{");
        if (!ast.getValues().isEmpty()) {
            printExpressions(ast.getValues());
        }
        print("}");
        return null;
    }

    private void wrapParens(Ast.Expression exp) {
        print("(");
        visit(exp);
        print(")");
    }

    private void printExpressions(List<Ast.Expression> exps) {
        int[] index = {0};
        exps.forEach(exp -> {
            index[0]++;
            visit(exp);
            if (index[0] != exps.size()) {
                print(", ");
            }
        });
    }

    private void printStatements(List<Ast.Statement> statements) {
        newline(++indent);
        int[] index = {0};
        statements.forEach(stmt -> {
            index[0]++;
            visit(stmt);
            if (index[0] != statements.size()) {
                newline(indent);
            } else {
                newline(--indent);
            }
        });
    }
}
