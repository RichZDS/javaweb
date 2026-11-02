package com.richzds.quiz.exam;

import java.util.List;

import com.richzds.quiz.domain.FillOptions;
import com.richzds.quiz.domain.OptionItem;
import com.richzds.quiz.domain.OptionShuffle;

/**
 * 开考时存进 exam_record.question_snapshot 的快照：试卷标题，以及每道题的内容、标准答案和分值（index 从 1 开始）。
 * 之后改题不影响这场考试的判分和成绩单。
 */
public record ExamSnapshot(String title, boolean shuffleOptions, List<Item> questions) {

    /**
     * 一道题。options、correctAnswer 都是原标签；optionOrder 是这场考试里的显示顺序（按显示顺序排的原标签），
     * 没打乱时为 null（旧快照里没有这个字段，读出来也是 null）。
     */
    public record Item(int index, Long questionId, Long categoryId, String categoryPath, String type,
                       String stem, List<OptionItem> options, int blankCount, String imageUrl, int score,
                       List<String> correctAnswer, List<List<String>> acceptedAnswers, FillOptions fillOptions,
                       String analysis, List<String> optionOrder) {

        /** 这道题在这场考试里的选项映射，没打乱时为 null。 */
        public OptionShuffle shuffle() {
            return OptionShuffle.of(optionOrder, options);
        }
    }
}
