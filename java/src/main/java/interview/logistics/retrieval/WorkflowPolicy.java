package interview.logistics.retrieval;

import interview.logistics.data.SopEntry;
import java.util.EnumSet;
import java.util.Set;

/** Uses business expressions and document titles, never SOP identifiers. */
public final class WorkflowPolicy {
    public enum Workflow { INBOUND, INVENTORY, PICKING, RETURNS, OUTBOUND, TRANSPORT, HANDOVER, STORAGE }
    public record Decision(Set<Workflow> workflows) {
        public Decision {
            var ordered = EnumSet.noneOf(Workflow.class);
            ordered.addAll(workflows);
            workflows = java.util.Collections.unmodifiableSet(ordered);
        }
        public boolean ambiguous() { return workflows.size() > 1; }
    }

    public Decision detect(NormalizedQuery query) {
        var explicit = detect(query.normalized());
        // An expansion may resolve an otherwise generic outbound question, but cannot
        // override a workflow that the employee explicitly named.
        if (explicit.workflows().isEmpty() || explicit.workflows().equals(Set.of(Workflow.OUTBOUND))) {
            var expanded = detect(query.normalized() + " " + String.join(" ", query.expansions()));
            if (!expanded.workflows().isEmpty()) return expanded;
        }
        return explicit;
    }

    public Decision detectExplicit(NormalizedQuery query) { return detect(query.normalized()); }

    private Decision detect(String text) {
        var flows = EnumSet.noneOf(Workflow.class);
        if (contains(text, "退货", "退回", "实退", "退少", "退多", "退错")) flows.add(Workflow.RETURNS);
        if (contains(text, "盘点", "盘库", "复盘", "盘盈", "盘亏", "清库点数")) flows.add(Workflow.INVENTORY);
        if (text.contains("账面") && contains(text, "实物", "实盘")) flows.add(Workflow.INVENTORY);
        if (contains(text, "拣货", "捡货", "实拣", "错拣", "多拣", "拣少", "库位缺货", "应拣", "超拣")) flows.add(Workflow.PICKING);
        if (contains(text, "入库", "收货", "到货", "来货", "实收", "应收", "上架")) flows.add(Workflow.INBOUND);
        if (contains(text, "运输", "物流", "扫描", "轨迹", "揽收", "运单", "节点")) flows.add(Workflow.TRANSPORT);
        if (contains(text, "出库", "出仓", "发货", "发出去", "待出库", "审核")) flows.add(Workflow.OUTBOUND);
        if (flows.isEmpty() && contains(text, "仓储", "移库", "库位标签", "待处理区", "库位容量")) flows.add(Workflow.STORAGE);
        if (flows.isEmpty() && contains(text, "交接", "追溯", "留痕", "交班", "接班")) flows.add(Workflow.HANDOVER);
        // Returning excess picked stock is not the customer-return workflow.
        if (flows.contains(Workflow.RETURNS) && !contains(text, "退货", "实退", "退少", "退多", "退错")
                && (flows.contains(Workflow.PICKING) || flows.contains(Workflow.INVENTORY) || flows.contains(Workflow.INBOUND))) {
            flows.remove(Workflow.RETURNS);
        }
        // These are workflow qualifications, e.g. return receiving and outbound picking.
        if (flows.contains(Workflow.RETURNS)) flows.remove(Workflow.INBOUND);
        if (flows.contains(Workflow.PICKING) || flows.contains(Workflow.TRANSPORT)) flows.remove(Workflow.OUTBOUND);
        return new Decision(flows);
    }

    public boolean compatible(Decision decision, SopEntry entry) {
        if (decision.workflows().isEmpty()) return true;
        String title = QueryNormalizer.canonical(entry.title());
        var document = detect(title).workflows();
        // Original data reuses generic outbound titles for picking clauses.
        // Inspect the clause only for that generic family; never classify by ID.
        if (document.equals(Set.of(Workflow.OUTBOUND)) && contains(entry.text(), "实拣", "拣货", "应拣", "超拣")) {
            document = Set.of(Workflow.PICKING);
        }
        if (title.startsWith("订单状态说明") && document.isEmpty()) {
            if (contains(entry.text(), "运输异常") && !contains(entry.text(), "待审核", "已出库")) document = Set.of(Workflow.TRANSPORT);
            else if (contains(entry.text(), "待审核", "已出库", "已审核待出库")) document = Set.of(Workflow.OUTBOUND);
        }
        if (decision.workflows().equals(Set.of(Workflow.OUTBOUND))) {
            return document.contains(Workflow.OUTBOUND) || document.contains(Workflow.PICKING);
        }
        return document.containsAll(decision.workflows());
    }

    private static boolean contains(String text, String... expressions) {
        for (String expression : expressions) if (text.contains(expression)) return true;
        return false;
    }
}
