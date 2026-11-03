package org.readybell.ordering.domain;

import java.util.List;

/** One page of orders plus the total match count. */
public record OrderPage(List<Order> items, long totalCount, int page, int pageSize) {

    public int pageCount() {
        return (int) ((totalCount + pageSize - 1) / pageSize);
    }
}
