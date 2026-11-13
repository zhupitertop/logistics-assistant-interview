package interview.logistics.routing;

import interview.logistics.order.OrderQueryResult;
import interview.logistics.retrieval.QueryNormalizer;
import interview.logistics.retrieval.EvidenceRequirements;
import interview.logistics.retrieval.WorkflowPolicy;
import java.util.Map;
import java.util.Set;

public final class StatusSopQueryBuilder {
    public record Decision(String query, String reason) { public boolean supported() { return query != null; } }
    private final QueryNormalizer normalizer = new QueryNormalizer(Map.of());
    private final WorkflowPolicy workflows = new WorkflowPolicy();

    public Decision build(String question, OrderQueryResult order) {
        if (!order.found()) return new Decision(null, "order_not_found");
        Set<WorkflowPolicy.Workflow> allowed = switch (order.orderStatus()) {
            case "待审核", "已审核待出库", "已出库" -> Set.of(WorkflowPolicy.Workflow.OUTBOUND, WorkflowPolicy.Workflow.PICKING);
            case "运输异常" -> Set.of(WorkflowPolicy.Workflow.TRANSPORT);
            default -> Set.of();
        };
        if (EvidenceRequirements.asksUnavailableOrderField(question)) return new Decision(null, "order_detail_not_available");
        if (allowed.isEmpty()) return new Decision(null, "unsupported_order_status");
        String problem = QuestionRouter.withoutOrderIds(question).strip();
        if (!problem.isEmpty()) {
            var explicit = workflows.detect(normalizer.normalize(problem)).workflows();
            if (!allowed.containsAll(explicit)) return new Decision(null, "status_workflow_conflict");
        }
        if (order.orderStatus().equals("已出库")
                && problem.matches("(?s).*(?:还没发|没发出去|未发货|还没出库|未出库|待出库).*")) {
            // The question cannot override the exact stored progress. Retrieve the
            // current stage's scope instead of treating the user's premise as data.
            problem = "出库进度说明";
        }
        // Status comes from the exact lookup, never from the question or a guessed cause.
        return new Decision("订单状态：" + order.orderStatus() + "。" + problem, "status_based_query");
    }
}
