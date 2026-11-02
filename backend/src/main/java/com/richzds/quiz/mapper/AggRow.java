package com.richzds.quiz.mapper;

import java.time.LocalDate;

/**
 * 聚合查询的通用结果行。SQL 里用下划线别名（group_id、stat_day、total_count…），按驼峰自动映射；
 * 别名匹配不区分大小写，所以 H2（小写标识符）和 MySQL 都能映射上。
 */
public class AggRow {

    private Long groupId;
    private LocalDate statDay;
    private Long totalCount;
    private Long distinctCount;
    private Long correctCount;
    private Long scoreSum;
    private Long passedCount;

    public Long getGroupId() {
        return groupId;
    }

    public void setGroupId(Long groupId) {
        this.groupId = groupId;
    }

    public LocalDate getStatDay() {
        return statDay;
    }

    public void setStatDay(LocalDate statDay) {
        this.statDay = statDay;
    }

    public long total() {
        return totalCount == null ? 0 : totalCount;
    }

    public Long getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(Long totalCount) {
        this.totalCount = totalCount;
    }

    public long distinct() {
        return distinctCount == null ? 0 : distinctCount;
    }

    public Long getDistinctCount() {
        return distinctCount;
    }

    public void setDistinctCount(Long distinctCount) {
        this.distinctCount = distinctCount;
    }

    public long correct() {
        return correctCount == null ? 0 : correctCount;
    }

    public Long getCorrectCount() {
        return correctCount;
    }

    public void setCorrectCount(Long correctCount) {
        this.correctCount = correctCount;
    }

    public long scores() {
        return scoreSum == null ? 0 : scoreSum;
    }

    public Long getScoreSum() {
        return scoreSum;
    }

    public void setScoreSum(Long scoreSum) {
        this.scoreSum = scoreSum;
    }

    public long passed() {
        return passedCount == null ? 0 : passedCount;
    }

    public Long getPassedCount() {
        return passedCount;
    }

    public void setPassedCount(Long passedCount) {
        this.passedCount = passedCount;
    }
}
