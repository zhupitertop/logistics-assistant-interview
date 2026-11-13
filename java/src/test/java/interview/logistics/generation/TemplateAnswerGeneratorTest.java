package interview.logistics.generation;

import interview.logistics.data.*;
import interview.logistics.order.OrderQueryTool;
import interview.logistics.retrieval.*;
import interview.logistics.routing.*;
import interview.logistics.trace.TraceStep;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class TemplateAnswerGeneratorTest {
    private final DataSnapshot data = new DataLoader().load(Path.of("data"));
    private final TemplateAnswerGenerator generator = new TemplateAnswerGenerator("off");

    private EvidenceCollection collect(DataSnapshot snapshot, String question) {
        return new EvidenceCollector(new QuestionRouter(), new OrderQueryTool(snapshot),
                new SopSearchService(snapshot, new IndexBuilder().build(snapshot))).collect(question);
    }

    @Test
    void answerChangesWithRetrievedSourceAndDisappearsWhenEvidenceIsRemoved() {
        String question = "盘点时发现账面数比实物多，我要记哪些信息";
        var before = generator.generate(question, collect(data, question));
        assertThat(before.answer()).contains("商品编号", "仓库编号", "系统库存", "实盘数量");
        var modified = new java.util.TreeMap<>(data.sops());
        var primary = modified.get("INV-01");
        String added = "本次还须记录复核凭证编号。";
        modified.put(primary.id(), new SopEntry(primary.id(), primary.title(), primary.text() + added));
        modified.put("HDO-05", new SopEntry("HDO-05", "作业交接", "仅在未命中材料中的秘密结论。"));
        var snapshot = new DataSnapshot(data.sourceDirectory(), modified, data.orders(), data.synonyms(), data.sourceHashes());
        var after = generator.generate(question, collect(snapshot, question));
        assertThat(after.answer()).contains(added).doesNotContain("秘密结论");
        assertThat(after.citations().stream().map(Citation::excerpt).collect(java.util.stream.Collectors.joining()))
                .isEqualTo(primary.text() + added);
        modified.entrySet().removeIf(entry -> entry.getValue().title().contains("盘点"));
        snapshot = new DataSnapshot(data.sourceDirectory(), modified, data.orders(), data.synonyms(), data.sourceHashes());
        var missing = generator.generate(question, collect(snapshot, question));
        assertThat(missing.status()).isEqualTo("insufficient_evidence");
        assertThat(missing.answer()).contains("现有数据无法确认").doesNotContain(added);
        assertThat(missing.citations()).isEmpty();
    }

    @Test
    void UnicodeExcerptsRemainContinuousAndReconstructEveryCondition() {
        String text = "条件：" + "𠀀".repeat(119) + "，未经复核不得放行。\n" + "保留原始记录。".repeat(30);
        var source = new SopEntry("TST-01", "连续原文", text);
        var citations = new CitationBuilder().build(source);
        assertThat(citations).hasSizeGreaterThan(2);
        for (var citation : citations) {
            assertThat(citation.excerpt().codePointCount(0, citation.excerpt().length())).isBetween(1, 120);
            assertThat(text).contains(citation.excerpt());
            assertThat(Character.isHighSurrogate(citation.excerpt().charAt(citation.excerpt().length() - 1))).isFalse();
        }
        assertThat(citations.stream().map(Citation::excerpt).collect(java.util.stream.Collectors.joining())).isEqualTo(text);
    }

    @Test
    void validatorRejectsForgedFactsCitationsAndDroppedConditions() {
        String question = "盘点少件记录什么";
        var evidence = collect(data, question);
        var original = generator.generate(question, evidence);
        var citation = original.citations().get(0);
        var validator = new EvidenceValidator();
        var bad = List.of(
                new GeneratedAnswer(original.status(), original.answer() + "库存已经丢失。", original.citations(), original.usedIds()),
                new GeneratedAnswer("order_not_found", original.answer(), original.citations(), original.usedIds()),
                new GeneratedAnswer(original.status(), original.answer(), List.of(new Citation("FAK-99", citation.title(), citation.excerpt())), List.of("FAK-99")),
                new GeneratedAnswer(original.status(), original.answer(), List.of(new Citation(citation.id(), "伪造标题", citation.excerpt())), original.usedIds()),
                new GeneratedAnswer(original.status(), original.answer(), List.of(new Citation(citation.id(), citation.title(), "并不在原文的句子")), original.usedIds()),
                new GeneratedAnswer(original.status(), original.answer(), List.of(new Citation(citation.id(), citation.title(), citation.excerpt().substring(0, 10))), original.usedIds()),
                new GeneratedAnswer(original.status(), original.answer(), original.citations(), List.of("FAK-99")));
        for (var value : bad) assertThatThrownBy(() -> validator.validate(question, evidence, value)).isInstanceOf(IllegalStateException.class);
        var noSearch = new EvidenceCollection(evidence.plan(), evidence.outcome(), evidence.reason(), evidence.order(), evidence.sops(), List.of());
        assertThatThrownBy(() -> validator.validate(question, noSearch, original)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void diagnosisKeepsCauseUncertainAndStateOnlyHasNoCitations() {
        String question = "HD202611001 怎么还没发出去，能确定卡在哪吗";
        var answer = generator.generate(question, collect(data, question));
        assertThat(answer.answer()).contains("已审核待出库", "待核实").doesNotContain("原因是库存不足");
        assertThat(answer.citations()).extracting(Citation::id).containsOnly("OUT-02");
        var state = generator.generate("HD202611002状态", collect(data, "HD202611002状态"));
        assertThat(state.answer()).contains("待审核", "WH01");
        assertThat(state.citations()).isEmpty();
    }

    @Test
    void unavailableEvidenceNeverFallsBackToBusinessKnowledge() {
        for (String question : List.of("这票现在是什么状态", "少件怎么办", "HD202611001运输扫描异常怎么处理", "跨境冷链破损最多赔多少")) {
            var answer = generator.generate(question, collect(data, question));
            assertThat(answer.status()).isEqualTo("insufficient_evidence");
            assertThat(answer.answer()).contains("现有数据无法确认");
            assertThat(answer.citations()).isEmpty();
        }
        var missing = generator.generate("HD202699999状态", collect(data, "HD202699999状态"));
        assertThat(missing.status()).isEqualTo("order_not_found");
        assertThat(missing.answer()).doesNotContain("已取消", "待审核");
    }

    @Test
    void unsupportedModelModesFailRatherThanCallingExternalServices() {
        for (String mode : new String[]{null, "", "on", "OFF", "unexpected"}) {
            assertThatThrownBy(() -> new TemplateAnswerGenerator(mode)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void traceInputsDefensivelyCopyNestedEvidenceLists() {
        var ids = new java.util.ArrayList<>(List.of("INV-01"));
        var trace = new TraceStep(1, "generate", Map.of("evidence_ids", ids), "ok", ids);
        ids.clear();
        assertThat(trace.input().get("evidence_ids")).isEqualTo(List.of("INV-01"));
        assertThat(trace.hitIds()).containsExactly("INV-01");
    }
}
