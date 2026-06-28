package com.ieltsstudio.service;

import com.ieltsstudio.entity.WordBook;
import com.ieltsstudio.entity.WordEntry;
import com.ieltsstudio.infra.RedisOps;
import com.ieltsstudio.mapper.WordBookMapper;
import com.ieltsstudio.mapper.WordEntryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncWordService {

    private final WordBookMapper wordBookMapper;
    private final WordEntryMapper wordEntryMapper;
    private final FileParseService fileParseService;
    private final AiParseService aiParseService;
    private final RedisOps redisOps;
    private final CacheManager cacheManager;

    private static final int MAX_WORDS_PER_UPLOAD = 30;
    private static final Duration INGEST_PROGRESS_TTL = Duration.ofMinutes(60);

    @Async("asyncParseExecutor")
    public void processWordFile(Long userId, Long bookId, byte[] fileBytes, String filename) {
        WordBook book = wordBookMapper.selectById(bookId);
        if (book == null || !book.getUserId().equals(userId)) return;

        book.setStatus("processing");
        wordBookMapper.updateById(book);
        writeProgress(bookId, 0, 0, 0, "processing", null);

        int total = 0;
        int saved = 0;
        int skipped = 0;
        Set<String> existing = loadExistingWords(bookId);
        warmDedupeSet(bookId, existing);

        try {
            String text = fileParseService.extractTextFromBytes(fileBytes, filename);
            if (text == null || text.trim().isEmpty()) {
                markFailed(book, "文件内容为空");
                writeProgress(bookId, total, saved, skipped, "failed", "文件内容为空");
                return;
            }

            List<Map<String, Object>> aiEntries = aiParseService.generateWordEntries(userId, text);
            if (aiEntries == null || aiEntries.isEmpty()) {
                markFailed(book, "AI未识别出词汇");
                writeProgress(bookId, total, saved, skipped, "failed", "AI未识别出词汇");
                return;
            }

            List<Map<String, Object>> limited = aiEntries.size() > MAX_WORDS_PER_UPLOAD
                    ? aiEntries.subList(0, MAX_WORDS_PER_UPLOAD)
                    : aiEntries;

            total = limited.size();
            writeProgress(bookId, total, 0, 0, "processing", null);

            Set<String> seenInBatch = new HashSet<>();

            for (Map<String, Object> e : limited) {
                try {
                    String word = str(e, "word");
                    String meaning = str(e, "meaning");
                    if (word == null || word.isBlank() || meaning == null || meaning.isBlank()) {
                        skipped++;
                        continue;
                    }
                    String key = word.toLowerCase();
                    if (isDuplicate(bookId, key, existing, seenInBatch)) {
                        log.debug("Skip duplicate word '{}' for book {}", word, bookId);
                        skipped++;
                        continue;
                    }
                    WordEntry entry = new WordEntry();
                    entry.setBookId(bookId);
                    entry.setUserId(userId);
                    entry.setWord(word);
                    entry.setPhonetic(str(e, "phonetic"));
                    entry.setPos(str(e, "pos"));
                    entry.setPosType(str(e, "posType"));
                    entry.setMeaning(meaning);
                    entry.setExample(str(e, "example"));
                    entry.setExampleTranslation(str(e, "exampleTranslation"));
                    entry.setRootMemory(str(e, "rootMemory"));
                    wordEntryMapper.insert(entry);
                    seenInBatch.add(key);
                    existing.add(key);
                    redisOps.sadd(dedupeSetKey(bookId), key);
                    saved++;
                } catch (Exception ex) {
                    log.warn("Failed to save word entry: {}", e, ex);
                }
            }

            int totalCount = wordEntryMapper.countByBookId(bookId);
            book.setWordCount(totalCount);
            book.setStatus("ready");
            wordBookMapper.updateById(book);
            evictWordCaches(userId, bookId);
            writeProgress(bookId, total, saved, skipped, "ready", null);
            log.info("Async word processing done for book {}: {} entries saved, {} skipped", bookId, saved, skipped);

        } catch (Exception ex) {
            log.error("Async word processing failed for book {}", bookId, ex);
            markFailed(book, ex.getMessage());
            writeProgress(bookId, total, saved, skipped, "failed", ex.getMessage());
        }
    }

    @Async("asyncParseExecutor")
    public void quickAddWords(Long userId, Long bookId, List<String> rawWords) {
        if (rawWords == null || rawWords.isEmpty()) return;
        log.info("Quick-add {} words to book {} for user {}", rawWords.size(), bookId, userId);
        writeProgress(bookId, rawWords.size(), 0, 0, "processing", null);
        try {
            String input = String.join(" ", rawWords);
            List<Map<String, Object>> aiEntries = aiParseService.generateWordEntries(userId, input);
            if (aiEntries == null || aiEntries.isEmpty()) {
                log.warn("AI returned no entries for quick-add words: {}", rawWords);
                writeProgress(bookId, rawWords.size(), 0, rawWords.size(), "failed", "AI未返回词条");
                return;
            }
            var existing = loadExistingWords(bookId);
            warmDedupeSet(bookId, existing);
            var seenInBatch = new HashSet<String>();

            int saved = 0;
            int skipped = 0;
            int total = aiEntries.size();

            for (Map<String, Object> e : aiEntries) {
                try {
                    String word = str(e, "word");
                    String meaning = str(e, "meaning");
                    if (word == null || word.isBlank() || meaning == null || meaning.isBlank()) {
                        log.warn("Skip invalid quick-add entry, missing word or meaning: {}", e);
                        skipped++;
                        continue;
                    }
                    String key = word.toLowerCase();
                    if (isDuplicate(bookId, key, existing, seenInBatch)) {
                        log.debug("Skip duplicate word '{}' for book {} (quick-add)", word, bookId);
                        skipped++;
                        continue;
                    }
                    WordEntry entry = new WordEntry();
                    entry.setBookId(bookId);
                    entry.setUserId(userId);
                    entry.setWord(word);
                    entry.setPhonetic(str(e, "phonetic"));
                    entry.setPos(str(e, "pos"));
                    entry.setPosType(str(e, "posType"));
                    entry.setMeaning(meaning);
                    entry.setExample(str(e, "example"));
                    entry.setExampleTranslation(str(e, "exampleTranslation"));
                    entry.setRootMemory(str(e, "rootMemory"));
                    wordEntryMapper.insert(entry);
                    seenInBatch.add(key);
                    existing.add(key);
                    redisOps.sadd(dedupeSetKey(bookId), key);
                    saved++;
                } catch (Exception ex) {
                    log.error("Failed to quick-add word entry: {}", e, ex);
                }
            }
            WordBook book = wordBookMapper.selectById(bookId);
            if (book != null) {
                book.setWordCount(wordEntryMapper.countByBookId(bookId));
                wordBookMapper.updateById(book);
            }
            evictWordCaches(userId, bookId);
            writeProgress(bookId, total, saved, skipped, "ready", null);
            log.info("Quick-add done: {} entries saved to book {}", saved, bookId);
        } catch (Exception ex) {
            log.error("Quick-add words failed for user {}", userId, ex);
            writeProgress(bookId, rawWords.size(), 0, 0, "failed", ex.getMessage());
        }
    }

    private void markFailed(WordBook book, String reason) {
        log.warn("Word processing failed for book {}: {}", book.getId(), reason);
        book.setStatus("failed");
        wordBookMapper.updateById(book);
    }

    private Set<String> loadExistingWords(Long bookId) {
        var existing = new HashSet<String>();
        try {
            List<String> existedWords = wordEntryMapper.findWordsByBookId(bookId);
            if (existedWords != null) existedWords.forEach(w -> {
                if (w != null) existing.add(w.toLowerCase());
            });
        } catch (Exception ignore) {
        }
        return existing;
    }

    private void warmDedupeSet(Long bookId, Set<String> existing) {
        if (existing.isEmpty()) return;
        redisOps.sadd(dedupeSetKey(bookId), existing.toArray(new String[0]));
    }

    private boolean isDuplicate(Long bookId, String key, Set<String> existing, Set<String> seenInBatch) {
        return existing.contains(key)
                || seenInBatch.contains(key)
                || Boolean.TRUE.equals(redisOps.sismember(dedupeSetKey(bookId), key));
    }

    private String dedupeSetKey(Long bookId) {
        return "ielts:book:" + bookId + ":wordset";
    }

    private String progressKey(Long bookId) {
        return "ielts:ingest:progress:" + bookId;
    }

    private void writeProgress(Long bookId, int total, int saved, int skipped, String status, String error) {
        Map<String, Object> data = new HashMap<>();
        data.put("total", total);
        data.put("saved", saved);
        data.put("skipped", skipped);
        data.put("status", status);
        if (error != null && !error.isBlank()) {
            data.put("error", error);
        }
        redisOps.hsetAll(progressKey(bookId), data);
        redisOps.expire(progressKey(bookId), INGEST_PROGRESS_TTL);
    }

    public Map<String, Object> getIngestProgress(Long userId, Long bookId) {
        WordBook book = wordBookMapper.selectById(bookId);
        if (book == null || !book.getUserId().equals(userId)) return Map.of();
        Map<String, Object> data = redisOps.hgetAll(progressKey(bookId));
        if (data == null || data.isEmpty()) return Map.of();
        data.put("bookId", bookId);
        return data;
    }

    private void evictWordCaches(Long userId, Long bookId) {
        Cache entries = cacheManager.getCache("entries");
        if (entries != null) entries.evict(userId + ":" + bookId);
        Cache books = cacheManager.getCache("books");
        if (books != null) books.evict(userId);
    }

    private String str(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v == null ? null : v.toString().trim();
    }
}
