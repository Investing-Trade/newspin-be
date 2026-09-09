-- 착수 시점(2026-09) 엔티티 기준 baseline 스키마.
-- 그동안 ddl-auto: update 로만 관리되던 것을 Flyway 로 고정한다.
-- 생성: Hibernate schema-generation → 제약 이름 안정화.

CREATE TABLE user (
    user_id     BIGINT NOT NULL AUTO_INCREMENT,
    email       VARCHAR(255),
    password    VARCHAR(255),
    created_at  DATETIME(6),
    updated_at  DATETIME(6),
    deleted_at  DATETIME(6),
    PRIMARY KEY (user_id)
) ENGINE=InnoDB;

CREATE TABLE stock (
    stock_id    BIGINT NOT NULL AUTO_INCREMENT,
    stock_code  VARCHAR(10) NOT NULL,
    stock_name  VARCHAR(100) NOT NULL,
    sector      ENUM ('BIO','CULTURE_ENTERTAINMENT','FOOD_FRANCHISE','IT_TECH','RETAIL','TRAVEL') NOT NULL,
    description TEXT,
    created_at  DATETIME(6),
    updated_at  DATETIME(6),
    deleted_at  DATETIME(6),
    PRIMARY KEY (stock_id),
    CONSTRAINT uk_stock_code UNIQUE (stock_code)
) ENGINE=InnoDB;
CREATE INDEX idx_stock_code ON stock (stock_code);
CREATE INDEX idx_stock_sector ON stock (sector);

CREATE TABLE stock_price (
    price_id    BIGINT NOT NULL AUTO_INCREMENT,
    stock_id    BIGINT NOT NULL,
    price_date  DATE NOT NULL,
    open_price  DECIMAL(15,2) NOT NULL,
    close_price DECIMAL(15,2) NOT NULL,
    high_price  DECIMAL(15,2) NOT NULL,
    low_price   DECIMAL(15,2) NOT NULL,
    volume      BIGINT NOT NULL,
    PRIMARY KEY (price_id),
    CONSTRAINT uk_stock_price_stock_date UNIQUE (stock_id, price_date),
    CONSTRAINT fk_stock_price_stock FOREIGN KEY (stock_id) REFERENCES stock (stock_id)
) ENGINE=InnoDB;
CREATE INDEX idx_stock_price_date ON stock_price (price_date);
CREATE INDEX idx_stock_price_stock_date ON stock_price (stock_id, price_date);

CREATE TABLE news_article (
    news_id          BIGINT NOT NULL AUTO_INCREMENT,
    title            VARCHAR(500) NOT NULL,
    content          TEXT NOT NULL,
    article_date     DATE NOT NULL,
    source           VARCHAR(100),
    sentiment        ENUM ('NEGATIVE','NEUTRAL','POSITIVE'),
    event_type       ENUM ('DISASTER','ECONOMIC','GEOPOLITICAL','PANDEMIC','POLICY','TECHNOLOGY'),
    sentiment_score  DOUBLE,
    sentiment_reason TEXT,
    created_at       DATETIME(6),
    updated_at       DATETIME(6),
    deleted_at       DATETIME(6),
    PRIMARY KEY (news_id)
) ENGINE=InnoDB;

CREATE TABLE news_stock (
    news_id  BIGINT NOT NULL,
    stock_id BIGINT NOT NULL,
    CONSTRAINT fk_news_stock_news FOREIGN KEY (news_id) REFERENCES news_article (news_id),
    CONSTRAINT fk_news_stock_stock FOREIGN KEY (stock_id) REFERENCES stock (stock_id)
) ENGINE=InnoDB;

CREATE TABLE event_stock_impact (
    impact_id     BIGINT NOT NULL AUTO_INCREMENT,
    news_id       BIGINT NOT NULL,
    stock_id      BIGINT NOT NULL,
    impact_rate   DOUBLE NOT NULL,
    impact_reason TEXT,
    PRIMARY KEY (impact_id),
    CONSTRAINT uk_event_stock_impact UNIQUE (news_id, stock_id),
    CONSTRAINT fk_impact_news FOREIGN KEY (news_id) REFERENCES news_article (news_id),
    CONSTRAINT fk_impact_stock FOREIGN KEY (stock_id) REFERENCES stock (stock_id)
) ENGINE=InnoDB;
CREATE INDEX idx_impact_news ON event_stock_impact (news_id);

