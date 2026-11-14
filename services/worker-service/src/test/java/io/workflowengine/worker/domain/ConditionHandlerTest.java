package io.workflowengine.worker.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.workflowengine.worker.domain.handler.ConditionHandler;
import io.workflowengine.worker.domain.model.NodeExecutionException;
import io.workflowengine.worker.domain.model.NodeResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class ConditionHandlerTest {

    static final ObjectMapper JSON = new ObjectMapper();
    private final ConditionHandler handler = new ConditionHandler();

    public static JsonNode json(String s) {
        try {
            return JSON.readTree(s);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    private NodeResult.Success eval(String config) {
        return (NodeResult.Success) handler.execute(Contexts.context(), json(config));
    }

    @ParameterizedTest(name = "{0} {1} {2} -> {3}")
    @CsvSource(
            delimiter = '|',
            value = {
                "5       | EQUALS       | 5       | true",
                "'\"5\"' | EQUALS       | 5       | true",
                "'\"a\"' | EQUALS       | '\"b\"' | false",
                "5       | NOT_EQUALS   | 6       | true",
                "10      | GREATER_THAN | 9.5     | true",
                "'\"3\"' | LESS_THAN    | 4       | true",
                "'\"hello world\"' | CONTAINS | '\"world\"' | true",
                "'[1,2,3]' | CONTAINS   | 2       | true",
                "'{\"k\":1}' | CONTAINS | '\"k\"' | true",
                "null    | EXISTS       | null    | false",
                "0       | EXISTS       | null    | true",
                "null    | EQUALS       | null    | true"
            })
    void operators(String left, String operator, String right, boolean expected) {
        NodeResult.Success result = eval(
                "{\"conditions\":[{\"left\":" + left + ",\"operator\":\"" + operator + "\",\"right\":" + right + "}]}");
        assertThat(result.output().get("result").asBoolean()).isEqualTo(expected);
        assertThat(result.branch()).isEqualTo(Boolean.toString(expected));
    }

    @Test
    void combinators() {
        String both =
                "[{\"left\":1,\"operator\":\"EQUALS\",\"right\":1},{\"left\":1,\"operator\":\"EQUALS\",\"right\":2}]";
        assertThat(eval("{\"combinator\":\"AND\",\"conditions\":" + both + "}").branch())
                .isEqualTo("false");
        assertThat(eval("{\"combinator\":\"OR\",\"conditions\":" + both + "}").branch())
                .isEqualTo("true");
    }

    @Test
    void orderingOnNonNumbersIsANonRetryableTypeError() {
        assertThatThrownBy(
                        () -> eval("{\"conditions\":[{\"left\":\"abc\",\"operator\":\"GREATER_THAN\",\"right\":1}]}"))
                .isInstanceOfSatisfying(NodeExecutionException.class, e -> {
                    assertThat(e.code()).isEqualTo("TYPE_MISMATCH");
                    assertThat(e.retryable()).isFalse();
                });
    }

    @Test
    void unknownOperatorIsInvalidConfiguration() {
        assertThatThrownBy(() -> eval("{\"conditions\":[{\"left\":1,\"operator\":\"MATCHES_REGEX\",\"right\":1}]}"))
                .isInstanceOfSatisfying(
                        NodeExecutionException.class, e -> assertThat(e.code()).isEqualTo("INVALID_CONFIGURATION"));
    }
}
