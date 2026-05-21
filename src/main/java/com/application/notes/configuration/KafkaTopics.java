package com.application.notes.configuration;

public final class KafkaTopics {

    public static final String NOTES_LIFECYCLE = "notes.lifecycle";
    public static final String AI_SUMMARY_READY = "ai.summary.ready";
    public static final String NOTES_SUMMARY_REQUEST = "notes.summary.request";

    private KafkaTopics() {
    }
}
