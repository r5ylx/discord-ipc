package com.r5ylx.discordipc.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Building the activity payload.
 * 1.0.0 had four bugs here, which left Rich Presence barely working.
 */
class RichPresenceTest {
    @Test
    @DisplayName("timestamps becomes a nested object")
    void timestampsAreNested() {
        JSONObject json = RichPresence.builder()
            .setStartTimestamp(1000L)
            .setEndTimestamp(2000L)
            .build()
            .toJson();

        assertTrue(json.has("timestamps"));
        assertEquals(1000L, json.getJSONObject("timestamps").getLong("start"));
        assertEquals(2000L, json.getJSONObject("timestamps").getLong("end"));

        // In 1.0.0 start and end went to the top level and timestamps stayed empty.
        assertFalse(json.has("start"));
        assertFalse(json.has("end"));
    }

    @Test
    @DisplayName("timestamps is omitted when no time is set")
    void timestampsAreOmittedWhenUnset() {
        JSONObject json = RichPresence.builder().setDetails("doing nothing").build().toJson();

        // In 1.0.0 the Builder took a primitive long, so an unset value still sent 0.
        assertFalse(json.has("timestamps"));
    }

    @Test
    @DisplayName("an Instant can be given instead")
    void acceptsInstant() {
        Instant now = Instant.ofEpochMilli(1_700_000_000_000L);

        JSONObject json = RichPresence.builder().setStartTimestamp(now).build().toJson();

        assertEquals(now.toEpochMilli(), json.getJSONObject("timestamps").getLong("start"));
    }

    @Test
    @DisplayName("party sits at the top level")
    void partyIsPlacedAtTopLevel() {
        JSONObject json = RichPresence.builder().setParty("party-1", 2, 4).build().toJson();

        assertTrue(json.has("party"));

        JSONObject party = json.getJSONObject("party");
        assertEquals("party-1", party.getString("id"));
        assertEquals(2, party.getJSONArray("size").getInt(0));
        assertEquals(4, party.getJSONArray("size").getInt(1));

        // In 1.0.0 this was party.put("party", party), putting the object inside itself.
        assertFalse(party.has("party"));
    }

    @Test
    @DisplayName("size is omitted when the headcount is not positive")
    void partySizeIsOmittedWhenNotPositive() {
        JSONObject party = RichPresence.builder().setParty("party-1", 0, 0).build()
            .toJson().getJSONObject("party");

        assertEquals("party-1", party.getString("id"));
        assertFalse(party.has("size"));
    }

    @Test
    @DisplayName("the asset keys are snake_case")
    void assetKeysUseSnakeCase() {
        JSONObject assets = RichPresence.builder()
            .setLargeImage("logo", "Talpius")
            .setSmallImage("icon", "status")
            .build()
            .toJson()
            .getJSONObject("assets");

        assertEquals("logo", assets.getString("large_image"));
        assertEquals("Talpius", assets.getString("large_text"));
        assertEquals("icon", assets.getString("small_image"));
        assertEquals("status", assets.getString("small_text"));

        // In 1.0.0 largeImageKey and the rest went out as they were, so no image showed.
        assertFalse(assets.has("largeImageKey"));
        assertFalse(assets.has("largeImageText"));
    }

    @Test
    @DisplayName("assets is omitted when no image is set")
    void assetsAreOmittedWhenUnset() {
        assertFalse(RichPresence.builder().setDetails("x").build().toJson().has("assets"));
    }

    @Test
    @DisplayName("the secrets keys match the specification")
    void secretKeys() {
        JSONObject secrets = RichPresence.builder()
            .setMatchSecret("m")
            .setJoinSecret("j")
            .setSpectateSecret("s")
            .build()
            .toJson()
            .getJSONObject("secrets");

        assertEquals("m", secrets.getString("match"));
        assertEquals("j", secrets.getString("join"));
        assertEquals("s", secrets.getString("spectate"));
    }

    @Test
    @DisplayName("anything left unset is omitted")
    void unsetFieldsAreOmitted() {
        JSONObject json = RichPresence.builder().build().toJson();

        assertFalse(json.has("state"));
        assertFalse(json.has("details"));
        assertFalse(json.has("timestamps"));
        assertFalse(json.has("assets"));
        assertFalse(json.has("party"));
        assertFalse(json.has("secrets"));

        // instance is a boolean, so it is always sent.
        assertTrue(json.has("instance"));
    }

    @Test
    @DisplayName("state and details go in unchanged")
    void stateAndDetails() {
        JSONObject json = RichPresence.builder()
            .setState("Waiting")
            .setDetails("In the lobby")
            .build()
            .toJson();

        assertEquals("Waiting", json.getString("state"));
        assertEquals("In the lobby", json.getString("details"));
    }
}
