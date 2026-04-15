package com.heima.englishnova.shared.dto;

import com.heima.englishnova.shared.enums.QuizMode;
import jakarta.validation.constraints.NotNull;

/**
 * 创建斩词会话请求。
 *
 * @param wordbookId 词书 ID
 * @param mode       斩词模式
 */
public record CreateQuizSessionRequest(
        @NotNull(message = "词书不能为空")
        Long wordbookId,
        QuizMode mode
) {
}
