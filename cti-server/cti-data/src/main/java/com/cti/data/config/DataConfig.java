package com.cti.data.config;

import com.cti.data.parquet.KlineArchive;
import com.cti.data.parquet.ParquetKlineArchive;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

@Configuration
@EnableConfigurationProperties(DataProperties.class)
public class DataConfig {

    @Bean
    public KlineArchive klineArchive(DataProperties properties) {
        return new ParquetKlineArchive(Path.of(properties.getDir()));
    }
}
