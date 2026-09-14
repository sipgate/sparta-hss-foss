package com.sipgate.sparta.hss.persistence.entities;

import static org.assertj.core.api.Assertions.assertThat;

import com.sipgate.sparta.hss.persistence.entities.LocationLTE;
import org.instancio.Instancio;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class LocationLTETest {
  @Nested
  class Copy {
    @Test
    void itCopysAllFieldsToNewInstance() {
      // GIVEN
      final var original = Instancio.create(LocationLTE.class);

      // WHEN
      final var copy = original.copy();

      // THEN
      assertThat(copy)
              .isNotSameAs(original)
              .usingRecursiveComparison()
              .isEqualTo(original);
    }
  }
}