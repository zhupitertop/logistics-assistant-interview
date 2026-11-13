package interview.logistics.order;

import com.fasterxml.jackson.annotation.JsonProperty;

public record OrderQueryResult(@JsonProperty("order_id") String orderId, boolean found,
                               @JsonProperty("order_status") String orderStatus, String warehouse) {}
