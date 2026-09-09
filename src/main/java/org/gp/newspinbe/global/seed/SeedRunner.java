package org.gp.newspinbe.global.seed;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.gp.newspinbe.domain.event.domain.EventStockImpact;
import org.gp.newspinbe.domain.event.domain.EventType;
import org.gp.newspinbe.domain.event.repository.EventStockImpactRepository;
import org.gp.newspinbe.domain.news.domain.NewsArticle;
import org.gp.newspinbe.domain.news.domain.NewsSentiment;
import org.gp.newspinbe.domain.news.repository.NewsArticleRepository;
import org.gp.newspinbe.domain.stock.domain.Stock;
import org.gp.newspinbe.domain.stock.domain.StockPrice;
import org.gp.newspinbe.domain.stock.domain.StockSector;
import org.gp.newspinbe.domain.stock.repository.StockPriceRepository;
import org.gp.newspinbe.domain.stock.repository.StockRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 로컬/개발 환경에서 빈 DB에 시드 데이터를 1회 적재한다 (C-2).
 *
 * <p>원본은 {@code tools/seed/data/} (저장소 커밋). 운영 배포 산출물(jar)에는 포함되지 않으며
 * prod 프로필에서는 빈 자체가 등록되지 않는다.
 *
 * <p>순서: stocks.csv → news.json(+news_stock) → prices.csv → events.json(eventType + event_stock_impact)
 */
