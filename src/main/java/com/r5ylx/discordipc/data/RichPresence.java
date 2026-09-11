package com.r5ylx.discordipc.data;

import java.time.Instant;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * What is shown on a Discord profile.
 *
 * <p>Built with {@link Builder}. Anything left unset is not sent.
 *
 * <pre>{@code
 * RichPresence presence = RichPresence.builder()
 *     .setDetails("In the lobby")
 *     .setState("Waiting")
 *     .setStartTimestamp(Instant.now())
 *     .setLargeImage("logo", "Talpius Client")
 *     .setParty("party-1", 2, 4)
 *     .build();
 * }</pre>
 *
 * <p>The JSON keys are snake_case, matching Discord's activity format.
 */
public final class RichPresence {
    private final String state;
    private final String details;
    private final Long startTimestamp;
    private final Long endTimestamp;
    private final String largeImageKey;
    private final String largeImageText;
    private final String smallImageKey;
    private final String smallImageText;
    private final String partyId;
    private final int partySize;
    private final int partyMax;
    private final String matchSecret;
    private final String joinSecret;
    private final String spectateSecret;
    private final boolean instance;

    private RichPresence(Builder builder) {
        this.state = builder.state;
        this.details = builder.details;
        this.startTimestamp = builder.startTimestamp;
        this.endTimestamp = builder.endTimestamp;
        this.largeImageKey = builder.largeImageKey;
        this.largeImageText = builder.largeImageText;
        this.smallImageKey = builder.smallImageKey;
        this.smallImageText = builder.smallImageText;
        this.partyId = builder.partyId;
        this.partySize = builder.partySize;
        this.partyMax = builder.partyMax;
        this.matchSecret = builder.matchSecret;
        this.joinSecret = builder.joinSecret;
        this.spectateSecret = builder.spectateSecret;
        this.instance = builder.instance;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Builds the activity object that is sent to Discord. */
    public JSONObject toJson() {
        JSONObject json = new JSONObject();

        if (state != null) {
            json.put("state", state);
        }

        if (details != null) {
            json.put("details", details);
        }

        if (startTimestamp != null || endTimestamp != null) {
            JSONObject timestamps = new JSONObject();

            if (startTimestamp != null) {
                timestamps.put("start", startTimestamp.longValue());
            }

            if (endTimestamp != null) {
                timestamps.put("end", endTimestamp.longValue());
            }

            json.put("timestamps", timestamps);
        }

        if (largeImageKey != null || smallImageKey != null) {
            JSONObject assets = new JSONObject();

            if (largeImageKey != null) {
                assets.put("large_image", largeImageKey);
            }

            if (largeImageText != null) {
                assets.put("large_text", largeImageText);
            }

            if (smallImageKey != null) {
                assets.put("small_image", smallImageKey);
            }

            if (smallImageText != null) {
                assets.put("small_text", smallImageText);
            }

            json.put("assets", assets);
        }

        if (partyId != null) {
            JSONObject party = new JSONObject();
            party.put("id", partyId);

            if (partySize > 0 && partyMax > 0) {
                party.put("size", new JSONArray().put(partySize).put(partyMax));
            }

            json.put("party", party);
        }

        if (matchSecret != null || joinSecret != null || spectateSecret != null) {
            JSONObject secrets = new JSONObject();

            if (matchSecret != null) {
                secrets.put("match", matchSecret);
            }

            if (joinSecret != null) {
                secrets.put("join", joinSecret);
            }

            if (spectateSecret != null) {
                secrets.put("spectate", spectateSecret);
            }

            json.put("secrets", secrets);
        }

        json.put("instance", instance);

        return json;
    }

    @Override
    public String toString() {
        return "RichPresence" + toJson();
    }

    /** Builds a {@link RichPresence}. */
    public static final class Builder {
        private String state;
        private String details;
        private Long startTimestamp;
        private Long endTimestamp;
        private String largeImageKey;
        private String largeImageText;
        private String smallImageKey;
        private String smallImageText;
        private String partyId;
        private int partySize;
        private int partyMax;
        private String matchSecret;
        private String joinSecret;
        private String spectateSecret;
        private boolean instance;

        /** The text shown on the second line. */
        public Builder setState(String state) {
            this.state = state;
            return this;
        }

        /** The text shown on the first line. */
        public Builder setDetails(String details) {
            this.details = details;
            return this;
        }

        /**
         * The point elapsed time is counted from.
         *
         * @param startTimestamp milliseconds since the epoch
         */
        public Builder setStartTimestamp(long startTimestamp) {
            this.startTimestamp = startTimestamp;
            return this;
        }

        /** The point elapsed time is counted from. */
        public Builder setStartTimestamp(Instant startTimestamp) {
            this.startTimestamp = startTimestamp == null ? null : startTimestamp.toEpochMilli();
            return this;
        }

        /**
         * The point remaining time counts down to. Setting it shows time left, not elapsed.
         *
         * @param endTimestamp milliseconds since the epoch
         */
        public Builder setEndTimestamp(long endTimestamp) {
            this.endTimestamp = endTimestamp;
            return this;
        }

        /** The point remaining time counts down to. */
        public Builder setEndTimestamp(Instant endTimestamp) {
            this.endTimestamp = endTimestamp == null ? null : endTimestamp.toEpochMilli();
            return this;
        }

        /**
         * The larger of the two images.
         *
         * @param key  the image key registered in the developer portal, or an image URL
         * @param text the caption shown when hovering over the image, or null for none
         */
        public Builder setLargeImage(String key, String text) {
            this.largeImageKey = key;
            this.largeImageText = text;
            return this;
        }

        public Builder setLargeImage(String key) {
            return setLargeImage(key, null);
        }

        /** The smaller of the two images. It is not shown unless the large one is set. */
        public Builder setSmallImage(String key, String text) {
            this.smallImageKey = key;
            this.smallImageText = text;
            return this;
        }

        public Builder setSmallImage(String key) {
            return setSmallImage(key, null);
        }

        /**
         * The party information.
         *
         * @param size the current headcount. Zero or less sends no headcount
         * @param max  the maximum headcount. Zero or less sends no headcount
         */
        public Builder setParty(String id, int size, int max) {
            this.partyId = id;
            this.partySize = size;
            this.partyMax = max;
            return this;
        }

        public Builder setMatchSecret(String matchSecret) {
            this.matchSecret = matchSecret;
            return this;
        }

        /** The secret that makes the join button appear. A party must be set as well. */
        public Builder setJoinSecret(String joinSecret) {
            this.joinSecret = joinSecret;
            return this;
        }

        /** The secret that makes the spectate button appear. */
        public Builder setSpectateSecret(String spectateSecret) {
            this.spectateSecret = spectateSecret;
            return this;
        }

        public Builder setInstance(boolean instance) {
            this.instance = instance;
            return this;
        }

        public RichPresence build() {
            return new RichPresence(this);
        }
    }
}
