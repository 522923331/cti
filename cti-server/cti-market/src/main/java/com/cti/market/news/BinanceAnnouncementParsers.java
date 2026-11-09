package com.cti.market.news;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class BinanceAnnouncementParsers {

    static final String URL_PREFIX = "https://www.binance.com/en/support/announcement/";

    private BinanceAnnouncementParsers() {
    }

    public static List<Announcement> parse(String json, ObjectMapper mapper) {
        JsonNode root = read(json, mapper);
        if (!"000000".equals(root.path("code").asText())) {
            throw new IllegalArgumentException("币安公告响应失败");
        }
        List<Announcement> articles = new ArrayList<>();
        for (JsonNode catalog : root.path("data").path("catalogs")) {
            String catalogName = catalog.path("catalogName").asText("");
            for (JsonNode article : catalog.path("articles")) {
                String code = article.path("code").asText("").trim();
                String title = article.path("title").asText("").trim();
                JsonNode releaseDate = article.get("releaseDate");
                if (code.isEmpty() || title.isEmpty() || releaseDate == null || !releaseDate.isNumber()) {
                    continue;
                }
                articles.add(new Announcement(code, title, releaseDate.asLong(), catalogName, URL_PREFIX + code));
            }
        }
        return articles;
    }

    private static JsonNode read(String json, ObjectMapper mapper) {
        try {
            return mapper.readTree(json);
        } catch (IOException ex) {
            throw new IllegalArgumentException("无法解析币安公告", ex);
        }
    }
}
