package interview.logistics.retrieval;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public record SopIndex(int schemaVersion, String tokenizerVersion, String rankingConfigVersion,
                       String sopSha256, String synonymsSha256, int documentCount,
                       double averageDocumentLength, List<Document> documents,
                       Map<String, List<Posting>> postings) {
    public SopIndex {
        documents = List.copyOf(documents);
        var copy = new TreeMap<String, List<Posting>>();
        postings.forEach((term, entries) -> copy.put(term, List.copyOf(entries)));
        postings = Collections.unmodifiableMap(copy);
    }
    public record Document(String id, int titleLength, int bodyLength) {}
    public record Posting(String id, int titleFrequency, int bodyFrequency) {}
}
