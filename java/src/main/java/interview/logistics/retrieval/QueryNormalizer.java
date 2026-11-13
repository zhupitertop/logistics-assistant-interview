package interview.logistics.retrieval;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public final class QueryNormalizer {
    private static final Set<String> QUESTION_WORDS = Set.of("什么", "哪些", "怎么", "如何", "是否", "请问", "一下", "现在", "一个", "我们", "我要",
            "情况", "条件", "情形", "需要", "应该", "应当", "何时");
    private final Map<String, List<String>> synonyms;
    private final List<String> keys;
    private final TextAnalyzer analyzer = new TextAnalyzer();

    public QueryNormalizer(Map<String, List<String>> synonyms) {
        var copy = new TreeMap<String, List<String>>();
        synonyms.forEach((key, values) -> {
            String normalized = canonical(key);
            if (normalized.isBlank()) throw new IllegalArgumentException("Synonym key must not be blank");
            var previous = copy.putIfAbsent(normalized, values.stream().map(QueryNormalizer::canonical).distinct().toList());
            if (previous != null) throw new IllegalArgumentException("Synonym keys collide after Unicode normalization: " + key);
        });
        this.synonyms = Map.copyOf(copy);
        this.keys = copy.keySet().stream().sorted(Comparator.comparingInt(String::length).reversed().thenComparing(Comparator.naturalOrder())).toList();
    }

    public NormalizedQuery normalize(String question) {
        if (question == null || question.codePoints().allMatch(cp -> Character.isWhitespace(cp) || Character.isSpaceChar(cp))) {
            throw new IllegalArgumentException("query must be a non-blank string");
        }
        if (question.codePointCount(0, question.length()) > 2000) throw new IllegalArgumentException("query exceeds 2000 code points");
        String normalized = canonical(question).replaceAll("(?i)hd\\d{9}", " ")
                .replaceAll("不是(?:在)?(?:盘点|盘库|入库|收货|退货|拣货|出库)", " ")
                .replaceAll("[\\s\\p{Z}]+", " ").strip();
        var expansions = new LinkedHashSet<String>();
        // Match longest phrases against the original normalized query exactly once.
        // Generated phrases are never fed back into the synonym matcher.
        for (int at = 0; at < normalized.length();) {
            String found = null;
            for (String key : keys) {
                if (normalized.startsWith(key, at)) { found = key; break; }
            }
            if (found == null) at += Character.charCount(normalized.codePointAt(at));
            else { expansions.addAll(synonyms.get(found)); at += found.length(); }
        }
        Map<String, Double> weights = new TreeMap<>();
        addTerms(weights, normalized, 1.0);
        expansions.forEach(phrase -> addTerms(weights, phrase, 0.5));
        return new NormalizedQuery(question, normalized, new ArrayList<>(expansions), weights);
    }

    private void addTerms(Map<String, Double> weights, String text, double weight) {
        for (String term : analyzer.analyze(text)) {
            if (!QUESTION_WORDS.contains(term)) weights.merge(term, weight, Math::max);
        }
    }

    static String canonical(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
    }
}
