package com.ieltsstudio.controller;

import com.ieltsstudio.common.Result;
import com.ieltsstudio.entity.ExamCollection;
import com.ieltsstudio.entity.ExamCollectionItem;
import com.ieltsstudio.security.AuthUser;
import com.ieltsstudio.service.ExamCollectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 试卷集接口控制器
 */
@RestController
@RequestMapping("/exam-collections")
@RequiredArgsConstructor
public class ExamCollectionController {

    private final ExamCollectionService collectionService;

    // ─── 试卷集 CRUD ────────────────────────────────────────────────────────────

    /** GET /exam-collections — 获取当前用户的所有试卷集 */
    @GetMapping
    public Result<?> list(@AuthenticationPrincipal AuthUser authUser) {
        return Result.success(collectionService.getUserCollections(authUser.getId()));
    }

    /** GET /exam-collections/{id} — 获取试卷集详情（含试卷列表） */
    @GetMapping("/{id}")
    public Result<?> detail(@PathVariable Long id,
                            @AuthenticationPrincipal AuthUser authUser) {
        Map<String, Object> detail = collectionService.getCollectionDetail(id);
        if (detail == null) return Result.notFound("试卷集不存在");
        ExamCollection c = (ExamCollection) detail.get("collection");
        if (!c.getUserId().equals(authUser.getId())) return Result.notFound("试卷集不存在");
        return Result.success(detail);
    }

    /** POST /exam-collections — 创建试卷集 */
    @PostMapping
    public Result<?> create(@RequestBody Map<String, String> body,
                            @AuthenticationPrincipal AuthUser authUser) {
        String title = body.getOrDefault("title", "").trim();
        if (title.isEmpty()) return Result.error(400, "标题不能为空");
        String desc = body.getOrDefault("description", "");
        ExamCollection c = collectionService.createCollection(authUser.getId(), title, desc);
        return Result.success(c);
    }

    /** PUT /exam-collections/{id} — 更新试卷集 */
    @PutMapping("/{id}")
    public Result<?> update(@PathVariable Long id,
                            @RequestBody Map<String, String> body,
                            @AuthenticationPrincipal AuthUser authUser) {
        ExamCollection c = collectionService.updateCollection(
                authUser.getId(), id, body.get("title"), body.get("description"));
        if (c == null) return Result.notFound("试卷集不存在或无权限");
        return Result.success(c);
    }

    /** DELETE /exam-collections/{id} — 删除试卷集（不删除内部试卷） */
    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id,
                            @AuthenticationPrincipal AuthUser authUser) {
        boolean ok = collectionService.deleteCollection(authUser.getId(), id);
        return ok ? Result.success(null) : Result.notFound("试卷集不存在或无权限");
    }

    // ─── 试卷集内试卷管理 ──────────────────────────────────────────────────────

    /** POST /exam-collections/{id}/exams — 添加试卷到试卷集 */
    @PostMapping("/{id}/exams")
    public Result<?> addExam(@PathVariable Long id,
                             @RequestBody Map<String, Long> body,
                             @AuthenticationPrincipal AuthUser authUser) {
        // Verify ownership
        ExamCollection c = collectionService.getById(id);
        if (c == null || !c.getUserId().equals(authUser.getId())) return Result.notFound("试卷集不存在");
        Long examId = body.get("examId");
        if (examId == null) return Result.error(400, "examId 不能为空");
        ExamCollectionItem item = collectionService.addExam(id, examId);
        if (item == null) return Result.error(400, "该试卷已在集合中");
        return Result.success(item);
    }

    /** DELETE /exam-collections/{id}/exams/{examId} — 从试卷集移除试卷 */
    @DeleteMapping("/{id}/exams/{examId}")
    public Result<?> removeExam(@PathVariable Long id, @PathVariable Long examId,
                                @AuthenticationPrincipal AuthUser authUser) {
        ExamCollection c = collectionService.getById(id);
        if (c == null || !c.getUserId().equals(authUser.getId())) return Result.notFound("试卷集不存在");
        boolean ok = collectionService.removeExam(id, examId);
        return ok ? Result.success(null) : Result.notFound("试卷不在集合中");
    }

    /** PUT /exam-collections/{id}/reorder — 重新排序试卷 */
    @PutMapping("/{id}/reorder")
    public Result<?> reorder(@PathVariable Long id,
                             @RequestBody Map<String, List<Long>> body,
                             @AuthenticationPrincipal AuthUser authUser) {
        ExamCollection c = collectionService.getById(id);
        if (c == null || !c.getUserId().equals(authUser.getId())) return Result.notFound("试卷集不存在");
        List<Long> examIds = body.get("examIds");
        if (examIds == null) return Result.error(400, "examIds 不能为空");
        collectionService.reorderItems(id, examIds);
        return Result.success(null);
    }
}
