package com.cti.data.parquet;

import com.cti.market.model.KlineBar;
import org.apache.avro.generic.GenericRecord;
import org.apache.hadoop.fs.Path;
import org.apache.parquet.avro.AvroParquetReader;
import org.apache.parquet.hadoop.ParquetReader;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParquetKlineArchiveTest {

    @Test
    void writesANewFileAndKeepsThePriceText() throws Exception {
        java.nio.file.Path root = Files.createTempDirectory("cti-parquet");
        ParquetKlineArchive archive = new ParquetKlineArchive(root);
        KlineBar bar = new KlineBar("BINANCE", "BTCUSDT", "1d", 1000, 1999,
                new BigDecimal("10.5"), new BigDecimal("11"), new BigDecimal("9"), new BigDecimal("10.25"),
                new BigDecimal("3"), new BigDecimal("30.75"), 8L, new BigDecimal("1"), new BigDecimal("10"), true);
        archive.write("BTCUSDT", "1d", List.of(bar));
        try (var files = Files.walk(root)) {
            java.nio.file.Path file = files.filter(path -> path.toString().endsWith(".parquet")).findFirst().orElseThrow();
            try (ParquetReader<GenericRecord> reader = AvroParquetReader.<GenericRecord>builder(new Path(file.toUri())).build()) {
                GenericRecord record = reader.read();
                assertEquals("10.25", record.get("close_price").toString());
                assertEquals(true, record.get("is_closed"));
                assertTrue(reader.read() == null);
            }
        }
    }
}
