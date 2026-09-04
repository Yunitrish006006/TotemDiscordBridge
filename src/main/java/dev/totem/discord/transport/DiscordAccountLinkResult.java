package dev.totem.discord.transport;

/** Bounded result returned by the account-link Worker client; never contains a bind code. */
public record DiscordAccountLinkResult(Status status, String discordName, int retryAfterSeconds) {
    public enum Status {
        LINKED,
        UNLINKED,
        NOT_LINKED,
        INVALID_CODE,
        INVALID_REQUEST,
        MINECRAFT_NAME_MISMATCH,
        MINECRAFT_ALREADY_BOUND,
        RATE_LIMITED,
        BUSY,
        UNAVAILABLE
    }

    public DiscordAccountLinkResult {
        discordName = sanitizeDiscordName(discordName);
        if (discordName.length() > 80) {
            discordName = discordName.substring(0, 80);
        }
        retryAfterSeconds = Math.max(0, retryAfterSeconds);
    }

    static DiscordAccountLinkResult of(Status status) {
        return new DiscordAccountLinkResult(status, "", 0);
    }

    private static String sanitizeDiscordName(String value) {
        if (value == null) return "";
        StringBuilder result = new StringBuilder(value.length());
        boolean previousWhitespace = false;
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            offset += Character.charCount(codePoint);
            boolean control = Character.isISOControl(codePoint)
                    || codePoint >= 0x202A && codePoint <= 0x202E
                    || codePoint >= 0x2066 && codePoint <= 0x2069;
            boolean whitespace = control || Character.isWhitespace(codePoint);
            if (whitespace) {
                if (!previousWhitespace && !result.isEmpty()) result.append(' ');
                previousWhitespace = true;
            } else {
                result.appendCodePoint(codePoint);
                previousWhitespace = false;
            }
        }
        return result.toString().trim();
    }
}
