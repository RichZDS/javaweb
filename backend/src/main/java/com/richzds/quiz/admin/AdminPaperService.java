package com.richzds.quiz.admin;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.richzds.quiz.common.BizException;
import com.richzds.quiz.common.PageQuery;
import com.richzds.quiz.common.PageResult;
import com.richzds.quiz.common.TimeUtil;
import com.richzds.quiz.dto.AdminDtos.AdminPaper;
import com.richzds.quiz.dto.AdminDtos.AdminPaperQuestion;
import com.richzds.quiz.dto.AdminDtos.AdminPaperRule;
import com.richzds.quiz.dto.AdminDtos.PaperInput;
import com.richzds.quiz.dto.AdminDtos.PaperQuestionInput;
import com.richzds.quiz.dto.AdminDtos.PaperRuleInput;
import com.richzds.quiz.entity.Category;
import com.richzds.quiz.entity.ExamPaper;
import com.richzds.quiz.entity.ExamRecord;
import com.richzds.quiz.entity.PaperQuestion;
import com.richzds.quiz.entity.PaperRule;
import com.richzds.quiz.entity.Question;
import com.richzds.quiz.exam.ExamService;
import com.richzds.quiz.exam.RuleDrawer;
import com.richzds.quiz.mapper.ExamPaperMapper;
import com.richzds.quiz.mapper.ExamRecordMapper;
import com.richzds.quiz.mapper.PaperQuestionMapper;
import com.richzds.quiz.mapper.PaperRuleMapper;
import com.richzds.quiz.service.CategoryService;
import com.richzds.quiz.service.CategoryTree;
import com.richzds.quiz.service.QuestionAssembler;
import com.richzds.quiz.service.QuestionService;

/**
 * 后台试卷管理（docs/api.md 第 9、9.1 节）。新建的卷是下架状态；总分由题目或规则算出。
 * 有人考过（有任何考试记录）的卷只能改标题、描述和上下架，也不能删除。
 */
@Service
public class AdminPaperService {

    public static final String ONLINE = ExamService.PAPER_ONLINE;
    public static final String OFFLINE = "OFFLINE";

    private final ExamPaperMapper paperMapper;
    private final PaperQuestionMapper paperQuestionMapper;
    private final PaperRuleMapper paperRuleMapper;
    private final ExamRecordMapper recordMapper;
    private final QuestionService questionService;
    private final CategoryService categoryService;
    private final QuestionAssembler assembler;
    private final ExamService examService;
    private final Clock clock;

    public AdminPaperService(ExamPaperMapper paperMapper, PaperQuestionMapper paperQuestionMapper,
                             PaperRuleMapper paperRuleMapper, ExamRecordMapper recordMapper,
                             QuestionService questionService, CategoryService categoryService,
                             QuestionAssembler assembler, ExamService examService, Clock clock) {
        this.paperMapper = paperMapper;
        this.paperQuestionMapper = paperQuestionMapper;
        this.paperRuleMapper = paperRuleMapper;
        this.recordMapper = recordMapper;
        this.questionService = questionService;
        this.categoryService = categoryService;
        this.assembler = assembler;
        this.examService = examService;
        this.clock = clock;
    }

    public PageResult<AdminPaper> list(Long categoryId, String status, PageQuery pageQuery) {
        if (status != null && !status.isBlank() && !ONLINE.equals(status) && !OFFLINE.equals(status)) {
            throw BizException.invalid("status", "只能是 ONLINE 或 OFFLINE");
        }
        Page<ExamPaper> page = pageQuery.toPage();
        paperMapper.selectPage(page, new LambdaQueryWrapper<ExamPaper>()
                .eq(categoryId != null, ExamPaper::getCategoryId, categoryId)
                .eq(status != null && !status.isBlank(), ExamPaper::getStatus, status)
                .orderByDesc(ExamPaper::getId));
        CategoryTree tree = categoryService.tree();
        Map<Long, Long> attempts = attempts();
        return PageResult.of(page, p -> view(p, tree, attempts.getOrDefault(p.getId(), 0L)));
    }

