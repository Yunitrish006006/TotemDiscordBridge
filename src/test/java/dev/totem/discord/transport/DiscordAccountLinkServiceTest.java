package dev.totem.discord.transport;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscordAccountLinkServiceTest {
    @Test
    void normalizesOnlyTheWorkerProtocolAlphabet() {
        assertEquals("ABCDEFGH", DiscordAccountLinkService.normalizeCode("abcdefgh"));
        assertEquals("", DiscordAccountLinkService.normalizeCode("ABCDI234"));
        assertEquals("", DiscordAccountLinkService.normalizeCode("short"));
    }

    @Test
    void accountLinkCredentialsRequireHttpsOutsideLoopbackDevelopment() {
        assertTrue(DiscordAccountLinkService.isAllowedEndpoint("https://worker.example"));
        assertTrue(DiscordAccountLinkService.isAllowedEndpoint("http://127.0.0.1:8787"));
        assertFalse(DiscordAccountLinkService.isAllowedEndpoint("http://worker.example"));
        assertFalse(DiscordAccountLinkService.isAllowedEndpoint("https://user@worker.example"));
        assertFalse(DiscordAccountLinkService.isAllowedEndpoint("not-a-url"));
    }

    @Test
    void parsesSuccessfulBindingWithoutReturningProtocolSecrets() {
        DiscordAccountLinkResult result = DiscordAccountLinkService.parseResponse(
                200,
                """
                        {"success":true,"data":{"discord_id":"123456789012345678","discord_name":"User"}}
                        """,
                DiscordAccountLinkService.Operation.VERIFY
        );

        assertEquals(DiscordAccountLinkResult.Status.LINKED, result.status());
        assertEquals("User", result.discordName());
        assertEquals(0, result.retryAfterSeconds());
    }

    @Test
    void mapsStableWorkerErrorsAndBoundsRemoteNames() {
        DiscordAccountLinkResult limited = DiscordAccountLinkService.parseResponse(
                429,
                """
                        {"success":false,"code":"rate_limited","retry_after_seconds":42}
                        """,
                DiscordAccountLinkService.Operation.VERIFY
        );
        assertEquals(DiscordAccountLinkResult.Status.RATE_LIMITED, limited.status());
        assertEquals(42, limited.retryAfterSeconds());

        DiscordAccountLinkResult missing = DiscordAccountLinkService.parseResponse(
                404,
                """
                        {"success":false,"code":"not_linked"}
                        """,
                DiscordAccountLinkService.Operation.STATUS
        );
        assertEquals(DiscordAccountLinkResult.Status.NOT_LINKED, missing.status());

        DiscordAccountLinkResult bounded = new DiscordAccountLinkResult(
                DiscordAccountLinkResult.Status.LINKED,
                "x".repeat(40) + "\n\u202eSpoofed" + "y".repeat(120),
                -1
        );
        assertEquals(80, bounded.discordName().length());
        assertFalse(bounded.discordName().contains("\n"));
        assertFalse(bounded.discordName().contains("\u202e"));
        assertEquals(0, bounded.retryAfterSeconds());
    }

    @Test
    void malformedWorkerResponsesFailClosed() {
        assertEquals(
                DiscordAccountLinkResult.Status.UNAVAILABLE,
                DiscordAccountLinkService.parseResponse(
                        502,
                        "not-json",
                        DiscordAccountLinkService.Operation.VERIFY
                ).status()
        );
    }
}
