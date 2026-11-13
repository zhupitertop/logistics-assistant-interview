package interview.logistics.routing;

import java.text.Normalizer;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import static interview.logistics.routing.RoutingPlan.Path.*;

public final class QuestionRouter {
    // Preserve the matched key exactly; accepting a lowercase spelling does not make it a known order.
    private static final Pattern ORDER = Pattern.compile("(?i)(?<![a-z0-9])HD[0-9]{9}(?![a-z0-9])");
    private static final Pattern ORDER_LIKE = Pattern.compile("(?iu)[hｈ][dｄ][0-9０-９]+");
    private static final List<String> DIAGNOSIS = List.of("为什么", "为何", "怎么还", "还没", "卡在", "卡住", "卡哪", "原因", "排查", "核查", "核对", "怎么处理", "怎么办", "如何处理", "能确定", "没发", "未出", "处理步骤");
    private static final List<String> PROCESS = List.of("流程", "规范", "步骤", "怎么做", "如何操作", "记录", "要记", "盘点", "入库", "拣货", "捡货", "实拣", "退货");

    public RoutingPlan route(String question) {
        if (question == null || question.codePoints().allMatch(cp -> Character.isWhitespace(cp) || Character.isSpaceChar(cp))) {
            throw new IllegalArgumentException("question must be a non-blank string");
        }
        if (question.codePointCount(0, question.length()) > 2000) throw new IllegalArgumentException("question exceeds 2000 code points");
        var ids = new LinkedHashSet<String>();
        var matcher = ORDER.matcher(question);
        while (matcher.find()) ids.add(matcher.group());
        // Any malformed extra identifier must not be silently ignored next to a valid one.
        String withoutIds = withoutOrderIds(question);
        if (ORDER_LIKE.matcher(withoutIds).find()) return new RoutingPlan(NEEDS_CLARIFICATION, null, "invalid_order_id");
        if (ids.size() > 1) return new RoutingPlan(NEEDS_CLARIFICATION, null, "multiple_orders");
        String text = Normalizer.normalize(withoutIds, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        if (ids.size() == 1) {
            String id = ids.iterator().next();
            return new RoutingPlan(contains(text, DIAGNOSIS) || contains(text, PROCESS) ? ORDER_DIAGNOSIS : ORDER_STATUS,
                    id, "single_order");
        }
        boolean general = contains(text, List.of("一般", "通常", "通用", "流程", "规范", "步骤", "怎么操作")) || contains(text, PROCESS);
        boolean needsSpecificOrder = contains(text, List.of("这票", "这单", "这个订单", "订单状态", "订单进度", "到哪了"))
                || (!general && contains(text, List.of("还没出库", "没发出去", "现在是什么状态")))
                || contains(text, List.of("为什么还没", "怎么还没"));
        if (needsSpecificOrder && !contains(text, List.of("一般", "通常", "通用"))) {
            return new RoutingPlan(NEEDS_CLARIFICATION, null, "missing_order_id");
        }
        return new RoutingPlan(SOP_ONLY, null, "sop_question");
    }

    public static String withoutOrderIds(String text) { return ORDER.matcher(text).replaceAll(" "); }
    private static boolean contains(String text, List<String> values) {
        return values.stream().anyMatch(text::contains);
    }
}
