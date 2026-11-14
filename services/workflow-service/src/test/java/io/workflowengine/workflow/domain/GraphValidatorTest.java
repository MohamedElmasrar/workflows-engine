package io.workflowengine.workflow.domain;

import static io.workflowengine.workflow.domain.DefinitionFixtures.definition;
import static org.assertj.core.api.Assertions.assertThat;

import io.workflowengine.workflow.domain.service.GraphValidator;
import io.workflowengine.workflow.domain.service.ValidationIssue;
import io.workflowengine.workflow.domain.service.ValidationResult;
import org.junit.jupiter.api.Test;

class GraphValidatorTest {

    private final GraphValidator validator =
            new GraphValidator(cron -> cron.equals("bad") ? "Invalid cron expression" : null);

    @Test
    void acceptsLinearWorkflow() {
        ValidationResult result = validator.validate(definition()
                .manualTrigger("start")
                .log("a", "hello {{ trigger.name }}")
                .log("b", "{{ nodes.a.output.message }}")
                .edge("start", "a")
                .edge("a", "b")
                .build());
        assertThat(result.errors()).isEmpty();
    }

    @Test
    void acceptsConditionalBranchesThatJoinAgain() {
        ValidationResult result = validator.validate(definition()
                .manualTrigger("start")
                .condition("check")
                .log("yes", "y")
                .log("no", "n")
                .log("join", "done")
                .edge("start", "check")
                .edge("check", "yes", "true")
                .edge("check", "no", "false")
                .edge("yes", "join")
                .edge("no", "join")
                .build());
        assertThat(result.errors()).isEmpty();
    }

    @Test
    void requiresExactlyOneTrigger() {
        assertThat(codes(definition().log("a", "x").build())).contains("MISSING_TRIGGER");
        assertThat(codes(definition().manualTrigger("t1").manualTrigger("t2").build()))
                .contains("MULTIPLE_TRIGGERS");
    }

    @Test
    void rejectsDuplicateNodeIds() {
        assertThat(codes(definition().manualTrigger("t").log("t", "x").build())).contains("DUPLICATE_NODE_ID");
    }

    @Test
    void rejectsDanglingEdgesAndSelfLoops() {
        assertThat(codes(definition().manualTrigger("t").edge("t", "ghost").build()))
                .contains("DANGLING_EDGE");
        assertThat(codes(definition()
                        .manualTrigger("t")
                        .log("a", "x")
                        .edge("t", "a")
                        .edge("a", "a")
                        .build()))
                .contains("SELF_LOOP");
    }

    @Test
    void detectsCyclesAndReportsInvolvedNodes() {
        ValidationResult result = validator.validate(definition()
                .manualTrigger("t")
                .log("a", "x")
                .log("b", "y")
                .log("c", "z")
                .edge("t", "a")
                .edge("a", "b")
                .edge("b", "c")
                .edge("c", "a")
                .build());
        assertThat(result.errors())
                .filteredOn(e -> e.code().equals("CYCLE_DETECTED"))
                .extracting(ValidationIssue::nodeId)
                .containsExactlyInAnyOrder("a", "b", "c");
    }

    @Test
    void rejectsUnreachableNodes() {
        ValidationResult result = validator.validate(
                definition().manualTrigger("t").log("orphan", "x").build());
        assertThat(result.errors())
                .extracting(ValidationIssue::code, ValidationIssue::nodeId)
                .contains(org.assertj.core.groups.Tuple.tuple("UNREACHABLE_NODE", "orphan"));
    }

    @Test
    void rejectsEdgesIntoTrigger() {
        assertThat(codes(definition()
                        .manualTrigger("t")
                        .log("a", "x")
                        .edge("t", "a")
                        .edge("a", "t")
                        .build()))
                .contains("TRIGGER_HAS_INPUT");
    }

    @Test
    void branchHandlesOnlyOnConditionsAndConditionsRequireHandles() {
        assertThat(codes(definition()
                        .manualTrigger("t")
                        .log("a", "x")
                        .edge("t", "a", "true")
                        .build()))
                .contains("INVALID_BRANCH_HANDLE");
        assertThat(codes(definition()
                        .manualTrigger("t")
                        .condition("c")
                        .log("a", "x")
                        .edge("t", "c")
                        .edge("c", "a")
                        .build()))
                .contains("MISSING_BRANCH_HANDLE");
    }

    @Test
    void templatesMayOnlyReferenceAncestors() {
        ValidationResult result = validator.validate(definition()
                .manualTrigger("t")
                .log("a", "{{ nodes.b.output.message }}")
                .log("b", "x")
                .edge("t", "a")
                .edge("t", "b")
                .build());
        assertThat(result.errors())
                .extracting(ValidationIssue::code, ValidationIssue::nodeId)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("INVALID_REFERENCE", "a"));
    }

    @Test
    void rejectsExpressionsOutsideThePathLanguage() {
        assertThat(codes(definition()
                        .manualTrigger("t")
                        .log("a", "{{ java.lang.Runtime.exec('id') }}")
                        .edge("t", "a")
                        .build()))
                .containsExactly("INVALID_TEMPLATE");
    }

    @Test
    void validatesScheduleConfiguration() {
        ValidationResult result = validator.validate(definition()
                .node("s", "SCHEDULE_TRIGGER", "{\"cron\": \"bad\", \"timezone\": \"Mars/Olympus\"}")
                .build());
        assertThat(result.errors()).extracting(ValidationIssue::code).contains("INVALID_CRON", "INVALID_TIMEZONE");
    }

    @Test
    void conditionOperatorsOtherThanExistsNeedRightOperand() {
        assertThat(codes(definition()
                        .manualTrigger("t")
                        .node("c", "CONDITION", "{\"conditions\": [{\"left\": 1, \"operator\": \"EQUALS\"}]}")
                        .log("a", "x")
                        .edge("t", "c")
                        .edge("c", "a", "true")
                        .build()))
                .containsExactly("MISSING_OPERAND");
    }

    private java.util.List<String> codes(io.workflowengine.workflow.domain.model.WorkflowDefinition definition) {
        return validator.validate(definition).errors().stream()
                .map(ValidationIssue::code)
                .toList();
    }
}
