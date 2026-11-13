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
}
