package com.richzds.quiz.exam;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.richzds.quiz.common.BizException;
import com.richzds.quiz.common.ErrorCode;
import com.richzds.quiz.common.JsonUtil;
import com.richzds.quiz.common.PageQuery;
import com.richzds.quiz.common.PageResult;
import com.richzds.quiz.common.Rates;
import com.richzds.quiz.common.TimeUtil;
import com.richzds.quiz.domain.AnswerKey;
import com.richzds.quiz.domain.OptionItem;
import com.richzds.quiz.domain.OptionShuffle;
import com.richzds.quiz.domain.QuestionType;
import com.richzds.quiz.dto.ExamDtos.CategoryScore;
import com.richzds.quiz.dto.ExamDtos.ExamResult;
import com.richzds.quiz.dto.ExamDtos.ExamSheet;
import com.richzds.quiz.dto.ExamDtos.Gap;
import com.richzds.quiz.dto.ExamDtos.PaperDetail;
import com.richzds.quiz.dto.ExamDtos.PaperItem;
import com.richzds.quiz.dto.ExamDtos.RecordItem;
import com.richzds.quiz.dto.ExamDtos.ResultItem;
import com.richzds.quiz.dto.ExamDtos.SaveAnswerRequest;
import com.richzds.quiz.dto.ExamDtos.SaveAnswerResult;
import com.richzds.quiz.dto.ExamDtos.SavedAnswer;
import com.richzds.quiz.dto.ExamDtos.SheetQuestion;
import com.richzds.quiz.dto.ExamDtos.TypeScore;
import com.richzds.quiz.entity.ExamAnswer;
import com.richzds.quiz.entity.ExamPaper;
import com.richzds.quiz.entity.ExamRecord;
import com.richzds.quiz.entity.PaperQuestion;
import com.richzds.quiz.entity.PaperRule;
import com.richzds.quiz.entity.Question;
import com.richzds.quiz.entity.User;
import com.richzds.quiz.mapper.ExamAnswerMapper;
import com.richzds.quiz.mapper.ExamPaperMapper;
import com.richzds.quiz.mapper.ExamRecordMapper;
import com.richzds.quiz.mapper.PaperQuestionMapper;
import com.richzds.quiz.mapper.PaperRuleMapper;
import com.richzds.quiz.mapper.QuestionMapper;
import com.richzds.quiz.mapper.UserMapper;
import com.richzds.quiz.service.CategoryService;
import com.richzds.quiz.service.CategoryTree;
import com.richzds.quiz.service.GradingService;
import com.richzds.quiz.service.QuestionAssembler;
import com.richzds.quiz.service.QuestionService;
import com.richzds.quiz.service.UserService;

/**
 * 模拟考试（docs/api.md 第 6 节）：试卷列表和须知、开考（存快照）、答案带版本保存、交卷、成绩单、考试记录。
 * 判分和结算在 {@link ExamSettler}；到时自动结算在 {@link ExamSettlementJob}，访问时发现过期也会立即结算。
 */
@Service
public class ExamService {

    public static final String PAPER_ONLINE = "ONLINE";
    public static final String MODE_FIXED = "FIXED";
    public static final String MODE_RULE = "RULE";

    /** 考前须知里的判分规则说明。 */
    public static final String SCORING_RULES = String.join("\n",
            "单选题、判断题：选对得分，选错或不选不得分。",
            "多选题：所选选项与正确答案完全一致才得分，少选、多选、错选均不得分。",
            "填空题：每一空都填对才得分；是否区分大小写和空格以题目设置为准。",
            "未作答的题记 0 分，不进错题本；答错的题自动进错题本。",
            "到截止时间自动交卷，只按截止前保存成功的答案判分。");

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() { };

    private final ExamPaperMapper paperMapper;
    private final PaperQuestionMapper paperQuestionMapper;
    private final PaperRuleMapper paperRuleMapper;
    private final ExamRecordMapper recordMapper;
    private final ExamAnswerMapper answerMapper;
    private final QuestionMapper questionMapper;
    private final UserMapper userMapper;
    private final UserService userService;
    private final QuestionService questionService;
    private final CategoryService categoryService;
    private final QuestionAssembler assembler;
    private final GradingService gradingService;
    private final ExamSettler settler;
    private final Clock clock;

