package org.readybell.ordering.adapter.in.web;

import java.util.List;

/** Paginated order list in the dashboard's {@code PaginatedResponse} shape. */
public record OrderPageResponse(List<OrderRowResponse> items, long totalCount, int pageCount, int currentPage) {}
