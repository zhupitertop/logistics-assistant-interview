package interview.logistics.retrieval;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class EvidenceRequirementsTest {
    @Test
    void actualDetailsAreDistinctFromProceduresAndSuspectedCauses() {
        for (String question : List.of("收件人电话是多少", "货现在到哪个城市了", "还有库存吗", "需要知道收件人电话是多少", "当前库存有多少")) {
            assertThat(EvidenceRequirements.asksUnavailableOrderField(question)).as(question).isTrue();
        }
        for (String question : List.of("异常升级前需要记录最后有效扫描站点吗", "如何核对运单号", "库存不足造成的吗", "在哪个仓库", "进度到哪了")) {
            assertThat(EvidenceRequirements.asksUnavailableOrderField(question)).as(question).isFalse();
        }
    }
    @Test
    void triggerConditionsAreNotDurationsButMixedQuestionsStillNeedTimeEvidence() {
        for (String question : List.of("什么时候应该复盘", "何时需要复盘", "什么情况下需要复盘")) {
            assertThat(EvidenceRequirements.asksDuration(question)).as(question).isFalse();
        }
        for (String question : List.of("什么时候能发货", "何时送到", "什么时候应该能发货", "复盘需要几小时", "什么时候应该复盘，预计要多久")) {
            assertThat(EvidenceRequirements.asksDuration(question)).as(question).isTrue();
            assertThat(EvidenceRequirements.supports(question, "初盘出现账实差异时，由第二名盘点人员复盘。")).isFalse();
        }
    }

    @Test
    void recordingAnUnrelatedQuantityDoesNotEstablishDiscrepancyEvidence() {
        String question = "盘点少了两件该登记什么";
        assertThat(EvidenceRequirements.asksQuantityRegistration(question)).isTrue();
        assertThat(EvidenceRequirements.supports(question, "盘点期间移动货物时登记移动数量、库位和时间。")).isFalse();
        assertThat(EvidenceRequirements.supports(question, "盘点数量有差异时记录系统库存和实盘数量。")).isTrue();
        assertThat(EvidenceRequirements.asksQuantityRegistration("盘点移动货物要登记哪些库位")).isFalse();
    }

    @Test
    void proceduralLookupStillNeedsEvidenceForTheRequestedField() {
        assertThat(EvidenceRequirements.requiresMoreThanStatus("怎么查收件人电话")).isTrue();
        assertThat(EvidenceRequirements.supports("如何核对收件人电话", "已审核待出库时先核对库存和作业任务。")).isFalse();
        assertThat(EvidenceRequirements.supports("如何核对收件人电话", "核对收件人联系方式并记录核实结果。")).isTrue();
        assertThat(EvidenceRequirements.supports("如何核对承运商", "向承运人核实并记录结果。")).isTrue();
    }

}
