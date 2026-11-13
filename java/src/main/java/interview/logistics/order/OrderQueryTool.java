package interview.logistics.order;

import interview.logistics.data.DataSnapshot;
import interview.logistics.data.OrderEntry;
import java.util.Map;

/** Exact key lookup only: no trimming, case folding, correction or fuzzy matching. */
public final class OrderQueryTool {
    private final Map<String, OrderEntry> orders;
    public OrderQueryTool(DataSnapshot data) { this.orders = data.orders(); }

    public OrderQueryResult queryOrder(String orderId) {
        if (orderId == null || orderId.codePoints().allMatch(cp -> Character.isWhitespace(cp) || Character.isSpaceChar(cp))) throw new IllegalArgumentException("order_id must be non-blank");
        var order = orders.get(orderId);
        return order == null ? new OrderQueryResult(orderId, false, null, null)
                : new OrderQueryResult(order.orderId(), true, order.orderStatus(), order.warehouse());
    }
}
