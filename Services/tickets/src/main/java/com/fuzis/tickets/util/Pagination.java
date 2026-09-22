package com.fuzis.tickets.util;

import com.fuzis.tickets.exception.ApiException;

public record Pagination(int page, int size, long offset) {
    public static Pagination of(int page, int size, int maxSize) {
        if (page < 1) {
            throw ApiException.badRequest("page must be greater than or equal to 1");
        }
        if (size < 1 || size > maxSize) {
            throw ApiException.badRequest("size must be between 1 and " + maxSize);
        }
        return new Pagination(page, size, (long) (page - 1) * size);
    }
}
