package plc.project;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

public class Interpreter implements Ast.Visitor<Environment.PlcObject> {

    private Scope scope = new Scope(null);

    private Scope funcScope;

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
        // Evaluate globals then functions
        ast.getGlobals().forEach(this::visit);
        ast.getFunctions().forEach(this::visit);
        return scope.lookupFunction("main", 0).invoke(Arrays.asList());
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
        if (ast.getName().equals("main") && !ast.getParameters().isEmpty()) {
            throw new RuntimeException("Invalid main arity");
        }
        scope.defineFunction(ast.getName(), ast.getParameters().size(), args -> {
            scope = new Scope(scope);

            for (String p : ast.getParameters()) {
                Environment.Variable v;
                try {
                    v = scope.lookupVariable(p);
                } catch (Exception ex) {
                    v = new Environment.Variable(p, true, Environment.create(args.getFirst().getValue()));
                    args.removeFirst();
                }
                scope.defineVariable(v.getName(), v.getMutable(), v.getValue());
            }

            // Evaluate function statements => return value in Return exception if thrown or NIL if not
            try {
                ast.getStatements().forEach(this::visit);
            } catch (Return ex) {
                return ex.value;
            } finally {
                // Return to parent scope
                scope = scope.getParent();
            }
            return Environment.NIL;
        });
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.Expression ast) {
        visit(ast.getExpression());
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.Declaration ast) {
        // Source: Peter Dobbins Lecture 25 25:05
        if (ast.getValue().isPresent()) {
            scope.defineVariable(ast.getName(), true, visit(ast.getValue().get()));
        } else {
            scope.defineVariable(ast.getName(), true, Environment.NIL);
        }
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.Assignment ast) {
        try {
            Ast.Expression.Access receiver = (Ast.Expression.Access) ast.getReceiver();
            Environment.PlcObject value = visit(ast.getValue());

            // Dealing with lists, super jank stuff
            if (receiver.getOffset().isPresent()) {
                Ast.Expression tempOffset = receiver.getOffset().get();
                BigInteger bigOffset = requireType(BigInteger.class, visit(tempOffset));
                int offset = bigOffset.intValue();

                Environment.PlcObject obj = scope.lookupVariable(receiver.getName()).getValue();
                List list = requireType(List.class, obj);
                list.set(offset, value.getValue());

                scope.lookupVariable(receiver.getName()).setValue(Environment.create(list));
                return Environment.NIL;
            }

            if (!scope.lookupVariable(receiver.getName()).getMutable()) {
                throw new RuntimeException("Immutable variable");
            }

            scope.lookupVariable(receiver.getName()).setValue(value);
        } catch (RuntimeException ex) {
            throw new RuntimeException(ex);
        }

        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.If ast) {
        Boolean condition = requireType(Boolean.class, visit(ast.getCondition()));
        try {
            scope = new Scope(scope);
            if (condition) {
                ast.getThenStatements().forEach(this::visit);
            } else {
                ast.getElseStatements().forEach(this::visit);
            }
        } finally {
            scope = scope.getParent();
        }
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.Switch ast) {
        Object conditionValue = visit(ast.getCondition()).getValue();
        List<Ast.Statement.Case> cases = ast.getCases();

        for (Ast.Statement.Case c : cases) {
            if (c.getValue().isPresent()) {
                Environment.PlcObject temp = c.getValue().map(this::visit).orElseGet(() -> Environment.NIL);
                if (temp.getValue().equals(conditionValue)) {
                    return visit(c);
                }
            }
        }
        return visit(cases.getLast());
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.Case ast) {
        // Evaluate statements
        ast.getStatements().forEach(this::visit);
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.While ast) {
        // Source: Peter Dobbins Lecture 26 42:30
        while (requireType(Boolean.class, visit(ast.getCondition()))) {
           try {
               scope = new Scope(scope);
               ast.getStatements().forEach(this::visit);
           } finally {
               scope = scope.getParent();
           }
        }
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.Return ast) {
        throw new Return(visit(ast.getValue()));
    }

    @Override
    public Environment.PlcObject visit(Ast.Expression.Literal ast) {
        return ast.getLiteral() == null ? Environment.NIL : Environment.create(ast.getLiteral());
    }

    @Override
    public Environment.PlcObject visit(Ast.Expression.Group ast) {
        return visit(ast.getExpression());
    }

    @Override
    public Environment.PlcObject visit(Ast.Expression.Binary ast) {
        Ast.Expression lhs = ast.getLeft();
        Ast.Expression rhs = ast.getRight();
        Environment.PlcObject lhsValue = visit(lhs);
        Object checkClass = lhsValue.getValue().getClass();
        RuntimeException err = new RuntimeException("Invalid class type");
        switch (ast.getOperator()) {
            case "&&":
                if (checkClass.equals(Boolean.class)) {
                    Boolean leftVal = requireType(Boolean.class, lhsValue);
                    if (!leftVal) {
                        return Environment.create(Boolean.FALSE);
                    }
                    Boolean rightVal = requireType(Boolean.class, visit(rhs));
                    Boolean compare = leftVal.booleanValue() == rightVal.booleanValue();
                    return Environment.create(compare);
                }
                throw err;
            case "||":
                if (checkClass.equals(Boolean.class)) {
                    Boolean leftVal = requireType(Boolean.class, lhsValue);
                    if (leftVal) {
                        return Environment.create(Boolean.TRUE);
                    }
                    Boolean rightVal = requireType(Boolean.class, visit(rhs));
                    if (rightVal) {
                        return Environment.create(Boolean.TRUE);
                    }
                    return Environment.create(Boolean.FALSE);
                }
                throw err;
            case "<":
                if (checkClass.equals(BigInteger.class)) {
                    BigInteger leftVal = requireType(BigInteger.class, lhsValue);
                    BigInteger rightVal = requireType(BigInteger.class, visit(rhs));
                    Boolean compare = leftVal.compareTo(rightVal) < 0;
                    return Environment.create(compare);
                }
                if (checkClass.equals(BigDecimal.class)) {
                    BigDecimal leftVal = requireType(BigDecimal.class, lhsValue);
                    BigDecimal rightVal = requireType(BigDecimal.class, visit(rhs));
                    Boolean compare = leftVal.compareTo(rightVal) < 0;
                    return Environment.create(compare);
                }
                if (checkClass.equals(Boolean.class)) {
                    Boolean leftVal = requireType(Boolean.class, lhsValue);
                    Boolean rightVal = requireType(Boolean.class, visit(rhs));
                    Boolean compare = leftVal.compareTo(rightVal) < 0;
                    return Environment.create(compare);
                }
                if (checkClass.equals(String.class)) {
                    String leftVal = requireType(String.class, lhsValue);
                    String rightVal = requireType(String.class, visit(rhs));
                    Boolean compare = leftVal.compareTo(rightVal) < 0;
                    return Environment.create(compare);
                }
                throw err;
            case ">":
                if (checkClass.equals(BigInteger.class)) {
                    BigInteger leftVal = requireType(BigInteger.class, lhsValue);
                    BigInteger rightVal = requireType(BigInteger.class, visit(rhs));
                    Boolean compare = leftVal.compareTo(rightVal) > 0;
                    return Environment.create(compare);
                }
                if (checkClass.equals(BigDecimal.class)) {
                    BigDecimal leftVal = requireType(BigDecimal.class, lhsValue);
                    BigDecimal rightVal = requireType(BigDecimal.class, visit(rhs));
                    Boolean compare = leftVal.compareTo(rightVal) > 0;
                    return Environment.create(compare);
                }
                if (checkClass.equals(Boolean.class)) {
                    Boolean leftVal = requireType(Boolean.class, lhsValue);
                    Boolean rightVal = requireType(Boolean.class, visit(rhs));
                    Boolean compare = leftVal.compareTo(rightVal) > 0;
                    return Environment.create(compare);
                }
                if (checkClass.equals(String.class)) {
                    String leftVal = requireType(String.class, lhsValue);
                    String rightVal = requireType(String.class, visit(rhs));
                    Boolean compare = leftVal.compareTo(rightVal) > 0;
                    return Environment.create(compare);
                }
                throw err;
            case "==":
                return Environment.create(Objects.equals(lhsValue.getValue(), visit(rhs).getValue()));
            case "!=":
                return Environment.create(!Objects.equals(lhsValue.getValue(), visit(rhs).getValue()));
            case "+":
                if (checkClass.equals(BigInteger.class)) {
                    return Environment.create(bigIntegerAdd(lhsValue, visit(rhs)));
                }
                if (checkClass.equals(BigDecimal.class)) {
                    return Environment.create(bigDecimalAdd(lhsValue, visit(rhs)));
                }
                // If either expression is a String concat
                if (checkClass.equals(String.class) || visit(rhs).getValue().getClass().equals(String.class)) {
                    String leftVal = visit(lhs).getValue().toString();
                    String rightVal = visit(rhs).getValue().toString();
                    String concat = leftVal.concat(rightVal);
                    return Environment.create(concat);
                }
                throw err;
            case "-":
                if (checkClass.equals(BigInteger.class)) {
                    return Environment.create(bigIntegerSub(lhsValue, visit(rhs)));
                }
                if (checkClass.equals(BigDecimal.class)) {
                    return Environment.create(bigDecimalSub(lhsValue, visit(rhs)));
                }
                throw err;
            case "*":
                if (checkClass.equals(BigInteger.class)) {
                   return Environment.create(bigIntegerMult(lhsValue, visit(rhs)));
                }
                if (checkClass.equals(BigDecimal.class)) {
                    return Environment.create(bigDecimalMult(lhsValue, visit(rhs)));
                }
                throw err;
            case "/":
                try {
                    if (checkClass.equals(BigInteger.class)) {
                        return Environment.create(bigIntegerDiv(lhsValue, visit(rhs)));
                    }
                    if (checkClass.equals(BigDecimal.class)) {
                        return Environment.create(bigDecimalDiv(lhsValue, visit(rhs)));
                    }
                } catch (ArithmeticException ex) {
                    throw new RuntimeException(ex.getMessage());
                }
            case "^":
                if (checkClass.equals(BigInteger.class)) {
                    return Environment.create(bigIntegerExp(lhsValue, visit(rhs)));
                }
                throw err;
            default:
                throw err;
        }
    }

    private BigInteger bigIntegerAdd(Environment.PlcObject lhs, Environment.PlcObject rhs) {
        BigInteger leftVal = requireType(BigInteger.class, lhs);
        BigInteger rightVal = requireType(BigInteger.class, rhs);
        return leftVal.add(rightVal);
    }

    private BigInteger bigIntegerSub(Environment.PlcObject lhs, Environment.PlcObject rhs) {
        BigInteger leftVal = requireType(BigInteger.class, lhs);
        BigInteger rightVal = requireType(BigInteger.class, rhs);
        return leftVal.subtract(rightVal);
    }

    private BigInteger bigIntegerMult(Environment.PlcObject lhs, Environment.PlcObject rhs) {
        BigInteger leftVal = requireType(BigInteger.class, lhs);
        BigInteger rightVal = requireType(BigInteger.class, rhs);
        return leftVal.multiply(rightVal);
    }

    private BigInteger bigIntegerDiv(Environment.PlcObject lhs, Environment.PlcObject rhs) {
        BigInteger leftVal = requireType(BigInteger.class, lhs);
        BigInteger rightVal = requireType(BigInteger.class, rhs);
        return leftVal.divide(rightVal);
    }

    private BigInteger bigIntegerExp(Environment.PlcObject lhs, Environment.PlcObject rhs) {
        BigInteger leftVal = requireType(BigInteger.class, lhs);
        BigInteger rightVal = requireType(BigInteger.class, rhs);
        return leftVal.pow(rightVal.intValue());
    }

    private BigDecimal bigDecimalAdd(Environment.PlcObject lhs, Environment.PlcObject rhs) {
        BigDecimal leftVal = requireType(BigDecimal.class, lhs);
        BigDecimal rightVal = requireType(BigDecimal.class, rhs);
        return leftVal.add(rightVal);
    }

    private BigDecimal bigDecimalSub(Environment.PlcObject lhs, Environment.PlcObject rhs) {
        BigDecimal leftVal = requireType(BigDecimal.class, lhs);
        BigDecimal rightVal = requireType(BigDecimal.class, rhs);
        return leftVal.subtract(rightVal);
    }

    private BigDecimal bigDecimalMult(Environment.PlcObject lhs, Environment.PlcObject rhs) {
        BigDecimal leftVal = requireType(BigDecimal.class, lhs);
        BigDecimal rightVal = requireType(BigDecimal.class, rhs);
        return leftVal.multiply(rightVal);
    }

    private BigDecimal bigDecimalDiv(Environment.PlcObject lhs, Environment.PlcObject rhs) {
        BigDecimal leftVal = requireType(BigDecimal.class, lhs);
        BigDecimal rightVal = requireType(BigDecimal.class, rhs);
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

            return Environment.create(access);
        }

        Object returnValue = scope.lookupVariable(ast.getName()).getValue().getValue();
        return Environment.create(returnValue);
    }

    @Override
    public Environment.PlcObject visit(Ast.Expression.Function ast) {
        List<Ast.Expression> args = ast.getArguments();
        Environment.Function func = scope.lookupFunction(ast.getName(), ast.getArguments().size());
        List<Environment.PlcObject> invokeArgs = new ArrayList<>();
        for (Ast.Expression exp : args) {
            invokeArgs.add(visit(exp));
        }
        Environment.PlcObject funcValue = func.invoke(invokeArgs);
        return Environment.create(funcValue.getValue());
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
    public static class Return extends RuntimeException {

        private final Environment.PlcObject value;

        private Return(Environment.PlcObject value) {
            this.value = value;
        }

    }

}
