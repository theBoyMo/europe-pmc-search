package com.example.downloaderdemo.event;

public class MessageEvent extends BaseEvent{

    public static final String NO_RESULTS_RETURNED_FROM_QUERY = "no_results_returned_from_query";
    public static final String ERROR_EXECUTING_QUERY = "error_executing_query";
    private String mMessage;

    public MessageEvent(String message) {
        mMessage = message;
    }

    public String getMessage() {
        return mMessage;
    }
}
