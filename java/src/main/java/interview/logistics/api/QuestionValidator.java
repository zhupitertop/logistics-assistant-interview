package interview.logistics.api;

import com.fasterxml.jackson.databind.JsonNode;

public final class QuestionValidator {
    private QuestionValidator() {}
    public static String fromJson(JsonNode body) {
        if (body == null || !body.isObject() || body.size() != 1 || !body.has("question") || !body.get("question").isTextual()) {
            throw new InvalidQuestionException();
        }
        return validate(body.get("question").asText());
    }
    public static String validate(String question) {
        if (question == null || question.codePoints().allMatch(cp -> Character.isWhitespace(cp) || Character.isSpaceChar(cp))
                || question.codePointCount(0, question.length()) > 2000) throw new InvalidQuestionException();
        return question;
    }
}
