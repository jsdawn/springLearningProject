package com.example.orderservice.dto;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CreateOrderRequest JSR303 冒烟测试（CI 用）：重点验证 @Valid 注解
 * 对嵌套 List 元素（CreateOrderItemRequest）的级联校验是否生效。
 */
class CreateOrderRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    private CreateOrderRequest request(Long userId, java.util.List<CreateOrderItemRequest> items) {
        CreateOrderRequest req = new CreateOrderRequest();
        req.setUserId(userId);
        req.setItems(items);
        return req;
    }

    @Test
    void validRequest_passes() {
        CreateOrderItemRequest item = new CreateOrderItemRequest();
        item.setProductId(1L);
        item.setQuantity(2);

        assertThat(validator.validate(request(1L, Arrays.asList(item)))).isEmpty();
    }

    @Test
    void nullUserIdAndEmptyItems_violated() {
        Set<ConstraintViolation<CreateOrderRequest>> violations =
                validator.validate(request(null, Collections.emptyList()));

        assertThat(violations).hasSize(2); // userId @NotNull + items @Size(min=1)
    }

    @Test
    void nestedInvalidItem_cascadeValidated() {
        // quantity 缺省为非法值时，@Valid 应传导到 item 级别校验
        CreateOrderItemRequest badItem = new CreateOrderItemRequest();
        badItem.setProductId(null);
        badItem.setQuantity(null);

        Set<ConstraintViolation<CreateOrderRequest>> violations =
                validator.validate(request(1L, Arrays.asList(badItem)));

        assertThat(violations).isNotEmpty(); // 嵌套字段违规被捕获
    }
}