CREATE TABLE user_news_progress (
    id         BIGINT NOT NULL AUTO_INCREMENT,
    user_id    BIGINT NOT NULL,
    news_id    BIGINT NOT NULL,
    learned_at DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_user_news_progress UNIQUE (user_id, news_id),
    CONSTRAINT fk_progress_user FOREIGN KEY (user_id) REFERENCES user (user_id),
    CONSTRAINT fk_progress_news FOREIGN KEY (news_id) REFERENCES news_article (news_id)
) ENGINE=InnoDB;

CREATE TABLE simulation_session (
    session_id              BIGINT NOT NULL AUTO_INCREMENT,
    user_id                 BIGINT NOT NULL,
    initial_capital         DECIMAL(15,2) NOT NULL,
    current_capital         DECIMAL(15,2) NOT NULL,
    start_date              DATE NOT NULL,
    end_date                DATE NOT NULL,
    current_simulation_date DATE NOT NULL,
    status                  ENUM ('ABANDONED','ACTIVE','COMPLETED') NOT NULL,
    created_at              DATETIME(6),
    updated_at              DATETIME(6),
    deleted_at              DATETIME(6),
    PRIMARY KEY (session_id),
    CONSTRAINT fk_session_user FOREIGN KEY (user_id) REFERENCES user (user_id)
) ENGINE=InnoDB;
CREATE INDEX idx_session_user ON simulation_session (user_id);
CREATE INDEX idx_session_status ON simulation_session (status);

CREATE TABLE portfolio (
    portfolio_id       BIGINT NOT NULL AUTO_INCREMENT,
    session_id         BIGINT NOT NULL,
    stock_id           BIGINT NOT NULL,
    quantity           BIGINT NOT NULL,
    avg_purchase_price DECIMAL(15,2) NOT NULL,
    created_at         DATETIME(6),
    updated_at         DATETIME(6),
    deleted_at         DATETIME(6),
    PRIMARY KEY (portfolio_id),
    CONSTRAINT uk_portfolio_session_stock UNIQUE (session_id, stock_id),
    CONSTRAINT fk_portfolio_session FOREIGN KEY (session_id) REFERENCES simulation_session (session_id),
    CONSTRAINT fk_portfolio_stock FOREIGN KEY (stock_id) REFERENCES stock (stock_id)
) ENGINE=InnoDB;
CREATE INDEX idx_portfolio_session ON portfolio (session_id);

CREATE TABLE trade (
    trade_id     BIGINT NOT NULL AUTO_INCREMENT,
    session_id   BIGINT NOT NULL,
    stock_id     BIGINT NOT NULL,
    trade_type   ENUM ('BUY','SELL') NOT NULL,
    quantity     BIGINT NOT NULL,
    price        DECIMAL(15,2) NOT NULL,
    total_amount DECIMAL(15,2) NOT NULL,
    trade_date   DATE NOT NULL,
    created_at   DATETIME(6),
    updated_at   DATETIME(6),
    deleted_at   DATETIME(6),
    PRIMARY KEY (trade_id),
    CONSTRAINT fk_trade_session FOREIGN KEY (session_id) REFERENCES simulation_session (session_id),
    CONSTRAINT fk_trade_stock FOREIGN KEY (stock_id) REFERENCES stock (stock_id)
) ENGINE=InnoDB;
CREATE INDEX idx_trade_session ON trade (session_id);
CREATE INDEX idx_trade_date ON trade (trade_date);
CREATE INDEX idx_trade_session_date ON trade (session_id, trade_date);

CREATE TABLE asset_history (
    history_id   BIGINT NOT NULL AUTO_INCREMENT,
    session_id   BIGINT NOT NULL,
    record_date  DATE NOT NULL,
    cash_balance DECIMAL(15,2) NOT NULL,
    stock_value  DECIMAL(15,2) NOT NULL,
    total_asset  DECIMAL(15,2) NOT NULL,
    profit_rate  DOUBLE NOT NULL,
    PRIMARY KEY (history_id),
    CONSTRAINT uk_asset_history_session_date UNIQUE (session_id, record_date),
    CONSTRAINT fk_asset_history_session FOREIGN KEY (session_id) REFERENCES simulation_session (session_id)
) ENGINE=InnoDB;
CREATE INDEX idx_asset_session ON asset_history (session_id);
CREATE INDEX idx_asset_date ON asset_history (record_date);
