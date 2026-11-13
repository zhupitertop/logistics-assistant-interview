package interview.logistics.retrieval;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class QueryNormalizerTest {
    @Test
    void unicodeAndOrderIdentifiersDoNotChangeOriginalQuestion() {
        var normalizer = new QueryNormalizer(Map.of("少件", List.of("数量短少")));
        String text = "ＨＤ２０２６１１００１　盘点少件";
        var result = normalizer.normalize(text);
        assertThat(result.original()).isEqualTo(text);
        assertThat(result.normalized()).doesNotContain("hd202611001").contains("盘点少件");
        assertThat(result.termWeights()).containsEntry("盘点", 1.0).containsEntry("少件", 1.0).containsEntry("短少", 0.5);
    }

    @Test
    void longestMatchExpandsOnlyOnceAndDoesNotInventAdjacentTerms() {
        var normalizer = new QueryNormalizer(Map.of(
                "盘库", List.of("盘点"), "库", List.of("仓库"), "盘点", List.of("递归错误")));
        var result = normalizer.normalize("盘库少件");
        assertThat(result.expansions()).containsExactly("盘点");
        assertThat(result.termWeights()).doesNotContainKeys("库盘", "点少", "递归", "错误", "仓库");
        assertThat(normalizer.normalize("盘库盘库").expansions()).containsExactly("盘点");
    }

    @Test
    void originalTermsCannotBeDownweightedByAnExpansion() {
        var query = new QueryNormalizer(Map.of("盘库", List.of("盘点"))).normalize("盘库盘点");
        assertThat(query.termWeights()).containsEntry("盘点", 1.0);
        assertThatThrownBy(() -> query.termWeights().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> new QueryNormalizer(Map.of("Ａ", List.of("一"), "a", List.of("二"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void invalidAndEmptyQuestionsAreRejectedButPureUnknownTermsAreAllowed() {
        assertThatThrownBy(() -> new QueryNormalizer(Map.of("", List.of("扩展")))).isInstanceOf(IllegalArgumentException.class);
        var normalizer = new QueryNormalizer(Map.of());
        for (String text : new String[]{null, "", "\u00a0\u3000", "字".repeat(2001)}) {
            assertThatThrownBy(() -> normalizer.normalize(text)).isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(normalizer.normalize("天文学").original()).isEqualTo("天文学");
    }
    @Test
    void genericQuestionPhrasesCannotOutweighTheRequestedAction() {
        var query = new QueryNormalizer(Map.of()).normalize("盘点什么情况下需要复盘");
        assertThat(query.termWeights()).doesNotContainKeys("情况", "需要").containsKeys("盘点", "复盘");
        assertThat(query.normalized()).contains("什么情况下需要");
    }

}