    public ExamService(ExamPaperMapper paperMapper, PaperQuestionMapper paperQuestionMapper,
                       PaperRuleMapper paperRuleMapper, ExamRecordMapper recordMapper, ExamAnswerMapper answerMapper,
                       QuestionMapper questionMapper, UserMapper userMapper, UserService userService,
                       QuestionService questionService, CategoryService categoryService, QuestionAssembler assembler,
                       GradingService gradingService, ExamSettler settler, Clock clock) {
        this.paperMapper = paperMapper;
        this.paperQuestionMapper = paperQuestionMapper;
        this.paperRuleMapper = paperRuleMapper;
        this.recordMapper = recordMapper;
        this.answerMapper = answerMapper;
        this.questionMapper = questionMapper;
        this.userMapper = userMapper;
        this.userService = userService;
        this.questionService = questionService;
        this.categoryService = categoryService;
        this.assembler = assembler;
        this.gradingService = gradingService;
        this.settler = settler;
        this.clock = clock;
    }

    /** 开考的结果：created=false 表示返回已有的进行中考试（HTTP 200），否则新建（HTTP 201）。 */
    public record Started(ExamSheet sheet, boolean created) {
    }

    // ------------------------------------------------------------------ 试卷

    /** 已上架的试卷；不传 categoryId 时按本人目标考试过滤（没设目标考试就列全部）。 */
    public List<PaperItem> papers(long userId, Long categoryId) {
        settleExpiredOf(userId);
        User user = userService.requireUser(userId);
        Long rootId = categoryId != null ? categoryId : user.getTargetCategoryId();
        List<ExamPaper> papers = paperMapper.selectList(new LambdaQueryWrapper<ExamPaper>()
                .eq(ExamPaper::getStatus, PAPER_ONLINE)
                .eq(rootId != null, ExamPaper::getCategoryId, rootId)
                .orderByAsc(ExamPaper::getId));
        Map<Long, MyStats> mine = myStats(userId);
        return papers.stream().map(p -> {
            MyStats m = mine.getOrDefault(p.getId(), MyStats.EMPTY);
            return new PaperItem(p.getId(), p.getTitle(), p.getCategoryId(), p.getMode(), questionCount(p),
                    p.getTotalScore(), p.getPassScore(), p.getDurationMin(), m.attempts(), m.best(), m.inProgress(),
                    Boolean.TRUE.equals(p.getShuffleOptions()));
        }).toList();
    }

    public PaperDetail paper(long userId, long paperId) {
        settleExpiredOf(userId);
        ExamPaper p = requireOnline(paperId);
        MyStats m = myStats(userId).getOrDefault(p.getId(), MyStats.EMPTY);
        return new PaperDetail(p.getId(), p.getTitle(), p.getCategoryId(), p.getMode(), questionCount(p),
                p.getTotalScore(), p.getPassScore(), p.getDurationMin(), m.attempts(), m.best(), m.inProgress(),
                Boolean.TRUE.equals(p.getShuffleOptions()), p.getDescription(), SCORING_RULES);
    }

    /** 本人每份试卷的已完成次数、最高分、进行中的记录。 */
    private record MyStats(int attempts, Integer best, Long inProgress) {
        static final MyStats EMPTY = new MyStats(0, null, null);
    }

    private Map<Long, MyStats> myStats(long userId) {
        Map<Long, MyStats> map = new HashMap<>();
        for (ExamRecord r : recordMapper.selectList(new LambdaQueryWrapper<ExamRecord>()
                .select(ExamRecord::getId, ExamRecord::getPaperId, ExamRecord::getStatus, ExamRecord::getScore)
                .eq(ExamRecord::getUserId, userId))) {
            MyStats m = map.getOrDefault(r.getPaperId(), MyStats.EMPTY);
            if (ExamSettler.STATUS_FINISHED.equals(r.getStatus())) {
                Integer best = m.best() == null || (r.getScore() != null && r.getScore() > m.best())
                        ? r.getScore() : m.best();
                m = new MyStats(m.attempts() + 1, best, m.inProgress());
            } else {
                m = new MyStats(m.attempts(), m.best(), r.getId());
            }
            map.put(r.getPaperId(), m);
        }
        return map;
    }

