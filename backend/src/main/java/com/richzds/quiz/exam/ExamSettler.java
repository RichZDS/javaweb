package com.richzds.quiz.exam;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.richzds.quiz.common.JsonUtil;
import com.richzds.quiz.common.TimeUtil;
import com.richzds.quiz.domain.AnswerKey;
import com.richzds.quiz.domain.QuestionType;
import com.richzds.quiz.entity.AnswerRecord;
import com.richzds.quiz.entity.ExamAnswer;
import com.richzds.quiz.entity.ExamPaper;
import com.richzds.quiz.entity.ExamRecord;
import com.richzds.quiz.mapper.AnswerRecordMapper;
import com.richzds.quiz.mapper.ExamAnswerMapper;
import com.richzds.quiz.mapper.ExamPaperMapper;
import com.richzds.quiz.mapper.ExamRecordMapper;
import com.richzds.quiz.service.CategoryService;
import com.richzds.quiz.service.CategoryTree;
import com.richzds.quiz.service.GradingService;
import com.richzds.quiz.service.PracticeService;
import com.richzds.quiz.service.WrongBookService;

/**
 * 考试结算：按快照里的标准答案判分，写每题得分、作答记录（来源 EXAM）和错题本，最后写总分。
 *
 * <p>只结算一次：先用 {@code UPDATE ... WHERE status = 'IN_PROGRESS'} 抢到这条记录，抢不到说明已被
 * 手动交卷、定时任务或另一个请求结算过，直接返回。保存答案时同样先锁这一行，所以结算读到的就是截止前收到的全部答案。
 */
@Component
public class ExamSettler {

    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    public static final String STATUS_FINISHED = "FINISHED";
    public static final String REASON_MANUAL = "MANUAL";
    public static final String REASON_TIMEOUT = "TIMEOUT";

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() { };

    private final ExamRecordMapper recordMapper;
    private final ExamAnswerMapper answerMapper;
    private final ExamPaperMapper paperMapper;
    private final AnswerRecordMapper answerRecordMapper;
    private final GradingService gradingService;
    private final WrongBookService wrongBookService;
    private final CategoryService categoryService;
    private final Clock clock;

    public ExamSettler(ExamRecordMapper recordMapper, ExamAnswerMapper answerMapper, ExamPaperMapper paperMapper,
                       AnswerRecordMapper answerRecordMapper, GradingService gradingService,
                       WrongBookService wrongBookService, CategoryService categoryService, Clock clock) {
        this.recordMapper = recordMapper;
        this.answerMapper = answerMapper;
        this.paperMapper = paperMapper;
        this.answerRecordMapper = answerRecordMapper;
        this.gradingService = gradingService;
        this.wrongBookService = wrongBookService;
        this.categoryService = categoryService;
        this.clock = clock;
    }

