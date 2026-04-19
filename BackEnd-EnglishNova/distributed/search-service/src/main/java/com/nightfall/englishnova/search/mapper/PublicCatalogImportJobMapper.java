package com.nightfall.englishnova.search.mapper;

import com.nightfall.englishnova.search.domain.po.PublicCatalogImportJobPo;
import com.nightfall.englishnova.search.domain.vo.PublicCatalogImportItemVo;
import com.nightfall.englishnova.search.domain.vo.PublicCatalogImportJobVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PublicCatalogImportJobMapper {

    void insertJob(PublicCatalogImportJobPo job);

    void insertItems(@Param("jobId") long jobId, @Param("words") List<String> words);

    PublicCatalogImportJobVo findJob(@Param("jobId") long jobId);

    PublicCatalogImportJobVo findNextRunnableJob();

    int startJob(@Param("jobId") long jobId);

    void resetRunningItems(@Param("jobId") long jobId);

    List<PublicCatalogImportItemVo> claimPendingItems(@Param("jobId") long jobId, @Param("limit") int limit);

    int markItemRunning(@Param("itemId") long itemId);

    void markItemImported(
            @Param("itemId") long itemId,
            @Param("entryId") long entryId,
            @Param("status") String status
    );

    void markItemSkipped(@Param("itemId") long itemId, @Param("entryId") Long entryId);

    void markItemFailed(@Param("itemId") long itemId, @Param("lastError") String lastError);

    void refreshJobCounters(@Param("jobId") long jobId);

    void completeJobIfFinished(@Param("jobId") long jobId);

    void failJob(@Param("jobId") long jobId, @Param("errorMessage") String errorMessage);

    void cancelJob(@Param("jobId") long jobId);

    void resetFailedItems(@Param("jobId") long jobId);
}
