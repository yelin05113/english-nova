package com.nightfall.englishnova.study.service.impl;

import com.nightfall.englishnova.shared.auth.CurrentUser;
import com.nightfall.englishnova.shared.auth.RequestUserExtractor;
import com.nightfall.englishnova.shared.dto.StudyAgendaDto;
import com.nightfall.englishnova.shared.dto.StudyProgressDto;
import com.nightfall.englishnova.study.domain.vo.AccuracyVo;
import com.nightfall.englishnova.study.domain.vo.ProgressVo;
import com.nightfall.englishnova.study.mapper.StudyProgressMapper;
import com.nightfall.englishnova.study.service.StudyAgendaService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class StudyAgendaServiceImpl implements StudyAgendaService {

    private final StudyProgressMapper studyProgressMapper;

    public StudyAgendaServiceImpl(StudyProgressMapper studyProgressMapper) {
        this.studyProgressMapper = studyProgressMapper;
    }

    @Override
    public StudyAgendaDto getTodayAgenda(HttpServletRequest request) {
        CurrentUser user = RequestUserExtractor.require(request);
        ProgressVo progress = loadProgress(user.id());
        int listeningCards = Math.min(12, Math.max(0, progress.getTotalWords() / 5));
        int estimatedMinutes = Math.max(5, (progress.getNewWords() * 2) + progress.getInProgressWords());
        return new StudyAgendaDto(
                progress.getNewWords(),
                progress.getInProgressWords(),
                listeningCards,
                estimatedMinutes,
                buildFocusAreas(progress)
        );
    }

    @Override
    public StudyProgressDto getProgress(HttpServletRequest request) {
        CurrentUser user = RequestUserExtractor.require(request);
        ProgressVo progress = loadProgress(user.id());
        AccuracyVo accuracy = studyProgressMapper.loadAccuracy(user.id());
        if (accuracy == null) {
            accuracy = new AccuracyVo();
        }
        int accuracyRate = accuracy.getAnsweredQuestions() == 0
                ? 0
                : Math.round((accuracy.getCorrectAnswers() * 100f) / accuracy.getAnsweredQuestions());

        return new StudyProgressDto(
                progress.getTotalWords(),
                progress.getClearedWords(),
                progress.getInProgressWords(),
                progress.getNewWords(),
                progress.getWordbooks(),
                accuracy.getAnsweredQuestions(),
                accuracy.getCorrectAnswers(),
                accuracyRate
        );
    }

    private ProgressVo loadProgress(long userId) {
        ProgressVo progress = studyProgressMapper.loadProgress(userId);
        return progress == null ? new ProgressVo() : progress;
    }

    private List<String> buildFocusAreas(ProgressVo progress) {
        if (progress.getTotalWords() == 0) {
            return List.of("先导入一个词书，系统会自动生成你的个人学习面板");
        }
        if (progress.getInProgressWords() > progress.getClearedWords()) {
            return List.of("优先清理答错词", "把未斩词书先开一轮", "搜索薄弱词义做强化");
        }
        if (progress.getNewWords() > 0) {
            return List.of("先处理新词", "保持单词斩杀节奏", "完成后再回看搜索高频词");
        }
        return List.of("本轮词书接近清空", "可以开始下一本词书", "继续巩固公共词库干扰项");
    }
}
