package com.richzds.quiz.exam;

import java.util.List;

import com.richzds.quiz.domain.FillOptions;
import com.richzds.quiz.domain.OptionItem;

/**
 * 开考时存进 exam_record.question_snapshot 的快照：试卷标题，以及每道题的内容、标准答案和分值（index 从 1 开始）。
 * 之后改题不影响这场考试的判分和成绩单。
 */
public record ExamSnapshot(String title, List<Item> questions) {

    public record Item(int index, Long questionId, Long categoryId, String categoryPath, String type,
                       String stem, List<OptionItem> options, int blankCount, String imageUrl, int score,
                       List<String> correctAnswer, List<List<String>> acceptedAnswers, FillOptions fillOptions,
                       String analysis) {
    }
}
