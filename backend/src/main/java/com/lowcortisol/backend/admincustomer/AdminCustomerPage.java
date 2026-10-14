package com.lowcortisol.backend.admincustomer;

import java.util.List;

public record AdminCustomerPage(List<AdminCustomerResponse> items, int page, int pageSize, long totalItems, long totalPages) {}