    private int questionCount(ExamPaper p) {
        if (MODE_RULE.equals(p.getMode())) {
            return paperRuleMapper.selectList(new LambdaQueryWrapper<PaperRule>().eq(PaperRule::getPaperId, p.getId()))
                    .stream().mapToInt(PaperRule::getCount).sum();
        }
        return Math.toIntExact(paperQuestionMapper.selectCount(
                new LambdaQueryWrapper<PaperQuestion>().eq(PaperQuestion::getPaperId, p.getId())));
    }

    private ExamPaper requireOnline(long paperId) {
        ExamPaper p = paperMapper.selectById(paperId);
        if (p == null || !PAPER_ONLINE.equals(p.getStatus())) {
            throw BizException.notFound("试卷");
        }
        return p;
    }

    // ------------------------------------------------------------------ 开考

    @Transactional
    public Started start(long userId, long paperId) {
        // 锁住本人这一行，同一个人并发开考时排队，避免同一份卷建出两场进行中的考试
        userMapper.update(null, new LambdaUpdateWrapper<User>().eq(User::getId, userId)
                .setSql("updated_at = updated_at"));
        ExamPaper paper = requireOnline(paperId);
        LocalDateTime now = TimeUtil.nowUtc(clock);
        ExamRecord running = recordMapper.selectOne(new LambdaQueryWrapper<ExamRecord>()
                .eq(ExamRecord::getUserId, userId)
                .eq(ExamRecord::getPaperId, paperId)
                .eq(ExamRecord::getStatus, ExamSettler.STATUS_IN_PROGRESS)
                .orderByDesc(ExamRecord::getId)
                .last("LIMIT 1"));
        if (running != null) {
            if (now.isBefore(running.getDeadlineAt())) {
                return new Started(sheet(running), false);
            }
            settler.settle(running.getId(), ExamSettler.REASON_TIMEOUT);
        }

        CategoryTree tree = categoryService.tree();
        boolean shuffle = Boolean.TRUE.equals(paper.getShuffleOptions());
        List<ExamSnapshot.Item> items = MODE_RULE.equals(paper.getMode())
                ? drawByRules(paper, tree, shuffle) : fixedItems(paper, tree, shuffle);
        if (items.isEmpty()) {
            throw BizException.conflict("这份试卷没有题目");
        }
        int total = items.stream().mapToInt(ExamSnapshot.Item::score).sum();
        ExamRecord record = new ExamRecord()
                .setUserId(userId)
                .setPaperId(paperId)
                .setQuestionSnapshot(JsonUtil.write(new ExamSnapshot(paper.getTitle(), shuffle, items)))
                .setTotalScore(total)
                .setPassScore(paper.getPassScore())
                .setStartedAt(now)
                .setDeadlineAt(now.plusMinutes(paper.getDurationMin()))
                .setStatus(ExamSettler.STATUS_IN_PROGRESS);
        recordMapper.insert(record);
        return new Started(sheet(record), true);
    }

    private List<ExamSnapshot.Item> fixedItems(ExamPaper paper, CategoryTree tree, boolean shuffle) {
        List<PaperQuestion> links = paperQuestionMapper.selectList(new LambdaQueryWrapper<PaperQuestion>()
                .eq(PaperQuestion::getPaperId, paper.getId())
                .orderByAsc(PaperQuestion::getSort).orderByAsc(PaperQuestion::getId));
        Map<Long, Question> questions = questionService.loadByIds(links.stream().map(PaperQuestion::getQuestionId).toList());
        List<ExamSnapshot.Item> items = new ArrayList<>();
        for (PaperQuestion link : links) {
            Question q = questions.get(link.getQuestionId());
            if (q != null) {
                items.add(item(items.size() + 1, q, link.getScore(), tree, shuffle));
            }
        }
        return items;
    }

