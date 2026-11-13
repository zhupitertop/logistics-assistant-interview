package interview.logistics.order;

import interview.logistics.data.*;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.*;

class OrderQueryToolTest {
    private final DataSnapshot data = new DataLoader().load(Path.of("data"));
    private final OrderQueryTool tool = new OrderQueryTool(data);

    @Test
    void exactLookupExposesOnlyActualFieldsAndNeverCorrectsIds() throws Exception {
        var found = tool.queryOrder("HD202611002");
        assertThat(found).isEqualTo(new OrderQueryResult("HD202611002", true, "待审核", "WH01"));
        var json = JsonFiles.mapper().valueToTree(found);
        assertThat(json.size()).isEqualTo(4);
        assertThat(json.get("order_id").asText()).isEqualTo("HD202611002");
        assertThat(json.get("order_status").asText()).isEqualTo("待审核");
        for (String id : new String[]{"HD202699999", "hd202611002", "HD202611002 ", "HD20261100", "HD2026110020", "ＨＤ２０２６１１００２"}) {
            assertThat(tool.queryOrder(id)).isEqualTo(new OrderQueryResult(id, false, null, null));
        }
    }

    @Test
    void invalidToolInputIsAnArgumentError() {
        for (String id : new String[]{null, "", " ", "\u00a0\u3000"}) {
            assertThatThrownBy(() -> tool.queryOrder(id)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void newOrdersAndUnknownStatusesNeedNoCodeChanges() {
        var orders = new java.util.TreeMap<>(data.orders());
        orders.put("HD202611005", new OrderEntry("HD202611005", "WH03", "处理中"));
        var snapshot = new DataSnapshot(data.sourceDirectory(), data.sops(), orders, data.synonyms(), data.sourceHashes());
        assertThat(new OrderQueryTool(snapshot).queryOrder("HD202611005"))
                .isEqualTo(new OrderQueryResult("HD202611005", true, "处理中", "WH03"));
    }
}
