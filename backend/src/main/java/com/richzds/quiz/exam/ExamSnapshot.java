package com.richzds.quiz.exam;

import java.util.List;

import com.richzds.quiz.domain.FillOptions;
import com.richzds.quiz.domain.OptionItem;

/**
 * 开考时存进 exam_record.question_snapshot 的题目快照：题目内容、标准答案和分值。
 * 之后改题不影响这场考试的判分和成绩单。
 */
public record ExamSnapshot(List<Item> questions) {

    public record Item(int index, Long questionId, Long categoryId, String categoryPath, String type,
                       String stem, List<OptionItem> options, int blankCount, String imageUrl, int score,
                       List<String> correctAnswer, List<List<String>> acceptedAnswers, FillOptions fillOptions,
                       String analysis) {
    }
}
