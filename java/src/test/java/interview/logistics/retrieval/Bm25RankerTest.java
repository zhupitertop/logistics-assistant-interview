package interview.logistics.retrieval;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class Bm25RankerTest {
    @Test
    void handComputedBm25MatchesTitleWeightAndLengthNormalization() {
        var index = new SopIndex(1, "t", "r", "s", "q", 2, 10,
                List.of(new SopIndex.Document("Z", 1, 4), new SopIndex.Document("A", 0, 15)),
                Map.of("盘点", List.of(new SopIndex.Posting("Z", 1, 1), new SopIndex.Posting("A", 0, 1))));
        var query = new NormalizedQuery("盘点", "盘点", List.of(), Map.of("盘点", 1.0));
        var results = new Bm25Ranker(index).rank(query);
        double expectedZ = Math.log(1 + 0.5 / 2.5) * 3 * 2.2 / (3 + 1.2 * (0.25 + 0.75 * 0.5));
        double expectedA = Math.log(1 + 0.5 / 2.5) * 2.2 / (1 + 1.2 * (0.25 + 0.75 * 1.5));
        assertThat(results.get(0).id()).isEqualTo("Z");
        assertThat(results.get(0).score()).isCloseTo(expectedZ, within(1e-12));
        assertThat(results.get(1).score()).isCloseTo(expectedA, within(1e-12));
        var half = new Bm25Ranker(index).rank(new NormalizedQuery("q", "q", List.of(), Map.of("盘点", 0.5)));
        assertThat(half.get(0).score()).isCloseTo(expectedZ / 2, within(1e-12));
    }

    @Test
    void exactTiesUseStableIdsAndAbsentTermsReturnNoCandidates() {
        var index = new SopIndex(1, "t", "r", "s", "q", 2, 2,
                List.of(new SopIndex.Document("Z", 1, 1), new SopIndex.Document("A", 1, 1)),
                Map.of("入库", List.of(new SopIndex.Posting("Z", 1, 1), new SopIndex.Posting("A", 1, 1))));
        var ranker = new Bm25Ranker(index);
        assertThat(ranker.rank(new NormalizedQuery("入库", "入库", List.of(), Map.of("入库", 1.0))))
                .extracting(Bm25Ranker.Candidate::id).containsExactly("A", "Z");
        assertThat(ranker.rank(new NormalizedQuery("未知", "未知", List.of(), Map.of("未知", 1.0)))).isEmpty();
    }
}
