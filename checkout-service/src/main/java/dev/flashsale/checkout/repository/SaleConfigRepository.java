package dev.flashsale.checkout.repository;

import dev.flashsale.checkout.domain.SaleConfig;
import dev.flashsale.checkout.error.ProductNotFoundException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SaleConfigRepository {

    private final JdbcTemplate jdbcTemplate;

    public SaleConfigRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public SaleConfig findBySku(String sku) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT sku, name, initial_stock, price_cents FROM sale_config WHERE sku = ?",
                    (rs, rowNum) -> new SaleConfig(
                            rs.getString("sku"),
                            rs.getString("name"),
                            rs.getInt("initial_stock"),
                            rs.getInt("price_cents")
                    ),
                    sku
            );
        } catch (EmptyResultDataAccessException e) {
            throw new ProductNotFoundException(sku);
        }
    }
}
