package com.cryptofolio.backend.service;

import com.cryptofolio.backend.model.Coin;
import com.cryptofolio.backend.repository.CoinRepository;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Fetches market data from CoinGecko and upserts it into the coin table. */
@Service
public class CoinGeckoService {

    private static final Logger log = LoggerFactory.getLogger(CoinGeckoService.class);

    private final CoinRepository coinRepository;
    private final RestClient restClient;
    private final String apiKey;

    public CoinGeckoService(
            CoinRepository coinRepository,
            @Value("${coingecko.base-url}") String baseUrl,
            @Value("${coingecko.api-key}") String apiKey) {
        this.coinRepository = coinRepository;
        this.apiKey = apiKey;
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record MarketChart(List<List<Double>> prices) {}

    /**
     * Fetches daily historical prices for a coin from CoinGecko.
     * Returns list of [timestamp_ms, price] pairs. Returns empty list on error.
     */
    public List<List<Double>> getHistoricalPrices(String coinId, String days) {
        try {
            MarketChart chart = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/coins/{id}/market_chart")
                            .queryParam("vs_currency", "eur")
                            .queryParam("days", days)
                            // No interval → CoinGecko auto-selects granularity:
                            // days=1 → ~5min, days≤90 → hourly, days>90 → daily
                            .build(coinId))
                    .header("accept", "application/json")
                    .header("x-cg-demo-api-key", apiKey == null ? "" : apiKey)
                    .retrieve()
                    .body(MarketChart.class);
            return chart != null && chart.prices() != null ? chart.prices() : List.of();
        } catch (RestClientException e) {
            log.warn("CoinGecko history fetch failed for {}: {}", coinId, e.getMessage());
            return List.of();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Market(
            String id,
            String name,
            String symbol,
            String image,
            @JsonProperty("current_price") BigDecimal currentPrice,
            @JsonProperty("market_cap") BigDecimal marketCap,
            @JsonProperty("market_cap_rank") Integer marketCapRank,
            @JsonProperty("total_volume") BigDecimal totalVolume,
            @JsonProperty("high_24h") BigDecimal high24h,
            @JsonProperty("low_24h") BigDecimal low24h,
            @JsonProperty("price_change_24h") BigDecimal priceChange24h,
            @JsonProperty("price_change_percentage_24h") BigDecimal priceChangePercentage24h) {}

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /** Mirrors the Django update_coins_from_coingecko view. Returns the upserted coins. */
    @Transactional
    public List<Coin> updateCoinsFromCoinGecko() {
        List<Market> markets = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/coins/markets")
                        .queryParam("vs_currency", "eur")
                        .queryParam("order", "market_cap_desc")
                        .queryParam("per_page", 250)
                        .queryParam("page", 1)
                        .queryParam("sparkline", true)
                        .queryParam("price_change_percentage", "1h")
                        .build())
                .header("accept", "application/json")
                .header("x-cg-demo-api-key", apiKey == null ? "" : apiKey)
                .retrieve()
                .body(new ParameterizedTypeReference<List<Market>>() {});

        if (markets == null) {
            return List.of();
        }

        List<Coin> updated = new ArrayList<>(markets.size());
        for (Market m : markets) {
            Coin coin = coinRepository.findByCoinId(m.id()).orElseGet(Coin::new);
            coin.setCoinId(m.id());
            coin.setName(m.name());
            coin.setSymbol(m.symbol());
            coin.setImage(m.image());
            coin.setPrice(orZero(m.currentPrice()));
            coin.setMarketCap(orZero(m.marketCap()));
            coin.setMarketCapRank(m.marketCapRank() == null ? 0 : m.marketCapRank());
            coin.setTotalVolume(orZero(m.totalVolume()));
            coin.setHigh24h(orZero(m.high24h()));
            coin.setLow24h(orZero(m.low24h()));
            coin.setPriceChange24h(orZero(m.priceChange24h()));
            coin.setPriceChangePercentage24h(orZero(m.priceChangePercentage24h()));
            coin.setLastUpdated(OffsetDateTime.now());
            updated.add(coinRepository.save(coin));
        }
        return updated;
    }
}