    /** 规则卷：每条规则从分类子树里该题型的已发布题中抽，规则之间不重复；题不够返回 409 带 gaps。 */
    private List<ExamSnapshot.Item> drawByRules(ExamPaper paper, CategoryTree tree, boolean shuffle) {
        List<PaperRule> rules = paperRuleMapper.selectList(new LambdaQueryWrapper<PaperRule>()
                .eq(PaperRule::getPaperId, paper.getId())
                .orderByAsc(PaperRule::getSort).orderByAsc(PaperRule::getId));
        List<RuleDrawer.Rule> drawRules = poolsFor(rules, tree);
        List<RuleDrawer.Gap> gaps = RuleDrawer.gaps(drawRules);
        if (!gaps.isEmpty()) {
            throw poolInsufficient(gaps, tree);
        }
        Map<Integer, List<Long>> picked = RuleDrawer.draw(drawRules, ThreadLocalRandom.current());
        Set<Long> allIds = new LinkedHashSet<>();
        picked.values().forEach(allIds::addAll);
        Map<Long, Question> questions = questionService.loadByIds(allIds);
        List<ExamSnapshot.Item> items = new ArrayList<>();
        for (int i = 0; i < rules.size(); i++) {
            for (Long id : picked.get(i)) {
                items.add(item(items.size() + 1, questions.get(id), rules.get(i).getScore(), tree, shuffle));
            }
        }
        return items;
    }

    /** 每条规则的候选池：分类子树里该题型的已发布题。后台上架校验也用它。 */
    public List<RuleDrawer.Rule> poolsFor(List<PaperRule> rules, CategoryTree tree) {
        Set<Long> chapters = new LinkedHashSet<>();
        for (PaperRule r : rules) {
            chapters.addAll(tree.chapterIdsUnder(r.getCategoryId()));
        }
        Map<Long, Map<String, Set<Long>>> byChapter = new HashMap<>();
        if (!chapters.isEmpty()) {
            for (Question q : questionMapper.selectList(new LambdaQueryWrapper<Question>()
                    .select(Question::getId, Question::getCategoryId, Question::getType)
                    .eq(Question::getStatus, QuestionService.STATUS_PUBLISHED)
                    .in(Question::getCategoryId, chapters))) {
                byChapter.computeIfAbsent(q.getCategoryId(), k -> new HashMap<>())
                        .computeIfAbsent(q.getType(), k -> new LinkedHashSet<>()).add(q.getId());
            }
        }
        List<RuleDrawer.Rule> out = new ArrayList<>();
        for (int i = 0; i < rules.size(); i++) {
            PaperRule r = rules.get(i);
            Set<Long> pool = new LinkedHashSet<>();
            for (Long chapter : tree.chapterIdsUnder(r.getCategoryId())) {
                pool.addAll(byChapter.getOrDefault(chapter, Map.of()).getOrDefault(r.getType(), Set.of()));
            }
            out.add(new RuleDrawer.Rule(i, r.getCategoryId(), r.getType(), r.getCount(), pool));
        }
        return out;
    }

    public static BizException poolInsufficient(List<RuleDrawer.Gap> gaps, CategoryTree tree) {
        List<Gap> data = gaps.stream().map(g -> new Gap(g.categoryId(), tree.path(g.categoryId()), g.type(),
                g.need(), g.available())).toList();
        return new BizException(ErrorCode.POOL_INSUFFICIENT, "规则卷题量不够", Map.of("gaps", data));
    }

    /** 一道题的快照；shuffle 为真时单选、多选题随机定一个选项显示顺序。 */
    private ExamSnapshot.Item item(int index, Question q, int score, CategoryTree tree, boolean shuffle) {
        AnswerKey key = AnswerKey.of(q);
        boolean fill = key.type() == QuestionType.FILL;
        List<OptionItem> options = assembler.optionItems(q);
        List<String> order = shuffle && key.type().isChoice()
                ? OptionShuffle.randomOrder(options, ThreadLocalRandom.current()) : null;
        return new ExamSnapshot.Item(index, q.getId(), q.getCategoryId(), tree.path(q.getCategoryId()), q.getType(),
                q.getStem(), options, key.blankCount(), q.getImageUrl(), score,
                fill ? null : key.correctAnswer(), fill ? key.acceptedAnswers() : null,
                fill ? key.fillOptions() : null, q.getAnalysis(), order);
    }