    public AdminPaper detail(long id) {
        return view(require(id));
    }

    @Transactional
    public AdminPaper create(PaperInput in) {
        CategoryTree tree = categoryService.tree();
        Content content = validate(in, tree);
        LocalDateTime now = TimeUtil.nowUtc(clock);
        ExamPaper p = new ExamPaper()
                .setTitle(in.title().strip())
                .setCategoryId(in.categoryId())
                .setDescription(blankToNull(in.description()))
                .setMode(in.mode())
                .setDurationMin(in.durationMin())
                .setTotalScore(content.total())
                .setPassScore(in.passScore())
                .setStatus(OFFLINE)
                .setCreatedAt(now)
                .setUpdatedAt(now);
        paperMapper.insert(p);
        writeContent(p.getId(), content);
        return view(p);
    }

    @Transactional
    public AdminPaper update(long id, PaperInput in) {
        ExamPaper p = require(id);
        CategoryTree tree = categoryService.tree();
        LocalDateTime now = TimeUtil.nowUtc(clock);
        if (attemptsOf(id) > 0) {
            // 有人考过：只允许改标题和描述，其余字段必须和现在一致
            if (!Objects.equals(in.categoryId(), p.getCategoryId()) || !Objects.equals(in.mode(), p.getMode())
                    || !Objects.equals(in.durationMin(), p.getDurationMin())
                    || !Objects.equals(in.passScore(), p.getPassScore())
                    || !sameContent(p, in)) {
                throw BizException.conflict("已有人考过这份试卷，只能改标题、描述和上下架");
            }
            paperMapper.update(null, new LambdaUpdateWrapper<ExamPaper>().eq(ExamPaper::getId, id)
                    .set(ExamPaper::getTitle, in.title().strip())
                    .set(ExamPaper::getDescription, blankToNull(in.description()))
                    .set(ExamPaper::getUpdatedAt, now));
            return view(require(id));
        }
        Content content = validate(in, tree);
        if (ONLINE.equals(p.getStatus())) {
            checkOnline(in.mode(), content, in.passScore(), tree);
        }
        paperMapper.update(null, new LambdaUpdateWrapper<ExamPaper>().eq(ExamPaper::getId, id)
                .set(ExamPaper::getTitle, in.title().strip())
                .set(ExamPaper::getCategoryId, in.categoryId())
                .set(ExamPaper::getDescription, blankToNull(in.description()))
                .set(ExamPaper::getMode, in.mode())
                .set(ExamPaper::getDurationMin, in.durationMin())
                .set(ExamPaper::getTotalScore, content.total())
                .set(ExamPaper::getPassScore, in.passScore())
                .set(ExamPaper::getUpdatedAt, now));
        paperQuestionMapper.delete(new LambdaQueryWrapper<PaperQuestion>().eq(PaperQuestion::getPaperId, id));
        paperRuleMapper.delete(new LambdaQueryWrapper<PaperRule>().eq(PaperRule::getPaperId, id));
        writeContent(id, content);
        return view(require(id));
    }

    /** 上架时校验：固定卷至少 1 道题，规则卷题量够（不够 409 带 gaps），两者都要求及格分不超过总分。 */
    @Transactional
    public AdminPaper changeStatus(long id, String status) {
        ExamPaper p = require(id);
        if (ONLINE.equals(status)) {
            CategoryTree tree = categoryService.tree();
            checkOnline(p.getMode(), currentContent(p), p.getPassScore(), tree);
        }
        paperMapper.update(null, new LambdaUpdateWrapper<ExamPaper>().eq(ExamPaper::getId, id)
                .set(ExamPaper::getStatus, status).set(ExamPaper::getUpdatedAt, TimeUtil.nowUtc(clock)));
        return view(require(id));
    }

    @Transactional
    public void delete(long id) {
        require(id);
        if (attemptsOf(id) > 0) {
            throw BizException.conflict("已有人考过这份试卷，不能删除，可以下架");
        }
        paperQuestionMapper.delete(new LambdaQueryWrapper<PaperQuestion>().eq(PaperQuestion::getPaperId, id));
        paperRuleMapper.delete(new LambdaQueryWrapper<PaperRule>().eq(PaperRule::getPaperId, id));
        paperMapper.deleteById(id);
    }

