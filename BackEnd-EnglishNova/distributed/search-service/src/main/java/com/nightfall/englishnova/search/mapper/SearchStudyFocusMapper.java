package com.nightfall.englishnova.search.mapper;

import com.nightfall.englishnova.search.domain.vo.StudyFocusCleanupVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SearchStudyFocusMapper {

    List<StudyFocusCleanupVo> loadStudyFocusCleanupRows();

    void updateStudyFocusCleanup(@Param("id") long id, @Param("focusLabel") String focusLabel);
}
