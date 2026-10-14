package com.lowcortisol.backend.admindashboard;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AdminDashboardController {
    private final AdminDashboardService dashboard;

    public AdminDashboardController(AdminDashboardService dashboard) { this.dashboard = dashboard; }

    @GetMapping("/api/admin/dashboard")
    public AdminDashboardResponse get(@RequestParam(required = false) String days,
        @RequestParam(required = false) String page, Authentication auth) {
        return dashboard.get(auth.getName(), days, page);
    }
}
