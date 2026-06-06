package com.cryptofolio.backend.dto;

import com.cryptofolio.backend.model.Coin;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Mirrors Django REST Framework's CoinSerializer. DecimalFields are emitted as
 * strings (DRF default) so the existing Svelte frontend keeps working unchanged.
 */
public record CoinDto(
        @JsonProperty("coin_id") String coinId,
        @JsonProperty("name") String name,
        @JsonProperty("symbol") String symbol,
        @JsonProperty("price") String price,
        @JsonProperty("market_cap") String marketCap,
        @JsonProperty("image") String image,
        @JsonProperty("market_cap_rank") Integer marketCapRank,
        @JsonProperty("total_volume") String totalVolume,
        @JsonProperty("high_24h") String high24h,
        @JsonProperty("low_24h") String low24h,
        @JsonProperty("price_change_24h") String priceChange24h,
        @JsonProperty("price_change_percentage_24h") String priceChangePercentage24h,
        @JsonProperty("last_updated") OffsetDateTime lastUpdated) {

    private static String str(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }

    public static CoinDto from(Coin coin) {
        return new CoinDto(
                coin.getCoinId(),
                coin.getName(),
                coin.getSymbol(),
                str(coin.getPrice()),
                str(coin.getMarketCap()),
                coin.getImage(),
                coin.getMarketCapRank(),
                str(coin.getTotalVolume()),
                str(coin.getHigh24h()),
                str(coin.getLow24h()),
                str(coin.getPriceChange24h()),
                str(coin.getPriceChangePercentage24h()),
                coin.getLastUpdated());
    }
}
