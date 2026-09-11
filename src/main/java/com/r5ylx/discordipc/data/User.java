package com.r5ylx.discordipc.data;

import java.util.Objects;
import java.util.Optional;

import org.json.JSONObject;

/** A Discord user. */
public final class User {
    private final String id;
    private final String username;
    private final String globalName;
    private final String discriminator;
    private final Avatar avatar;

    public User(String id, String username, String globalName, String discriminator, String avatarHash) {
        this.id = Objects.requireNonNull(id, "id");
        this.username = username;
        this.globalName = globalName;
        this.discriminator = discriminator;
        this.avatar = new Avatar(id, avatarHash, discriminator);
    }

    /** Reads a user object as it arrives from Discord. */
    public static User fromJson(JSONObject data) {
        Objects.requireNonNull(data, "data");

        return new User(
            data.optString("id", null),
            data.optString("username", null),
            data.optString("global_name", null),
            data.optString("discriminator", null),
            data.optString("avatar", null));
    }

    /** The numeric id, which never changes. */
    public String getId() {
        return id;
    }

    /** The unique name shown after the {@code @}. */
    public String getUsername() {
        return username;
    }

    /** The display name. Returns empty when it is not set. */
    public Optional<String> getGlobalName() {
        return Optional.ofNullable(globalName);
    }

    /**
     * The legacy four-digit discriminator.
     * It is {@code "0"} for a user who moved to the new username scheme.
     */
    public Optional<String> getDiscriminator() {
        return Optional.ofNullable(discriminator);
    }

    public Avatar getAvatar() {
        return avatar;
    }

    /** The display name if there is one, otherwise the username. */
    public String getDisplayName() {
        return globalName != null ? globalName : username;
    }

    @Override
    public String toString() {
        return "User[id=" + id + ", username=" + username + "]";
    }

    /** The icon of the user. */
    public static final class Avatar {
        private static final String CDN_BASE = "https://cdn.discordapp.com";
        private static final String AVATAR_URL = CDN_BASE + "/avatars/%s/%s.%s";
        private static final String DEFAULT_AVATAR_URL = CDN_BASE + "/embed/avatars/%d.png";

        /** How many default icons the new username scheme has. */
        private static final int MODERN_DEFAULT_VARIANTS = 6;
        /** How many default icons the legacy scheme has. */
        private static final int LEGACY_DEFAULT_VARIANTS = 5;

        private final String userId;
        private final String hash;
        private final String discriminator;

        private Avatar(String userId, String hash, String discriminator) {
            this.userId = userId;
            this.hash = hash;
            this.discriminator = discriminator;
        }

        /** The hash of the image. Returns empty for a default icon. */
        public Optional<String> getHash() {
            return Optional.ofNullable(hash);
        }

        /** Whether no icon was set and the default image is used instead. */
        public boolean isDefault() {
            return hash == null || hash.isEmpty();
        }

        /** The URL of the PNG. */
        public String getUrl() {
            return getUrl(ImageFormat.PNG);
        }

        /**
         * The URL in the given format.
         * Asking for {@link ImageFormat#GIF} on an icon that does not animate gives PNG.
         */
        public String getUrl(ImageFormat format) {
            Objects.requireNonNull(format, "format");

            if (isDefault()) {
                return String.format(DEFAULT_AVATAR_URL, defaultIndex());
            }

            ImageFormat actual = format == ImageFormat.GIF && !isAnimated() ? ImageFormat.PNG : format;
            return String.format(AVATAR_URL, userId, hash, actual.getExtension());
        }

        /** Whether the icon animates. Its hash starts with {@code a_}. */
        public boolean isAnimated() {
            return hash != null && hash.startsWith("a_");
        }

        /**
         * The number of the default icon.
         *
         * <p>The legacy scheme (a user who still has a discriminator) takes it from the
         * discriminator; the new username scheme takes it from the high bits of the id.
         */
        private int defaultIndex() {
            if (discriminator != null && !discriminator.isEmpty() && !"0".equals(discriminator)) {
                try {
                    return Integer.parseInt(discriminator) % LEGACY_DEFAULT_VARIANTS;
                } catch (NumberFormatException e) {
                    // A discriminator that is not a number is treated as the new scheme.
                }
            }

            try {
                return (int) ((Long.parseUnsignedLong(userId) >> 22) % MODERN_DEFAULT_VARIANTS);
            } catch (NumberFormatException e) {
                return 0;
            }
        }

        /** The image format of the icon. */
        public enum ImageFormat {
            PNG("png"),
            JPEG("jpeg"),
            WEBP("webp"),
            GIF("gif");

            private final String extension;

            ImageFormat(String extension) {
                this.extension = extension;
            }

            public String getExtension() {
                return extension;
            }
        }
    }
}
