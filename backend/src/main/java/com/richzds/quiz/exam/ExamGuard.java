package com.richzds.quiz.exam;

import java.time.Clock;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Component;

import com.richzds.quiz.common.BizException;
import com.richzds.quiz.common.ErrorCode;
import com.richzds.quiz.common.JsonUtil;
import com.richzds.quiz.common.TimeUtil;
import com.richzds.quiz.entity.ExamRecord;
import com.richzds.quiz.mapper.ExamRecordMapper;

/** 考试进行中（未到截止时间）时，不允许查看考卷里题目的答案。 */
@Component
public class ExamGuard {

    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";

    private final ExamRecordMapper examRecordMapper;
    private final Clock clock;

    public ExamGuard(ExamRecordMapper examRecordMapper, Clock clock) {
        this.examRecordMapper = examRecordMapper;
        this.clock = clock;
    }

    public void assertNotInProgressExam(long userId, long questionId) {
        List<ExamRecord> running = examRecordMapper.selectList(new LambdaQueryWrapper<ExamRecord>()
                .select(ExamRecord::getId, ExamRecord::getQuestionSnapshot)
                .eq(ExamRecord::getUserId, userId)
                .eq(ExamRecord::getStatus, STATUS_IN_PROGRESS)
                .gt(ExamRecord::getDeadlineAt, TimeUtil.nowUtc(clock)));
        for (ExamRecord record : running) {
            ExamSnapshot snapshot = JsonUtil.read(record.getQuestionSnapshot(), ExamSnapshot.class);
            if (snapshot != null && snapshot.questions().stream()
                    .anyMatch(item -> item.questionId() != null && item.questionId() == questionId)) {
                throw new BizException(ErrorCode.EXAM_IN_PROGRESS);
            }
        }
    }
}
