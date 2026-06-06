package com.cryptofolio.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Request body for POST /api/profile/{hashId}/filtered_coins/update/. */
public class FilteredCoinsRequest {

    @JsonProperty("coin_ids")
    private List<String> coinIds = List.of();

    public List<String> getCoinIds() {
        return coinIds;
    }

    public void setCoinIds(List<String> coinIds) {
        this.coinIds = coinIds == null ? List.of() : coinIds;
    }
}
