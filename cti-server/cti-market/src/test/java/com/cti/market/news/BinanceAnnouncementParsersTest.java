package com.cti.market.news;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BinanceAnnouncementParsersTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void keepsOfficialArticlesAndDropsRowsWithoutACodeOrTime() {
        String json = """
                {"code":"000000","data":{"catalogs":[{"catalogName":"New Cryptocurrency Listing","articles":[
                  {"code":"abc","title":"Binance Will List Bitcoin","releaseDate":1793274532329},
                  {"code":"","title":"missing code","releaseDate":1},
                  {"code":"nodate","title":"No Date","releaseDate":null}
                ]}]}}
                """;
        List<Announcement> articles = BinanceAnnouncementParsers.parse(json, mapper);
        assertEquals(1, articles.size());
        assertEquals("abc", articles.get(0).code());
        assertEquals("Binance Will List Bitcoin", articles.get(0).title());
        assertEquals(1793274532329L, articles.get(0).publishedAtMs());
        assertEquals("New Cryptocurrency Listing", articles.get(0).catalogName());
        assertEquals("https://www.binance.com/en/support/announcement/abc", articles.get(0).sourceUrl());
    }

    @Test
    void rejectsAnUnsuccessfulPayload() {
        assertThrows(IllegalArgumentException.class,
                () -> BinanceAnnouncementParsers.parse("{\"code\":\"100001\",\"data\":{}}", mapper));
    }
}
