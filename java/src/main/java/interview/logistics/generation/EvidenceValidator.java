package interview.logistics.generation;

import interview.logistics.routing.EvidenceCollection;
import java.util.LinkedHashSet;
import java.util.stream.Collectors;

public final class EvidenceValidator {
    public void validate(String question, EvidenceCollection evidence, GeneratedAnswer generated) {
        if (!generated.status().equals(AnswerPolicy.status(evidence))) fail();
        var sources = evidence.sops().stream().collect(Collectors.toMap(entry -> entry.id(), entry -> entry));
        var retrievedIds = evidence.trace().stream().filter(step -> step.tool().equals("sop_search") && step.outcome().equals("ok"))
                .flatMap(step -> step.hitIds().stream()).collect(Collectors.toSet());
        var used = new LinkedHashSet<String>();
        for (var citation : generated.citations()) {
            var source = sources.get(citation.id());
            if (source == null || !retrievedIds.contains(citation.id()) || !source.title().equals(citation.title())
                    || citation.excerpt() == null || citation.excerpt().isEmpty()
                    || citation.excerpt().codePointCount(0, citation.excerpt().length()) > 120
                    || !source.text().contains(citation.excerpt())) fail();
            used.add(citation.id());
        }
        if (!generated.usedIds().equals(used.stream().toList())) fail();
        if (generated.status().equals("answered") && !evidence.sops().isEmpty()) {
            // Quote the entire primary clause, retaining all conditions and negations.
            String primary = evidence.sops().get(0).id();
            if (!used.equals(new LinkedHashSet<>(java.util.List.of(primary)))) fail();
            String quoted = generated.citations().stream().map(Citation::excerpt).collect(Collectors.joining());
            if (!quoted.equals(evidence.sops().get(0).text())) fail();
        } else if (!generated.citations().isEmpty()) fail();
        // All variable business content must be the source fields/citations above.
        // Free-form model prose or tampered assertions cannot pass this comparison.
        if (!generated.answer().equals(AnswerPolicy.render(question, evidence, generated.citations()))) fail();
    }

    private void fail() { throw new IllegalStateException("Generated answer failed evidence validation"); }
}
