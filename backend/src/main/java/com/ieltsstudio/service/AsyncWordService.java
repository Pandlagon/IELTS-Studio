package com.ieltsstudio.service;

import com.ieltsstudio.entity.WordBook;
import com.ieltsstudio.entity.WordEntry;
import com.ieltsstudio.mapper.WordBookMapper;
import com.ieltsstudio.mapper.WordEntryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncWordService {

    private final WordBookMapper wordBookMapper;
    private final WordEntryMapper wordEntryMapper;
    private final FileParseService fileParseService;
    private final AiParseService aiParseService;

    private static final int MAX_WORDS_PER_UPLOAD = 30;

    @Async
    public void processWordFile(Long userId, Long bookId, byte[] fileBytes, String filename) {
        WordBook book = wordBookMapper.selectById(bookId);
        if (book == null || !book.getUserId().equals(userId)) return;

        // Mark as processing
        book.setStatus("processing");
        wordBookMapper.updateById(book);

        try {
            String text = fileParseService.extractTextFromBytes(fileBytes, filename);
            if (text == null || text.trim().isEmpty()) {
                markFailed(book, "文件内容为空");
                return;
            }

            List<Map<String, Object>> aiEntries = aiParseService.generateWordEntries(text);
            if (aiEntries == null || aiEntries.isEmpty()) {
                markFailed(book, "AI未识别出词汇");
                return;
            }

            List<Map<String, Object>> limited = aiEntries.size() > MAX_WORDS_PER_UPLOAD
                    ? aiEntries.subList(0, MAX_WORDS_PER_UPLOAD)
                    : aiEntries;

            int saved = 0;
            for (Map<String, Object> e : limited) {
                try {
                    String word = str(e, "word");
                    String meaning = str(e, "meaning");
                    if (word == null || word.isBlank() || meaning == null || meaning.isBlank()) continue;
                    WordEntry entry = new WordEntry();
                    entry.setBookId(bookId);
                    entry.setUserId(userId);
                    entry.setWord(word);
                    entry.setPhonetic(str(e, "phonetic"));
                    entry.setPos(str(e, "pos"));
                    entry.setPosType(str(e, "posType"));
                    entry.setMeaning(meaning);
                    entry.setExample(str(e, "example"));
                    wordEntryMapper.insert(entry);
                    saved++;
                } catch (Exception ex) {
                    log.warn("Failed to save word entry: {}", e, ex);
                }
            }

            // Refresh count and mark ready
            int total = wordEntryMapper.countByBookId(bookId);
            book.setWordCount(total);
            book.setStatus("ready");
            wordBookMapper.updateById(book);
            log.info("Async word processing done for book {}: {} entries saved", bookId, saved);

        } catch (Exception ex) {
            log.error("Async word processing failed for book {}", bookId, ex);
            markFailed(book, ex.getMessage());
        }
    }

    @Async
    public void quickAddWords(Long userId, Long bookId, List<String> rawWords) {
        if (rawWords == null || rawWords.isEmpty()) return;
        log.info("Quick-add {} words to book {} for user {}", rawWords.size(), bookId, userId);
        try {
            String input = String.join(" ", rawWords);
            List<Map<String, Object>> aiEntries = aiParseService.generateWordEntries(input);
            if (aiEntries == null || aiEntries.isEmpty()) {
                log.warn("AI returned no entries for quick-add words: {}", rawWords);
                return;
            }
            int saved = 0;
            for (Map<String, Object> e : aiEntries) {
                try {
                    String word = str(e, "word");
                    String meaning = str(e, "meaning");
                    if (word == null || word.isBlank() || meaning == null || meaning.isBlank()) continue;
                    WordEntry entry = new WordEntry();
                    entry.setBookId(bookId);
                    entry.setUserId(userId);
                    entry.setWord(word);
                    entry.setPhonetic(str(e, "phonetic"));
                    entry.setPos(str(e, "pos"));
                    entry.setPosType(str(e, "posType"));
                    entry.setMeaning(meaning);
                    entry.setExample(str(e, "example"));
                    wordEntryMapper.insert(entry);
                    saved++;
                } catch (Exception ex) {
                    log.warn("Failed to quick-add word entry: {}", e, ex);
                }
            }
            WordBook book = wordBookMapper.selectById(bookId);
            if (book != null) {
                book.setWordCount(wordEntryMapper.countByBookId(bookId));
                wordBookMapper.updateById(book);
            }
            log.info("Quick-add done: {} entries saved to book {}", saved, bookId);
        } catch (Exception ex) {
            log.error("Quick-add words failed for user {}", userId, ex);
        }
    }

    private void markFailed(WordBook book, String reason) {
        log.warn("Word processing failed for book {}: {}", book.getId(), reason);
        book.setStatus("failed");
        wordBookMapper.updateById(book);
    }

    private String str(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v == null ? null : v.toString().trim();
    }
}
