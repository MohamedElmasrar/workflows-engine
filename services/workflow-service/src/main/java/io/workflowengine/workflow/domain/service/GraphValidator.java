package io.workflowengine.workflow.domain.service;

import com.fasterxml.jackson.databind.JsonNode;
import io.workflowengine.contracts.NodeType;
import io.workflowengine.workflow.domain.model.WorkflowDefinition;
import io.workflowengine.workflow.domain.model.WorkflowDefinition.Edge;
import io.workflowengine.workflow.domain.model.WorkflowDefinition.Node;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Semantic validation of a workflow graph (structural validation is done against the JSON schema first). Enforces
 * the DAG execution model: one trigger, valid references, no cycles, everything reachable from the trigger, branch
 * handles only on CONDITION nodes, and templates that only reference ancestor nodes.
 */
public class GraphValidator {

    private final ScheduleRules scheduleRules;

    public GraphValidator(ScheduleRules scheduleRules) {
        this.scheduleRules = scheduleRules;
    }

    public ValidationResult validate(WorkflowDefinition definition) {
        List<ValidationIssue> errors = new ArrayList<>();
        Map<String, Node> nodes = indexNodes(definition, errors);
        checkTriggers(definition, errors);
        Map<String, List<Edge>> outgoing = new HashMap<>();
        Map<String, List<Edge>> incoming = new HashMap<>();
        checkEdges(definition, nodes, outgoing, incoming, errors);

        boolean acyclic = checkAcyclic(nodes, outgoing, errors);
        definition.trigger().ifPresent(trigger -> checkReachability(trigger, nodes, outgoing, errors));
        for (Node node : nodes.values()) {
            checkConfiguration(node, errors);
            if (node.nodeType().map(t -> t == NodeType.CONDITION).orElse(false)) {
                checkConditionBranches(node, outgoing.getOrDefault(node.id(), List.of()), errors);
            }
        }
        if (acyclic) {
            checkTemplateReferences(nodes, incoming, errors);
        }
        return new ValidationResult(errors);
    }

    private Map<String, Node> indexNodes(WorkflowDefinition definition, List<ValidationIssue> errors) {
        Map<String, Node> nodes = new LinkedHashMap<>();
        for (Node node : definition.nodes()) {
            if (node.id() == null) {
                errors.add(ValidationIssue.general("NODE_ID_MISSING", "Every node needs an id"));
                continue;
            }
            if (nodes.putIfAbsent(node.id(), node) != null) {
                errors.add(ValidationIssue.node("DUPLICATE_NODE_ID", node.id(), "Node id '" + node.id() + "' is used more than once"));
            }
            if (node.nodeType().isEmpty()) {
                errors.add(ValidationIssue.node("UNKNOWN_NODE_TYPE", node.id(), "Unknown node type '" + node.type() + "'"));
            }
        }
        if (nodes.isEmpty()) {
            errors.add(ValidationIssue.general("EMPTY_WORKFLOW", "The workflow has no nodes"));
        }
        return nodes;
    }

    private void checkTriggers(WorkflowDefinition definition, List<ValidationIssue> errors) {
        List<Node> triggers = definition.triggerNodes();
        if (triggers.isEmpty()) {
            errors.add(ValidationIssue.general("MISSING_TRIGGER", "The workflow needs exactly one trigger node"));
        } else if (triggers.size() > 1) {
            triggers.forEach(t -> errors.add(
                    ValidationIssue.node("MULTIPLE_TRIGGERS", t.id(), "Only one trigger node is allowed per workflow")));
        }
    }

    private void checkEdges(
            WorkflowDefinition definition,
            Map<String, Node> nodes,
            Map<String, List<Edge>> outgoing,
            Map<String, List<Edge>> incoming,
            List<ValidationIssue> errors) {
        Set<String> edgeIds = new HashSet<>();
        Set<String> connections = new HashSet<>();
        for (Edge edge : definition.edges()) {
            if (edge.id() == null || !edgeIds.add(edge.id())) {
                errors.add(ValidationIssue.edge("DUPLICATE_EDGE_ID", edge.id(), "Edge ids must be present and unique"));
            }
            Node source = nodes.get(edge.source());
            Node target = nodes.get(edge.target());
            if (source == null || target == null) {
                errors.add(ValidationIssue.edge(
                        "DANGLING_EDGE", edge.id(), "Edge references a node that does not exist"));
                continue;
            }
            if (edge.source().equals(edge.target())) {
                errors.add(ValidationIssue.edge("SELF_LOOP", edge.id(), "A node cannot connect to itself"));
                continue;
            }
            if (target.isTrigger()) {
                errors.add(ValidationIssue.edge(
                        "TRIGGER_HAS_INPUT", edge.id(), "Trigger nodes cannot have incoming connections"));
            }
            boolean sourceIsCondition =
                    source.nodeType().map(t -> t == NodeType.CONDITION).orElse(false);
            if (edge.sourceHandle() != null && !sourceIsCondition) {
                errors.add(ValidationIssue.edge(
                        "INVALID_BRANCH_HANDLE", edge.id(), "Only CONDITION nodes have branch outputs"));
            }
            if (!connections.add(edge.source() + "|" + edge.sourceHandle() + "|" + edge.target())) {
                errors.add(ValidationIssue.edge("DUPLICATE_EDGE", edge.id(), "The same connection exists twice"));
            }
            outgoing.computeIfAbsent(edge.source(), k -> new ArrayList<>()).add(edge);
            incoming.computeIfAbsent(edge.target(), k -> new ArrayList<>()).add(edge);
        }
    }

