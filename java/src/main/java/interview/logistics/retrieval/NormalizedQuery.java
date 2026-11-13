package interview.logistics.retrieval;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public record NormalizedQuery(String original, String normalized, List<String> expansions,
                              Map<String, Double> termWeights) {
    public NormalizedQuery {
        expansions = List.copyOf(expansions);
        termWeights = Collections.unmodifiableMap(new TreeMap<>(termWeights));
    }
}
