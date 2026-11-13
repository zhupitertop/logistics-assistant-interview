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
        boolean procedure = q.matches("(?s).*(?:规范|流程|记录|登记|核查|核对|怎么|如何|应该|需要|要记).*");
        return !procedure && q.matches("(?s).*(?:承运商|承运人|运单号|物流单号|收货人|收货地址|商品明细|任务号|负责人|处理人|扫描站点|签收时间).*");
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
