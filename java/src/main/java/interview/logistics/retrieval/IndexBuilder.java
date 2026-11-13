package interview.logistics.retrieval;

import interview.logistics.data.DataSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

public final class IndexBuilder {
    public static final int SCHEMA_VERSION = 1;
    // Bump when scoring, normalization or workflow/evidence policy changes.
    public static final String RANKING_VERSION = "bm25-log1p-k1-1.2-b-0.75-title2-expansion0.5-original-flow-action-facts-v5";
    private final TextAnalyzer analyzer = new TextAnalyzer();

    public SopIndex build(DataSnapshot data) {
        List<SopIndex.Document> documents = new ArrayList<>();
        Map<String, List<SopIndex.Posting>> postings = new TreeMap<>();
        long length = 0;
        for (var sop : data.sops().values()) {
            var title = analyzer.analyze(sop.title());
            var body = analyzer.analyze(sop.text());
            var titleTerms = frequencies(title);
            var bodyTerms = frequencies(body);
            documents.add(new SopIndex.Document(sop.id(), title.size(), body.size()));
            length += title.size() + body.size();
            var terms = new TreeSet<>(titleTerms.keySet());
            terms.addAll(bodyTerms.keySet());
            for (String term : terms) {
                postings.computeIfAbsent(term, ignored -> new ArrayList<>()).add(new SopIndex.Posting(
                        sop.id(), titleTerms.getOrDefault(term, 0), bodyTerms.getOrDefault(term, 0)));
            }
        }
        return new SopIndex(SCHEMA_VERSION, TextAnalyzer.VERSION, RANKING_VERSION,
                data.sourceHashes().get("sop.json"), data.sourceHashes().get("synonyms.json"),
                documents.size(), documents.isEmpty() ? 0 : (double) length / documents.size(), documents, postings);
    }

    private Map<String, Integer> frequencies(List<String> terms) {
        Map<String, Integer> counts = new TreeMap<>();
        terms.forEach(term -> counts.merge(term, 1, Integer::sum));
        return counts;
    }
}
