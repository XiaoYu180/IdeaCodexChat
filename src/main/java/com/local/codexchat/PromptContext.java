package com.local.codexchat;

final class PromptContext {
    enum Type {
        CODE,
        CONSOLE,
        IMAGE
    }

    private final Type type;
    private final String title;
    private final String body;
    private final String imagePath;

    PromptContext(Type type, String title, String body) {
        this(type, title, body, null);
    }

    PromptContext(Type type, String title, String body, String imagePath) {
        this.type = type;
        this.title = title;
        this.body = body;
        this.imagePath = imagePath;
    }

    Type type() {
        return type;
    }

    String title() {
        return title;
    }

    String body() {
        return body;
    }

    String imagePath() {
        return imagePath;
    }

    String preview(int maxChars) {
        if (body.length() <= maxChars) {
            return body;
        }
        return body.substring(0, Math.max(0, maxChars)) + "\n...";
    }
}
