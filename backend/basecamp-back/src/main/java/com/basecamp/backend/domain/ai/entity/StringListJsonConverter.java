package com.basecamp.backend.domain.ai.entity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.List;

/** List&lt;String&gt; ↔ JSON 배열 문자열. 요약의 장단점 목록을 TEXT 컬럼 하나에 담는다. */
@Converter
public class StringListJsonConverter implements AttributeConverter<List<String>, String> {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final TypeReference<List<String>> TYPE = new TypeReference<>() {};

  @Override
  public String convertToDatabaseColumn(List<String> attribute) {
    try {
      return MAPPER.writeValueAsString(attribute == null ? List.of() : attribute);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("장단점 목록을 JSON 으로 바꾸지 못했습니다.", e);
    }
  }

  @Override
  public List<String> convertToEntityAttribute(String dbData) {
    if (dbData == null || dbData.isBlank()) {
      return List.of();
    }
    try {
      return MAPPER.readValue(dbData, TYPE);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("장단점 JSON 을 읽지 못했습니다.", e);
    }
  }
}
