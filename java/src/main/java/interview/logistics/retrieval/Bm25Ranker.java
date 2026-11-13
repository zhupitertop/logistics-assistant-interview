package interview.logistics.retrieval;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

public final class Bm25Ranker {
    public static final double K1 = 1.2;
    public static final double B = 0.75;
    public static final double TITLE_WEIGHT = 2.0;
    public record Candidate(String id, double score, List<String> matchedTerms) {
        public Candidate { matchedTerms = List.copyOf(matchedTerms); }
    }
    private final SopIndex index;
    private final Map<String, Integer> lengths;

    public Bm25Ranker(SopIndex index) {
        this.index = index;
        var lengths = new HashMap<String, Integer>();
        index.documents().forEach(doc -> lengths.put(doc.id(), doc.titleLength() + doc.bodyLength()));
        this.lengths = Map.copyOf(lengths);
    }

    public List<Candidate> rank(NormalizedQuery query) {
        if (index.documentCount() == 0 || index.averageDocumentLength() <= 0) return List.of();
        Map<String, Double> scores = new HashMap<>();
        Map<String, TreeSet<String>> matches = new HashMap<>();
        for (var term : query.termWeights().entrySet()) {
            var postings = index.postings().get(term.getKey());
            if (postings == null) continue;
            double idf = Math.log1p((index.documentCount() - postings.size() + 0.5) / (postings.size() + 0.5));
            for (var posting : postings) {
                double tf = TITLE_WEIGHT * posting.titleFrequency() + posting.bodyFrequency();
                double normalization = K1 * (1 - B + B * lengths.get(posting.id()) / index.averageDocumentLength());
                double contribution = term.getValue() * idf * tf * (K1 + 1) / (tf + normalization);
                if (contribution > 0) {
                    scores.merge(posting.id(), contribution, Double::sum);
                    matches.computeIfAbsent(posting.id(), ignored -> new TreeSet<>()).add(term.getKey());
                }
            }
        }
        return scores.entrySet().stream().map(entry -> new Candidate(entry.getKey(), entry.getValue(),
                        new ArrayList<>(matches.get(entry.getKey()))))
                .sorted(Comparator.comparingDouble(Candidate::score).reversed().thenComparing(Candidate::id)).toList();
    }
}
