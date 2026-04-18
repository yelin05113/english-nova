package com.nightfall.englishnova.study.service;

import com.nightfall.englishnova.shared.dto.StudyAgendaDto;
import com.nightfall.englishnova.shared.dto.StudyProgressDto;
import jakarta.servlet.http.HttpServletRequest;

public interface StudyAgendaService {

    StudyAgendaDto getTodayAgenda(HttpServletRequest request);

    StudyProgressDto getProgress(HttpServletRequest request);
}
