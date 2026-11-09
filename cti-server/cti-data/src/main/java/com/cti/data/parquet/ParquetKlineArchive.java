package com.cti.data.parquet;

import com.cti.common.Versions;
import com.cti.market.model.KlineBar;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.hadoop.fs.Path;
import org.apache.parquet.avro.AvroParquetWriter;
import org.apache.parquet.hadoop.ParquetWriter;
import org.apache.parquet.hadoop.metadata.CompressionCodecName;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

public class ParquetKlineArchive implements KlineArchive {

    private static final Schema SCHEMA = new Schema.Parser().parse("""
            {
              "type": "record",
              "name": "Kline",
              "namespace": "cti",
              "fields": [
                {"name": "exchange_code", "type": "string"},
                {"name": "symbol", "type": "string"},
                {"name": "interval_code", "type": "string"},
                {"name": "open_time", "type": "long"},
                {"name": "close_time", "type": "long"},
                {"name": "open_price", "type": "string"},
                {"name": "high_price", "type": "string"},
                {"name": "low_price", "type": "string"},
                {"name": "close_price", "type": "string"},
                {"name": "volume", "type": "string"},
                {"name": "quote_volume", "type": ["null", "string"], "default": null},
                {"name": "trade_count", "type": ["null", "long"], "default": null},
                {"name": "taker_buy_base_volume", "type": ["null", "string"], "default": null},
                {"name": "taker_buy_quote_volume", "type": ["null", "string"], "default": null},
                {"name": "is_closed", "type": "boolean"},
                {"name": "source", "type": "string"},
                {"name": "data_version", "type": "string"}
              ]
            }
            """);

    private final java.nio.file.Path root;

    public ParquetKlineArchive(java.nio.file.Path root) {
        this.root = root;
    }

    @Override
    public void write(String symbol, String interval, List<KlineBar> bars) {
        if (bars.isEmpty()) {
            return;
        }
        long min = bars.get(0).openTime();
        long max = bars.get(0).openTime();
        for (KlineBar bar : bars) {
            min = Math.min(min, bar.openTime());
            max = Math.max(max, bar.openTime());
        }
        java.nio.file.Path dir = root.resolve("raw/binance/spot/klines/interval=" + interval + "/symbol=" + symbol);
        try {
            Files.createDirectories(dir);
            java.nio.file.Path file = dir.resolve("part-" + min + "-" + max + "-" + System.currentTimeMillis() + ".parquet");
            try (ParquetWriter<GenericRecord> writer = AvroParquetWriter.<GenericRecord>builder(new Path(file.toUri()))
                    .withSchema(SCHEMA)
                    .withCompressionCodec(CompressionCodecName.UNCOMPRESSED)
                    .build()) {
                for (KlineBar bar : bars) {
                    writer.write(record(bar));
                }
            }
        } catch (IOException ex) {
            throw new IllegalStateException("写入 Parquet 失败: " + symbol + " " + interval, ex);
        }
    }

    private static GenericRecord record(KlineBar bar) {
        GenericRecord record = new GenericData.Record(SCHEMA);
        record.put("exchange_code", bar.exchangeCode());
        record.put("symbol", bar.symbol());
        record.put("interval_code", bar.intervalCode());
        record.put("open_time", bar.openTime());
        record.put("close_time", bar.closeTime());
        record.put("open_price", bar.open().toPlainString());
        record.put("high_price", bar.high().toPlainString());
        record.put("low_price", bar.low().toPlainString());
        record.put("close_price", bar.close().toPlainString());
        record.put("volume", bar.volume().toPlainString());
        record.put("quote_volume", bar.quoteVolume() == null ? null : bar.quoteVolume().toPlainString());
        record.put("trade_count", bar.tradeCount());
        record.put("taker_buy_base_volume", bar.takerBuyBase() == null ? null : bar.takerBuyBase().toPlainString());
        record.put("taker_buy_quote_volume", bar.takerBuyQuote() == null ? null : bar.takerBuyQuote().toPlainString());
        record.put("is_closed", bar.closed());
        record.put("source", "binance-spot");
        record.put("data_version", Versions.RAW_DATA);
        return record;
    }
}
