package com.cryptofolio.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically refreshes coin market data from CoinGecko so prices stay live.
 *
 * <p>Interval is controlled by {@code coingecko.refresh.interval-ms} (default 60s) and the
 * whole job can be disabled with {@code coingecko.refresh.enabled=false}. Note the CoinGecko
 * demo key allows ~10k calls/month, so a 60s interval is fine for intermittent/dev use but
 * should be raised to ~300000 (5 min) for continuous 24/7 operation.
 */
@Component
@ConditionalOnProperty(value = "coingecko.refresh.enabled", havingValue = "true", matchIfMissing = true)
public class CoinPriceRefreshJob {

    private static final Logger log = LoggerFactory.getLogger(CoinPriceRefreshJob.class);

    private final CoinGeckoService coinGeckoService;

    public CoinPriceRefreshJob(CoinGeckoService coinGeckoService) {
        this.coinGeckoService = coinGeckoService;
    }

    @Scheduled(
            fixedRateString = "${coingecko.refresh.interval-ms:60000}",
            initialDelayString = "${coingecko.refresh.initial-delay-ms:5000}")
    public void refreshPrices() {
        try {
            int count = coinGeckoService.updateCoinsFromCoinGecko().size();
            log.info("Live price refresh: updated {} coins from CoinGecko", count);
        } catch (Exception e) {
            // Network blips and rate-limit (429) responses must not kill the scheduler.
            log.warn("Live price refresh failed: {}", e.getMessage());
        }
    }
}
