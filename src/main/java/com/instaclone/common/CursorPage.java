package com.instaclone.common;

import java.util.List;

public record CursorPage<T>(List<T> items, String nextCursor, boolean hasMore) {

    public static <T> CursorPage<T> of(List<T> pageItems, int requestedLimit, java.util.function.Function<T, Cursor> cursorOf) {
        boolean hasMore = pageItems.size() > requestedLimit;
        List<T> trimmed = hasMore ? pageItems.subList(0, requestedLimit) : pageItems;
        String next = hasMore ? cursorOf.apply(trimmed.get(trimmed.size() - 1)).encode() : null;
        return new CursorPage<>(trimmed, next, hasMore);
    }
}
