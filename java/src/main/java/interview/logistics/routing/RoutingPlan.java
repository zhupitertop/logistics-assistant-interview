package interview.logistics.routing;

public record RoutingPlan(Path path, String orderId, String reason) {
    public enum Path { SOP_ONLY, ORDER_STATUS, ORDER_DIAGNOSIS, NEEDS_CLARIFICATION }
}
