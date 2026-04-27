package com.ieltsstudio.controller;

import com.ieltsstudio.common.Result;
import com.ieltsstudio.security.AuthUser;
import com.ieltsstudio.service.ClozeService;
import com.ieltsstudio.service.WordBookService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.ieltsstudio.service.AsyncWordService;
import java.util.List;
import java.util.Map;

/**
 * 词书接口控制器
 *
 * <p>接口路径前缀：{@code /words}，所有接口均需登录认证。
 *
 * <p>功能覆盖：
 * <ul>
 *   <li>词书的增删查</li>
 *   <li>词条的增删改查</li>
 *   <li>通过文件批量导入单词（异步处理）</li>
 *   <li>快速批量添加单词（异步处理）</li>
 *   <li>词条跨词书复制</li>
 * </ul>
 */
@RestController
@RequestMapping("/words")
@RequiredArgsConstructor
public class WordBookController {

    private final WordBookService wordBookService;
    private final AsyncWordService asyncWordService;
    private final ClozeService clozeService;

    // ─── 词书管理 ──────────────────────────────────────────────────────────────

    /**
     * GET /words/books — 获取当前用户所有词书
     * <p>若用户尚无默认词书，自动创建后再返回。
     */
    @GetMapping("/books")
    public Result<?> getBooks(@AuthenticationPrincipal AuthUser authUser) {
        wordBookService.ensureDefaultBook(authUser.getId());
        return Result.success(wordBookService.getUserBooks(authUser.getId()));
    }

    /**
     * GET /words/books/{id} — 获取词书详情
     */
    @GetMapping("/books/{id}")
    public Result<?> getBook(@PathVariable Long id,
                             @AuthenticationPrincipal AuthUser authUser) {
        var book = wordBookService.getBook(authUser.getId(), id);
        return book != null ? Result.success(book) : Result.notFound("词书不存在");
    }

    /**
     * POST /words/books — 创建新词书
     * <p>请求体：{@code { "name": "词书名（必填，最多50字）", "description": "描述（可选）" }}
     */
    @PostMapping("/books")
    public Result<?> createBook(@RequestBody Map<String, String> body,
                                @AuthenticationPrincipal AuthUser authUser) {
        String name = body.getOrDefault("name", "").trim();
        if (name.isEmpty()) return Result.error("词书名称不能为空");
        if (name.length() > 50) return Result.error("词书名称不超过50字");
        String desc = body.getOrDefault("description", "");
        return Result.success(wordBookService.createBook(authUser.getId(), name, desc));
    }

    /**
     * DELETE /words/books/{id} — 删除词书
     * <p>默认词书不可删除。
     */
    @DeleteMapping("/books/{id}")
    public Result<?> deleteBook(@PathVariable Long id,
                                @AuthenticationPrincipal AuthUser authUser) {
        boolean ok = wordBookService.deleteBook(authUser.getId(), id);
        return ok ? Result.success(null) : Result.error("无法删除该词书");
    }

    // ─── 词条管理 ──────────────────────────────────────────────────────────────

    /**
     * GET /words/books/{id}/entries — 获取词书下的所有词条
     */
    @GetMapping("/books/{id}/entries")
    public Result<?> getEntries(@PathVariable Long id,
                                @AuthenticationPrincipal AuthUser authUser) {
        return Result.success(wordBookService.getEntries(authUser.getId(), id));
    }

    /**
     * POST /words/books/default/entries — 添加单词到默认词书
     * <p>请求体：{@code { "word": "单词", "meaning": "释义", "example": "例句（可选）" }}
     */
    @PostMapping("/books/default/entries")
    public Result<?> addToDefault(@RequestBody Map<String, String> body,
                                  @AuthenticationPrincipal AuthUser authUser) {
        var entry = wordBookService.addWordToDefaultBook(authUser.getId(), body);
        return entry != null ? Result.success(entry) : Result.error("单词或释义不能为空");
    }

    /**
     * PUT /words/entries/{id} — 修改词条的释义或例句
     * <p>请求体：{@code { "meaning": "新释义", "example": "新例句" }}
     */
    @PutMapping("/entries/{id}")
    public Result<?> updateEntry(@PathVariable Long id,
                                 @RequestBody Map<String, String> body,
                                 @AuthenticationPrincipal AuthUser authUser) {
        var entry = wordBookService.updateEntry(authUser.getId(), id, body.get("meaning"), body.get("example"));
        return entry != null ? Result.success(entry) : Result.notFound("词条不存在");
    }

