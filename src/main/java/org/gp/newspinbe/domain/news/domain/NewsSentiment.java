package org.gp.newspinbe.domain.news.domain;

public enum NewsSentiment {
    POSITIVE("호재"),
    NEGATIVE("악재"),
    NEUTRAL("중립");

    private final String description;

    NewsSentiment(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
