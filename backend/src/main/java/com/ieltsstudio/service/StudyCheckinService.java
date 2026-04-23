package com.ieltsstudio.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ieltsstudio.entity.StudyCheckin;
import com.ieltsstudio.mapper.StudyCheckinMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class StudyCheckinService {

    private final StudyCheckinMapper studyCheckinMapper;

    /**
     * Check in for today. Idempotent: if already checked in today, it will not create a duplicate.
     */
    public Map<String, Object> checkinToday(Long userId) {
        LocalDate today = LocalDate.now();
        boolean already = studyCheckinMapper.countByUserIdAndDate(userId, today) > 0;
        if (!already) {
            StudyCheckin sc = new StudyCheckin();
            sc.setUserId(userId);
            sc.setCheckinDate(today);
            try {
                studyCheckinMapper.insert(sc);
            } catch (DuplicateKeyException ignore) {
                // concurrent request
            }
        }
        return getStats(userId);
    }

    public Map<String, Object> getStats(Long userId) {
        LocalDate today = LocalDate.now();
        boolean checkedInToday = studyCheckinMapper.countByUserIdAndDate(userId, today) > 0;
        long totalDays = studyCheckinMapper.countByUserId(userId);

        // Compute current streak: consecutive days ending at today if checked in today, else ending at yesterday.
        LocalDate cursor = checkedInToday ? today : today.minusDays(1);

        QueryWrapper<StudyCheckin> qw = new QueryWrapper<>();
        qw.eq("user_id", userId).orderByDesc("checkin_date");
        qw.select("checkin_date");
        List<StudyCheckin> list = studyCheckinMapper.selectList(qw);

        int streak = 0;
        for (StudyCheckin sc : list) {
            if (sc.getCheckinDate() == null) continue;
            if (sc.getCheckinDate().isEqual(cursor)) {
                streak++;
                cursor = cursor.minusDays(1);
                continue;
            }
            // since list is desc, once we pass the expected cursor, streak ends
            if (sc.getCheckinDate().isBefore(cursor)) break;
        }

        Map<String, Object> res = new HashMap<>();
        res.put("checkedInToday", checkedInToday);
        res.put("totalDays", totalDays);
        res.put("currentStreak", streak);
        res.put("today", today.toString());
        return res;
    }

    public List<String> getRecentDates(Long userId, int days) {
        int d = Math.max(1, Math.min(days, 365));
        LocalDate from = LocalDate.now().minusDays(d - 1L);
        List<LocalDate> dates = studyCheckinMapper.findDatesFrom(userId, from);
        List<String> out = new ArrayList<>();
        for (LocalDate dt : dates) {
            if (dt != null) out.add(dt.toString());
        }
        return out;
    }
}
