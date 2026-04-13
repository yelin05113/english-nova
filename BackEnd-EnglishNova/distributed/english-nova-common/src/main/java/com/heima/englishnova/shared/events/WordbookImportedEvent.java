package com.heima.englishnova.shared.events;

public record WordbookImportedEvent(
        long userId,
        long wordbookId
) implements java.io.Serializable {
}
