package interview.logistics.routing;

import org.junit.jupiter.api.Test;
import static interview.logistics.routing.RoutingPlan.Path.*;
import static org.assertj.core.api.Assertions.*;

class QuestionRouterTest {
    private final QuestionRouter router = new QuestionRouter();

    @Test
    void classifiesTheThreePathsAndStatusPredicates() {
        for (String q : new String[]{"盘点少件记录什么", "一般未出库该怎么排查", "订单出库规范是什么"}) {
            assertThat(router.route(q).path()).as(q).isEqualTo(SOP_ONLY);
        }
        for (String q : new String[]{"HD202611002", "HD202611002现在是什么状态", "这票HD202611002有没有运输异常", "HD202611002进度到哪了"}) {
            assertThat(router.route(q).path()).as(q).isEqualTo(ORDER_STATUS);
        }
        for (String q : new String[]{"HD202611001怎么还没发出去，能确定卡在哪吗", "HD202611004为什么异常", "HD202611001状态和下一步怎么处理", "HD202611001对应的出库流程是什么"}) {
            assertThat(router.route(q).path()).as(q).isEqualTo(ORDER_DIAGNOSIS);
        }
    }

    @Test
    void requiresOneExactIdAndDoesNotTruncateMalformedIds() {
        for (String q : new String[]{"这票的状态是什么", "为什么还没出库", "为什么还没入库", "现在是什么状态"}) {
            assertThat(router.route(q).reason()).as(q).isEqualTo("missing_order_id");
        }
        assertThat(router.route("HD202611001和HD202611002现在状态").reason()).isEqualTo("multiple_orders");
        for (String q : new String[]{"HD2026110012状态", "HD20261100状态", "ＨＤ２０２６１１００１状态", "HD202611001与HD123现在状态"}) {
            assertThat(router.route(q).reason()).as(q).isEqualTo("invalid_order_id");
        }
        assertThat(router.route("HD202611001，重复确认HD202611001的状态").orderId()).isEqualTo("HD202611001");
        assertThat(router.route("hd202611001状态").orderId()).isEqualTo("hd202611001");
    }

    @Test
    void rejectsInvalidQuestionsBeforeCallingTools() {
        for (String q : new String[]{null, "", "\u00a0\u3000", "字".repeat(2001)}) {
            assertThatThrownBy(() -> router.route(q)).isInstanceOf(IllegalArgumentException.class);
        }
    }
    @Test
    void contactLocationAndStockRequestsNeedMoreThanProgressFields() {
        for (String question : new String[]{"HD202611001 收件人电话是多少", "HD202611001 货现在到哪个城市了",
                "HD202611001 还有库存吗", "HD202611001 当前库存有多少"}) {
            assertThat(router.route(question).path()).as(question).isEqualTo(ORDER_DIAGNOSIS);
        }
        assertThat(router.route("HD202611001 在哪个仓库").path()).isEqualTo(ORDER_STATUS);
        assertThat(router.route("HD202611001进度到哪了").path()).isEqualTo(ORDER_STATUS);
    }

}