    // ------------------------------------------------------------------ 答卷

    /** 进行中返回 ExamSheet，已结算返回 ExamResult；过了截止时间的先结算。 */
    @Transactional
    public Object record(long userId, long recordId) {
        ExamRecord r = requireOwn(userId, recordId);
        if (ExamSettler.STATUS_IN_PROGRESS.equals(r.getStatus())) {
            if (TimeUtil.nowUtc(clock).isBefore(r.getDeadlineAt())) {
                return sheet(r);
            }
            settler.settle(recordId, ExamSettler.REASON_TIMEOUT);
            r = recordMapper.selectById(recordId);
        }
        return result(r);
    }

    /**
     * 保存一题答案。只接受比已存版本大的 version；截止后返回 409 EXAM_EXPIRED 且不保存。
     * 先 SELECT ... FOR UPDATE 锁住考试记录这一行，和交卷 / 结算互斥。
     */
    @Transactional
    public SaveAnswerResult saveAnswer(long userId, long recordId, long questionId, SaveAnswerRequest req) {
        requireOwn(userId, recordId);
        LocalDateTime now = TimeUtil.nowUtc(clock);
        ExamRecord r = recordMapper.selectOne(new LambdaQueryWrapper<ExamRecord>()
                .eq(ExamRecord::getId, recordId).last("FOR UPDATE"));
        if (!ExamSettler.STATUS_IN_PROGRESS.equals(r.getStatus()) || !now.isBefore(r.getDeadlineAt())) {
            throw new BizException(ErrorCode.EXAM_EXPIRED, "考试已结束，答案不再接收");
        }
        ExamSnapshot.Item item = JsonUtil.read(r.getQuestionSnapshot(), ExamSnapshot.class).questions().stream()
                .filter(i -> i.questionId() == questionId).findFirst()
                .orElseThrow(() -> BizException.notFound("本场考试里的这道题"));
        OptionShuffle shuffle = item.shuffle();
        List<String> given = shuffle == null ? req.userAnswer() : shuffle.toOriginal(req.userAnswer());
        List<String> answer = gradingService.normalize(QuestionType.valueOf(item.type()), given, item.blankCount());
        int version = req.version();
        boolean marked = Boolean.TRUE.equals(req.marked());
        int updated = answerMapper.update(null, new LambdaUpdateWrapper<ExamAnswer>()
                .eq(ExamAnswer::getRecordId, recordId)
                .eq(ExamAnswer::getQuestionId, questionId)
                .lt(ExamAnswer::getVersion, version)
                .set(ExamAnswer::getUserAnswer, JsonUtil.write(answer))
                .set(ExamAnswer::getVersion, version)
                .set(ExamAnswer::getMarked, marked)
                .set(ExamAnswer::getReceivedAt, now));
        if (updated > 0) {
            return new SaveAnswerResult(true, version);
        }
        ExamAnswer existing = findAnswer(recordId, questionId);
        if (existing == null) {
            try {
                answerMapper.insert(new ExamAnswer().setRecordId(recordId).setQuestionId(questionId)
                        .setUserAnswer(JsonUtil.write(answer)).setVersion(version).setMarked(marked)
                        .setReceivedAt(now));
                return new SaveAnswerResult(true, version);
            } catch (DuplicateKeyException e) {
                existing = findAnswer(recordId, questionId);
            }
        }
        return new SaveAnswerResult(false, existing == null ? 0 : existing.getVersion());
    }

    /** 交卷：可重复调用，只结算一次。 */
    @Transactional
    public ExamResult submit(long userId, long recordId) {
        requireOwn(userId, recordId);
        settler.settle(recordId, ExamSettler.REASON_MANUAL);
        return result(recordMapper.selectById(recordId));
    }

