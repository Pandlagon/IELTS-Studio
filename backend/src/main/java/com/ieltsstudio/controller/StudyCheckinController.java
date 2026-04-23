package com.ieltsstudio.controller;

import com.ieltsstudio.common.Result;
import com.ieltsstudio.security.AuthUser;
import com.ieltsstudio.service.StudyCheckinService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/checkins")
@RequiredArgsConstructor
public class StudyCheckinController {

    private final StudyCheckinService studyCheckinService;

    /**
     * POST /checkins/today - daily check-in
     */
    @PostMapping("/today")
    public Result<?> checkinToday(@AuthenticationPrincipal AuthUser authUser) {
        return Result.success(studyCheckinService.checkinToday(authUser.getId()));
    }

    /**
     * GET /checkins/stats - total days, current streak, and whether checked in today
     */
    @GetMapping("/stats")
    public Result<?> stats(@AuthenticationPrincipal AuthUser authUser) {
        return Result.success(studyCheckinService.getStats(authUser.getId()));
    }

    /**
     * GET /checkins/recent?days=30 - recent check-in dates for calendar
     */
    @GetMapping("/recent")
    public Result<?> recent(@AuthenticationPrincipal AuthUser authUser,
                            @RequestParam(name = "days", defaultValue = "30") int days) {
        return Result.success(studyCheckinService.getRecentDates(authUser.getId(), days));
    }
}
