package plc.project;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class Interpreter implements Ast.Visitor<Environment.PlcObject> {

    private Scope scope = new Scope(null);

    public Interpreter(Scope parent) {
        scope = new Scope(parent);
        scope.defineFunction("print", 1, args -> {
            System.out.println(args.get(0).getValue());
            return Environment.NIL;
        });
    }

    public Scope getScope() {
        return scope;
    }

    @Override
    public Environment.PlcObject visit(Ast.Source ast) {
        throw new UnsupportedOperationException(); //TODO
    }

    @Override
    public Environment.PlcObject visit(Ast.Global ast) {
        Optional<Ast.Expression> values = ast.getValue();
        Environment.PlcObject obj = values.map(this::visit).orElseGet(() -> Environment.NIL);
        scope.getParent().defineVariable(ast.getName(), ast.getMutable(), obj);
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Function ast) {
        throw new UnsupportedOperationException(); //TODO
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.Expression ast) {
        throw new UnsupportedOperationException(); //TODO
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.Declaration ast) {
        throw new UnsupportedOperationException(); //TODO (in lecture)
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.Assignment ast) {
        throw new UnsupportedOperationException(); //TODO
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.If ast) {
        throw new UnsupportedOperationException(); //TODO
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.Switch ast) {
        throw new UnsupportedOperationException(); //TODO
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.Case ast) {
        throw new UnsupportedOperationException(); //TODO
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.While ast) {
        throw new UnsupportedOperationException(); //TODO (in lecture)
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.Return ast) {
        throw new UnsupportedOperationException(); //TODO
    }

    @Override
    public Environment.PlcObject visit(Ast.Expression.Literal ast) {
        return ast.getLiteral() == null ? Environment.NIL : Environment.create(ast.getLiteral());
    }

    @Override
    public Environment.PlcObject visit(Ast.Expression.Group ast) {
        System.out.println(ast);
        return visit(ast.getExpression());
    }

    @Override
    public Environment.PlcObject visit(Ast.Expression.Binary ast) {
        Ast.Expression lhs = ast.getLeft();
        Ast.Expression rhs = ast.getRight();
        Object checkClass = visit(lhs).getValue().getClass();
        RuntimeException err = new RuntimeException("Invalid class type");
        switch (ast.getOperator()) {
            case "&&":
                if (checkClass.equals(Boolean.class)) {
                    Boolean leftVal = requireType(Boolean.class, visit(lhs));
                    Boolean rightVal = requireType(Boolean.class, visit(rhs));
                    Boolean compare = leftVal.booleanValue() == rightVal.booleanValue();
                    return Environment.create(compare);
                }
                throw err;
            case "||":
                if (checkClass.equals(Boolean.class)) {
                    Boolean leftVal = requireType(Boolean.class, visit(lhs));
                    if (leftVal) {
                        return Environment.create(leftVal);
                    }
                    Boolean rightVal = requireType(Boolean.class, visit(rhs));
                    if (rightVal) {
                        return Environment.create(rightVal);
                    }
                }
                throw err;
            case "+":
                // Check if LHS is String
                if (checkClass.equals(String.class)) {
                    String leftVal = requireType(String.class, visit(lhs));
                    String rightVal = requireType(String.class, visit(rhs));
                    String concat = leftVal + rightVal;
                    return Environment.create(concat);
                }
                if (checkClass.equals(BigInteger.class)) {
                    return Environment.create(bigIntegerAdd(lhs, rhs));
                }
                if (checkClass.equals(BigDecimal.class)) {
                    return Environment.create(bigDecimalAdd(lhs, rhs));
                }
                throw err;
            case "-":
                if (checkClass.equals(BigInteger.class)) {
                    return Environment.create(bigIntegerSub(lhs, rhs));
                }
                if (checkClass.equals(BigDecimal.class)) {
                    return Environment.create(bigDecimalSub(lhs, rhs));
                }
                throw err;
            case "*":
                if (checkClass.equals(BigInteger.class)) {
                   return Environment.create(bigIntegerMult(lhs, rhs));
                }
                if (checkClass.equals(BigDecimal.class)) {
                    return Environment.create(bigDecimalMult(lhs, rhs));
                }
                throw err;
            case "/":
                if (checkClass.equals(BigInteger.class)) {
                    return Environment.create(bigIntegerDiv(lhs, rhs));
                }
                if (checkClass.equals(BigDecimal.class)) {
                    return Environment.create(bigDecimalDiv(lhs, rhs));
                }
                throw err;
            case "^":
                if (checkClass.equals(BigInteger.class)) {
                    return Environment.create(bigIntegerExp(lhs, rhs));
                }
                throw err;
            default:
                throw err;
        }
    }

    private BigInteger bigIntegerAdd(Ast.Expression lhs, Ast.Expression rhs) {
        BigInteger leftVal = requireType(BigInteger.class, visit(lhs));
        BigInteger rightVal = requireType(BigInteger.class, visit(rhs));
        return leftVal.add(rightVal);
    }

    private BigInteger bigIntegerSub(Ast.Expression lhs, Ast.Expression rhs) {
        BigInteger leftVal = requireType(BigInteger.class, visit(lhs));
        BigInteger rightVal = requireType(BigInteger.class, visit(rhs));
        return leftVal.subtract(rightVal);
    }

    private BigInteger bigIntegerMult(Ast.Expression lhs, Ast.Expression rhs) {
        BigInteger leftVal = requireType(BigInteger.class, visit(lhs));
        BigInteger rightVal = requireType(BigInteger.class, visit(rhs));
        return leftVal.multiply(rightVal);
    }

    private BigInteger bigIntegerDiv(Ast.Expression lhs, Ast.Expression rhs) {
        BigInteger leftVal = requireType(BigInteger.class, visit(lhs));
        BigInteger rightVal = requireType(BigInteger.class, visit(rhs));
        return leftVal.divide(rightVal);
    }

    private BigInteger bigIntegerExp(Ast.Expression lhs, Ast.Expression rhs) {
        BigInteger leftVal = requireType(BigInteger.class, visit(lhs));
        BigInteger rightVal = requireType(BigInteger.class, visit(rhs));
        return leftVal.pow(rightVal.intValue());
    }

    private BigDecimal bigDecimalAdd(Ast.Expression lhs, Ast.Expression rhs) {
        BigDecimal leftVal = requireType(BigDecimal.class, visit(lhs));
        BigDecimal rightVal = requireType(BigDecimal.class, visit(rhs));
        return leftVal.add(rightVal);
    }

    private BigDecimal bigDecimalSub(Ast.Expression lhs, Ast.Expression rhs) {
        BigDecimal leftVal = requireType(BigDecimal.class, visit(lhs));
        BigDecimal rightVal = requireType(BigDecimal.class, visit(rhs));
        return leftVal.subtract(rightVal);
    }

    private BigDecimal bigDecimalMult(Ast.Expression lhs, Ast.Expression rhs) {
        BigDecimal leftVal = requireType(BigDecimal.class, visit(lhs));
        BigDecimal rightVal = requireType(BigDecimal.class, visit(rhs));
        return leftVal.multiply(rightVal);
    }

    private BigDecimal bigDecimalDiv(Ast.Expression lhs, Ast.Expression rhs) {
        BigDecimal leftVal = requireType(BigDecimal.class, visit(lhs));
        BigDecimal rightVal = requireType(BigDecimal.class, visit(rhs));
        return leftVal.divide(rightVal, RoundingMode.HALF_EVEN);
    }

    @Override
    public Environment.PlcObject visit(Ast.Expression.Access ast) {
        Optional<Ast.Expression> exp = ast.getOffset();

        if (exp.isPresent()) {
            Environment.PlcObject value = exp.map(this::visit).orElseGet(() -> Environment.NIL);
            BigInteger temp = requireType(BigInteger.class, value);
            int offset = temp.intValue();

            Environment.Variable var = scope.lookupVariable(ast.getName());
            List list = requireType(List.class, var.getValue());

            if (offset < 0 || offset > list.size()-1) {
                throw new RuntimeException("Invalid offset");
            }

            Object access = list.get(offset);
            System.out.println(access);

            return Environment.create(access);
        }

        Object returnValue = scope.lookupVariable(ast.getName()).getValue().getValue();
        return Environment.create(returnValue);
    }

    @Override
    public Environment.PlcObject visit(Ast.Expression.Function ast) {
       throw new UnsupportedOperationException(); //TODO
    }

    @Override
    public Environment.PlcObject visit(Ast.Expression.PlcList ast) {
        List<Object> objects = new ArrayList<>();
        for (Ast.Expression exp : ast.getValues()) {
            objects.add(visit(exp).getValue());
        }
        return Environment.create(objects);
    }

    /**
     * Helper function to ensure an object is of the appropriate type.
     */
    private static <T> T requireType(Class<T> type, Environment.PlcObject object) {
        if (type.isInstance(object.getValue())) {
            return type.cast(object.getValue());
        } else {
            throw new RuntimeException("Expected type " + type.getName() + ", received " + object.getValue().getClass().getName() + ".");
        }
    }

    /**
     * Exception class for returning values.
     */
    private static class Return extends RuntimeException {

        private final Environment.PlcObject value;

        private Return(Environment.PlcObject value) {
            this.value = value;
        }

    }

}
