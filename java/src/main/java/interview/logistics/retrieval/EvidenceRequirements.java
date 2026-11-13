package interview.logistics.retrieval;

import java.text.Normalizer;
import java.util.Locale;

/** Requested fact types, not mappings from questions to clauses or answers. */
public final class EvidenceRequirements {
    private EvidenceRequirements() {}
    private static String canonical(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
    }
    public static boolean asksDuration(String question) {
        return canonical(question).matches("(?s).*(?:什么时候|何时|多久|多长时间|几天|几小时|预计).*");
    }
    public static boolean asksNumericLimit(String question) {
        return canonical(question).matches("(?s).*(?:上限|下限|(?:最多|至少|允许|可以|能).{0,8}(?:几件|多少件)|(?:金额|运费|费用|价格|温度|湿度|重量).{0,12}(?:多少|几)).*");
    }
    public static boolean asksIncidentAttribution(String question) {
        return canonical(question).matches("(?s).*(?:(?:谁|哪个人|哪位).*(?:偷|盗|造成|导致)|(?:偷|盗).*(?:谁|哪个人)|(?:责任人|责任方).*(?:是谁|确定|认定)).*");
    }
    public static boolean asksUnavailableOrderField(String question) {
        String q = canonical(question);
        // Modal words alone (e.g. "需要知道电话") do not turn a factual request into a procedure.
        boolean procedure = q.matches("(?s).*(?:(?:怎么|如何).{0,8}(?:查询|查|联系|记录|登记|核对|核查|处理)"
                + "|(?:应该|应当|需要|须|要).{0,6}(?:记录|登记|核对|核查|确认|处理)|规范|流程|步骤|要记).*");
        boolean missingField = q.matches("(?s).*(?:承运商|承运人|运单号|物流单号|收货人|收件人|收货地址|收件地址|仓库地址"
                + "|电话|手机号|联系方式|商品明细|任务号|负责人|处理人|扫描站点|签收时间).*");
        boolean physicalLocation = q.matches("(?s).*(?:所在城市|当前位置|实际位置|目前位置|目前去向|到(?:哪个|哪座|什么)城市"
                + "|(?:货物|货|包裹).{0,6}(?:在哪里|在哪儿|在哪个地方)).*");
        boolean currentStock = q.matches("(?s).*(?:(?:还有|有没有|是否有|有无|剩余|剩下|当前|实际|可用).{0,4}库存"
                + "|库存.{0,5}(?:多少|够不够|充足|足够|有吗)).*");
        return !procedure && (missingField || physicalLocation || currentStock);
    }
    public static boolean requiresMoreThanStatus(String question) {
        return asksDuration(question) || asksNumericLimit(question) || asksIncidentAttribution(question) || asksUnavailableOrderField(question);
    }
    public static boolean supports(String question, String sourceText) {
        // A normative corpus plus order progress fields contains no incident investigation dossier.
        if (asksIncidentAttribution(question)) return false;
        if (asksDuration(question) && !sourceText.matches("(?s).*(?:[0-9]+|[一二三四五六七八九十百]+)\\s*(?:分钟|小时|工作日|天|周).*") ) return false;
        if (asksNumericLimit(question) && !sourceText.matches("(?s).*[0-9]+(?:\\.[0-9]+)?\\s*(?:元|件|度|℃|%|％|公斤|千克|吨).*") ) return false;
        return true;
    }
}
