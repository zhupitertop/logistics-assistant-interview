package interview.logistics.data;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

public final class DataLoader {
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Z]{2,4}-\\d{2}|HD\\d{9}", Pattern.CASE_INSENSITIVE);

    public DataSnapshot load(Path directory) {
        Map<String, String> hashes = new TreeMap<>();
        JsonNode sopData = read(directory, "sop.json", hashes);
        JsonNode orderData = read(directory, "orders.json", hashes);
        JsonNode synonymData = read(directory, "synonyms.json", hashes);
        require(sopData.isArray() && !sopData.isEmpty(), "sop.json must be a non-empty array");
        require(orderData.isArray() && !orderData.isEmpty(), "orders.json must be a non-empty array");
        Map<String, SopEntry> sops = new TreeMap<>();
        for (JsonNode node : sopData) {
            fields(node, Set.of("id", "title", "text"), "sop.json");
            String id = string(node, "id", "sop.json");
            require(id.matches("[A-Z]{2,4}-\\d{2}"), "sop.json invalid SOP id: " + id);
            var entry = new SopEntry(id, string(node, "title", id), string(node, "text", id));
            require(sops.putIfAbsent(id, entry) == null, "sop.json duplicate id: " + id);
        }
        Map<String, OrderEntry> orders = new TreeMap<>();
        for (JsonNode node : orderData) {
            fields(node, Set.of("order_id", "warehouse", "order_status"), "orders.json");
            String id = string(node, "order_id", "orders.json");
            String warehouse = string(node, "warehouse", id);
            require(id.matches("HD\\d{9}"), "orders.json invalid order id: " + id);
            require(warehouse.matches("WH\\d{2}"), "orders.json invalid warehouse for " + id);
            var entry = new OrderEntry(id, warehouse, string(node, "order_status", id));
            require(orders.putIfAbsent(id, entry) == null, "orders.json duplicate order: " + id);
        }
        require(synonymData.isObject(), "synonyms.json must be an object");
        Map<String, List<String>> synonyms = new TreeMap<>();
        synonymData.fields().forEachRemaining(entry -> {
            phrase(entry.getKey());
            JsonNode values = entry.getValue();
            require(values.isTextual() || (values.isArray() && !values.isEmpty()), "synonyms.json expansion must be a string or non-empty array");
            if (values.isTextual()) {
                phrase(values.asText());
                synonyms.put(entry.getKey(), List.of(values.asText()));
                return;
            }
            List<String> expansions = new ArrayList<>();
            for (JsonNode value : values) {
                require(value.isTextual(), "synonyms.json expansion must be a string");
                String text = value.asText();
                phrase(text);
                require(!expansions.contains(text), "synonyms.json duplicate expansion: " + text);
                expansions.add(text);
            }
            synonyms.put(entry.getKey(), expansions);
        });
        return new DataSnapshot(directory, sops, orders, synonyms, hashes);
    }

    private JsonNode read(Path directory, String name, Map<String, String> hashes) {
        try {
            byte[] bytes = Files.readAllBytes(directory.resolve(name));
            JsonNode node = JsonFiles.mapper().readTree(bytes);
            require(node != null, name + " must contain JSON");
            hashes.put(name, JsonFiles.sha256(bytes));
            return node;
        } catch (IOException e) {
            throw new DataValidationException("Cannot read valid " + name + ": " + e.getMessage(), e);
        }
    }

    private static void fields(JsonNode node, Set<String> expected, String context) {
        require(node.isObject(), context + " entry must be an object");
        Set<String> actual = new HashSet<>();
        node.fieldNames().forEachRemaining(actual::add);
        require(actual.equals(expected), context + " fields must be " + expected);
    }

    private static String string(JsonNode node, String field, String context) {
        JsonNode value = node.get(field);
        require(value != null && value.isTextual() && !blank(value.asText()),
                context + " field " + field + " must be a non-blank string");
        return value.asText(); // Preserve original text for exact citations.
    }

    private static void phrase(String text) {
        require(!blank(text) && text.codePointCount(0, text.length()) <= 24,
                "synonyms.json phrase must contain 1 to 24 code points");
        require(!IDENTIFIER.matcher(text).find() && !text.matches(".*[。；？！?!].*"),
                "synonyms.json allows query phrases, not identifiers or answer sentences");
    }

    private static boolean blank(String text) {
        return text.codePoints().allMatch(cp -> Character.isWhitespace(cp) || Character.isSpaceChar(cp));
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new DataValidationException(message);
    }
}
