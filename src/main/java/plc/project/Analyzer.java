package plc.project;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * See the specification for information about what the different visit
 * methods should do.
 */
public final class Analyzer implements Ast.Visitor<Void> {

    public Scope scope;
    private Ast.Function function;

    public Analyzer(Scope parent) {
        scope = new Scope(parent);
        scope.defineFunction("print", "System.out.println", Arrays.asList(Environment.Type.ANY), Environment.Type.NIL, args -> Environment.NIL);
    }

    public Scope getScope() {
        return scope;
    }

    @Override
    public Void visit(Ast.Source ast) {
        throw new UnsupportedOperationException();  // TODO
    }

    @Override
    public Void visit(Ast.Global ast) {
        throw new UnsupportedOperationException();  // TODO
    }

    @Override
    public Void visit(Ast.Function ast) {
        throw new UnsupportedOperationException();  // TODO
    }

    @Override
    public Void visit(Ast.Statement.Expression ast) {
        throw new UnsupportedOperationException();  // TODO
    }

    @Override
    public Void visit(Ast.Statement.Declaration ast) {
        throw new UnsupportedOperationException();  // TODO
    }

    @Override
    public Void visit(Ast.Statement.Assignment ast) {
        throw new UnsupportedOperationException();  // TODO
    }

    @Override
    public Void visit(Ast.Statement.If ast) {
        throw new UnsupportedOperationException();  // TODO
    }

    @Override
    public Void visit(Ast.Statement.Switch ast) {
        throw new UnsupportedOperationException();  // TODO
    }

    @Override
    public Void visit(Ast.Statement.Case ast) {
        throw new UnsupportedOperationException();  // TODO
    }

    @Override
    public Void visit(Ast.Statement.While ast) {
        throw new UnsupportedOperationException();  // TODO
    }

    @Override
    public Void visit(Ast.Statement.Return ast) {
        throw new UnsupportedOperationException();  // TODO
    }

    @Override
    public Void visit(Ast.Expression.Literal ast) {
        Object literal = ast.getLiteral();
        if (literal instanceof String) {
            ast.setType(Environment.Type.STRING);
        } else if (Objects.isNull(literal)) {
            ast.setType(Environment.Type.NIL);
        } else if (literal instanceof Boolean) {
            ast.setType(Environment.Type.BOOLEAN);
        } else if (literal instanceof Character) {
            ast.setType(Environment.Type.CHARACTER);
        } else if (literal instanceof BigInteger temp) {
            if (temp.bitLength() > 32) {
                throw new RuntimeException("Value out of range of 32 bits");
            }
            ast.setType(Environment.Type.INTEGER);
        } else if (literal instanceof BigDecimal temp) {
            if (temp.doubleValue() == Double.POSITIVE_INFINITY || temp.doubleValue() == Double.NEGATIVE_INFINITY) {
                throw new RuntimeException("Value of of range of 64 bits");
            }
            ast.setType(Environment.Type.DECIMAL);
        }
        return null;
    }

    @Override
    public Void visit(Ast.Expression.Group ast) {
        if (!(ast.getExpression() instanceof Ast.Expression.Binary)) {
            throw new RuntimeException("Not binary expression.");
        }
        visit(ast.getExpression());
        ast.setType(ast.getType());
        return null;
    }

