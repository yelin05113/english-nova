package com.nightfall.englishnova.importservice.service;

import com.nightfall.englishnova.shared.auth.CurrentUser;
import com.nightfall.englishnova.shared.dto.ImportPresetDto;
import com.nightfall.englishnova.shared.dto.ImportTaskDto;
import com.nightfall.englishnova.shared.dto.ImportTaskRequest;
import com.nightfall.englishnova.shared.enums.WordImportPlatform;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ImportTaskService {

    List<ImportPresetDto> listPresets();

    List<ImportTaskDto> listTasks(CurrentUser user);

    ImportTaskDto createTask(CurrentUser user, ImportTaskRequest request);

    ImportTaskDto importFile(CurrentUser user, WordImportPlatform platform, String sourceName, MultipartFile file);
}
