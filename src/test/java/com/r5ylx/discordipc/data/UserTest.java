package com.r5ylx.discordipc.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.r5ylx.discordipc.data.User.Avatar.ImageFormat;

class UserTest {
    private static JSONObject payload() {
        return new JSONObject()
            .put("id", "1193242580113170596")
            .put("username", "n4yxr")
            .put("global_name", "み")
            .put("discriminator", "0")
            .put("avatar", "a1895cdb3039903e58df0d8b518456dd");
    }

    @Test
    @DisplayName("reads the discriminator")
    void readsDiscriminator() {
        JSONObject json = payload().put("discriminator", "1234");

        // In 1.0.0 the key was mistyped as "discri2minator", so this was always null.
        assertEquals("1234", User.fromJson(json).getDiscriminator().orElseThrow());
    }

    @Test
    @DisplayName("reads the basic fields")
    void readsBasicFields() {
        User user = User.fromJson(payload());

        assertEquals("1193242580113170596", user.getId());
        assertEquals("n4yxr", user.getUsername());
        assertEquals("み", user.getGlobalName().orElseThrow());
        assertEquals("み", user.getDisplayName());
    }

    @Test
    @DisplayName("falls back to the username when there is no display name")
    void fallsBackToUsername() {
        JSONObject json = payload();
        json.remove("global_name");

        assertEquals("n4yxr", User.fromJson(json).getDisplayName());
        assertTrue(User.fromJson(json).getGlobalName().isEmpty());
    }

    @Test
    @DisplayName("null is rejected")
    void rejectsNull() {
        assertThrows(NullPointerException.class, () -> User.fromJson(null));
    }

    // ---- the icon ----

    @Test
    @DisplayName("builds the avatar URL")
    void buildsAvatarUrl() {
        User.Avatar avatar = User.fromJson(payload()).getAvatar();

        assertFalse(avatar.isDefault());
        assertEquals(
            "https://cdn.discordapp.com/avatars/1193242580113170596/a1895cdb3039903e58df0d8b518456dd.png",
            avatar.getUrl());
        assertEquals(
            "https://cdn.discordapp.com/avatars/1193242580113170596/a1895cdb3039903e58df0d8b518456dd.webp",
            avatar.getUrl(ImageFormat.WEBP));
    }

    @Test
    @DisplayName("a missing avatar does not throw")
    void handlesMissingAvatar() {
        JSONObject json = payload();
        json.remove("avatar");

        User.Avatar avatar = User.fromJson(json).getAvatar();

        // In 1.0.0 this was written as hash.equals("null"), so a NullPointerException came out here.
        assertTrue(avatar.isDefault());
        assertTrue(avatar.getHash().isEmpty());
    }

    @Test
    @DisplayName("the new scheme picks one of 6 default avatars from the id")
    void modernDefaultAvatar() {
        JSONObject json = payload();
        json.remove("avatar");

        String url = User.fromJson(json).getAvatar().getUrl();
        long expected = (Long.parseUnsignedLong("1193242580113170596") >> 22) % 6;

        assertEquals("https://cdn.discordapp.com/embed/avatars/" + expected + ".png", url);
    }

    @Test
    @DisplayName("the legacy scheme picks one of 5 default avatars from the discriminator")
    void legacyDefaultAvatar() {
        JSONObject json = payload().put("discriminator", "1234");
        json.remove("avatar");

        // 1234 % 5 = 4
        assertEquals(
            "https://cdn.discordapp.com/embed/avatars/4.png",
            User.fromJson(json).getAvatar().getUrl());
    }

    @Test
    @DisplayName("an animated avatar keeps GIF")
    void animatedAvatarKeepsGif() {
        User.Avatar avatar = User.fromJson(payload().put("avatar", "a_1234567890")).getAvatar();

        assertTrue(avatar.isAnimated());
        assertTrue(avatar.getUrl(ImageFormat.GIF).endsWith(".gif"));
    }

    @Test
    @DisplayName("asking a static avatar for GIF gives PNG")
    void staticAvatarFallsBackToPng() {
        User.Avatar avatar = User.fromJson(payload()).getAvatar();

        assertFalse(avatar.isAnimated());
        assertTrue(avatar.getUrl(ImageFormat.GIF).endsWith(".png"));
    }
}