    /**
     * POST /words/entries/{id}/copy-to-default — 将指定词条复制到默认词书
     */
    @PostMapping("/entries/{id}/copy-to-default")
    public Result<?> copyToDefault(@PathVariable Long id,
                                   @AuthenticationPrincipal AuthUser authUser) {
        var entry = wordBookService.copyToDefaultBook(authUser.getId(), id);
        return entry != null ? Result.success(entry) : Result.notFound("词条不存在");
    }

    /**
     * DELETE /words/entries/{id} — 删除词条
     */
    @DeleteMapping("/entries/{id}")
    public Result<?> deleteEntry(@PathVariable Long id,
                                 @AuthenticationPrincipal AuthUser authUser) {
        boolean ok = wordBookService.deleteEntry(authUser.getId(), id);
        return ok ? Result.success(null) : Result.error("条目不存在");
    }

    // ─── 批量导入 ──────────────────────────────────────────────────────────────

    /**
     * POST /words/books/{id}/upload — 上传文件批量导入单词（异步）
     * <p>支持 Excel / CSV 格式，后台异步处理，接口立即返回任务状态。
     */
    @PostMapping("/books/{id}/upload")
    public Result<?> uploadWords(@PathVariable Long id,
                                 @RequestParam("file") MultipartFile file,
                                 @AuthenticationPrincipal AuthUser authUser) {
        try {
            Map<String, Object> result = wordBookService.startUpload(authUser.getId(), id, file);
            return Result.success(result);
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            return Result.error("上传失败: " + e.getMessage());
        }
    }

    /**
     * POST /words/books/default/quick-add — 快速批量添加单词到默认词书（异步）
     *
     * <p>请求体：{@code { "words": ["apple", "banana", ...] }}
     * <p>后台自动查询释义并保存，接口立即返回处理数量。
     */
    @PostMapping("/books/default/quick-add")
    public Result<?> quickAddWords(@RequestBody Map<String, Object> body,
                                   @AuthenticationPrincipal AuthUser authUser) {
        @SuppressWarnings("unchecked")
        List<String> words = (List<String>) body.get("words");
        if (words == null || words.isEmpty()) return Result.error("单词列表不能为空");
        var defaultBook = wordBookService.ensureDefaultBook(authUser.getId());
        asyncWordService.quickAddWords(authUser.getId(), defaultBook.getId(), words);
        return Result.success(Map.of("status", "processing", "count", words.size()));
    }

    // ─── 完形填空练习 ─────────────────────────────────────────────────────────────────

    /**
     * POST /words/cloze/generate — AI 生成完形填空
     * <p>请求体：{@code { "words": ["apple","banana",...], "meanings": ["苹果","香蕉",...], "difficulty": "medium" }}
     * <p>数据不落库，一次性返回。
     */
    @SuppressWarnings("unchecked")
    @PostMapping("/cloze/generate")
    public Result<?> generateCloze(@RequestBody Map<String, Object> body,
                                   @AuthenticationPrincipal AuthUser authUser) {
        List<String> words = (List<String>) body.get("words");
        List<String> meanings = (List<String>) body.get("meanings");
        String difficulty = (String) body.getOrDefault("difficulty", "medium");
        if (words == null || words.isEmpty()) return Result.error("请至少选择 1 个单词");
        if (words.size() > 10) return Result.error("最多选择 10 个单词");
        try {
            Map<String, Object> result = clozeService.generate(words, meanings, difficulty);
            return Result.success(result);
        } catch (Exception e) {
            return Result.error("生成完形填空失败：" + e.getMessage());
        }
    }

    /**
     * POST /words/cloze/check — AI 批改完形填空
     * <p>请求体：{@code { "passage": "...", "blanks": [...], "userAnswers": {"1":"A","2":"C"} }}
     */
    @SuppressWarnings("unchecked")
    @PostMapping("/cloze/check")
    public Result<?> checkCloze(@RequestBody Map<String, Object> body,
                                @AuthenticationPrincipal AuthUser authUser) {
        String passage = (String) body.get("passage");
        List<Map<String, Object>> blanks = (List<Map<String, Object>>) body.get("blanks");
        Map<String, String> userAnswers = (Map<String, String>) body.get("userAnswers");
        if (passage == null || blanks == null || userAnswers == null) {
            return Result.error("缺少必要参数");
        }
        try {
            Map<String, Object> result = clozeService.check(passage, blanks, userAnswers);
            return Result.success(result);
        } catch (Exception e) {
            return Result.error("批改失败：" + e.getMessage());
        }
    }
}
