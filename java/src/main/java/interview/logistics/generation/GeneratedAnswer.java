package interview.logistics.generation;

import java.util.List;

public record GeneratedAnswer(String status, String answer, List<Citation> citations, List<String> usedIds) {
    public GeneratedAnswer { citations = List.copyOf(citations); usedIds = List.copyOf(usedIds); }
}
