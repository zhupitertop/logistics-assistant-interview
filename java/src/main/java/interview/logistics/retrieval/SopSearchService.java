package interview.logistics.retrieval;

import interview.logistics.data.DataSnapshot;
import interview.logistics.data.SopEntry;
import java.util.List;

public final class SopSearchService {
    public record Hit(String id, String title, String text, double score, List<String> matchedTerms) {
        public Hit { matchedTerms = List.copyOf(matchedTerms); }
    }
    public record SearchResult(NormalizedQuery query, WorkflowPolicy.Decision workflow, String outcome,
                               List<Bm25Ranker.Candidate> candidates, List<Hit> hits) {
        public SearchResult { candidates = List.copyOf(candidates); hits = List.copyOf(hits); }
    }
    private final DataSnapshot data;
    private final QueryNormalizer normalizer;
    private final Bm25Ranker ranker;
    private final WorkflowPolicy workflows = new WorkflowPolicy();

    public SopSearchService(DataSnapshot data, SopIndex index) {
        this.data = data;
        this.normalizer = new QueryNormalizer(data.synonyms());
        this.ranker = new Bm25Ranker(index);
    }

    /** The tool-facing contract: at most three unchanged source SOP entries. */
    public List<SopEntry> searchSop(String question) {
        return search(question).hits().stream().map(hit -> data.sops().get(hit.id())).toList();
    }

    public SearchResult search(String question) {
        var query = normalizer.normalize(question);
        var flow = workflows.detect(query);
        if (flow.ambiguous()) return new SearchResult(query, flow, "ambiguous_workflow", List.of(), List.of());
        if (workflows.detectExplicit(query).workflows().isEmpty() && query.normalized().matches("(?s).*(?:少件|少了|多件|多了|差异|数量不符).*")) {
            return new SearchResult(query, flow, "workflow_required", List.of(), List.of());
        }
        // Filter by workflow before taking the candidate limit; unrelated workflows
        // must not crowd the correct one out of the recall pool.
        var candidates = ranker.rank(query).stream()
                .filter(candidate -> workflows.compatible(flow, data.sops().get(candidate.id())))
                .filter(candidate -> quantityCompatible(query, data.sops().get(candidate.id())))
                .limit(10).toList();
        var hits = candidates.stream().filter(candidate -> supports(query, candidate))
                .limit(3).map(candidate -> {
                    var source = data.sops().get(candidate.id());
                    return new Hit(source.id(), source.title(), source.text(), candidate.score(), candidate.matchedTerms());
                }).toList();
        return new SearchResult(query, flow, hits.isEmpty() ? "no_supported_evidence" : "ok", candidates, hits);
    }

    private boolean quantityCompatible(NormalizedQuery query, SopEntry source) {
        String question = query.normalized() + " " + String.join(" ", query.expansions());
        int requested = direction(question);
        int offered = direction(source.title());
        return requested == 0 || offered == 0 || requested == offered;
    }

    private int direction(String text) {
        boolean shortfall = text.matches("(?s).*(?:短少|少件|少了|盘亏|缺货|不足).*");
        boolean surplus = text.matches("(?s).*(?:多件|多了|多收|多拣|多退|溢收|盘盈).*");
        return shortfall == surplus ? 0 : shortfall ? -1 : 1;
    }

    private boolean supports(NormalizedQuery query, Bm25Ranker.Candidate candidate) {
        String question = query.normalized() + " " + String.join(" ", query.expansions());
        String text = data.sops().get(candidate.id()).text();
        if (!EvidenceRequirements.supports(question, text)) return false;
        if (question.matches("(?s).*(?:为什么|为何|怎么还|卡在哪|卡住|原因|造成|导致|排查|核查|怎么办|怎么处理|如何处理|如何操作|怎么做).*")
                && !text.matches("(?s).*(?:核对|确认|核实|检查|记录|登记|暂停|联系|提交|复核|复盘|查看|隔离|拍照|提报|清点).*") ) {
            return false; // A progress definition alone cannot supply handling steps.
        }
        if (question.matches("(?s).*(?:赔付|赔偿|退款|赔多少|最多赔|赔付上限).*")) {
            // A process clause or a statement that rules are missing cannot establish a monetary rule.
            if (!text.matches("(?s).*(?:赔付|赔偿|退款).*[0-9０-９]+(?:\\.[0-9]+)?\\s*(?:元|%|％).*")) return false;
        }
        // Reject incidental single-character overlaps with unrelated questions.
        return candidate.matchedTerms().stream().filter(term -> term.codePointCount(0, term.length()) >= 2).count() >= 2
                || (query.termWeights().size() == 1 && !workflows.detect(query).workflows().isEmpty());
    }
}
