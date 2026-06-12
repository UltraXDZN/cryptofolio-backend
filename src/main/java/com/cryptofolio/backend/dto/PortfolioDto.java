package com.cryptofolio.backend.dto;

import com.cryptofolio.backend.model.PortfolioRecord;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Mirrors Django REST Framework's PortfolioSerializer. DecimalFields are emitted
 * as strings; current_price mirrors the DRF FloatField (numeric). total_quantity
 * is only populated by the aggregated portfolio endpoint.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PortfolioDto(
        @JsonProperty("id") Long id,
        @JsonProperty("coin") Long coin,
        @JsonProperty("coin_symbol") String coinSymbol,
        @JsonProperty("coin_name") String coinName,
        @JsonProperty("quantity") String quantity,
        @JsonProperty("trading_pair") String tradingPair,
        @JsonProperty("exchange_price") String exchangePrice,
        @JsonProperty("bought_price") String boughtPrice,
        @JsonProperty("notes") String notes,
        @JsonProperty("added_at") OffsetDateTime addedAt,
        @JsonProperty("current_price") Double currentPrice,
        @JsonProperty("coin_image") String coinImage,
        @JsonProperty("transaction_type") String transactionType,
        @JsonProperty("total_quantity") Double totalQuantity,
        @JsonProperty("sent_from") String sentFrom,
        @JsonProperty("sent_to") String sentTo) {

    private static String str(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }

    public static PortfolioDto from(PortfolioRecord r) {
        return build(r, str(r.getQuantity()), null);
    }

    /** Used by the aggregated portfolio endpoint: quantity becomes the summed total. */
    public static PortfolioDto aggregated(PortfolioRecord r, double totalQuantity) {
        return build(r, String.valueOf(totalQuantity), totalQuantity);
    }

    private static PortfolioDto build(PortfolioRecord r, String quantity, Double totalQuantity) {
        var coin = r.getCoin();
        Double currentPrice = coin.getPrice() == null ? null : coin.getPrice().doubleValue();
        return new PortfolioDto(
                r.getId(),
                coin.getId(),
                coin.getSymbol(),
                coin.getName(),
                quantity,
                r.getTradingPair(),
                str(r.getExchangePrice()),
                str(r.getBoughtPrice()),
                r.getNotes(),
                r.getAddedAt(),
                currentPrice,
                coin.getImage(),
                r.getTransactionType(),
                totalQuantity,
                r.getSentFrom(),
                r.getSentTo());
    }
}
