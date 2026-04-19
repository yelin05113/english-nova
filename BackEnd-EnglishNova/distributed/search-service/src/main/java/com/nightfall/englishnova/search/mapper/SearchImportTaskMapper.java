package com.nightfall.englishnova.search.mapper;

import com.nightfall.englishnova.search.domain.vo.ImportTaskCleanupVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SearchImportTaskMapper {

    List<ImportTaskCleanupVo> loadImportTaskCleanupRows();

    void updateImportTaskCleanup(
            @Param("taskId") String taskId,
            @Param("sourceName") String sourceName,
            @Param("errorMessage") String errorMessage
    );
}
