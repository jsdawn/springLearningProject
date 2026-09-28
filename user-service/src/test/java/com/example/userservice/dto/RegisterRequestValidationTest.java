package com.example.userservice.dto;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RegisterRequest JSR303 冒烟测试（CI 用）：standalone Validator 校验注解约束，
 * 不依赖 Spring 上下文与数据库。
 */
class RegisterRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    private RegisterRequest validRequest() {
        RegisterRequest req = new RegisterRequest();
        req.setUsername("alice");
        req.setPassword("123456");
        req.setNickname("Alice");
        req.setPhone("13800138000");
        req.setEmail("alice@example.com");
        return req;
    }

    @Test
    void validRequest_passes() {
        assertThat(validator.validate(validRequest())).isEmpty();
    }

    @Test
    void blankFields_violated() {
        RegisterRequest req = validRequest();
        req.setUsername("");
        req.setPassword("");

        // 每个空字段同时违反 @NotBlank 和 @Size（min 长度约束），共 4 处
        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(req);
        assertThat(violations).hasSize(4);
    }

    @Test
    void shortUsername_violated() {
        RegisterRequest req = validRequest();
        req.setUsername("ab"); // 最短 3 位

        assertThat(validator.validate(req)).hasSize(1);
    }

    @Test
    void malformedPhoneAndEmail_violated() {
        RegisterRequest req = validRequest();
        req.setPhone("12345");
        req.setEmail("not-an-email");

        assertThat(validator.validate(req)).hasSize(2);
    }
}
