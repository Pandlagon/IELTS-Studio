package com.ieltsstudio.service;

import com.ieltsstudio.entity.ExamCollection;
import com.ieltsstudio.entity.ExamCollectionItem;
import com.ieltsstudio.mapper.ExamCollectionMapper;
import com.ieltsstudio.mapper.ExamCollectionItemMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ExamCollectionService {

    private final ExamCollectionMapper collectionMapper;
    private final ExamCollectionItemMapper itemMapper;

    // ── Collection CRUD ─────────────────────────────────────────────────

    public List<ExamCollection> getUserCollections(Long userId) {
        return collectionMapper.findByUserId(userId);
    }

    public ExamCollection getById(Long id) {
        return collectionMapper.selectById(id);
    }

    public ExamCollection createCollection(Long userId, String title, String description) {
        ExamCollection c = new ExamCollection();
        c.setUserId(userId);
        c.setTitle(title);
        c.setDescription(description);
        c.setDuration(0);
        collectionMapper.insert(c);
        return c;
    }

    public ExamCollection updateCollection(Long userId, Long id, String title, String description) {
        ExamCollection c = collectionMapper.selectById(id);
        if (c == null || !c.getUserId().equals(userId)) return null;
        if (title != null) c.setTitle(title);
        if (description != null) c.setDescription(description);
        collectionMapper.updateById(c);
        return c;
    }

    @Transactional
    public boolean deleteCollection(Long userId, Long id) {
        ExamCollection c = collectionMapper.selectById(id);
        if (c == null || !c.getUserId().equals(userId)) return false;
        itemMapper.removeAllByCollection(id);
        collectionMapper.deleteById(id);
        return true;
    }

    // ── Item management ─────────────────────────────────────────────────

    public List<ExamCollectionItem> getItems(Long collectionId) {
        return itemMapper.findByCollectionId(collectionId);
    }

    public Map<String, Object> getCollectionDetail(Long collectionId) {
        ExamCollection c = collectionMapper.selectById(collectionId);
        if (c == null) return null;
        List<ExamCollectionItem> items = itemMapper.findByCollectionId(collectionId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("collection", c);
        result.put("items", items);
        return result;
    }

    public ExamCollectionItem addExam(Long collectionId, Long examId) {
        // Check for duplicates
        List<ExamCollectionItem> existing = itemMapper.findByCollectionId(collectionId);
        boolean alreadyExists = existing.stream().anyMatch(i -> i.getExamId().equals(examId));
        if (alreadyExists) return null;

        int maxOrder = itemMapper.getMaxSortOrder(collectionId);
        ExamCollectionItem item = new ExamCollectionItem();
        item.setCollectionId(collectionId);
        item.setExamId(examId);
        item.setSortOrder(maxOrder + 1);
        itemMapper.insert(item);
        return item;
    }

    public boolean removeExam(Long collectionId, Long examId) {
        return itemMapper.removeByCollectionAndExam(collectionId, examId) > 0;
    }

    @Transactional
    public void reorderItems(Long collectionId, List<Long> examIds) {
        for (int i = 0; i < examIds.size(); i++) {
            List<ExamCollectionItem> items = itemMapper.findByCollectionId(collectionId);
            for (ExamCollectionItem item : items) {
                if (item.getExamId().equals(examIds.get(i))) {
                    item.setSortOrder(i);
                    itemMapper.updateById(item);
                    break;
                }
            }
        }
    }
}
