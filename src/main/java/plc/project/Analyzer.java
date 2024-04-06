package plc.project;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;
import java.util.List;

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
        if (!(ast.getExpression() instanceof Ast.Expression.Function)) {
            throw new RuntimeException("Expression is not of class Ast.Expression.Function");
        }
        visit(ast.getExpression());
        return null;
    }

    @Override
    public Void visit(Ast.Statement.Declaration ast) {
        Environment.Variable var;
        Environment.PlcObject value = Environment.NIL;
        RuntimeException nameEx = new RuntimeException("Variable name and jvmName do not match");
        // LET x: Integer = 1;
        if (ast.getTypeName().isPresent() && ast.getValue().isPresent()) {
            visit(ast.getValue().get());
            String typeName = ast.getTypeName().get();

            var = new Environment.Variable(ast.getName(), ast.getName(), Environment.getType(typeName), true, value);
            if (checkNames(var)) throw nameEx;
            requireAssignable(var.getType(), ast.getValue().get().getType());

            scope.defineVariable(var.getName(), var.getJvmName(), var.getType(), var.getMutable(), var.getValue());
        } else if (ast.getTypeName().isPresent()) {
            String typeName = ast.getTypeName().get();
            var = new Environment.Variable(ast.getName(), ast.getName(), Environment.getType(typeName), true, value);

            if (checkNames(var)) throw nameEx;
            scope.defineVariable(var.getName(), var.getJvmName(), var.getType(), var.getMutable(), var.getValue());
        } else if (ast.getValue().isPresent()) {
            visit(ast.getValue().get());
            var = new Environment.Variable(ast.getName(), ast.getName(), ast.getValue().get().getType(), true, value);

            if (checkNames(var)) throw nameEx;
            scope.defineVariable(var.getName(), var.getJvmName(), var.getType(), var.getMutable(), var.getValue());
        } else {
                throw new RuntimeException("Type not specified");
        }
        ast.setVariable(var);
        return null;
    }

    public boolean checkNames(Environment.Variable var) {
        return (!var.getName().equals(var.getJvmName()));
    }

    @Override
    public Void visit(Ast.Statement.Assignment ast) {
        if (!(ast.getReceiver() instanceof Ast.Expression.Access)) {
            throw new RuntimeException("Receiver is not of class Ast.Expression.Access");
        }
        visit(ast.getReceiver()); visit(ast.getValue());
        requireAssignable(ast.getReceiver().getType(), ast.getValue().getType());
        return null;
    }

    @Override
    public Void visit(Ast.Statement.If ast) {
        visit(ast.getCondition());
        if (!ast.getCondition().getType().equals(Environment.Type.BOOLEAN)) {
            throw new RuntimeException("Expected boolean");
        }
        if (ast.getThenStatements().isEmpty()) {
            throw new RuntimeException("Missing statements");
        }
        try {
            scope = new Scope(scope);
            ast.getThenStatements().forEach(this::visit);
        } finally {
            scope = scope.getParent();
        }
        return null;
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
        return null;
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
        ast.setType(ast.getExpression().getType());
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
        ast.getArguments().forEach(this::visit);

        List<Environment.Type> types = new ArrayList<>();
        ast.getArguments().forEach(exp -> {
            types.add(exp.getType());
        });
        for (int i = 0; i < types.size(); i++) {
            requireAssignable(func.getParameterTypes().get(i), types.get(i));
        }

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