    // ------------------------------------------------------------------ 校验

    /** 校验后的试卷内容：固定卷的题或规则卷的规则，以及算出的总分。 */
    private record Content(List<PaperQuestion> questions, List<PaperRule> rules, int total) {
    }

    private Content validate(PaperInput in, CategoryTree tree) {
        Category exam = tree.get(in.categoryId());
        if (exam == null || exam.getLevel() != CategoryTree.LEVEL_EXAM) {
            throw BizException.invalid("categoryId", "必须是一级分类（考试）");
        }
        if (ExamService.MODE_FIXED.equals(in.mode())) {
            List<PaperQuestionInput> qs = in.questions() == null ? List.of() : in.questions();
            Map<Long, Question> questions = questionService.loadByIds(
                    qs.stream().map(PaperQuestionInput::questionId).toList());
            Set<Long> seen = new HashSet<>();
            List<PaperQuestion> out = new ArrayList<>();
            int total = 0;
            for (int i = 0; i < qs.size(); i++) {
                PaperQuestionInput item = qs.get(i);
                String field = "questions[" + i + "].questionId";
                Question q = questions.get(item.questionId());
                if (q == null || !QuestionService.STATUS_PUBLISHED.equals(q.getStatus())) {
                    throw BizException.invalid(field, "题目不存在或不是已发布状态");
                }
                if (!exam.getId().equals(tree.rootOf(q.getCategoryId()))) {
                    throw BizException.invalid(field, "题目不属于这份试卷的考试");
                }
                if (!seen.add(q.getId())) {
                    throw BizException.invalid(field, "同一道题重复了");
                }
                out.add(new PaperQuestion().setQuestionId(q.getId()).setScore(item.score())
                        .setSort(item.sort() == null ? i : item.sort()));
                total += item.score();
            }
            return new Content(out, List.of(), total);
        }
        List<PaperRuleInput> rs = in.rules() == null ? List.of() : in.rules();
        List<PaperRule> out = new ArrayList<>();
        int total = 0;
        for (int i = 0; i < rs.size(); i++) {
            PaperRuleInput r = rs.get(i);
            if (!tree.exists(r.categoryId()) || !exam.getId().equals(tree.rootOf(r.categoryId()))) {
                throw BizException.invalid("rules[" + i + "].categoryId", "分类不存在或不属于这份试卷的考试");
            }
            out.add(new PaperRule().setCategoryId(r.categoryId()).setType(r.type()).setCount(r.count())
                    .setScore(r.score()).setSort(i));
            total += r.count() * r.score();
        }
        return new Content(List.of(), out, total);
    }

    private void checkOnline(String mode, Content content, int passScore, CategoryTree tree) {
        if (ExamService.MODE_FIXED.equals(mode)) {
            if (content.questions().isEmpty()) {
                throw BizException.conflict("固定卷至少要有 1 道题才能上架");
            }
        } else {
            if (content.rules().isEmpty()) {
                throw BizException.conflict("规则卷至少要有 1 条规则才能上架");
            }
            List<RuleDrawer.Gap> gaps = RuleDrawer.gaps(examService.poolsFor(content.rules(), tree));
            if (!gaps.isEmpty()) {
                throw ExamService.poolInsufficient(gaps, tree);
            }
        }
        if (passScore > content.total()) {
            throw BizException.conflict("及格分 " + passScore + " 超过总分 " + content.total());
        }
    }

