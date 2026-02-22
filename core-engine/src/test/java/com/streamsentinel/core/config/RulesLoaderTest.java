package com.streamsentinel.core.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link RulesLoader}.
 */
class RulesLoaderTest {

    @Test
    @DisplayName("Should load test rules from classpath")
    void shouldLoadFromClasspath() {
        RulesConfig config = RulesLoader.fromClasspath("test-rules.yml");

        assertThat(config).isNotNull();
        assertThat(config.getRules()).hasSize(2);
        assertThat(config.getRules().get(0).getName()).isEqualTo("test_rate");
        assertThat(config.getRules().get(0).getType()).isEqualTo("rate");
        assertThat(config.getRules().get(1).getName()).isEqualTo("test_threshold");
        assertThat(config.getRules().get(1).getType()).isEqualTo("threshold");
    }

    @Test
    @DisplayName("Should throw when classpath resource does not exist")
    void shouldThrowForMissingResource() {
        assertThatThrownBy(() -> RulesLoader.fromClasspath("does-not-exist.yml"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }
}
