package com.cryptofolio.backend.dto;

import com.cryptofolio.backend.model.PortfolioProfile;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;

/** Mirrors Django REST Framework's ProfileSerializer. */
public record ProfileDto(
        @JsonProperty("hash_id") String hashId,
        @JsonProperty("tag") String tag,
        @JsonProperty("created_at") OffsetDateTime createdAt) {

    public static ProfileDto from(PortfolioProfile profile) {
        return new ProfileDto(profile.getHashId(), profile.getTag(), profile.getCreatedAt());
    }
}