    private boolean sameContent(ExamPaper p, PaperInput in) {
        Content current = currentContent(p);
        if (ExamService.MODE_FIXED.equals(p.getMode())) {
            List<PaperQuestionInput> qs = in.questions() == null ? List.of() : in.questions();
            if (qs.size() != current.questions().size()) {
                return false;
            }
            for (int i = 0; i < qs.size(); i++) {
                PaperQuestion c = current.questions().get(i);
                if (!c.getQuestionId().equals(qs.get(i).questionId()) || !c.getScore().equals(qs.get(i).score())) {
                    return false;
                }
            }
            return true;
        }
        List<PaperRuleInput> rs = in.rules() == null ? List.of() : in.rules();
        if (rs.size() != current.rules().size()) {
            return false;
        }
        for (int i = 0; i < rs.size(); i++) {
            PaperRule c = current.rules().get(i);
            PaperRuleInput r = rs.get(i);
            if (!c.getCategoryId().equals(r.categoryId()) || !c.getType().equals(r.type())
                    || !c.getCount().equals(r.count()) || !c.getScore().equals(r.score())) {
                return false;
            }
        }
        return true;
    }

    private Content currentContent(ExamPaper p) {
        List<PaperQuestion> qs = paperQuestionMapper.selectList(new LambdaQueryWrapper<PaperQuestion>()
                .eq(PaperQuestion::getPaperId, p.getId())
                .orderByAsc(PaperQuestion::getSort).orderByAsc(PaperQuestion::getId));
        List<PaperRule> rs = paperRuleMapper.selectList(new LambdaQueryWrapper<PaperRule>()
                .eq(PaperRule::getPaperId, p.getId())
                .orderByAsc(PaperRule::getSort).orderByAsc(PaperRule::getId));
        int total = qs.stream().mapToInt(PaperQuestion::getScore).sum()
                + rs.stream().mapToInt(r -> r.getCount() * r.getScore()).sum();
        return new Content(qs, rs, total);
    }

    private void writeContent(long paperId, Content content) {
        for (PaperQuestion q : content.questions()) {
            paperQuestionMapper.insert(q.setId(null).setPaperId(paperId));
        }
        for (PaperRule r : content.rules()) {
            paperRuleMapper.insert(r.setId(null).setPaperId(paperId));
        }
    }

    // ------------------------------------------------------------------ 组装

    private ExamPaper require(long id) {
        ExamPaper p = paperMapper.selectById(id);
        if (p == null) {
            throw BizException.notFound("试卷");
        }
        return p;
    }

    private long attemptsOf(long paperId) {
        return recordMapper.selectCount(new LambdaQueryWrapper<ExamRecord>().eq(ExamRecord::getPaperId, paperId));
    }

    private Map<Long, Long> attempts() {
        Map<Long, Long> map = new HashMap<>();
        recordMapper.countByPaper().forEach(r -> map.put(r.getGroupId(), r.total()));
        return map;
    }

    private AdminPaper view(ExamPaper p) {
        return view(p, categoryService.tree(), attemptsOf(p.getId()));
    }

    private AdminPaper view(ExamPaper p, CategoryTree tree, long attempts) {
        Content c = currentContent(p);
        boolean fixed = ExamService.MODE_FIXED.equals(p.getMode());
        List<AdminPaperQuestion> questions = null;
        List<AdminPaperRule> rules = null;
        int count;
        if (fixed) {
            Map<Long, Question> qs = questionService.loadByIds(
                    c.questions().stream().map(PaperQuestion::getQuestionId).toList());
            questions = c.questions().stream().map(pq -> {
                Question q = qs.get(pq.getQuestionId());
                return new AdminPaperQuestion(pq.getQuestionId(), pq.getScore(), pq.getSort(),
                        q == null ? null : q.getType(), q == null ? "" : assembler.preview(q.getStem()));
            }).toList();
            count = questions.size();
        } else {
            rules = c.rules().stream().map(r -> new AdminPaperRule(r.getCategoryId(), tree.path(r.getCategoryId()),
                    r.getType(), r.getCount(), r.getScore())).toList();
            count = c.rules().stream().mapToInt(PaperRule::getCount).sum();
        }
        Category exam = tree.get(p.getCategoryId());
        return new AdminPaper(p.getId(), p.getTitle(), p.getCategoryId(), exam == null ? null : exam.getName(),
                p.getDescription(), p.getMode(), p.getDurationMin(), p.getTotalScore(), p.getPassScore(), p.getStatus(),
                count, attempts, questions, rules, TimeUtil.toInstant(p.getCreatedAt()),
                TimeUtil.toInstant(p.getUpdatedAt()));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
