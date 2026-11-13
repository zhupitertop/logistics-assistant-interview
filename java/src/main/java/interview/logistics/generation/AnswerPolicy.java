package interview.logistics.generation;

import interview.logistics.routing.EvidenceCollection;
import interview.logistics.routing.RoutingPlan;
import java.util.List;

final class AnswerPolicy {
    private AnswerPolicy() {}

    static String status(EvidenceCollection evidence) {
        return switch (evidence.outcome()) {
            case "order_not_found" -> "order_not_found";
            case "insufficient_evidence" -> "insufficient_evidence";
            case "evidence_ready" -> {
                if (evidence.sops().isEmpty() && (evidence.order() == null || !evidence.order().found())) {
                    throw new IllegalStateException("Ready evidence has no facts");
                }
                yield "answered";
            }
            default -> throw new IllegalStateException("Unknown evidence outcome");
        };
    }

    static String render(String question, EvidenceCollection evidence, List<Citation> citations) {
        String status = status(evidence);
        if (status.equals("order_not_found")) {
            return "未在现有数据中找到订单 " + evidence.order().orderId() + "。";
        }
        String prefix = evidence.order() != null && evidence.order().found()
                ? "订单 " + evidence.order().orderId() + " 当前状态：" + evidence.order().orderStatus()
                  + "。仓库：" + evidence.order().warehouse() + "。" : "";
        if (status.equals("insufficient_evidence")) {
            String detail = switch (evidence.reason()) {
                case "missing_order_id" -> "请提供需要查询的订单号。";
                case "multiple_orders" -> "请明确本次要查询的一票订单。";
                case "invalid_order_id" -> "请核对并提供完整订单号。";
                case "order_detail_not_available" -> "现有订单字段不包含所需明细，需要补充相应业务记录。";
                case "unsupported_order_status" -> "缺少适用于该状态的排查规则。";
                case "status_workflow_conflict" -> "已知订单状态与所问流程不一致，需要补充相应记录核实。";
                case "ambiguous_workflow", "workflow_required" -> "请明确是入库、盘点、出库实拣还是退货等具体流程。";
                default -> question.matches("(?s).*(?:赔付|赔偿|赔多少|最多赔).*")
                        ? "本次未检索到所需的赔付规则。" : "本次未检索到足够的规范依据。";
            };
            return prefix + "现有数据无法确认。" + detail;
        }
        if (citations.isEmpty()) return prefix;
        StringBuilder answer = new StringBuilder(prefix);
        if (!prefix.isEmpty()) answer.append('\n');
        answer.append("规范依据（同一条规范的连续原文）：");
        for (int i = 0; i < citations.size(); i++) {
            answer.append('\n').append('[').append(i + 1).append("] ").append(citations.get(i).excerpt());
        }
        if (evidence.plan().path() == RoutingPlan.Path.ORDER_DIAGNOSIS) {
            answer.append("\n以上为排查依据，具体原因待核实；仅凭订单状态无法确定库存、作业任务或承运商是否存在异常。");
        }
        return answer.toString();
    }
}
