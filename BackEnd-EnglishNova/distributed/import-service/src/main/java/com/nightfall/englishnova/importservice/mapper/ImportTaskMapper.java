package com.nightfall.englishnova.importservice.mapper;

import com.nightfall.englishnova.importservice.domain.vo.ImportTaskVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.sql.Timestamp;
import java.util.List;

@Mapper
public interface ImportTaskMapper {

    List<ImportTaskVo> listByUser(@Param("userId") long userId);

    void insertTask(
            @Param("taskId") String taskId,
            @Param("userId") long userId,
            @Param("wordbookId") Long wordbookId,
            @Param("platform") String platform,
            @Param("sourceName") String sourceName,
            @Param("estimatedCards") int estimatedCards,
            @Param("importedCards") int importedCards,
            @Param("status") String status,
            @Param("queuedAt") Timestamp queuedAt,
            @Param("finishedAt") Timestamp finishedAt,
            @Param("queueName") String queueName
    );
}
