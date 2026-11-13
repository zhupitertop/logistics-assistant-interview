package interview.logistics.application;

import interview.logistics.api.AskResponse;
import interview.logistics.api.QuestionValidator;
import interview.logistics.generation.TemplateAnswerGenerator;
import interview.logistics.routing.EvidenceCollector;
import interview.logistics.trace.TraceStep;
import java.util.ArrayList;
import java.util.Map;

public final class AskService {
    private final EvidenceCollector collector;
    private final TemplateAnswerGenerator generator;
    public AskService(EvidenceCollector collector, TemplateAnswerGenerator generator) {
        this.collector = collector; this.generator = generator;
    }
    public AskResponse ask(String question) {
        QuestionValidator.validate(question);
        var evidence = collector.collect(question);
        var generated = generator.generate(question, evidence);
        var trace = new ArrayList<>(evidence.trace());
        trace.add(new TraceStep(trace.size() + 1, "generate", Map.of("question", question, "mode", "template", "evidence_ids", generated.usedIds()), "ok", generated.usedIds()));
        return new AskResponse(generated.status(), generated.answer(), generated.citations(), trace);
    }
}
