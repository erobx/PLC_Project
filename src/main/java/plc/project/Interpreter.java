package plc.project;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
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
        // Evaluate globals then functions
        ast.getGlobals().forEach(this::visit);
        ast.getFunctions().forEach(this::visit);
        // Check if main exists
        try {
            // Should return the value of the main function
            return scope.lookupFunction("main", 0).invoke(Arrays.asList());
        } catch (RuntimeException ex) {
            System.out.println("Missing main");
        }
        return null;
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
        scope.defineFunction(ast.getName(), ast.getParameters().size(), args -> {
            // Change scope to scope of function
            Scope childScope = new Scope(scope);

            // Define new variables for childScope, checking if they already exist before
            for (String p : ast.getParameters()) {
                try {
                    Environment.Variable v = childScope.lookupVariable(p);
                    childScope.defineVariable(v.getName(), v.getMutable(), v.getValue());
                } catch (RuntimeException ex) {
                    System.out.println(ex);
                }
            }

            // Evaluate function statements => return value in Return exception if thrown or NIL if not
            for (Ast.Statement stmt : ast.getStatements()) {
                try {
                    visit(stmt);
                } catch (Return ex) {
                    return ex.value;
                }
            }

            // Return to parent scope
            scope = childScope.getParent();
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
        if ( ast.getValue().isPresent()) {
            scope.defineVariable(ast.getName(), true, visit(ast.getValue().get()));
        }else {
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

            scope.lookupVariable(receiver.getName()).setValue(value);
        } catch (RuntimeException ex) {
            throw new RuntimeException(ex);
        }

        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.If ast) {
        Boolean condition = requireType(Boolean.class, visit(ast.getCondition()));
        if (condition) {
            Ast.Statement thenStatement = ast.getThenStatements().getFirst();
            visit(thenStatement);
        } else {
            Ast.Statement elseStatement = ast.getElseStatements().getFirst();
            visit(elseStatement);
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
                    BigInteger leftVal = requireType(BigInteger.class, visit(lhs));
                    BigInteger rightVal = requireType(BigInteger.class, visit(rhs));
                    Boolean compare = leftVal.compareTo(rightVal) < 0;
                    return Environment.create(compare);
                }
                if (checkClass.equals(BigDecimal.class)) {
                    BigDecimal leftVal = requireType(BigDecimal.class, visit(lhs));
                    BigDecimal rightVal = requireType(BigDecimal.class, visit(rhs));
                    Boolean compare = leftVal.compareTo(rightVal) < 0;
                    return Environment.create(compare);
                }
                if (checkClass.equals(Boolean.class)) {
                    Boolean leftVal = requireType(Boolean.class, visit(lhs));
                    Boolean rightVal = requireType(Boolean.class, visit(rhs));
                    Boolean compare = leftVal.compareTo(rightVal) < 0;
                    return Environment.create(compare);
                }
                if (checkClass.equals(String.class)) {
                    String leftVal = requireType(String.class, visit(lhs));
                    String rightVal = requireType(String.class, visit(rhs));
                    Boolean compare = leftVal.compareTo(rightVal) < 0;
                    return Environment.create(compare);
                }
                throw err;
            case ">":
                if (checkClass.equals(BigInteger.class)) {
                    BigInteger leftVal = requireType(BigInteger.class, visit(lhs));
                    BigInteger rightVal = requireType(BigInteger.class, visit(rhs));
                    Boolean compare = leftVal.compareTo(rightVal) > 0;
                    return Environment.create(compare);
                }
                if (checkClass.equals(BigDecimal.class)) {
                    BigDecimal leftVal = requireType(BigDecimal.class, visit(lhs));
                    BigDecimal rightVal = requireType(BigDecimal.class, visit(rhs));
                    Boolean compare = leftVal.compareTo(rightVal) > 0;
                    return Environment.create(compare);
                }
                if (checkClass.equals(Boolean.class)) {
                    Boolean leftVal = requireType(Boolean.class, visit(lhs));
                    Boolean rightVal = requireType(Boolean.class, visit(rhs));
                    Boolean compare = leftVal.compareTo(rightVal) > 0;
                    return Environment.create(compare);
                }
                if (checkClass.equals(String.class)) {
                    String leftVal = requireType(String.class, visit(lhs));
                    String rightVal = requireType(String.class, visit(rhs));
                    Boolean compare = leftVal.compareTo(rightVal) > 0;
                    return Environment.create(compare);
                }
                throw err;
            case "==":
                if (checkClass.equals(BigInteger.class)) {
                    BigInteger leftVal = requireType(BigInteger.class, visit(lhs));
                    BigInteger rightVal = requireType(BigInteger.class, visit(rhs));
                    Boolean compare = leftVal.equals(rightVal);
                    return Environment.create(compare);
                }
                if (checkClass.equals(BigDecimal.class)) {
                    BigDecimal leftVal = requireType(BigDecimal.class, visit(lhs));
                    BigDecimal rightVal = requireType(BigDecimal.class, visit(rhs));
                    Boolean compare = leftVal.equals(rightVal);
                    return Environment.create(compare);
                }
                if (checkClass.equals(String.class)) {
                    String leftVal = requireType(String.class, visit(lhs));
                    String rightVal = requireType(String.class, visit(rhs));
                    Boolean compare = leftVal.equals(rightVal);
                    return Environment.create(compare);
                }
                if (checkClass.equals(Boolean.class)) {
                    Boolean leftVal = requireType(Boolean.class, visit(lhs));
                    Boolean rightVal = requireType(Boolean.class, visit(rhs));
                    Boolean compare = leftVal.equals(rightVal);
                    return Environment.create(compare);
                }
                throw err;
            case "!=":
                if (checkClass.equals(BigInteger.class)) {
                    BigInteger leftVal = requireType(BigInteger.class, visit(lhs));
                    BigInteger rightVal = requireType(BigInteger.class, visit(rhs));
                    Boolean compare = !leftVal.equals(rightVal);
                    return Environment.create(compare);
                }
                if (checkClass.equals(BigDecimal.class)) {
                    BigDecimal leftVal = requireType(BigDecimal.class, visit(lhs));
                    BigDecimal rightVal = requireType(BigDecimal.class, visit(rhs));
                    Boolean compare = !leftVal.equals(rightVal);
                    return Environment.create(compare);
                }
                if (checkClass.equals(String.class)) {
                    String leftVal = requireType(String.class, visit(lhs));
                    String rightVal = requireType(String.class, visit(rhs));
                    Boolean compare = !leftVal.equals(rightVal);
                    return Environment.create(compare);
                }
                if (checkClass.equals(Boolean.class)) {
                    Boolean leftVal = requireType(Boolean.class, visit(lhs));
                    Boolean rightVal = requireType(Boolean.class, visit(rhs));
                    Boolean compare = !leftVal.equals(rightVal);
                    return Environment.create(compare);
                }
                throw err;
            case "+":
                // If either expression is a String concat
                if (checkClass.equals(String.class) || visit(rhs).getValue().getClass().equals(String.class)) {
                    String leftVal = visit(lhs).getValue().toString();
                    String rightVal = visit(rhs).getValue().toString();
                    String concat = leftVal.concat(rightVal);
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
//            System.out.println(access);

            return Environment.create(access);
        }

        Object returnValue = scope.lookupVariable(ast.getName()).getValue().getValue();
        return Environment.create(returnValue);
    }

    @Override
    public Environment.PlcObject visit(Ast.Expression.Function ast) {
        List<Ast.Expression> args = ast.getArguments();
        if (args.isEmpty()) {
            return Environment.create(ast.getName());
        }

        Environment.Function func = scope.lookupFunction(ast.getName(), ast.getArguments().size());
        List< Environment.PlcObject> invokeArgs = new ArrayList<>();
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
