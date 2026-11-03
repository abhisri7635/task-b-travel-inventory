package com.example.navan.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;

import javax.sql.DataSource;

/**
 * Creates a HikariCP-wrapped SQLiteDataSource with WAL mode, FK enforcement,
 * and busy_timeout set via SQLiteConfig — applied to every connection in the pool.
 */
@Configuration
public class SQLiteDataSourceConfig {

    @Value("${spring.datasource.url}")
    private String jdbcUrl;

    @Value("${spring.datasource.hikari.maximum-pool-size:5}")
    private int maxPoolSize;

    @Bean
    @Primary
    public DataSource dataSource() {
        SQLiteConfig sqliteConf = new SQLiteConfig();
        sqliteConf.setJournalMode(SQLiteConfig.JournalMode.WAL);
        sqliteConf.enforceForeignKeys(true);
        sqliteConf.setBusyTimeout(5000);

        SQLiteDataSource sqLiteDataSource = new SQLiteDataSource(sqliteConf);
        sqLiteDataSource.setUrl(jdbcUrl);

        HikariConfig hikariConf = new HikariConfig();
        hikariConf.setDataSource(sqLiteDataSource);
        hikariConf.setMaximumPoolSize(maxPoolSize);
        hikariConf.setMinimumIdle(1);

        return new HikariDataSource(hikariConf);
    }
}
