package interview.logistics.retrieval;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Shared by index construction and the future query side; never alters source text. */
public final class TextAnalyzer {
    public static final String VERSION = "han-bigram-latin-token-v1";

    public List<String> analyze(String text) {
        int[] points = Normalizer.normalize(text, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT).codePoints().toArray();
        List<String> tokens = new ArrayList<>();
        int start = 0;
        while (start < points.length) {
            int kind = kind(points[start]);
            if (kind == 0) { start++; continue; }
            int end = start + 1;
            while (end < points.length && kind(points[end]) == kind) end++;
            if (kind == 1 && end - start > 1) {
                for (int i = start; i + 1 < end; i++) tokens.add(new String(points, i, 2));
            } else {
                tokens.add(new String(points, start, end - start));
            }
            start = end;
        }
        return List.copyOf(tokens);
    }

    private int kind(int cp) {
        if (Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN) return 1;
        return Character.isLetterOrDigit(cp) ? 2 : 0;
    }
}
