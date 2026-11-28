package com.basecamp.backend.domain.ai.service;

import java.util.regex.Pattern;

/**
 * LLM 입출력 텍스트 가드레일.
 *
 * <ul>
 *   <li>입력: 전화번호·이메일을 LLM 에 보내기 전에 가린다(외부 API·로그로 개인정보가 나가지 않게)
 *   <li>입력: {@code <}, {@code >} 를 바꿔 사용자 텍스트가 {@code </review>} 같은 태그를 닫고 지시문처럼 보이지 않게 한다
 *   <li>출력: 모델이 만든 문장에 개인정보 패턴이 있는지 확인한다
 * </ul>
 */
final class AiTextGuard {

  static final String PHONE_MASK = "[전화번호]";
  static final String EMAIL_MASK = "[이메일]";

  private static final Pattern EMAIL =
      Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

  /**
   * 010-1234-5678, 01012345678, 02-123-4567, +82 10-1234-5678 등. 0 으로 시작하는 9~11자리 번호만 잡아 가격과 섞이지 않게
   * 한다.
   */
  private static final Pattern PHONE =
      Pattern.compile("(?:\\+82[-.\\s]?|(?<!\\d)0)\\d{1,2}[-.\\s]?\\d{3,4}[-.\\s]?\\d{4}(?!\\d)");

  private AiTextGuard() {}

  /** LLM 에 넣을 사용자 텍스트: 개인정보를 가리고 태그 문자를 무력화한다. */
  static String sanitizeInput(String text) {
    if (text == null) {
      return "";
    }
    String masked =
        PHONE.matcher(EMAIL.matcher(text).replaceAll(EMAIL_MASK)).replaceAll(PHONE_MASK);
    return masked.replace("<", "&lt;").replace(">", "&gt;");
  }

  /** 모델 출력에 전화번호·이메일이 있는지. */
  static boolean containsPii(String text) {
    return text != null && (EMAIL.matcher(text).find() || PHONE.matcher(text).find());
  }
}
