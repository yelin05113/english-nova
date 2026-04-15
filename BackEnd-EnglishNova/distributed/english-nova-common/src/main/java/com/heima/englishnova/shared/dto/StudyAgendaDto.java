package com.heima.englishnova.shared.dto;

import java.util.List;

/**
 * 今日学习计划。
 *
 * @param newCards         新学卡片数
 * @param reviewCards      复习卡片数
 * @param listeningCards   听力卡片数
 * @param estimatedMinutes 预计学习分钟数
 * @param focusAreas       关注领域列表
 */
public record StudyAgendaDto(
        int newCards,
        int reviewCards,
        int listeningCards,
        int estimatedMinutes,
        List<String> focusAreas
) {
}
