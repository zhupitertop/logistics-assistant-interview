package interview.logistics.routing;

import interview.logistics.data.*;
import interview.logistics.order.OrderQueryTool;
import interview.logistics.retrieval.*;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Executors;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class EvidenceCollectorTest {
    private final DataSnapshot data = new DataLoader().load(Path.of("data"));
    private final OrderQueryTool orders = spy(new OrderQueryTool(data));
    private final SopSearchService sops = spy(new SopSearchService(data, new IndexBuilder().build(data)));
    private final EvidenceCollector collector = new EvidenceCollector(new QuestionRouter(), orders, sops);

    @Test
    void sopPathOnlyCallsSearchAndReturnsRealSourceEvidence() {
        var result = collector.collect("盘点时发现账面数比实物多，我要记哪些信息");
        verifyNoInteractions(orders);
        verify(sops).search("盘点时发现账面数比实物多，我要记哪些信息");
        assertThat(result.sops()).anyMatch(entry -> entry.id().equals("INV-01"));
        assertThat(result.trace()).hasSize(1);
        assertThat(result.trace().get(0).tool()).isEqualTo("sop_search");
    }

    @Test
    void stateOnlyNeverInvokesSearch() {
        var result = collector.collect("HD202611002 现在是什么状态");
        assertThat(result.order().orderStatus()).isEqualTo("待审核");
        assertThat(result.outcome()).isEqualTo("evidence_ready");
        assertThat(result.sops()).isEmpty();
        assertThat(result.trace()).hasSize(1);
        assertThat(result.trace().get(0).hitIds()).containsExactly("HD202611002");
        verify(orders).queryOrder("HD202611002");
        verifyNoInteractions(sops);
    }

    @Test
    void investigationQueriesOrderBeforeSearchAndUsesActualStatus() {
        String question = "HD202611001 怎么还没发出去，能确定卡在哪吗";
        var result = collector.collect(question);
        var sequence = inOrder(orders, sops);
        sequence.verify(orders).queryOrder("HD202611001");
        sequence.verify(sops).search(argThat(query -> query.contains("已审核待出库") && !query.contains("HD202611001")));
        assertThat(result.order().orderStatus()).isEqualTo("已审核待出库");
        assertThat(result.sops()).anyMatch(entry -> entry.id().equals("OUT-02"));
        assertThat(result.trace()).extracting(step -> step.seq()).containsExactly(1, 2);
        assertThat(result.trace()).extracting(step -> step.tool()).containsExactly("order_query", "sop_search");
        assertThat(result.trace().get(1).input().get("query")).asString().contains("订单状态：已审核待出库");
        assertThat(result.trace().get(1).hitIds()).contains("OUT-02");
        assertThat(result.outcome()).isEqualTo("evidence_ready");
    }

    @Test
    void missingOrderStopsWithoutSearchingOrInventingFields() {
        var result = collector.collect("HD202699999 为什么还没发货");
        verifyNoInteractions(sops);
        assertThat(result.outcome()).isEqualTo("order_not_found");
        assertThat(result.order().found()).isFalse();
        assertThat(result.order().warehouse()).isNull();
        assertThat(result.order().orderStatus()).isNull();
        assertThat(result.trace()).hasSize(1);
        assertThat(result.trace().get(0).outcome()).isEqualTo("not_found");
    }

    @Test
    void clarificationNeverInvokesToolsAndNoHitsRemainInsufficient() {
        for (String q : List.of("这票现在状态如何", "HD202611001 HD202611002状态", "HD123状态")) {
            var result = collector.collect(q);
            assertThat(result.outcome()).isEqualTo("insufficient_evidence");
            assertThat(result.trace()).isEmpty();
        }
        verifyNoInteractions(orders, sops);
        var result = collector.collect("跨境冷链的破损赔付上限是多少");
        assertThat(result.outcome()).isEqualTo("insufficient_evidence");
        assertThat(result.trace().get(0).outcome()).isEqualTo("no_hit");
        assertThat(result.trace().get(0).hitIds()).isEmpty();
    }

    @Test
    void workflowConflictKeepsKnownStatusWithoutMakingUpEvidence() {
        var result = collector.collect("HD202611001 运输扫描异常怎么处理");
        assertThat(result.order().found()).isTrue();
        assertThat(result.order().orderStatus()).isEqualTo("已审核待出库");
        assertThat(result.reason()).isEqualTo("status_workflow_conflict");
        assertThat(result.outcome()).isEqualTo("insufficient_evidence");
        verifyNoInteractions(sops);
    }

    @Test
    void shippedStatusIsNotOverriddenByAnUnshippedPremise() {
        var result = collector.collect("HD202611003 为什么还没发出去");
        assertThat(result.order().orderStatus()).isEqualTo("已出库");
        assertThat(result.trace().get(1).input().get("query")).asString().contains("已出库").doesNotContain("没发出去", "待出库");
        assertThat(result.sops().get(0).id()).isEqualTo("ST-04");
    }

    @Test
    void unknownStatusIsReturnedButDoesNotBecomeAnInventedStage() {
        var changed = new java.util.TreeMap<>(data.orders());
        changed.put("HD202611001", new OrderEntry("HD202611001", "WH01", "处理中"));
        var snapshot = new DataSnapshot(data.sourceDirectory(), data.sops(), changed, data.synonyms(), data.sourceHashes());
        var tool = new OrderQueryTool(snapshot);
        var collection = new EvidenceCollector(new QuestionRouter(), tool, sops);
        assertThat(collection.collect("HD202611001状态").order().orderStatus()).isEqualTo("处理中");
        var result = collection.collect("HD202611001为什么卡住");
        assertThat(result.reason()).isEqualTo("unsupported_order_status");
        assertThat(result.order().orderStatus()).isEqualTo("处理中");
        verifyNoInteractions(sops);
    }

    @Test
    void repeatedAndConcurrentCollectionsDoNotLeakTraceOrEvidence() throws Exception {
        var plain = new EvidenceCollector(new QuestionRouter(), new OrderQueryTool(data),
                new SopSearchService(data, new IndexBuilder().build(data)));
        var questions = List.of("盘点少件记录什么", "HD202611002状态", "HD202611004异常怎么处理", "HD202699999状态");
        var expected = questions.stream().map(plain::collect).toList();
        var executor = Executors.newFixedThreadPool(4);
        try {
            var tasks = new java.util.ArrayList<java.util.concurrent.Callable<EvidenceCollection>>();
            for (int i = 0; i < 40; i++) { String q = questions.get(i % 4); tasks.add(() -> plain.collect(q)); }
            var results = executor.invokeAll(tasks);
            for (int i = 0; i < results.size(); i++) assertThat(results.get(i).get()).isEqualTo(expected.get(i % 4));
        } finally { executor.shutdownNow(); }
        assertThatThrownBy(() -> expected.get(0).trace().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> expected.get(0).trace().get(0).input().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
}
