package ca.vicilearning.dashboard.sync;

public class SimplybookApiException extends RuntimeException {

    private final int code;

    public SimplybookApiException(int code, String message) {
        super("SimplyBook.me error " + code + ": " + message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
