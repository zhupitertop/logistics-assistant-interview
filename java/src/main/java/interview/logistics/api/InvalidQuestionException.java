package interview.logistics.api;
public class InvalidQuestionException extends RuntimeException {
    public InvalidQuestionException() { super("question 必须是 1 至 2000 字的非空字符串，请求只能包含 question 字段"); }
}
