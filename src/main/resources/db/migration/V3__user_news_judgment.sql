-- 학습-평가 사일로 완화 (S-2). 개별 뉴스 감성 판단의 정오 이력을 남겨
-- 최종 투자 리포트가 이벤트 3건뿐 아니라 일상 뉴스 판단까지 채점 입력으로 쓸 수 있게 한다.
ALTER TABLE user_news_progress
    ADD COLUMN user_sentiment ENUM ('NEGATIVE','NEUTRAL','POSITIVE') NULL,
    ADD COLUMN ai_sentiment   ENUM ('NEGATIVE','NEUTRAL','POSITIVE') NULL,
    ADD COLUMN judged_at      DATETIME(6) NULL;