@Slf4j
@Component
@Profile({"local", "dev"})
@ConditionalOnProperty(prefix = "newspin.seed", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(SeedProperties.class)
@RequiredArgsConstructor
public class SeedRunner implements ApplicationRunner {

    private final SeedProperties properties;
    private final StockRepository stockRepository;
    private final NewsArticleRepository newsArticleRepository;
    private final StockPriceRepository stockPriceRepository;
    private final EventStockImpactRepository eventStockImpactRepository;

    private final ObjectMapper json = JsonMapper.builder().addModule(new JavaTimeModule()).build();

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws IOException {
        if (stockRepository.count() > 0) {
            log.info("[seed] 이미 데이터가 있어 건너뜀 (stock {}건)", stockRepository.count());
            return;
        }
        long t0 = System.currentTimeMillis();
        Map<String, Stock> stocks = loadStocks();
        Map<Integer, NewsArticle> newsById = loadNews(stocks);
        int prices = loadPrices(stocks);
        int impacts = loadEvents(newsById, stocks);
        log.info("[seed] 완료 — stock {}, news {}, price {}, impact {} ({}ms)",
                stocks.size(), newsById.size(), prices, impacts, System.currentTimeMillis() - t0);
    }

    private Map<String, Stock> loadStocks() throws IOException {
        List<Stock> batch = new ArrayList<>();
        for (String line : readAll("stocks.csv")) {
            if (line.isBlank() || line.startsWith("stockCode")) {
                continue;
            }
            String[] c = splitCsv(line, 4);
            batch.add(Stock.createStock(c[0].trim(), c[1].trim(),
                    StockSector.valueOf(c[2].trim()), c[3].trim()));
        }
        Map<String, Stock> map = new HashMap<>();
        for (Stock s : stockRepository.saveAll(batch)) {
            map.put(s.getStockCode(), s);
        }
        return map;
    }

    private Map<Integer, NewsArticle> loadNews(Map<String, Stock> stocks) throws IOException {
        SeedNews[] rows = json.readValue(read("news.json"), SeedNews[].class);
        Map<Integer, NewsArticle> byId = new HashMap<>();
        List<NewsArticle> batch = new ArrayList<>();
        List<Integer> ids = new ArrayList<>();
        int skipped = 0;
        for (SeedNews r : rows) {
            List<String> related = r.relatedStocks() == null ? List.of() : r.relatedStocks();
            if (!stocks.keySet().containsAll(related)) {
                skipped++; // 미등록 종목 참조 뉴스 (예: 상장 전 종목) 는 제외
                continue;
            }
            NewsArticle article = NewsArticle.createNewsWithSentiment(
                    r.title(), r.content(), r.articleDate(), r.source(), r.sentiment());
            related.forEach(code -> article.addRelatedStock(stocks.get(code)));
            batch.add(article);
            ids.add(r.articleId());
        }
        List<NewsArticle> saved = newsArticleRepository.saveAll(batch);
        for (int i = 0; i < saved.size(); i++) {
            byId.put(ids.get(i), saved.get(i));
        }
        if (skipped > 0) {
            log.info("[seed] 미등록 종목 참조로 뉴스 {}건 제외", skipped);
        }
        return byId;
    }

    private int loadPrices(Map<String, Stock> stocks) throws IOException {
        List<StockPrice> batch = new ArrayList<>();
        for (String line : readAll("prices.csv")) {
            if (line.isBlank() || line.startsWith("stockCode")) {
                continue;
            }
            String[] c = splitCsv(line, 7);
            Stock stock = stocks.get(c[0].trim());
            if (stock == null) {
                continue;
            }
            batch.add(StockPrice.createStockPrice(stock, LocalDate.parse(c[1].trim()),
                    new BigDecimal(c[2].trim()), new BigDecimal(c[5].trim()),
                    new BigDecimal(c[3].trim()), new BigDecimal(c[4].trim()),
                    Long.parseLong(c[6].trim())));
        }
        stockPriceRepository.saveAll(batch);
        return batch.size();
    }

    private int loadEvents(Map<Integer, NewsArticle> newsById, Map<String, Stock> stocks) throws IOException {
        SeedEvent[] events = json.readValue(read("events.json"), SeedEvent[].class);
        List<EventStockImpact> batch = new ArrayList<>();
        for (SeedEvent e : events) {
            NewsArticle article = newsById.get(e.articleId());
            if (article == null) {
                log.warn("[seed] 이벤트 기사 {} 를 찾지 못해 건너뜀", e.articleId());
                continue;
            }
            article.markAsEvent(e.eventType());
            for (SeedImpact impact : e.impacts()) {
                Stock stock = stocks.get(impact.stockCode());
                if (stock == null) {
                    continue;
                }
                batch.add(EventStockImpact.createImpact(article, stock, impact.impactRate(), impact.impactReason()));
            }
        }
        eventStockImpactRepository.saveAll(batch);
        return batch.size();
    }

    // --- io helpers -------------------------------------------------------

    /** {@code newspin.seed.dir} 아래 파일을 우선 읽고, 없으면 클래스패스에서 읽는다. */
    private byte[] read(String name) throws IOException {
        Path p = Path.of(properties.dir(), name);
        if (Files.exists(p)) {
            return Files.readAllBytes(p);
        }
        ClassPathResource cp = new ClassPathResource("seed/" + name);
        if (cp.exists()) {
            try (var in = cp.getInputStream()) {
                return in.readAllBytes();
            }
        }
        throw new IOException("시드 파일을 찾을 수 없음: " + p + " (또는 classpath:seed/" + name + ")");
    }

    private List<String> readAll(String name) throws IOException {
        return new String(read(name), StandardCharsets.UTF_8).lines().toList();
    }

    private static String[] splitCsv(String line, int expected) {
        String[] parts = line.split(",", expected);
        if (parts.length < expected) {
            throw new IllegalStateException("CSV 컬럼 부족: " + line);
        }
        return parts;
    }

    // --- seed dto -------------------------------------------------------

    private record SeedNews(
            @com.fasterxml.jackson.annotation.JsonProperty("article_id") int articleId,
            String title, String content, LocalDate articleDate, String source,
            NewsSentiment sentiment, List<String> relatedStocks) {
    }

    private record SeedEvent(int articleId, EventType eventType, String summary, List<SeedImpact> impacts) {
    }

    private record SeedImpact(String stockCode, double impactRate, String impactReason) {
    }
}
