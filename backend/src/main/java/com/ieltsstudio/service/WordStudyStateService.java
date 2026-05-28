package com.ieltsstudio.service;

import com.ieltsstudio.entity.WordStudyState;
import com.ieltsstudio.mapper.WordStudyStateMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class WordStudyStateService {

    private final WordStudyStateMapper wordStudyStateMapper;

    public WordStudyState getState(Long userId, String bookId) {
        return wordStudyStateMapper.findByUserIdAndBookId(userId, bookId);
    }

    public WordStudyState saveState(Long userId, String bookId, Map<String, Object> body) {
        WordStudyState state = wordStudyStateMapper.findByUserIdAndBookId(userId, bookId);
        if (state == null) {
            state = new WordStudyState();
            state.setUserId(userId);
            state.setBookId(bookId);
        }
        state.setKnownIds(str(body.get("knownIds"), "[]"));
        state.setUnknownIds(str(body.get("unknownIds"), "[]"));
        state.setReviewStates(str(body.get("reviewStates"), "{}"));
        state.setErrorCounts(str(body.get("errorCounts"), "{}"));
        state.setSortMode(str(body.get("sortMode"), "order"));
        state.setBatchSize(number(body.get("batchSize")));
        state.setBatchIndex(number(body.get("batchIndex")));
        state.setCurrentIndex(number(body.get("currentIndex")));

        if (state.getId() == null) wordStudyStateMapper.insert(state);
        else wordStudyStateMapper.updateById(state);
        return state;
    }

    private String str(Object value, String fallback) {
        if (value == null) return fallback;
        return value.toString();
    }

    private Integer number(Object value) {
        if (value instanceof Number n) return n.intValue();
        if (value == null) return 0;
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}
