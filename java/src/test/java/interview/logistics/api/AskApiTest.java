package interview.logistics.api;

import interview.logistics.application.AskService;
import interview.logistics.data.DataSnapshot;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "agent.llm=off")
@AutoConfigureMockMvc
class AskApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired DataSnapshot data;
    @SpyBean AskService service;

    private String request(String question) throws Exception {
        return mvc.perform(post("/ask").contentType("application/json").content(mapper.writeValueAsString(Map.of("question", question))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
    }

    @Test
    void fivePublishedCasesMeetContractAndCitationsResolveToTrace() throws Exception {
        String[] questions = {"盘点时发现账面数比实物多，我要记哪些信息", "HD202611002 现在是什么状态", "HD202611001 怎么还没发出去，能确定卡在哪吗", "HD202699999 这票的状态是什么", "跨境冷链的破损赔付上限是多少"};
        String[] statuses = {"answered", "answered", "answered", "order_not_found", "insufficient_evidence"};
        String[] facts = {"商品编号", "待审核", "具体原因待核实", "未在现有数据中找到订单", "赔付规则"};
        var tools = List.of(List.of("sop_search", "generate"), List.of("order_query", "generate"), List.of("order_query", "sop_search", "generate"), List.of("order_query", "generate"), List.of("sop_search", "generate"));
        for (int i = 0; i < questions.length; i++) {
            JsonNode response = mapper.readTree(request(questions[i]));
            assertThat(response.size()).isEqualTo(4);
            assertThat(response.get("status").asText()).isEqualTo(statuses[i]);
            assertThat(response.get("answer").asText()).contains(facts[i]);
            var actualTools = new java.util.ArrayList<String>();
            var retrieved = new java.util.HashSet<String>();
            int seq = 1;
            for (JsonNode step : response.get("trace")) {
                assertThat(step.get("seq").asInt()).isEqualTo(seq++);
                actualTools.add(step.get("tool").asText());
                assertThat(step.get("input").isObject()).isTrue();
                assertThat(step.get("outcome").asText()).isIn("ok", "not_found", "no_hit");
                if (step.get("tool").asText().equals("sop_search")) step.get("hit_ids").forEach(id -> retrieved.add(id.asText()));
            }
            assertThat(actualTools).isEqualTo(tools.get(i));
            var used = new java.util.LinkedHashSet<String>();
            for (JsonNode citation : response.get("citations")) {
                String id = citation.get("id").asText();
                String excerpt = citation.get("excerpt").asText();
                assertThat(retrieved).contains(id);
                assertThat(data.sops().get(id).title()).isEqualTo(citation.get("title").asText());
                assertThat(data.sops().get(id).text()).contains(excerpt);
                assertThat(excerpt.codePointCount(0, excerpt.length())).isBetween(1, 120);
                assertThat(response.get("answer").asText()).contains(excerpt);
                used.add(id);
            }
            var last = response.get("trace").get(response.get("trace").size() - 1);
            assertThat(last.get("input").get("mode").asText()).isEqualTo("template");
            assertThat(last.get("input").get("evidence_ids")).isEqualTo(last.get("hit_ids"));
            assertThat(mapper.convertValue(last.get("hit_ids"), List.class)).isEqualTo(used.stream().toList());
            if (i == 0) assertThat(used).contains("INV-01");
            if (i == 2) assertThat(used).contains("OUT-02");
            if (i == 1 || i == 3 || i == 4) assertThat(response.get("citations").size()).isZero();
        }
    }

    @Test
    void rejectsMalformedOrCoercedRequestsWithoutInvokingApplicationService() throws Exception {
        for (String body : List.of("", "{", "null", "[]", "{}", "42", "true", "\"text\"", "{\"question\":null}", "{\"question\":42}", "{\"question\":true}", "{\"question\":[]}", "{\"question\":{}}", "{\"question\":\"\"}", "{\"question\":\" \\t\\u00a0\\u3000\"}", "{\"question\":\"盘点\",\"extra\":1}", "{\"question\":\"盘点\",\"question\":\"入库\"}", "{\"question\":\"盘点\"} {}")) {
            mvc.perform(post("/ask").contentType("application/json").content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
        }
        verifyNoInteractions(service);
    }

    @Test
    void unicodeLengthIsMeasuredInCodePointsAndMethodsAreChecked() throws Exception {
        request("𠀀".repeat(2000));
        mvc.perform(post("/ask").contentType("application/json").content(mapper.writeValueAsString(Map.of("question", "𠀀".repeat(2001)))))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/ask").contentType("text/plain").content("盘点"))
                .andExpect(status().isUnsupportedMediaType());
        mvc.perform(get("/ask")).andExpect(status().isMethodNotAllowed()).andExpect(header().string("Allow", "POST"));
        mvc.perform(post("/ask").contentType("application/json").accept("text/plain").content("{\"question\":\"盘点\"}"))
                .andExpect(status().isNotAcceptable());
    }

    @Test
    void repeatedResponsesAreIdenticalAndClarificationsStillRecordGeneration() throws Exception {
        String question = "HD202611001 怎么还没发出去，能确定卡在哪吗";
        assertThat(request(question)).isEqualTo(request(question));
        var response = mapper.readTree(request("这票是什么状态"));
        assertThat(response.get("status").asText()).isEqualTo("insufficient_evidence");
        assertThat(response.get("trace").size()).isEqualTo(1);
        assertThat(response.get("trace").get(0).get("tool").asText()).isEqualTo("generate");
    }
    @Test
    void concurrentBusinessRequestsKeepIndependentAnswersAndTraces() throws Exception {
        var questions = List.of("盘点少件记录什么", "HD202611002状态", "HD202611001为什么还没发出去", "HD202699999状态", "这票现在状态是什么");
        var expected = new java.util.ArrayList<String>();
        for (String question : questions) expected.add(request(question));
        var executor = java.util.concurrent.Executors.newFixedThreadPool(4);
        try {
            var tasks = new java.util.ArrayList<java.util.concurrent.Callable<String>>();
            for (int i = 0; i < 25; i++) { String question = questions.get(i % questions.size()); tasks.add(() -> request(question)); }
            var results = executor.invokeAll(tasks);
            for (int i = 0; i < results.size(); i++) assertThat(results.get(i).get()).isEqualTo(expected.get(i % questions.size()));
        } finally { executor.shutdownNow(); }
    }

    @Test
    void givenInventoryFieldsAreNotReplacedByLegacySyntheticFields() throws Exception {
        var response = mapper.readTree(request("盘点时发现账面数比实物多，我要记哪些信息"));
        assertThat(response.get("answer").asText()).contains("商品编号", "仓库编号", "系统库存", "实盘数量")
                .doesNotContain("商品编码", "复核人", "盘点人");
        mvc.perform(get("/web/app.js")).andExpect(status().isOk());
        mvc.perform(get("/web/style.css")).andExpect(status().isOk());
    }

    @Test
    void upstreamOrderIsAvailableWithExactWarehouseAndState() throws Exception {
        var response = mapper.readTree(request("HD202611005 现在是什么状态"));
        assertThat(response.get("status").asText()).isEqualTo("answered");
        assertThat(response.get("answer").asText()).contains("HD202611005", "WH03", "运输异常");
        assertThat(response.get("citations").size()).isZero();
        assertThat(response.get("trace").size()).isEqualTo(2);
        assertThat(response.get("trace").get(0).get("tool").asText()).isEqualTo("order_query");
    }

    @Test
    void upstreamTransportClauseIsRetrievedAndQuotedAfterMerge() throws Exception {
        String addition = "升级异常前，须先记录最后有效扫描站点。";
        assertThat(data.sops().get("TRA-02").text()).endsWith(addition);
        var response = mapper.readTree(request("HD202611005 异常升级前需要记录最后有效扫描站点吗"));
        assertThat(response.get("status").asText()).isEqualTo("answered");
        assertThat(response.get("answer").asText()).contains(addition, "运输异常", "具体原因待核实");
        boolean cited = false;
        for (var citation : response.get("citations")) {
            if (citation.get("id").asText().equals("TRA-02") && citation.get("excerpt").asText().contains(addition)) cited = true;
        }
        assertThat(cited).isTrue();
        assertThat(response.get("trace").get(0).get("tool").asText()).isEqualTo("order_query");
        assertThat(response.get("trace").get(1).get("tool").asText()).isEqualTo("sop_search");
        assertThat(response.get("trace").get(1).get("hit_ids").toString()).contains("TRA-02");
        assertThat(response.get("trace").get(2).get("hit_ids").toString()).contains("TRA-02");
    }

    @Test
    void unsupportedFactTypesDoNotBorrowNearbyProcedureClauses() throws Exception {
        for (String question : List.of("盘点差异是谁偷走了货物", "HD202611001 什么时候能发货，预计几天", "运输异常多久能够恢复",
                "入库差异冷链温度上限是多少度", "退货数量不符最多可以少几件", "HD202611005 最后有效扫描站点在哪里",
                "HD202611001 承运商是谁", "HD202611001 运费是多少")) {
            var response = mapper.readTree(request(question));
            assertThat(response.get("status").asText()).as(question).isEqualTo("insufficient_evidence");
            assertThat(response.get("answer").asText()).contains("现有数据无法确认");
            assertThat(response.get("citations").size()).isZero();
        }
    }

    @Test
    void suspectedInventoryCauseInvokesInvestigationRatherThanOnlyReadingStatus() throws Exception {
        var response = mapper.readTree(request("HD202611001 是库存不足造成的吗"));
        assertThat(response.get("answer").asText()).contains("已审核待出库", "具体原因待核实");
        assertThat(response.get("trace").get(1).get("tool").asText()).isEqualTo("sop_search");
        assertThat(response.get("answer").asText()).doesNotContain("原因是库存不足", "确定是库存不足");
    }

    @Test
    void unavailableContactLocationAndStockDetailsCannotBeAnsweredWithStatus() throws Exception {
        for (String question : List.of("HD202611001 收件人电话是多少", "HD202611001 货现在到哪个城市了",
                "HD202611001 还有库存吗", "HD202611001 需要知道收件人电话是多少", "HD202611001 当前库存有多少")) {
            var response = mapper.readTree(request(question));
            assertThat(response.get("status").asText()).as(question).isEqualTo("insufficient_evidence");
            assertThat(response.get("answer").asText()).contains("现有数据无法确认", "现有订单字段不包含所需明细");
            assertThat(response.get("citations").isEmpty()).isTrue();
            assertThat(response.get("trace").findValuesAsText("tool")).containsExactly("order_query", "generate");
        }
        var warehouse = mapper.readTree(request("HD202611001 在哪个仓库"));
        assertThat(warehouse.get("status").asText()).isEqualTo("answered");
        assertThat(warehouse.get("answer").asText()).contains("WH01");
        assertThat(warehouse.get("trace").findValuesAsText("tool")).containsExactly("order_query", "generate");
    }

    @Test
    void proceduralWhenQuestionsUseTriggerConditionsWithoutInventingAnEta() throws Exception {
        for (String question : List.of("盘点时，什么时候应该复盘", "盘点何时需要复盘", "盘点什么情况下需要复盘")) {
            var response = mapper.readTree(request(question));
            assertThat(response.get("status").asText()).as(question).isEqualTo("answered");
            assertThat(response.get("answer").asText()).contains("初盘出现账实差异时", "第二名盘点人员", "初盘数和复盘数");
            assertThat(response.get("citations").findValuesAsText("id")).containsOnly("INV-04");
        }
        for (String question : List.of("盘点复盘需要几小时", "盘点什么时候应该复盘，预计要多久", "HD202611001 什么时候能发货")) {
            var response = mapper.readTree(request(question));
            assertThat(response.get("status").asText()).as(question).isEqualTo("insufficient_evidence");
            assertThat(response.get("citations").isEmpty()).isTrue();
        }
    }

    @Test
    void shortageRegistrationUsesMismatchEvidenceInsteadOfUnrelatedMovementRecords() throws Exception {
        for (String question : List.of("我是在做盘点，不是收货，少了两件该登记什么", "盘点少了两件要登记哪些信息",
                "清库点数发现数量短少，怎么登记")) {
            var response = mapper.readTree(request(question));
            assertThat(response.get("status").asText()).as(question).isEqualTo("answered");
            assertThat(response.get("citations").findValuesAsText("id")).containsOnly("INV-01");
            assertThat(response.get("answer").asText()).contains("商品编号", "仓库编号", "系统库存", "实盘数量")
                    .doesNotContain("目标库位", "移动数量");
        }
    }

}
