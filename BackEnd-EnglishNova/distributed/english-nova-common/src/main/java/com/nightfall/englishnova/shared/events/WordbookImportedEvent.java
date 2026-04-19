package com.nightfall.englishnova.shared.events;

/**
 * 词书导入完成事件，由导入服务发出，通知搜索引擎同步索引。
 *
 * @param userId     用户 ID
 * @param wordbookId 词书 ID
 */
public record WordbookImportedEvent(
        long userId,
        long wordbookId
) implements java.io.Serializable {
}
