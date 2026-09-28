package com.example.productservice.dto;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ProductPageQuery JSR303 冒烟测试（CI 用）：分页参数边界校验。
 */
class ProductPageQueryValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void defaults_areValid() {
        assertThat(validator.validate(new ProductPageQuery())).isEmpty();
    }

    @Test
    void pageSizeOver100_violated() {
        ProductPageQuery query = new ProductPageQuery();
        query.setPageSize(101);

        Set<ConstraintViolation<ProductPageQuery>> violations = validator.validate(query);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage()).contains("最大为 100");
    }

    @Test
    void pageNumBelow1_violated() {
        ProductPageQuery query = new ProductPageQuery();
        query.setPageNum(0);

        assertThat(validator.validate(query)).hasSize(1);
    }
}