    /** Kahn's algorithm; any node left with unresolved in-degree is part of (or downstream of) a cycle. */
    private boolean checkAcyclic(
            Map<String, Node> nodes, Map<String, List<Edge>> outgoing, List<ValidationIssue> errors) {
        Map<String, Integer> inDegree = new HashMap<>();
        nodes.keySet().forEach(id -> inDegree.put(id, 0));
        outgoing.values().forEach(edges -> edges.forEach(e -> inDegree.merge(e.target(), 1, Integer::sum)));
        Deque<String> ready = new ArrayDeque<>();
        inDegree.forEach((id, degree) -> {
            if (degree == 0) {
                ready.add(id);
            }
        });
        int visited = 0;
        while (!ready.isEmpty()) {
            String id = ready.poll();
            visited++;
            for (Edge e : outgoing.getOrDefault(id, List.of())) {
                if (inDegree.merge(e.target(), -1, Integer::sum) == 0) {
                    ready.add(e.target());
                }
            }
        }
        if (visited == nodes.size()) {
            return true;
        }
        inDegree.forEach((id, degree) -> {
            if (degree > 0) {
                errors.add(ValidationIssue.node(
                        "CYCLE_DETECTED", id, "Node is part of a cycle; only acyclic workflows are supported"));
            }
        });
        return false;
    }

    private void checkReachability(
            Node trigger, Map<String, Node> nodes, Map<String, List<Edge>> outgoing, List<ValidationIssue> errors) {
        Set<String> reached = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>(List.of(trigger.id()));
        while (!queue.isEmpty()) {
            String id = queue.poll();
            if (reached.add(id)) {
                outgoing.getOrDefault(id, List.of()).forEach(e -> queue.add(e.target()));
            }
        }
        nodes.keySet().stream()
                .filter(id -> !reached.contains(id))
                .forEach(id -> errors.add(
                        ValidationIssue.node("UNREACHABLE_NODE", id, "Node is not reachable from the trigger")));
    }

    private void checkConditionBranches(Node node, List<Edge> outgoing, List<ValidationIssue> errors) {
        for (Edge edge : outgoing) {
            if (!"true".equals(edge.sourceHandle()) && !"false".equals(edge.sourceHandle())) {
                errors.add(ValidationIssue.edge(
                        "MISSING_BRANCH_HANDLE", edge.id(), "Connections from a condition must use its true or false output"));
            }
        }
        if (outgoing.isEmpty()) {
            errors.add(ValidationIssue.node("CONDITION_WITHOUT_BRANCH", node.id(), "Condition has no outgoing branch"));
        }
    }

    private void checkConfiguration(Node node, List<ValidationIssue> errors) {
        if (node.nodeType().isEmpty()) {
            return;
        }
        JsonNode config = node.config();
        switch (node.nodeType().get()) {
            case SCHEDULE_TRIGGER -> {
                String problem = scheduleRules.cronProblem(config.path("cron").asText(""));
                if (problem != null) {
                    errors.add(ValidationIssue.node("INVALID_CRON", node.id(), problem));
                }
                try {
                    ZoneId.of(config.path("timezone").asText(""));
                } catch (DateTimeException e) {
                    errors.add(ValidationIssue.node(
                            "INVALID_TIMEZONE", node.id(), "Unknown time zone '" + config.path("timezone").asText() + "'"));
                }
            }
            case HTTP_REQUEST -> {
                String url = config.path("url").asText("");
                if (!url.startsWith("{{") && !url.matches("(?i)^https?://.+")) {
                    errors.add(ValidationIssue.node("INVALID_URL", node.id(), "URL must start with http:// or https://"));
                }
            }
            case CONDITION -> {
                for (JsonNode condition : config.path("conditions")) {
                    if (!"EXISTS".equals(condition.path("operator").asText()) && !condition.has("right")) {
                        errors.add(ValidationIssue.node(
                                "MISSING_OPERAND", node.id(), "Operator " + condition.path("operator").asText() + " requires a right-hand value"));
                    }
                }
            }
            default -> {
                // remaining node types are fully described by the JSON schema
            }
        }
    }

    private void checkTemplateReferences(
            Map<String, Node> nodes, Map<String, List<Edge>> incoming, List<ValidationIssue> errors) {
        for (Node node : nodes.values()) {
            Set<String> ancestors = null;
            for (TemplateReferences.Reference ref : TemplateReferences.find(node.config())) {
                if (!ref.valid()) {
                    errors.add(ValidationIssue.node(
                            "INVALID_TEMPLATE", node.id(), "Unsupported expression '{{ " + ref.expression() + " }}'"));
                    continue;
                }
                if (ref.nodeId() == null) {
                    continue;
                }
                if (ancestors == null) {
                    ancestors = ancestors(node.id(), incoming);
                }
                if (!ancestors.contains(ref.nodeId())) {
                    errors.add(ValidationIssue.node(
                            "INVALID_REFERENCE",
                            node.id(),
                            "'" + ref.expression() + "' references node '" + ref.nodeId() + "', which does not run before this node"));
                }
            }
        }
    }

    private static Set<String> ancestors(String nodeId, Map<String, List<Edge>> incoming) {
        Set<String> result = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>(List.of(nodeId));
        while (!queue.isEmpty()) {
            for (Edge e : incoming.getOrDefault(queue.poll(), List.of())) {
                if (result.add(e.source())) {
                    queue.add(e.source());
                }
            }
        }
        return result;
    }
}
