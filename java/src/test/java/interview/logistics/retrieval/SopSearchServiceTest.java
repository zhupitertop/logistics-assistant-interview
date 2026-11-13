package interview.logistics.retrieval;

import interview.logistics.data.*;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import static org.assertj.core.api.Assertions.*;

class SopSearchServiceTest {
    private final DataSnapshot data = new DataLoader().load(Path.of("data"));
    private final SopSearchService service = new SopSearchService(data, new IndexBuilder().build(data));

    @Test
    void explicitWorkflowWinsOverGenericSynonymsAndNegatedWorkflow() {
        for (String question : List.of("退货入库收少了要记录哪些信息", "不是盘点，是退货少件怎么登记")) {
            var result = service.search(question);
            assertThat(result.workflow().workflows()).containsExactly(WorkflowPolicy.Workflow.RETURNS);
            assertThat(result.hits()).isNotEmpty().allMatch(hit -> hit.title().contains("退货"));
        }
        assertThat(service.search("入库与盘点差异怎么处理").outcome()).isEqualTo("ambiguous_workflow");
        assertThat(service.search("少件怎么办").outcome()).isEqualTo("workflow_required");
        assertThat(service.search("盘点").hits()).isNotEmpty();
    }

    @Test
    void quantitiesDoNotMixShortageWithExcessAndNoHitIsNotPadded() {
        assertThat(service.search("入库少件记录什么").hits()).noneMatch(hit -> hit.title().contains("溢收"));
        assertThat(service.search("退货多件怎么登记").hits()).noneMatch(hit -> hit.title().contains("短少"));
        var single = new DataSnapshot(Path.of("data"), Map.of("XYZ-99", new SopEntry("XYZ-99", "入库短少登记", "入库短少记录商品编码。")),
                Map.of(), Map.of(), Map.of("sop.json", "s", "synonyms.json", "q"));
        var tool = new SopSearchService(single, new IndexBuilder().build(single));
        assertThat(tool.searchSop("入库短少记录什么")).containsExactly(single.sops().get("XYZ-99"));
        assertThat(tool.searchSop("星际旅行费用")).isEmpty();
    }

    @Test
    void changingIdsDoesNotChangeTheSelectedContent() {
        var original = data.sops().get("INV-01");
        var changed = new java.util.TreeMap<>(data.sops());
        changed.remove(original.id());
        changed.put("NEW-99", new SopEntry("NEW-99", original.title(), original.text()));
        var snapshot = new DataSnapshot(data.sourceDirectory(), changed, data.orders(), data.synonyms(), data.sourceHashes());
        var result = new SopSearchService(snapshot, new IndexBuilder().build(snapshot))
                .searchSop("盘点时发现账面数比实物多，我要记哪些信息");
        assertThat(result).anyMatch(hit -> hit.id().equals("NEW-99") && hit.text().equals(original.text()));
    }

    @Test
    void identicalAndConcurrentQueriesAreDeterministicAndRequestIsolated() throws Exception {
        String first = "入库少件怎么登记";
        String second = "退货多件如何处理";
        var expectedFirst = service.search(first);
        var expectedSecond = service.search(second);
        var executor = Executors.newFixedThreadPool(4);
        try {
            var tasks = new java.util.ArrayList<java.util.concurrent.Callable<SopSearchService.SearchResult>>();
            for (int i = 0; i < 40; i++) { final String question = i % 2 == 0 ? first : second; tasks.add(() -> service.search(question)); }
            var results = executor.invokeAll(tasks);
            for (int i = 0; i < results.size(); i++) assertThat(results.get(i).get()).isEqualTo(i % 2 == 0 ? expectedFirst : expectedSecond);
        } finally { executor.shutdownNow(); }
        assertThat(new SopSearchService(data, new IndexBuilder().build(data)).search(first)).isEqualTo(expectedFirst);
    }
    @Test
    void pureStateDefinitionsCannotReplaceInvestigationSteps() {
        var snapshot = new DataSnapshot(Path.of("data"), Map.of(
                "AAA-01", new SopEntry("AAA-01", "订单状态说明", "已审核待出库指已经审核通过但尚未出库。已出库指已完成出库流程。"),
                "ZZZ-99", new SopEntry("ZZZ-99", "订单出库规范", "已审核待出库时，先核对作业任务，再确认可用库存。")),
                Map.of(), Map.of(), Map.of("sop.json", "s", "synonyms.json", "q"));
        var results = new SopSearchService(snapshot, new IndexBuilder().build(snapshot)).searchSop("已审核待出库为什么还没发");
        assertThat(results).extracting(SopEntry::id).containsExactly("ZZZ-99");
    }

}
