package io.workflowengine.worker.domain.handler;

import com.fasterxml.jackson.databind.JsonNode;
import io.workflowengine.worker.domain.model.NodeExecutionException;
import java.math.BigDecimal;
import java.util.Optional;

/**
 * Typed comparison strategies for CONDITION nodes. Numbers and numeric strings compare numerically; ordering operators
 * require numeric operands. Nothing here evaluates user-supplied code.
 */
public enum ConditionOperator {
    EQUALS {
        @Override
        public boolean test(JsonNode left, JsonNode right) {
            Optional<BigDecimal> l = number(left);
            Optional<BigDecimal> r = number(right);
            if (l.isPresent() && r.isPresent()) {
                return l.get().compareTo(r.get()) == 0;
            }
            if (isAbsent(left) || isAbsent(right)) {
                return isAbsent(left) && isAbsent(right);
            }
            if (left.isValueNode() && right.isValueNode()) {
                return left.asText().equals(right.asText());
            }
            return left.equals(right);
        }
    },
    NOT_EQUALS {
        @Override
        public boolean test(JsonNode left, JsonNode right) {
            return !EQUALS.test(left, right);
        }
    },
    GREATER_THAN {
        @Override
        public boolean test(JsonNode left, JsonNode right) {
            return compare(left, right) > 0;
        }
    },
    LESS_THAN {
        @Override
        public boolean test(JsonNode left, JsonNode right) {
            return compare(left, right) < 0;
        }
    },
    CONTAINS {
        @Override
        public boolean test(JsonNode left, JsonNode right) {
            if (isAbsent(left)) {
                return false;
            }
            if (left.isTextual()) {
                return !isAbsent(right) && left.asText().contains(right.asText());
            }
            if (left.isArray()) {
                for (JsonNode element : left) {
                    if (EQUALS.test(element, right)) {
                        return true;
                    }
                }
                return false;
            }
            if (left.isObject()) {
                return !isAbsent(right) && left.has(right.asText());
            }
            return false;
        }
    },
    EXISTS {
        @Override
        public boolean test(JsonNode left, JsonNode right) {
            return !isAbsent(left);
        }
    };

    public abstract boolean test(JsonNode left, JsonNode right);

    static boolean isAbsent(JsonNode node) {
        return node == null || node.isNull() || node.isMissingNode();
    }

    static Optional<BigDecimal> number(JsonNode node) {
        if (isAbsent(node)) {
            return Optional.empty();
        }
        if (node.isNumber()) {
            return Optional.of(node.decimalValue());
        }
        if (node.isTextual()) {
            try {
                return Optional.of(new BigDecimal(node.asText().strip()));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    static int compare(JsonNode left, JsonNode right) {
        Optional<BigDecimal> l = number(left);
        Optional<BigDecimal> r = number(right);
        if (l.isEmpty() || r.isEmpty()) {
            throw new NodeExecutionException(
                    "TYPE_MISMATCH",
                    "GREATER_THAN and LESS_THAN need numeric operands, got " + left + " and " + right,
                    false);
        }
        return l.get().compareTo(r.get());
    }
}
