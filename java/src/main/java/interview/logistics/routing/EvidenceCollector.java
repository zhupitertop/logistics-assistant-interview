package interview.logistics.routing;

import interview.logistics.data.SopEntry;
import interview.logistics.order.OrderQueryResult;
import interview.logistics.order.OrderQueryTool;
import interview.logistics.retrieval.SopSearchService;
import interview.logistics.trace.TraceStep;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static interview.logistics.routing.RoutingPlan.Path.*;

public final class EvidenceCollector {
    private final QuestionRouter router;
    private final OrderQueryTool orders;
    private final SopSearchService sops;
    private final StatusSopQueryBuilder queryBuilder = new StatusSopQueryBuilder();

    public EvidenceCollector(QuestionRouter router, OrderQueryTool orders, SopSearchService sops) {
        this.router = router; this.orders = orders; this.sops = sops;
    }

    public EvidenceCollection collect(String question) {
        var plan = router.route(question);
        var trace = new ArrayList<TraceStep>();
        if (plan.path() == NEEDS_CLARIFICATION) {
            return new EvidenceCollection(plan, "insufficient_evidence", plan.reason(), null, List.of(), trace);
        }
        OrderQueryResult order = null;
        if (plan.path() == ORDER_STATUS || plan.path() == ORDER_DIAGNOSIS) {
            order = orders.queryOrder(plan.orderId());
            trace.add(new TraceStep(1, "order_query", Map.of("order_id", plan.orderId()), order.found() ? "ok" : "not_found", order.found() ? List.of(order.orderId()) : List.of()));
            if (!order.found()) return new EvidenceCollection(plan, "order_not_found", "order_not_found", order, List.of(), trace);
            if (plan.path() == ORDER_STATUS) return new EvidenceCollection(plan, "evidence_ready", "order_fields_only", order, List.of(), trace);
        }
        String query = question;
        if (plan.path() == ORDER_DIAGNOSIS) {
            var decision = queryBuilder.build(question, order);
            if (!decision.supported()) return new EvidenceCollection(plan, "insufficient_evidence", decision.reason(), order, List.of(), trace);
            query = decision.query();
        }
        var result = sops.search(query);
        var hits = result.hits().stream().map(hit -> new SopEntry(hit.id(), hit.title(), hit.text())).toList();
        trace.add(new TraceStep(trace.size() + 1, "sop_search", Map.of("query", query), hits.isEmpty() ? "no_hit" : "ok",
                hits.stream().map(SopEntry::id).toList()));
        return new EvidenceCollection(plan, hits.isEmpty() ? "insufficient_evidence" : "evidence_ready", result.outcome(), order, hits, trace);
    }
}
