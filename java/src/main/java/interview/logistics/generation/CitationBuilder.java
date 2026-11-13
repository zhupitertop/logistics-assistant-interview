package interview.logistics.generation;

import interview.logistics.data.SopEntry;
import java.util.ArrayList;
import java.util.List;

public final class CitationBuilder {
    public List<Citation> build(SopEntry source) {
        List<Citation> result = new ArrayList<>();
        String text = source.text();
        for (int start = 0; start < text.length();) {
            int end = text.offsetByCodePoints(start, Math.min(120, text.codePointCount(start, text.length())));
            if (end < text.length()) {
                for (int at = end - 1; at > start; at--) {
                    if ("。；\n".indexOf(text.charAt(at)) >= 0) { end = at + 1; break; }
                }
            }
            result.add(new Citation(source.id(), source.title(), text.substring(start, end)));
            start = end;
        }
        return List.copyOf(result);
    }
}
