package com.cti.market.news;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

public final class TitleNewsClassifier implements NewsClassifier {

    private static final BigDecimal MATCHED = new BigDecimal("0.400000");
    private static final BigDecimal UNKNOWN = new BigDecimal("0.200000");
    private static final String[][] ASSETS = {
            {"bitcoin", "BTC"}, {"btc", "BTC"},
            {"ethereum", "ETH"}, {"ether", "ETH"}, {"eth", "ETH"},
            {"binance coin", "BNB"}, {"bnb", "BNB"},
            {"solana", "SOL"}, {"sol", "SOL"},
            {"ripple", "XRP"}, {"xrp", "XRP"},
            {"chainlink", "LINK"}, {"link", "LINK"},
            {"avalanche", "AVAX"}, {"avax", "AVAX"}
    };

    @Override
    public Optional<NewsDraft> classify(String title, List<String> knownAssets) {
        if (title == null || title.isBlank()) {
            return Optional.empty();
        }
        String text = title.toLowerCase(Locale.ROOT);
        String category = category(text);
        String sentiment = sentiment(text);
        String impact = impact(text);
        BigDecimal confidence = "OTHER".equals(category) && "UNCERTAIN".equals(sentiment) ? UNKNOWN : MATCHED;
        return Optional.of(new NewsDraft(category, sentiment, impact, confidence, assets(text, knownAssets), title));
    }

    private static String category(String text) {
        if (has(text, "hack", "exploit", "stolen", "vulnerability")) {
            return "SECURITY";
        }
        if (has(text, "unlock", "vesting")) {
            return "TOKEN_UNLOCK";
        }
        if (has(text, "etf")) {
            return "ETF";
        }
        if (has(text, "usdt", "usdc", "stablecoin", "busd", "fdusd")) {
            return "STABLECOIN";
        }
        if (has(text, "sec", "cftc", "regulation", "regulatory", "lawsuit")) {
            return "REGULATION";
        }
        if (has(text, "fomc", "inflation", "interest rate")) {
            return "MACRO";
        }
        if (has(text, "list", "listing", "delist", "delisting", "removal", "binance", "withdrawal",
                "deposit", "maintenance", "suspend", "wallet")) {
            return "EXCHANGE";
        }
        if (has(text, "mainnet", "hard fork", "upgrade")) {
            return "PROJECT";
        }
        return "OTHER";
    }

    private static String sentiment(String text) {
        boolean positive = has(text, "list", "listing", "launch", "approve", "approval", "reopen", "resume", "partnership");
        boolean negative = has(text, "delist", "delisting", "removal", "suspend", "halt", "hack", "exploit", "ban", "stolen", "discontinue");
        if (positive && negative) {
            return "UNCERTAIN";
        }
        if (positive) {
            return "POSITIVE";
        }
        if (negative) {
            return "NEGATIVE";
        }
        if (has(text, "maintenance", "api update", "fee schedule")) {
            return "NEUTRAL";
        }
        return "UNCERTAIN";
    }

    private static String impact(String text) {
        if (has(text, "hack", "exploit", "stolen")) {
            return "CRITICAL";
        }
        if (has(text, "delist", "delisting", "removal", "suspend", "halt", "ban", "lawsuit")) {
            return "HIGH";
        }
        if (has(text, "list", "listing", "etf", "approval", "unlock")) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private static List<String> assets(String text, List<String> knownAssets) {
        List<String> found = new ArrayList<>();
        for (String[] alias : ASSETS) {
            if (!known(knownAssets, alias[1]) || found.contains(alias[1]) || !has(text, alias[0])) {
                continue;
            }
            found.add(alias[1]);
        }
        if (found.isEmpty()) {
            found.add("MARKET_WIDE");
        }
        return List.copyOf(found);
    }

    private static boolean known(List<String> knownAssets, String asset) {
        if (knownAssets == null) {
            return false;
        }
        for (String known : knownAssets) {
            if (asset.equalsIgnoreCase(known)) {
                return true;
            }
        }
        return false;
    }

    private static boolean has(String text, String... words) {
        for (String word : words) {
            if (Pattern.compile("(?<![a-z0-9-])" + Pattern.quote(word) + "(?![a-z0-9-])").matcher(text).find()) {
                return true;
            }
        }
        return false;
    }
}
