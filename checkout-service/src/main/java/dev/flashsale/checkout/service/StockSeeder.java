package dev.flashsale.checkout.service;

import dev.flashsale.checkout.config.AppProperties;
import dev.flashsale.checkout.domain.SaleConfig;
import dev.flashsale.checkout.redis.StockAndIdempotencyService;
import dev.flashsale.checkout.repository.SaleConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Postgres is the source of truth for the sale's initial stock; Redis is the live
 * mutable counter. On startup, seed Redis only if it doesn't already hold a value,
 * so restarting the service mid-sale doesn't reset remaining stock back to full.
 */
@Component
public class StockSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StockSeeder.class);

    private final AppProperties appProperties;
    private final SaleConfigRepository saleConfigRepository;
    private final StockAndIdempotencyService stockService;

    public StockSeeder(AppProperties appProperties,
                        SaleConfigRepository saleConfigRepository,
                        StockAndIdempotencyService stockService) {
        this.appProperties = appProperties;
        this.saleConfigRepository = saleConfigRepository;
        this.stockService = stockService;
    }

    @Override
    public void run(ApplicationArguments args) {
        SaleConfig config = saleConfigRepository.findBySku(appProperties.sku());
        stockService.seedStockIfAbsent(config.sku(), config.initialStock());
        log.info("Stock for {} is at {} remaining", config.sku(), stockService.remainingStock(config.sku()));
    }
}
