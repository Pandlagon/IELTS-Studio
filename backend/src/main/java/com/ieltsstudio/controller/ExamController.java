package com.ieltsstudio.controller;

import com.ieltsstudio.common.Result;
import com.ieltsstudio.dto.SubmitAnswerRequest;
import com.ieltsstudio.dto.TranslateRequest;
import com.ieltsstudio.security.AuthUser;
import com.ieltsstudio.service.AiParseService;
import com.ieltsstudio.service.ExamService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 试卷接口控制器
 *
 * <p>接口路径前缀：{@code /exams}，所有接口均需登录认证。
 *
 * <p>功能覆盖：
 * <ul>
 *   <li>试卷列表/详情/题目查询</li>
 *   <li>试卷上传（触发异步解析）</li>
 *   <li>答案提交与自动判分</li>
 *   <li>考试记录查询与 AI 反馈保存</li>
 *   <li>写作 AI 评分</li>
 *   <li>错题本查询</li>
 *   <li>试卷删除（级联删除关联数据）</li>
 * </ul>
 */
@RestController
@RequestMapping("/exams")
@RequiredArgsConstructor
public class ExamController {

    private final ExamService examService;
    private final AiParseService aiParseService;

    // ─── 试卷查询 ──────────────────────────────────────────────────────────────

    /** GET /exams — 获取当前用户的所有试卷 */
    @GetMapping
    public Result<?> getUserExams(@AuthenticationPrincipal AuthUser authUser) {
        return Result.success(examService.getUserExams(authUser.getId()));
    }

    /** GET /exams/{id} — 获取试卷详情 */
    @GetMapping("/{id}")
    public Result<?> getExam(@PathVariable Long id,
                              @AuthenticationPrincipal AuthUser authUser) {
        var exam = examService.getExamById(id);
        if (exam == null) return Result.notFound("试卷不存在");
        return Result.success(exam);
    }

    /** GET /exams/{id}/questions — 获取试卷题目列表 */
    @GetMapping("/{id}/questions")
    public Result<?> getQuestions(@PathVariable Long id) {
        return Result.success(examService.getExamQuestions(id));
    }

    // ─── 试卷上传 ──────────────────────────────────────────────────────────────

