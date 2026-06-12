package com.nightfall.englishnova.search.mapper;

import com.nightfall.englishnova.search.domain.vo.ExampleEnrichmentTaskVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ExampleEnrichmentTaskMapper {

    void insertTasksForUserWordbook(@Param("userId") long userId, @Param("wordbookId") long wordbookId);

    void upsertTask(@Param("entryType") String entryType, @Param("entryId") long entryId);

    int backfillPublicTasks(@Param("limit") int limit, @Param("publicLimit") int publicLimit);

    int backfillUserTasks(@Param("limit") int limit);

    int resetPublicIncompleteTasks(@Param("limit") int limit, @Param("publicLimit") int publicLimit);

    int skipOutOfScopePublicTasks(@Param("publicLimit") int publicLimit);

    void resetTimedOutRunningTasks(@Param("timeoutMinutes") int timeoutMinutes);

    List<ExampleEnrichmentTaskVo> findPendingTasks(
            @Param("limit") int limit,
            @Param("maxRetries") int maxRetries
    );

    List<ExampleEnrichmentTaskVo> findPendingAudioOnlyTasks(
            @Param("limit") int limit,
            @Param("maxRetries") int maxRetries,
            @Param("audioScopeField") String audioScopeField,
            @Param("audioScopeOrder") String audioScopeOrder,
            @Param("audioScopeLimit") int audioScopeLimit
    );

    List<ExampleEnrichmentTaskVo> findPendingTextOnlyTasks(
            @Param("limit") int limit,
            @Param("maxRetries") int maxRetries,
            @Param("publicLimit") int publicLimit
    );

    int markTaskRunning(
            @Param("taskId") long taskId,
            @Param("maxRetries") int maxRetries
    );

    void markTaskSucceeded(@Param("taskId") long taskId);

    void markTaskSkipped(@Param("taskId") long taskId, @Param("lastError") String lastError);

    void markTaskFailed(@Param("taskId") long taskId, @Param("lastError") String lastError);
}
