package com.richzds.quiz.mapper;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.richzds.quiz.entity.AnswerRecord;

public interface AnswerRecordMapper extends BaseMapper<AnswerRecord> {

    /**
     * 本人按章节（题目当前所属章节）汇总：做过的不同题数、作答条数、全对条数。
     * 只算正式作答（answered = TRUE）且题目仍是已发布状态。
     */
    @Select("""
            SELECT q.category_id AS group_id,
                   COUNT(DISTINCT a.question_id) AS distinct_count,
                   COUNT(*) AS total_count,
                   SUM(CASE WHEN a.is_correct = TRUE THEN 1 ELSE 0 END) AS correct_count
            FROM answer_record a JOIN question q ON q.id = a.question_id
            WHERE a.user_id = #{userId} AND a.answered = TRUE AND q.status = 'PUBLISHED'
            GROUP BY q.category_id
            """)
    List<AggRow> progressByChapter(@Param("userId") long userId);

    /*
     * 以下统计查询都只算正式作答（answered = TRUE）；rootId 为 null 时不按考试过滤。
     * “哪一天”按北京时间：graded_at 是 UTC，加 8 小时再取日期（Asia/Shanghai 自 1991 年起无夏令时，固定 +8）。
     */

    /** 作答条数和全对条数；from 不为 null 时只算 graded_at >= from。 */
    @Select("""
            <script>
            SELECT COUNT(*) AS total_count, SUM(CASE WHEN is_correct = TRUE THEN 1 ELSE 0 END) AS correct_count
            FROM answer_record
            WHERE user_id = #{userId} AND answered = TRUE
            <if test="rootId != null">AND root_category_id = #{rootId}</if>
            <if test="from != null">AND graded_at &gt;= #{from}</if>
            </script>
            """)
    AggRow countAnswers(@Param("userId") long userId, @Param("rootId") Long rootId,
                        @Param("from") LocalDateTime from);

    /** 有作答的（北京时间）日期个数。 */
    @Select("""
            <script>
            SELECT COUNT(DISTINCT CAST(TIMESTAMPADD(HOUR, 8, graded_at) AS DATE))
            FROM answer_record
            WHERE user_id = #{userId} AND answered = TRUE
            <if test="rootId != null">AND root_category_id = #{rootId}</if>
            </script>
            """)
    long countStudyDays(@Param("userId") long userId, @Param("rootId") Long rootId);

    /** 从 from 起按北京时间每天的作答条数和全对条数。 */
    @Select("""
            <script>
            SELECT CAST(TIMESTAMPADD(HOUR, 8, graded_at) AS DATE) AS stat_day, COUNT(*) AS total_count,
                   SUM(CASE WHEN is_correct = TRUE THEN 1 ELSE 0 END) AS correct_count
            FROM answer_record
            WHERE user_id = #{userId} AND answered = TRUE AND graded_at &gt;= #{from}
            <if test="rootId != null">AND root_category_id = #{rootId}</if>
            GROUP BY CAST(TIMESTAMPADD(HOUR, 8, graded_at) AS DATE)
            </script>
            """)
    List<AggRow> dailyCounts(@Param("userId") long userId, @Param("rootId") Long rootId,
                             @Param("from") LocalDateTime from);

    /** 按作答时所在章节（category_id_snapshot）汇总：不同题数、作答条数、全对条数。 */
    @Select("""
            <script>
            SELECT category_id_snapshot AS group_id, COUNT(DISTINCT question_id) AS distinct_count,
                   COUNT(*) AS total_count, SUM(CASE WHEN is_correct = TRUE THEN 1 ELSE 0 END) AS correct_count
            FROM answer_record
            WHERE user_id = #{userId} AND answered = TRUE
            <if test="rootId != null">AND root_category_id = #{rootId}</if>
            GROUP BY category_id_snapshot
            </script>
            """)
    List<AggRow> countByChapter(@Param("userId") long userId, @Param("rootId") Long rootId);

    /** 排行榜：from 起每个正常状态用户的作答条数，多的在前。 */
    @Select("""
            <script>
            SELECT a.user_id AS group_id, COUNT(*) AS total_count
            FROM answer_record a JOIN app_user u ON u.id = a.user_id
            WHERE a.answered = TRUE AND a.graded_at &gt;= #{from} AND u.status = 'ACTIVE'
            <if test="rootId != null">AND a.root_category_id = #{rootId}</if>
            GROUP BY a.user_id
            ORDER BY total_count DESC, a.user_id ASC
            </script>
            """)
    List<AggRow> leaderboard(@Param("rootId") Long rootId, @Param("from") LocalDateTime from);

    /** 若干道题各自的作答条数和全对条数（后台题目列表用）。 */
    @Select("""
            <script>
            SELECT question_id AS group_id, COUNT(*) AS total_count,
                   SUM(CASE WHEN is_correct = TRUE THEN 1 ELSE 0 END) AS correct_count
            FROM answer_record
            WHERE answered = TRUE AND question_id IN
            <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            GROUP BY question_id
            </script>
            """)
    List<AggRow> countByQuestions(@Param("ids") Collection<Long> ids);

    /** 若干用户各自的作答条数（后台用户列表用）。 */
    @Select("""
            <script>
            SELECT user_id AS group_id, COUNT(*) AS total_count
            FROM answer_record
            WHERE answered = TRUE AND user_id IN
            <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            GROUP BY user_id
            </script>
            """)
    List<AggRow> countByUsers(@Param("ids") Collection<Long> ids);

    /** 看板：从 from 起按北京时间每天的作答条数和活跃人数（有作答的不同用户数）。 */
    @Select("""
            SELECT CAST(TIMESTAMPADD(HOUR, 8, graded_at) AS DATE) AS stat_day, COUNT(*) AS total_count,
                   COUNT(DISTINCT user_id) AS distinct_count
            FROM answer_record
            WHERE answered = TRUE AND graded_at >= #{from}
            GROUP BY CAST(TIMESTAMPADD(HOUR, 8, graded_at) AS DATE)
            """)
    List<AggRow> dailyActivity(@Param("from") LocalDateTime from);

    /** 若干次练习各自的提交数和全对数（练习记录列表用）。 */
    @Select("""
            <script>
            SELECT source_id AS group_id, COUNT(*) AS total_count,
                   SUM(CASE WHEN is_correct = TRUE THEN 1 ELSE 0 END) AS correct_count
            FROM answer_record
            WHERE source = #{source} AND source_id IN
            <foreach collection="sourceIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            GROUP BY source_id
            </script>
            """)
    List<AggRow> countBySources(@Param("source") String source, @Param("sourceIds") List<Long> sourceIds);
}
