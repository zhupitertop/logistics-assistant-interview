package interview.logistics.routing;

import interview.logistics.data.SopEntry;
import interview.logistics.order.OrderQueryResult;
import interview.logistics.trace.TraceStep;
import java.util.List;

/** Internal tool result, not the final /ask response and not a diagnosis. */
public record EvidenceCollection(RoutingPlan plan, String outcome, String reason, OrderQueryResult order,
                                 List<SopEntry> sops, List<TraceStep> trace) {
    public EvidenceCollection { sops = List.copyOf(sops); trace = List.copyOf(trace); }
}
