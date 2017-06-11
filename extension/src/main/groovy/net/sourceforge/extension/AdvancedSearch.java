package net.sourceforge.extension;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by c.karalis on 11/3/2014.
 */
public class AdvancedSearch {

    /**
     * Defines whether the operands should be matched altogether or at least one of them
     */
    public enum Operator {
        AND, OR
    }

    private Operator operator;
    private Map<String, Object> operands;

    /**
     * Default constructor with no operands and operator {@link Operator.AND}
     */
    public AdvancedSearch() {
        this.operator = Operator.AND;
        this.operands = new HashMap<>();
    }

    public Operator getOperator() {
        return operator;
    }

    public void setOperator(Operator operator) {
        this.operator = operator;
    }

    public Map<String, Object> getOperands() {
        return operands;
    }

    public void setOperands(Map<String, Object> operands) {
        this.operands = operands;
    }
}