    public PageResult<RecordItem> records(long userId, Long paperId, PageQuery pageQuery) {
        settleExpiredOf(userId);
        Page<ExamRecord> page = pageQuery.toPage();
        recordMapper.selectPage(page, new LambdaQueryWrapper<ExamRecord>()
                .select(ExamRecord::getId, ExamRecord::getPaperId, ExamRecord::getQuestionSnapshot,
                        ExamRecord::getStatus, ExamRecord::getScore, ExamRecord::getTotalScore,
                        ExamRecord::getPassed, ExamRecord::getStartedAt, ExamRecord::getSubmittedAt)
                .eq(ExamRecord::getUserId, userId)
                .eq(paperId != null, ExamRecord::getPaperId, paperId)
                .orderByDesc(ExamRecord::getStartedAt).orderByDesc(ExamRecord::getId));
        return PageResult.of(page, r -> new RecordItem(r.getId(), r.getPaperId(), snapshot(r).title(), r.getStatus(),
                r.getScore(), r.getTotalScore(), r.getPassed(), TimeUtil.toInstant(r.getStartedAt()),
                TimeUtil.toInstant(r.getSubmittedAt())));
    }

    /** 本人已过截止时间的考试逐个结算（每个一个事务）。 */
    public void settleExpiredOf(long userId) {
        for (Long id : settler.expiredIds(userId)) {
            settler.settle(id, ExamSettler.REASON_TIMEOUT);
        }
    }

    // ------------------------------------------------------------------ 组装

    private ExamSheet sheet(ExamRecord r) {
        ExamSnapshot snap = snapshot(r);
        Map<Long, OptionShuffle> shuffles = new HashMap<>();
        List<SheetQuestion> questions = snap.questions().stream().map(i -> {
            OptionShuffle sh = i.shuffle();
            if (sh != null) {
                shuffles.put(i.questionId(), sh);
            }
            return new SheetQuestion(i.index(), i.questionId(), i.type(), assembler.html(i.stem()),
                    assembler.optionViews(sh == null ? i.options() : sh.displayOptions(i.options())), i.blankCount(),
                    i.imageUrl(), i.score(), sh == null ? null : sh.mapping());
        }).toList();
        // 库里存的是原标签，回显时换成这场考试的显示标签
        List<SavedAnswer> answers = answerMapper.selectList(new LambdaQueryWrapper<ExamAnswer>()
                        .eq(ExamAnswer::getRecordId, r.getId()).orderByAsc(ExamAnswer::getId))
                .stream().map(a -> {
                    List<String> stored = JsonUtil.read(a.getUserAnswer(), STRING_LIST);
                    OptionShuffle sh = shuffles.get(a.getQuestionId());
                    return new SavedAnswer(a.getQuestionId(), sh == null ? stored : sh.toDisplay(stored),
                            a.getVersion(), Boolean.TRUE.equals(a.getMarked()), TimeUtil.toInstant(a.getReceivedAt()));
                })
                .toList();
        int durationMin = (int) Duration.between(r.getStartedAt(), r.getDeadlineAt()).toMinutes();
        return new ExamSheet(r.getId(), r.getPaperId(), snap.title(), r.getStatus(), snap.shuffleOptions(),
                TimeUtil.toInstant(r.getStartedAt()), TimeUtil.toInstant(r.getDeadlineAt()), clock.instant(),
                durationMin, r.getTotalScore(), r.getPassScore(), questions, answers);
    }

