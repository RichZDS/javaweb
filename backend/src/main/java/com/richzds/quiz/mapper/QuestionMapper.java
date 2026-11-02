package com.richzds.quiz.mapper;

import java.util.Collection;
import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.richzds.quiz.entity.Question;

public interface QuestionMapper extends BaseMapper<Question> {

    /** 每个章节未归档（草稿 + 已发布）的题数，后台分类树用。 */
    @Select("SELECT category_id AS group_id, COUNT(*) AS total_count FROM question "
            + "WHERE status <> 'ARCHIVED' GROUP BY category_id")
    List<AggRow> countActiveByCategory();

    /** 某章节当前最大的 sort，没有题时为 null。 */
    @Select("SELECT MAX(sort) FROM question WHERE category_id = #{categoryId}")
    Integer maxSort(@Param("categoryId") long categoryId);

    /** 每个章节已发布的题数。 */
    @Select("SELECT category_id AS group_id, COUNT(*) AS total_count FROM question "
            + "WHERE status = 'PUBLISHED' GROUP BY category_id")
    List<AggRow> countPublishedByCategory();

    /**
     * 练习抽题的候选池：已发布、在给定章节里、符合题型 / 标签 / 范围 / 来源（错题本、收藏）条件的题 id，
     * 按章节内顺序排好（顺序练习直接用，随机练习由调用方打乱）。
     *
     * @param source  null、WRONG（本人错题本里未移出的）或 FAVORITE（本人收藏的）
     * @param scope   ALL、UNDONE（从没正式作答过）或 WRONG_EVER（作答过且从没全对过）
     * @param tagLike 形如 {@code %,链表,%}，已转义；不按标签筛时为 null
     */
    @Select("""
            <script>
            SELECT q.id FROM question q
            <if test="source == 'WRONG'">
              JOIN wrong_question w ON w.question_id = q.id AND w.user_id = #{userId} AND w.removed = FALSE
            </if>
            <if test="source == 'FAVORITE'">
              JOIN favorite f ON f.question_id = q.id AND f.user_id = #{userId}
            </if>
            WHERE q.status = 'PUBLISHED'
            <if test="chapterIds != null">
              AND q.category_id IN
              <foreach collection="chapterIds" item="c" open="(" separator="," close=")">#{c}</foreach>
            </if>
            <if test="types != null and types.size() > 0">
              AND q.type IN
              <foreach collection="types" item="t" open="(" separator="," close=")">#{t}</foreach>
            </if>
            <if test="tagLike != null">
              AND CONCAT(',', q.tags, ',') LIKE #{tagLike}
            </if>
            <if test="scope == 'UNDONE'">
              AND NOT EXISTS (SELECT 1 FROM answer_record a
                              WHERE a.user_id = #{userId} AND a.question_id = q.id AND a.answered = TRUE)
            </if>
            <if test="scope == 'WRONG_EVER'">
              AND EXISTS (SELECT 1 FROM answer_record a
                          WHERE a.user_id = #{userId} AND a.question_id = q.id AND a.answered = TRUE)
              AND NOT EXISTS (SELECT 1 FROM answer_record a2
                              WHERE a2.user_id = #{userId} AND a2.question_id = q.id AND a2.is_correct = TRUE)
            </if>
            ORDER BY q.sort, q.id
            </script>
            """)
    List<Long> selectPoolIds(@Param("userId") long userId,
                             @Param("source") String source,
                             @Param("chapterIds") Collection<Long> chapterIds,
                             @Param("types") Collection<String> types,
                             @Param("tagLike") String tagLike,
                             @Param("scope") String scope);
}
