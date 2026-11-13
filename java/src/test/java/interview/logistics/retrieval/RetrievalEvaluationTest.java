package interview.logistics.retrieval;

import interview.logistics.data.*;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.ArrayList;
import static org.assertj.core.api.Assertions.*;

class RetrievalEvaluationTest {
    @Test
    void developmentQueriesRespectTopThreeAndWorkflow() throws Exception {
        evaluate("development.json");
    }

    @Test
    void separateRegressionQueriesRespectTopThreeAndWorkflow() throws Exception {
        evaluate("regression.json");
    }

    static void evaluate(String filename) throws Exception {
        var data = new DataLoader().load(Path.of("data"));
        var service = new SopSearchService(data, new IndexBuilder().build(data));
        var cases = JsonFiles.mapper().readTree(Path.of("java/src/test/resources/retrieval", filename).toFile());
        var failures = new ArrayList<String>();
        int passed = 0;
        for (var item : cases) {
            String question = item.get("question").asText();
            var result = service.search(question);
            var ids = result.hits().stream().map(SopSearchService.Hit::id).toList();
            var expected = new ArrayList<String>();
            item.get("expectedIds").forEach(id -> expected.add(id.asText()));
            boolean ok = expected.isEmpty() ? ids.isEmpty() : ids.stream().anyMatch(expected::contains);
            if (!item.get("workflow").isNull()) {
                var flow = WorkflowPolicy.Workflow.valueOf(item.get("workflow").asText());
                ok &= result.workflow().workflows().contains(flow);
                // Independent fixture labels, not the production classifier, verify no cross-flow leakage.
                var allowed = switch (flow) {
                    case INBOUND -> java.util.Set.of("IN", "STO");
                    case INVENTORY -> java.util.Set.of("INV");
                    case PICKING -> java.util.Set.of("OUT");
                    case RETURNS -> java.util.Set.of("RET");
                    case OUTBOUND -> java.util.Set.of("OUT", "ST");
                    case TRANSPORT -> java.util.Set.of("TRA", "ST");
                    case HANDOVER -> java.util.Set.of("HDO");
                    case STORAGE -> java.util.Set.of("STO");
                };
                ok &= ids.stream().allMatch(id -> allowed.contains(id.substring(0, id.indexOf('-'))));
            }
            System.out.println("EVAL " + filename + " | " + question + " | " + ids + " | " + (ok ? "PASS" : "FAIL"));
            if (ok) passed++; else failures.add(question + " expected " + expected + " but got " + ids);
            assertThat(result.hits()).hasSizeLessThanOrEqualTo(3);
            assertThat(result.candidates()).hasSizeLessThanOrEqualTo(10);
            for (var hit : result.hits()) {
                assertThat(hit.text()).isEqualTo(data.sops().get(hit.id()).text());
                assertThat(hit.title()).isEqualTo(data.sops().get(hit.id()).title());
            }
        }
        System.out.println("EVAL SUMMARY " + filename + " " + passed + "/" + cases.size());
        assertThat(failures).isEmpty();
    }
}
