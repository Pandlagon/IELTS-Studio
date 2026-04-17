package com.ieltsstudio.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ieltsstudio.entity.Exam;
import com.ieltsstudio.entity.ExamRecord;
import com.ieltsstudio.entity.ErrorBook;
import com.ieltsstudio.entity.Question;
import com.ieltsstudio.mapper.ExamMapper;
import com.ieltsstudio.mapper.ExamRecordMapper;
import com.ieltsstudio.mapper.ErrorBookMapper;
import com.ieltsstudio.mapper.QuestionMapper;
import com.ieltsstudio.dto.SubmitAnswerRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

/**
 * 试卷核心业务服务
 *
 * <p>负责以下功能模块：
 * <ul>
 *   <li>试卷上传与异步解析触发</li>
 *   <li>答案提交、自动判分与成绩保存</li>
 *   <li>雅思 Band 分计算</li>
 *   <li>答题记录查询与 AI 反馈保存</li>
 *   <li>错题本自动入库</li>
 *   <li>试卷及关联数据的级联删除</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExamService {

    private final ExamMapper examMapper;
    private final QuestionMapper questionMapper;
    private final ExamRecordMapper examRecordMapper;
    private final ErrorBookMapper errorBookMapper;
    private final AsyncParseService asyncParseService;
    private final ObjectMapper objectMapper;

    // ─── 试卷查询 ──────────────────────────────────────────────────────────────

    /** 获取指定用户的所有试卷列表 */
    public List<Exam> getUserExams(Long userId) {
        return examMapper.findByUserId(userId);
    }

    /** 根据 ID 获取试卷详情 */
    public Exam getExamById(Long examId) {
        return examMapper.selectById(examId);
    }

    /** 获取试卷下的所有题目 */
    public List<Question> getExamQuestions(Long examId) {
        return questionMapper.findByExamId(examId);
    }

    // ─── 试卷上传 ──────────────────────────────────────────────────────────────

    /**
     * 上传并触发异步解析
     *
     * <p>先在数据库中创建状态为 {@code processing} 的试卷记录，
     * 再将文件内容交由 {@link AsyncParseService} 异步处理，
     * 接口立即返回试卷对象，前端可轮询状态。
     *
     * @param userId        当前用户 ID
     * @param file          上传的文件（PDF / Word）
     * @param title         试卷标题
     * @param type          题型（reading / writing）
     * @param duration      考试时长（分钟），默认 60
     * @param parsePrecise  是否启用精准解析（Qwen 视觉模型）
     * @param extractedText 前端预提取的文字（普通解析时传入，精准解析时可为空）
     */
    public Exam uploadExam(Long userId, MultipartFile file, String title, String type,
                           Integer duration, boolean parsePrecise, String extractedText) throws Exception {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("未选择文件");
        }
        // 创建试卷记录，状态置为"解析中"
        Exam exam = new Exam();
        exam.setUserId(userId);
        exam.setTitle(title);
        exam.setType(type);
        exam.setStatus("processing");
        exam.setQuestionCount(0);
        exam.setDuration(duration != null && duration > 0 ? duration : 60);
        exam.setDifficulty("中等");
        examMapper.insert(exam);

        // 读取文件字节后交由异步服务处理（避免文件流在异步线程中已关闭）
        byte[] fileBytes = file.getBytes();
        String originalFilename = file.getOriginalFilename();
        asyncParseService.parseAndSave(exam.getId(), fileBytes, originalFilename, parsePrecise, extractedText, type);

        return exam;
    }

    public Exam uploadExamImages(Long userId, MultipartFile[] files, String title, String type,
                                 Integer duration, boolean parsePrecise, String extractedText) throws Exception {
        if (files == null || files.length == 0) {
            throw new RuntimeException("未选择图片文件");
        }
        Exam exam = new Exam();
        exam.setUserId(userId);
        exam.setTitle(title);
        exam.setType(type);
        exam.setStatus("processing");
        exam.setQuestionCount(0);
        exam.setDuration(duration != null && duration > 0 ? duration : 60);
        exam.setDifficulty("中等");
        examMapper.insert(exam);

        List<byte[]> bytesList = new ArrayList<>();
        List<String> nameList = new ArrayList<>();
        for (MultipartFile f : files) {
            if (f == null || f.isEmpty()) continue;
            bytesList.add(f.getBytes());
            nameList.add(f.getOriginalFilename());
        }
        if (bytesList.isEmpty()) {
            throw new RuntimeException("未选择图片文件");
        }
        asyncParseService.parseAndSaveImages(exam.getId(), bytesList, nameList, parsePrecise, extractedText, type);
        return exam;
    }

    // ─── 答案提交与判分 ────────────────────────────────────────────────────────

    /**
     * 提交答案并完成自动判分
     *
     * <p>处理流程：
     * <ol>
     *   <li>逐题比对用户答案与正确答案（写作题跳过，等待 AI 评分）</li>
     *   <li>统计得分，计算 Band 分</li>
     *   <li>错误的客观题自动写入错题本</li>
     *   <li>保存本次考试记录</li>
     * </ol>
     *
     * @return 包含 recordId、得分、Band 分和各题详情的 Map
     */
    @Transactional
    public Map<String, Object> submitAnswers(Long userId, SubmitAnswerRequest req) {
        List<Question> questions = questionMapper.findByExamId(req.getExamId());
        Map<Long, String> userAnswers = req.getAnswers();
        Exam exam = examMapper.selectById(req.getExamId());
        String examTitle = exam != null ? exam.getTitle() : "";

        int correct = 0;       // 答对题数
        int gradableTotal = 0; // 可判分题数（不含写作题）
        List<Map<String, Object>> results = new ArrayList<>();

        for (Question q : questions) {
            boolean isWrite = "write".equals(q.getType());
            String rawUserAnswer = userAnswers.getOrDefault(q.getId(), "");
            String userAnswer = rawUserAnswer.trim().toUpperCase();
            String correctAnswer = q.getAnswer().trim().toUpperCase();
            boolean isCorrect = !isWrite && userAnswer.equals(correctAnswer);

            // 写作题不计入客观得分
            if (!isWrite) {
                gradableTotal++;
                if (isCorrect) correct++;
            }

            // 封装单题结果
            Map<String, Object> qResult = new HashMap<>();
            qResult.put("id", q.getId());
            qResult.put("questionNumber", q.getQuestionNumber());
            qResult.put("questionText", q.getQuestionText());
            qResult.put("type", q.getType());
            qResult.put("userAnswer", rawUserAnswer);
            qResult.put("answer", q.getAnswer());
            qResult.put("isCorrect", isCorrect);
            qResult.put("isWrite", isWrite);
            qResult.put("explanation", q.getExplanation());
            qResult.put("locatorText", q.getLocatorText());
            qResult.put("options", q.getOptions());
            results.add(qResult);

            // 客观题答错且有作答时，自动写入错题本
            if (!isWrite && !isCorrect && !rawUserAnswer.isBlank()) {
                ErrorBook entry = new ErrorBook();
                entry.setUserId(userId);
                entry.setExamId(req.getExamId());
                entry.setQuestionId(q.getId());
                entry.setUserAnswer(rawUserAnswer);
                entry.setCorrectAnswer(q.getAnswer());
                entry.setReviewCount(0);
                entry.setMastered(0);
                errorBookMapper.insert(entry);
            }
        }

        // 根据正确率计算雅思 Band 分
        double band = calculateBand(correct, gradableTotal);

        // 保存考试记录
        ExamRecord record = new ExamRecord();
        record.setUserId(userId);
        record.setExamId(req.getExamId());
        record.setCorrectCount(correct);
        record.setTotalCount(gradableTotal);
        record.setBandScore(band);
        record.setTimeUsed(req.getTimeUsed());
        try {
            record.setAnswersJson(objectMapper.writeValueAsString(userAnswers));
        } catch (Exception e) {
            log.warn("序列化答案失败", e);
        }
        examRecordMapper.insert(record);

        // 构建返回结果
        Map<String, Object> result = new HashMap<>();
        result.put("recordId", record.getId());
        result.put("examTitle", examTitle);
        result.put("correct", correct);
        result.put("total", gradableTotal);
        result.put("band", band);
        result.put("questions", results);
        return result;
    }

    // ─── 记录查询 ──────────────────────────────────────────────────────────────

    /**
     * 获取单次考试记录详情（含 AI 写作反馈）
     *
     * @param recordId 记录 ID
     * @param userId   当前用户 ID（用于鉴权，只能查自己的记录）
     * @return 记录详情 Map；记录不存在或无权限时返回 null
     */
    public Map<String, Object> getRecordById(Long recordId, Long userId) {
        ExamRecord record = examRecordMapper.findByIdWithExam(recordId);
        if (record == null || !record.getUserId().equals(userId)) return null;

        List<Question> questions = questionMapper.findByExamId(record.getExamId());

        // 反序列化已保存的用户答案（questionId -> answer）
        Map<Long, String> savedAnswers = new HashMap<>();
        try {
            if (record.getAnswersJson() != null) {
                Map<?, ?> raw = objectMapper.readValue(record.getAnswersJson(), Map.class);
                raw.forEach((k, v) -> savedAnswers.put(Long.valueOf(k.toString()), v != null ? v.toString() : ""));
            }
        } catch (Exception e) {
            log.warn("解析答案 JSON 失败", e);
        }

        // 反序列化已保存的 AI 写作反馈（questionId -> feedbackObject）
        Map<String, Object> aiFeedbackMap = new HashMap<>();
        try {
            if (record.getAiFeedbackJson() != null) {
                Map<?, ?> raw = objectMapper.readValue(record.getAiFeedbackJson(), Map.class);
                raw.forEach((k, v) -> aiFeedbackMap.put(k.toString(), v));
            }
        } catch (Exception e) {
            log.warn("解析 AI 反馈 JSON 失败", e);
        }

        // 组装每题的结果（含 AI 评分）
        List<Map<String, Object>> qResults = new ArrayList<>();
        for (Question q : questions) {
            boolean isWrite = "write".equals(q.getType());
            String userAnswer = savedAnswers.getOrDefault(q.getId(), "");
            boolean isCorrect = !isWrite && userAnswer.trim().toUpperCase().equals(q.getAnswer().trim().toUpperCase());

            Map<String, Object> qr = new HashMap<>();
            qr.put("id", q.getId());
            qr.put("questionNumber", q.getQuestionNumber());
            qr.put("questionText", q.getQuestionText());
            qr.put("type", q.getType());
            qr.put("userAnswer", userAnswer);
            qr.put("answer", q.getAnswer());
            qr.put("isCorrect", isCorrect);
            qr.put("isWrite", isWrite);
            qr.put("explanation", q.getExplanation());
            qr.put("locatorText", q.getLocatorText());
            qr.put("options", q.getOptions());

            // 写作题附加 AI 评分结果
            if (isWrite) {
                Object feedback = aiFeedbackMap.get(q.getId().toString());
                if (feedback != null) qr.put("aiGrade", feedback);
            }
            qResults.add(qr);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("recordId", record.getId());
        result.put("examId", record.getExamId());
        result.put("examTitle", record.getExamTitle());
        result.put("examType", record.getExamType());
        result.put("correct", record.getCorrectCount());
        result.put("total", record.getTotalCount());
        result.put("band", record.getBandScore());
        result.put("timeUsed", record.getTimeUsed());
        result.put("submittedAt", record.getSubmittedAt());
        result.put("questions", qResults);
        return result;
    }

    /**
     * 保存写作题的 AI 评分反馈
     *
     * @param recordId             考试记录 ID
     * @param userId               当前用户 ID（鉴权用）
     * @param feedbackByQuestionId 各写作题的 AI 反馈（questionId -> feedbackObject）
     * @param band                 AI 给出的最终 Band 分（可为 null 表示不更新）
     * @return 保存成功返回 true，记录不存在或无权限返回 false
     */
    public boolean saveAiFeedback(Long recordId, Long userId, Map<String, Object> feedbackByQuestionId, Double band) {
        ExamRecord record = examRecordMapper.selectById(recordId);
        if (record == null || !record.getUserId().equals(userId)) return false;
        try {
            record.setAiFeedbackJson(objectMapper.writeValueAsString(feedbackByQuestionId));
            if (band != null) record.setBandScore(band);
            examRecordMapper.updateById(record);
            return true;
        } catch (Exception e) {
            log.warn("保存 AI 反馈失败", e);
            return false;
        }
    }

    // ─── 删除试卷 ──────────────────────────────────────────────────────────────

    /**
     * 级联删除试卷及其所有关联数据
     *
     * <p>删除顺序：考试记录 → 错题本条目 → 题目 → 试卷本身
     *
     * @return 删除成功返回 true；试卷不存在或无权限返回 false
     */
    @Transactional
    public boolean deleteExam(Long userId, Long examId) {
        Exam exam = examMapper.selectById(examId);
        if (exam == null || !exam.getUserId().equals(userId)) return false;

        // 1. 删除该试卷的所有考试记录
        examRecordMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ExamRecord>()
                .eq(ExamRecord::getExamId, examId));

        // 2. 删除该试卷相关的错题本条目
        errorBookMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ErrorBook>()
                .eq(ErrorBook::getExamId, examId));

        // 3. 删除该试卷的所有题目
        questionMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Question>()
                .eq(Question::getExamId, examId));

        // 4. 最后删除试卷本身
        examMapper.deleteById(examId);

        log.info("已删除试卷 {} 及其所有关联数据，操作用户: {}", examId, userId);
        return true;
    }

    // ─── 历史记录 / 错题本 ─────────────────────────────────────────────────────

    /** 获取用户所有的考试历史记录 */
    public List<ExamRecord> getUserHistory(Long userId) {
        return examRecordMapper.findByUserId(userId);
    }

    /** 获取用户未掌握的错题列表 */
    public List<ErrorBook> getUserErrorBook(Long userId) {
        return errorBookMapper.findUnmasteredByUserId(userId);
    }

    // ─── Band 分计算 ───────────────────────────────────────────────────────────

    /**
     * 根据正确题数与总题数计算雅思 Band 分
     *
     * <p>换算标准参考雅思官方评分表（阅读部分）：
     * <pre>
     * 正确率 ≥ 92.5% → Band 9.0
     * 正确率 ≥ 87.5% → Band 8.5
     * ...
     * 正确率 &lt; 32.5% → Band 4.0
     * </pre>
     */
    private double calculateBand(int correct, int total) {
        if (total == 0) return 0;
        double ratio = (double) correct / total;
        if (ratio >= 0.925) return 9.0;
        if (ratio >= 0.875) return 8.5;
        if (ratio >= 0.825) return 8.0;
        if (ratio >= 0.775) return 7.5;
        if (ratio >= 0.700) return 7.0;
        if (ratio >= 0.625) return 6.5;
        if (ratio >= 0.550) return 6.0;
        if (ratio >= 0.475) return 5.5;
        if (ratio >= 0.400) return 5.0;
        if (ratio >= 0.325) return 4.5;
        return 4.0;
    }
}
