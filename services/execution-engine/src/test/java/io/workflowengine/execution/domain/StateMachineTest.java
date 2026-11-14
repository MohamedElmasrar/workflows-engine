package io.workflowengine.execution.domain;

import static io.workflowengine.execution.domain.model.ExecutionStatus.*;
import static io.workflowengine.execution.domain.model.NodeStatus.*;
import static org.assertj.core.api.Assertions.assertThat;

import io.workflowengine.execution.domain.model.ExecutionStatus;
import io.workflowengine.execution.domain.model.NodeStatus;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Verifies every (from, to) pair of both state machines against the documented transition tables. */
class StateMachineTest {

    private static final Map<ExecutionStatus, Set<ExecutionStatus>> EXECUTION = Map.of(
            ExecutionStatus.PENDING,
                    EnumSet.of(ExecutionStatus.RUNNING, ExecutionStatus.CANCELLED, ExecutionStatus.FAILED),
            ExecutionStatus.RUNNING,
                    EnumSet.of(
                            ExecutionStatus.COMPLETED,
                            ExecutionStatus.FAILED,
                            ExecutionStatus.CANCELLED,
                            ExecutionStatus.TIMED_OUT),
            ExecutionStatus.COMPLETED, EnumSet.noneOf(ExecutionStatus.class),
            ExecutionStatus.FAILED, EnumSet.noneOf(ExecutionStatus.class),
            ExecutionStatus.CANCELLED, EnumSet.noneOf(ExecutionStatus.class),
            ExecutionStatus.TIMED_OUT, EnumSet.noneOf(ExecutionStatus.class));

    private static final Map<NodeStatus, Set<NodeStatus>> NODE = Map.of(
            NodeStatus.PENDING,
            EnumSet.of(READY, SKIPPED, NodeStatus.CANCELLED),
            READY,
            EnumSet.of(QUEUED, NodeStatus.RUNNING, NodeStatus.CANCELLED),
            QUEUED,
            EnumSet.of(
                    NodeStatus.RUNNING,
                    NodeStatus.COMPLETED,
                    READY,
                    NodeStatus.FAILED,
                    NodeStatus.CANCELLED,
                    NodeStatus.TIMED_OUT),
            NodeStatus.RUNNING,
            EnumSet.of(NodeStatus.COMPLETED, READY, NodeStatus.FAILED, NodeStatus.CANCELLED, NodeStatus.TIMED_OUT),
            NodeStatus.COMPLETED,
            EnumSet.noneOf(NodeStatus.class),
            NodeStatus.FAILED,
            EnumSet.noneOf(NodeStatus.class),
            SKIPPED,
            EnumSet.noneOf(NodeStatus.class),
            NodeStatus.CANCELLED,
            EnumSet.noneOf(NodeStatus.class),
            NodeStatus.TIMED_OUT,
            EnumSet.noneOf(NodeStatus.class));

    static Stream<Arguments> executionPairs() {
        return Stream.of(ExecutionStatus.values())
                .flatMap(from -> Stream.of(ExecutionStatus.values()).map(to -> Arguments.of(from, to)));
    }

    static Stream<Arguments> nodePairs() {
        return Stream.of(NodeStatus.values())
                .flatMap(from -> Stream.of(NodeStatus.values()).map(to -> Arguments.of(from, to)));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("executionPairs")
    void executionTransitions(ExecutionStatus from, ExecutionStatus to) {
        assertThat(from.canTransitionTo(to)).isEqualTo(EXECUTION.get(from).contains(to));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("nodePairs")
    void nodeTransitions(NodeStatus from, NodeStatus to) {
        assertThat(from.canTransitionTo(to)).isEqualTo(NODE.get(from).contains(to));
    }
}
