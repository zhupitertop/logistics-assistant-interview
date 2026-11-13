package interview.logistics.data;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class DataLoaderTest {
    @TempDir Path directory;
    private final DataLoader loader = new DataLoader();

    @BeforeEach
    void fixtures() throws Exception {
        for (String name : List.of("sop.json", "orders.json", "synonyms.json")) {
            Files.copy(Path.of("data", name), directory.resolve(name));
        }
    }

    @Test
    void loadsOriginalTextAndIsDeeplyImmutable() throws Exception {
        var tree = (ArrayNode) JsonFiles.mapper().readTree(directory.resolve("sop.json").toFile());
        ((ObjectNode) tree.get(0)).put("text", "  原文：盘点 <b>少件</b>，𠀀。\n保留空格  ");
        JsonFiles.mapper().writeValue(directory.resolve("sop.json").toFile(), tree);
        var data = loader.load(directory);
        assertThat(data.sops()).hasSize(47);
        assertThat(data.orders().get("HD202611002").orderStatus()).isEqualTo("待审核");
        assertThat(data.sops().get(tree.get(0).get("id").asText()).text()).isEqualTo(tree.get(0).get("text").asText());
        assertThat(data.sourceHashes().get("sop.json")).isEqualTo(JsonFiles.sha256(Files.readAllBytes(directory.resolve("sop.json"))));
        assertThatThrownBy(() -> data.sops().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> data.orders().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> data.synonyms().get("少件").add("入库")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsMissingMalformedDuplicateAndTrailingJson() throws Exception {
        var file = directory.resolve("sop.json");
        for (String bad : List.of("{", "[] {}", "null", "[]", "[null]",
                "[{\"id\":\"ABC-01\",\"id\":\"ABC-02\",\"title\":\"t\",\"text\":\"x\"}]")) {
            Files.writeString(file, bad);
            assertThatThrownBy(() -> loader.load(directory)).as(bad).isInstanceOf(DataValidationException.class);
        }
        Files.delete(file);
        assertThatThrownBy(() -> loader.load(directory)).hasMessageContaining("sop.json");
    }

    @Test
    void rejectsWrongTypesBlankFieldsUnknownFieldsAndDuplicateIds() throws Exception {
        var original = (ArrayNode) JsonFiles.mapper().readTree(directory.resolve("sop.json").toFile());
        for (String field : List.of("id", "title", "text")) {
            for (String value : List.of("null", "42", "[]", "{}", "\"\\u00a0\\u3000\"")) {
                var changed = original.deepCopy();
                ((ObjectNode) changed.get(0)).set(field, JsonFiles.mapper().readTree(value));
                JsonFiles.mapper().writeValue(directory.resolve("sop.json").toFile(), changed);
                assertThatThrownBy(() -> loader.load(directory)).isInstanceOf(DataValidationException.class);
            }
        }
        var changed = original.deepCopy();
        ((ObjectNode) changed.get(0)).put("group", "inbound");
        JsonFiles.mapper().writeValue(directory.resolve("sop.json").toFile(), changed);
        assertThatThrownBy(() -> loader.load(directory)).hasMessageContaining("fields");
        original.add(original.get(0).deepCopy());
        JsonFiles.mapper().writeValue(directory.resolve("sop.json").toFile(), original);
        assertThatThrownBy(() -> loader.load(directory)).hasMessageContaining("duplicate id");
    }

    @Test
    void rejectsBadOrderRecordsButDoesNotInventStatusRestrictions() throws Exception {
        Path file = directory.resolve("orders.json");
        var original = (ArrayNode) JsonFiles.mapper().readTree(file.toFile());
        var changed = original.deepCopy();
        ((ObjectNode) changed.get(0)).put("order_status", "新的待核实状态");
        JsonFiles.mapper().writeValue(file.toFile(), changed);
        assertThat(loader.load(directory).orders().get("HD202611001").orderStatus()).isEqualTo("新的待核实状态");
        for (String field : List.of("order_id", "warehouse", "order_status")) {
            changed = original.deepCopy();
            ((ObjectNode) changed.get(0)).put(field, 42);
            JsonFiles.mapper().writeValue(file.toFile(), changed);
            assertThatThrownBy(() -> loader.load(directory)).isInstanceOf(DataValidationException.class);
        }
        original.add(original.get(0).deepCopy());
        JsonFiles.mapper().writeValue(file.toFile(), original);
        assertThatThrownBy(() -> loader.load(directory)).hasMessageContaining("duplicate order");
    }

    @Test
    void rejectsAnswerMappingsAndMalformedSynonymValues() throws Exception {
        for (String bad : List.of("[]", "{\"少件\":[]}", "{\"少件\":42}", "{\"少件\":[null]}",
                "{\"少件\":[\"INV-01\"]}", "{\"HD202611001\":[\"状态\"]}",
                "{\"少件\":[\"数量短少\",\"数量短少\"]}", "{\"少件\":[\"应当这样做。\"]}")) {
            Files.writeString(directory.resolve("synonyms.json"), bad);
            assertThatThrownBy(() -> loader.load(directory)).as(bad).isInstanceOf(DataValidationException.class);
        }
    }
}