    /** 成绩单完全由快照和结算时写下的每题得分算出，之后改题不影响。 */
    private ExamResult result(ExamRecord r) {
        ExamSnapshot snap = snapshot(r);
        Map<Long, ExamAnswer> answers = new HashMap<>();
        for (ExamAnswer a : answerMapper.selectList(new LambdaQueryWrapper<ExamAnswer>()
                .eq(ExamAnswer::getRecordId, r.getId()))) {
            answers.put(a.getQuestionId(), a);
        }
        Map<String, int[]> byType = new LinkedHashMap<>();
        for (QuestionType t : QuestionType.values()) {
            byType.put(t.name(), null);
        }
        Map<Long, int[]> byCategory = new LinkedHashMap<>();
        Map<Long, String> categoryPaths = new HashMap<>();
        List<ResultItem> items = new ArrayList<>();
        int correctCount = 0;
        int wrongCount = 0;
        int unanswered = 0;
        for (ExamSnapshot.Item i : snap.questions()) {
            ExamAnswer a = answers.get(i.questionId());
            List<String> given = a == null ? List.of() : JsonUtil.read(a.getUserAnswer(), STRING_LIST);
            boolean answered = ExamSettler.isAnswered(given);
            boolean correct = a != null && Boolean.TRUE.equals(a.getIsCorrect());
            int score = a == null || a.getScore() == null ? 0 : a.getScore();
            if (!answered) {
                unanswered++;
            } else if (correct) {
                correctCount++;
            } else {
                wrongCount++;
            }
            int[] t = byType.get(i.type());
            if (t == null) {
                t = new int[2];
                byType.put(i.type(), t);
            }
            t[0] += score;
            t[1] += i.score();
            int[] c = byCategory.computeIfAbsent(i.categoryId(), k -> new int[2]);
            c[0] += score;
            c[1] += i.score();
            categoryPaths.putIfAbsent(i.categoryId(), i.categoryPath());
            boolean fill = QuestionType.FILL.name().equals(i.type());
            OptionShuffle sh = i.shuffle();
            items.add(new ResultItem(i.index(), i.questionId(), i.type(), assembler.html(i.stem()),
                    assembler.optionViews(sh == null ? i.options() : sh.displayOptions(i.options())), i.blankCount(),
                    i.imageUrl(), sh == null ? given : sh.toDisplay(given),
                    fill ? null : sh == null ? i.correctAnswer() : sh.toDisplay(i.correctAnswer()),
                    fill ? i.acceptedAnswers() : null, correct, score, i.score(), assembler.html(i.analysis()),
                    sh == null ? null : sh.mapping()));
        }
        List<TypeScore> types = byType.entrySet().stream().filter(e -> e.getValue() != null)
                .map(e -> new TypeScore(e.getKey(), e.getValue()[0], e.getValue()[1],
                        Rates.rate(e.getValue()[0], e.getValue()[1])))
                .toList();
        List<CategoryScore> categories = byCategory.entrySet().stream()
                .map(e -> new CategoryScore(e.getKey(), categoryPaths.get(e.getKey()), e.getValue()[0],
                        e.getValue()[1], Rates.rate(e.getValue()[0], e.getValue()[1])))
                .toList();
        int score = r.getScore() == null ? 0 : r.getScore();
        long duration = r.getSubmittedAt() == null ? 0
                : Math.max(0, Duration.between(r.getStartedAt(), r.getSubmittedAt()).getSeconds());
        return new ExamResult(r.getId(), r.getPaperId(), snap.title(), r.getStatus(), score, r.getTotalScore(),
                r.getPassScore(), Boolean.TRUE.equals(r.getPassed()), TimeUtil.toInstant(r.getStartedAt()),
                TimeUtil.toInstant(r.getSubmittedAt()), duration, correctCount, wrongCount, unanswered, types,
                categories, items);
    }

    private ExamRecord requireOwn(long userId, long recordId) {
        ExamRecord r = recordMapper.selectById(recordId);
        if (r == null || r.getUserId() != userId) {
            throw BizException.notFound("考试记录");
        }
        return r;
    }

    private ExamAnswer findAnswer(long recordId, long questionId) {
        return answerMapper.selectOne(new LambdaQueryWrapper<ExamAnswer>()
                .eq(ExamAnswer::getRecordId, recordId).eq(ExamAnswer::getQuestionId, questionId));
    }

    private static ExamSnapshot snapshot(ExamRecord r) {
        return JsonUtil.read(r.getQuestionSnapshot(), ExamSnapshot.class);
    }
}
