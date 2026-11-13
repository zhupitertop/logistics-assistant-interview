package interview.logistics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

class DataBaselineTest {
    private final ObjectMapper mapper = new ObjectMapper();

    private JsonNode read(String name) throws Exception {
        return mapper.readTree(Path.of("data", name).toFile());
    }

    private Set<String> fields(JsonNode node) {
        Set<String> fields = new HashSet<>();
        node.fieldNames().forEachRemaining(fields::add);
        return fields;
    }

    @Test
    void sopBaselineHas47UniqueEntriesAcrossEightGroups() throws Exception {
        var sop = read("sop.json");
        assertThat(sop.isArray()).isTrue();
        assertThat(sop.size()).isEqualTo(47);
        Set<String> ids = new HashSet<>();
        Map<String, Integer> groups = new TreeMap<>();
        for (var entry : sop) {
            assertThat(fields(entry)).containsExactlyInAnyOrder("id", "title", "text");
            for (String field : new String[]{"id", "title", "text"}) {
                assertThat(entry.get(field).isTextual()).isTrue();
                assertThat(entry.get(field).asText()).isNotBlank();
            }
            String id = entry.get("id").asText();
            assertThat(id).matches("[A-Z]{2,4}-\\d{2}");
            assertThat(ids.add(id)).as("unique SOP id %s", id).isTrue();
            groups.merge(id.substring(0, id.indexOf('-')), 1, Integer::sum);
        }
        assertThat(groups).isEqualTo(Map.of(
                "IN", 6, "INV", 7, "OUT", 7, "RET", 6,
                "TRA", 6, "ST", 5, "STO", 5, "HDO", 5));
        assertThat(ids).contains("INV-01", "OUT-02", "TRA-02");
    }

    @Test
    void ordersHaveExactFieldsAndPreservePublishedExamples() throws Exception {
        var orders = read("orders.json");
        assertThat(orders.isArray()).isTrue();
        Set<String> ids = new HashSet<>();
        Map<String, String> actual = new TreeMap<>();
        for (var order : orders) {
            assertThat(fields(order)).containsExactlyInAnyOrder("order_id", "warehouse", "order_status");
            for (String field : new String[]{"order_id", "warehouse", "order_status"}) {
                assertThat(order.get(field).isTextual()).isTrue();
                assertThat(order.get(field).asText()).isNotBlank();
            }
            String id = order.get("order_id").asText();
            assertThat(id).matches("HD\\d{9}");
            assertThat(order.get("warehouse").asText()).matches("WH\\d{2}");
            assertThat(ids.add(id)).isTrue();
            actual.put(id, order.get("warehouse").asText() + ":" + order.get("order_status").asText());
        }
        assertThat(actual).containsAllEntriesOf(Map.of(
                "HD202611001", "WH01:已审核待出库",
                "HD202611002", "WH01:待审核",
                "HD202611003", "WH02:已出库",
                "HD202611004", "WH02:运输异常"));
        assertThat(ids).doesNotContain("HD202699999");
    }

    @Test
    void synonymsContainOnlyQueryPhrasesRatherThanIdsOrAnswers() throws Exception {
        var synonyms = read("synonyms.json");
        assertThat(synonyms.isObject()).isTrue();
        assertThat(synonyms.size()).isPositive();
        var fields = synonyms.fields();
        while (fields.hasNext()) {
            var entry = fields.next();
            assertThat(entry.getKey()).isNotBlank();
            assertThat(entry.getKey()).doesNotContain("？", "?");
            var expansions = entry.getValue().isTextual() ? mapper.createArrayNode().add(entry.getValue().asText()) : entry.getValue();
            assertThat(expansions.isArray()).isTrue();
            assertThat(expansions.size()).isPositive();
            Set<String> unique = new HashSet<>();
            for (var expansion : expansions) {
                assertThat(expansion.isTextual()).isTrue();
                String phrase = expansion.asText();
                assertThat(phrase).isNotBlank();
                assertThat(phrase.codePointCount(0, phrase.length())).isLessThanOrEqualTo(24);
                assertThat(phrase).doesNotContainPattern("[A-Z]{2,4}-\\d{2}|HD\\d{9}");
                assertThat(phrase).doesNotContain("。", "；", "？", "?");
                assertThat(unique.add(phrase)).isTrue();
            }
        }
        // Preserve the original supplied seed; the workflow policy handles contextual ambiguity.
        assertThat(synonyms.get("少件").asText()).isEqualTo("入库差异");
    }
}
