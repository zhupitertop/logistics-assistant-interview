package interview.logistics.api;

import interview.logistics.generation.Citation;
import interview.logistics.trace.TraceStep;
import java.util.List;

public record AskResponse(String status, String answer, List<Citation> citations, List<TraceStep> trace) {
    public AskResponse { citations = List.copyOf(citations); trace = List.copyOf(trace); }
}
