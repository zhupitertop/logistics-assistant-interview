package interview.logistics.trace;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public record TraceStep(int seq, String tool, Map<String, Object> input, String outcome,
                        @JsonProperty("hit_ids") List<String> hitIds) {
    public TraceStep {
        Map<String, Object> copy = new TreeMap<>();
        input.forEach((key, value) -> {
            if (value instanceof String) copy.put(key, value);
            else if (value instanceof List<?> values && values.stream().allMatch(item -> item instanceof String)) {
                copy.put(key, List.copyOf(values));
            } else throw new IllegalArgumentException("Trace input supports strings and string lists only");
        });
        input = Collections.unmodifiableMap(copy);
        hitIds = List.copyOf(hitIds);
    }
}