    @Override
    public Void visit(Ast.Expression.Binary ast) {
        visit(ast.getLeft()); visit(ast.getRight());
        Environment.Type leftType = ast.getLeft().getType();
        Environment.Type rightType = ast.getRight().getType();

        switch (ast.getOperator()) {
            case "&&", "||":
                if (leftType.equals(Environment.Type.BOOLEAN)) {
                    if (!rightType.equals(Environment.Type.BOOLEAN)) {
                        throw new RuntimeException("Expected boolean");
                    }
                    ast.setType(Environment.Type.BOOLEAN);
                } else {
                    throw new RuntimeException("Expected boolean");
                }
                break;
            case "<", ">", "==", "!=":
                RuntimeException ex = new RuntimeException("Unexpected type");
                if (leftType.equals(Environment.Type.INTEGER)) {
                    if (!rightType.equals(Environment.Type.INTEGER)) {
                        throw ex;
                    }
                } else if (leftType.equals(Environment.Type.DECIMAL)) {
                    if (!rightType.equals(Environment.Type.DECIMAL)) {
                        throw ex;
                    }
                } else if (leftType.equals(Environment.Type.CHARACTER)) {
                    if (!rightType.equals(Environment.Type.CHARACTER)) {
                        throw ex;
                    }
                } else if (leftType.equals(Environment.Type.STRING)) {
                    if (!rightType.equals(Environment.Type.STRING)) {
                        throw ex;
                    }
                } else {
                    throw ex;
                }
                ast.setType(Environment.Type.BOOLEAN);
                break;
            case "+":
                if (leftType.equals(Environment.Type.STRING) || rightType.equals(Environment.Type.STRING)) {
                    ast.setType(Environment.Type.STRING);
                } else if (leftType.equals(Environment.Type.INTEGER)) {
                    if (!rightType.equals(Environment.Type.INTEGER)) {
                        throw new RuntimeException("Expected integer");
                    }
                    ast.setType(Environment.Type.INTEGER);
                } else if (leftType.equals(Environment.Type.DECIMAL)) {
                    if (!rightType.equals(Environment.Type.DECIMAL)) {
                        throw new RuntimeException("Expected decimal");
                    }
                    ast.setType(Environment.Type.DECIMAL);
                } else {
                    throw new RuntimeException("Unexpected type");
                }
                break;
            case "-", "*", "/":
                if (leftType.equals(Environment.Type.INTEGER)) {
                    if (!rightType.equals(Environment.Type.INTEGER)) {
                        throw new RuntimeException("Expected integer");
                    }
                    ast.setType(Environment.Type.INTEGER);
                } else if (leftType.equals(Environment.Type.DECIMAL)) {
                    if (!rightType.equals(Environment.Type.DECIMAL)) {
                        throw new RuntimeException("Expected decimal");
                    }
                    ast.setType(Environment.Type.DECIMAL);
                } else {
                    throw new RuntimeException("Unexpected type");
                }
                break;
            case "^":
                if (!leftType.equals(Environment.Type.INTEGER)) {
                    throw new RuntimeException("Expected integer");
                }
                if (!rightType.equals(Environment.Type.INTEGER)) {
                    throw new RuntimeException("Expected integer");
                }
                ast.setType(Environment.Type.INTEGER);
                break;
        }
        return null;
    }

    @Override
    public Void visit(Ast.Expression.Access ast) {
        if (ast.getOffset().isPresent()) {
            Ast.Expression offset = ast.getOffset().get();
            visit(offset);
            if (offset.getType() == null) {
                throw new RuntimeException("Offset not an integer.");
            }
        }
        Environment.Variable var = scope.lookupVariable(ast.getName());
        ast.setVariable(var);
        return null;
    }

    @Override
    public Void visit(Ast.Expression.Function ast) {
        Environment.Function func = scope.lookupFunction(ast.getName(), ast.getArguments().size());
        ast.setFunction(func);
        return null;
    }

    @Override
    public Void visit(Ast.Expression.PlcList ast) {
        throw new UnsupportedOperationException();  // TODO
    }

    public static void requireAssignable(Environment.Type target, Environment.Type type) {
        // Assign every type to any
        if (target.getName().equals(Environment.Type.ANY.getName())) {
            return;
        }
        // Comparable
        if (target.getName().equals(Environment.Type.COMPARABLE.getName())) {
            switch (type.getName()) {
                case "Integer":
                    return;
                case "Decimal":
                    return;
                case "Character":
                    return;
                case "String":
                    return;
                default:
                    throw new RuntimeException("Expected type " + target.getName() + ", received " + type.getName() + ".");
            }
        }
        // Equal types
        if (!target.getName().equals(type.getName())) {
            throw new RuntimeException("Expected type " + target.getName() + ", received " + type.getName() + ".");
        }
    }
}