    /**
     * POST /exams/upload — 上传试卷并触发异步解析
     *
     * <p>请求参数：
     * <ul>
     *   <li>{@code file}          — 文件（PDF / DOC / DOCX），最大 20MB</li>
     *   <li>{@code title}         — 试卷标题</li>
     *   <li>{@code type}          — 题型（reading / writing），默认 reading</li>
     *   <li>{@code duration}      — 时长（分钟），默认 60</li>
     *   <li>{@code parsePrecise}  — 是否精准解析（Qwen 视觉），默认 false</li>
     *   <li>{@code extractedText} — 前端预提取的文字（普通解析时传入）</li>
     * </ul>
     */
    @PostMapping("/upload")
    public Result<?> uploadExam(
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "files", required = false) MultipartFile[] files,
            @RequestParam("title") String title,
            @RequestParam(value = "type", defaultValue = "reading") String type,
            @RequestParam(value = "duration", defaultValue = "60") Integer duration,
            @RequestParam(value = "parsePrecise", defaultValue = "false") Boolean parsePrecise,
            @RequestParam(value = "extractedText", required = false) String extractedText,
            @AuthenticationPrincipal AuthUser authUser) throws Exception {
        var exam = (files != null && files.length > 0)
                ? examService.uploadExamImages(authUser.getId(), files, title, type, duration, parsePrecise, extractedText)
                : examService.uploadExam(authUser.getId(), file, title, type, duration, parsePrecise, extractedText);
        return Result.success(exam);
    }

    // ─── 答案提交 ──────────────────────────────────────────────────────────────

    /**
     * POST /exams/submit — 提交答案，返回得分与各题详情
     */
    @PostMapping("/submit")
    public Result<?> submitAnswers(@Valid @RequestBody SubmitAnswerRequest req,
                                   @AuthenticationPrincipal AuthUser authUser) {
        return Result.success(examService.submitAnswers(authUser.getId(), req));
    }

    // ─── 试卷删除 ──────────────────────────────────────────────────────────────

    /** DELETE /exams/{id} — 删除试卷及所有关联数据 */
    @DeleteMapping("/{id}")
    public Result<?> deleteExam(@PathVariable Long id,
                                @AuthenticationPrincipal AuthUser authUser) {
        boolean ok = examService.deleteExam(authUser.getId(), id);
        return ok ? Result.success(null) : Result.notFound("试卷不存在或无权限删除");
    }

    // ─── 考试记录 ──────────────────────────────────────────────────────────────

    /** GET /exams/history — 获取当前用户的考试历史列表 */
    @GetMapping("/history")
    public Result<?> getHistory(@AuthenticationPrincipal AuthUser authUser) {
        return Result.success(examService.getUserHistory(authUser.getId()));
    }

    /** GET /exams/records/{id} — 获取单次考试记录详情（含 AI 写作反馈） */
    @GetMapping("/records/{id}")
    public Result<?> getRecord(@PathVariable Long id,
                               @AuthenticationPrincipal AuthUser authUser) {
        var record = examService.getRecordById(id, authUser.getId());
        if (record == null) return Result.notFound("记录不存在");
        return Result.success(record);
    }

    /**
     * PATCH /exams/records/{id}/ai-feedback — 保存写作题的 AI 评分结果
     *
     * <p>请求体：{@code { "feedback": { "questionId": {...} }, "band": 6.5 }}
     */
    @PatchMapping("/records/{id}/ai-feedback")
    public Result<?> saveAiFeedback(@PathVariable Long id,
                                    @RequestBody Map<String, Object> body,
                                    @AuthenticationPrincipal AuthUser authUser) {
        @SuppressWarnings("unchecked")
        Map<String, Object> feedback = (Map<String, Object>) body.get("feedback");
        Double band = body.get("band") != null ? Double.valueOf(body.get("band").toString()) : null;
        boolean ok = examService.saveAiFeedback(id, authUser.getId(), feedback, band);
        return ok ? Result.success(null) : Result.notFound("记录不存在");
    }

    // ─── 错题本 ────────────────────────────────────────────────────────────────

    /** GET /exams/errors — 获取当前用户未掌握的错题列表 */
    @GetMapping("/errors")
    public Result<?> getErrorBook(@AuthenticationPrincipal AuthUser authUser) {
        return Result.success(examService.getUserErrorBook(authUser.getId()));
    }

    // ─── 写作评分 ──────────────────────────────────────────────────────────────

    /**
     * POST /exams/grade-writing — AI 写作评分
     *
     * <p>请求体：{@code { "taskPrompt": "...", "userEssay": "...", "wordLimit": 250 }}
     * <p>评分维度：TR（任务回应）、CC（连贯衔接）、LR（词汇丰富度）、GRA（语法范围）
     */
    @PostMapping("/grade-writing")
    public Result<?> gradeWriting(@RequestBody Map<String, Object> req) {
        try {
            String taskPrompt = (String) req.getOrDefault("taskPrompt", "");
            String userEssay  = (String) req.getOrDefault("userEssay", "");
            int wordLimit = req.get("wordLimit") instanceof Number
                    ? ((Number) req.get("wordLimit")).intValue() : 250;
            Map<String, Object> result = aiParseService.gradeWriting(taskPrompt, userEssay, wordLimit);
            return Result.success(result);
        } catch (Exception e) {
            return Result.error("评分失败: " + e.getMessage());
        }
    }

    /**
     * POST /exams/translate — reading translate for selected text (requires auth)
     * body: { passage: "...", selectedText: "..." }
     */
    @PostMapping("/translate")
    public Result<?> translate(@Valid @RequestBody TranslateRequest req,
                               @AuthenticationPrincipal AuthUser authUser) {
        try {
            return Result.success(aiParseService.translateWithContext(req.getPassage(), req.getSelectedText()));
        } catch (Exception e) {
            return Result.error("翻译失败: " + e.getMessage());
        }
    }
}
