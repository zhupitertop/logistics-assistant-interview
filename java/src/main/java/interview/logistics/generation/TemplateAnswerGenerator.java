package interview.logistics.generation;

import interview.logistics.routing.EvidenceCollection;
import java.util.List;

/** Receives only this request's evidence; has no access to the full source repository. */
public final class TemplateAnswerGenerator {
    private final CitationBuilder citations = new CitationBuilder();
    private final EvidenceValidator validator = new EvidenceValidator();

    public TemplateAnswerGenerator(String mode) {
        if (!"off".equals(mode)) throw new IllegalArgumentException("AGENT_LLM currently supports off only; no model service is configured");
    }

    public GeneratedAnswer generate(String question, EvidenceCollection evidence) {
        String status = AnswerPolicy.status(evidence);
        var selected = status.equals("answered") && !evidence.sops().isEmpty()
                ? citations.build(evidence.sops().get(0)) : List.<Citation>of();
        var result = new GeneratedAnswer(status, AnswerPolicy.render(question, evidence, selected), selected,
                selected.stream().map(Citation::id).distinct().toList());
        validator.validate(question, evidence, result);
        return result;
    }
}
