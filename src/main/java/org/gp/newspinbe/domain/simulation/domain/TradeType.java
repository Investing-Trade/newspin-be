package org.gp.newspinbe.domain.simulation.domain;

public enum TradeType {
    BUY("매수"),
    SELL("매도");

    private final String description;

    TradeType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
