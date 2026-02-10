package org.gp.newspinbe.domain.stock.domain;

public enum StockSector {
    BIO("바이오"),
    IT_TECH("IT/테크"),
    RETAIL("유통"),
    TRAVEL("여행"),
    FOOD_FRANCHISE("외식/프랜차이즈"),
    CULTURE_ENTERTAINMENT("문화/엔터테인먼트");

    private final String description;

    StockSector(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