    /**
     * 结算一场考试。已过截止时间的一律记为 TIMEOUT，交卷时间记为截止时间；否则按 reason（手动交卷）。
     *
     * @return 这次调用是否真的做了结算（false 表示早已结算）
     */
    @Transactional
    public boolean settle(long recordId, String reason) {
        ExamRecord record = recordMapper.selectById(recordId);
        if (record == null || !STATUS_IN_PROGRESS.equals(record.getStatus())) {
            return false;
        }
        LocalDateTime now = TimeUtil.nowUtc(clock);
        boolean expired = !now.isBefore(record.getDeadlineAt());
        String finalReason = expired ? REASON_TIMEOUT : reason;
        LocalDateTime submittedAt = expired ? record.getDeadlineAt() : now;
        int claimed = recordMapper.update(null, new LambdaUpdateWrapper<ExamRecord>()
                .eq(ExamRecord::getId, recordId)
                .eq(ExamRecord::getStatus, STATUS_IN_PROGRESS)
                .set(ExamRecord::getStatus, STATUS_FINISHED)
                .set(ExamRecord::getFinishReason, finalReason)
                .set(ExamRecord::getSubmittedAt, submittedAt));
        if (claimed == 0) {
            return false;
        }

        ExamSnapshot snapshot = JsonUtil.read(record.getQuestionSnapshot(), ExamSnapshot.class);
        Map<Long, ExamAnswer> answers = new HashMap<>();
        // 加锁读：MySQL 可重复读下普通 SELECT 用的是事务开始时的快照，可能漏掉刚提交的最后一题答案
        for (ExamAnswer a : answerMapper.selectList(new LambdaQueryWrapper<ExamAnswer>()
                .eq(ExamAnswer::getRecordId, recordId).last("FOR UPDATE"))) {
            answers.put(a.getQuestionId(), a);
        }
        ExamPaper paper = paperMapper.selectById(record.getPaperId());
        CategoryTree tree = categoryService.tree();
        int total = 0;
        for (ExamSnapshot.Item item : snapshot.questions()) {
            ExamAnswer saved = answers.get(item.questionId());
            List<String> given = saved == null ? List.of() : JsonUtil.read(saved.getUserAnswer(), STRING_LIST);
            boolean answered = isAnswered(given);
            boolean correct = answered && gradingService.grade(keyOf(item), given);
            int score = correct ? item.score() : 0;
            total += score;
            if (saved != null) {
                answerMapper.update(null, new LambdaUpdateWrapper<ExamAnswer>()
                        .eq(ExamAnswer::getId, saved.getId())
                        .set(ExamAnswer::getIsCorrect, correct)
                        .set(ExamAnswer::getScore, score));
            }
            Long rootId = tree.rootOf(item.categoryId());
            if (rootId == null) {
                rootId = paper != null ? paper.getCategoryId() : 0L;
            }
            try {
                answerRecordMapper.insert(new AnswerRecord()
                        .setUserId(record.getUserId())
                        .setQuestionId(item.questionId())
                        .setSource(PracticeService.SOURCE_EXAM)
                        .setSourceId(recordId)
                        .setUserAnswer(JsonUtil.write(given == null ? List.of() : given))
                        .setAnswered(answered)
                        .setIsCorrect(correct)
                        .setCategoryIdSnapshot(item.categoryId())
                        .setRootCategoryId(rootId)
                        .setGradedAt(submittedAt));
            } catch (DuplicateKeyException e) {
                // 理论上抢占已保证只结算一次；唯一索引兜底，不重复记
            }
            // 答错的进错题本；没答的不进
            if (answered) {
                wrongBookService.onGraded(record.getUserId(), item.questionId(), correct, given, false);
            }
        }
        recordMapper.update(null, new LambdaUpdateWrapper<ExamRecord>()
                .eq(ExamRecord::getId, recordId)
                .set(ExamRecord::getScore, total)
                .set(ExamRecord::getPassed, total >= record.getPassScore()));
        return true;
    }

    /**
     * 已过截止时间但还没结算的考试 id；userId 为 null 时查全部。
     * 调用方逐个调 {@link #settle}（经代理，每条一个事务）。
     */
    public List<Long> expiredIds(Long userId) {
        return recordMapper.selectList(new LambdaQueryWrapper<ExamRecord>()
                        .select(ExamRecord::getId)
                        .eq(userId != null, ExamRecord::getUserId, userId)
                        .eq(ExamRecord::getStatus, STATUS_IN_PROGRESS)
                        .le(ExamRecord::getDeadlineAt, TimeUtil.nowUtc(clock))
                        .orderByAsc(ExamRecord::getDeadlineAt))
                .stream().map(ExamRecord::getId).toList();
    }

    public static AnswerKey keyOf(ExamSnapshot.Item item) {
        QuestionType type = QuestionType.valueOf(item.type());
        return new AnswerKey(type, item.correctAnswer(), item.acceptedAnswers(), item.fillOptions());
    }

    /** 选择题至少选了一项、填空题至少填了一空，才算作答。 */
    static boolean isAnswered(List<String> answer) {
        return answer != null && answer.stream().anyMatch(s -> s != null && !s.isBlank());
    }
}
