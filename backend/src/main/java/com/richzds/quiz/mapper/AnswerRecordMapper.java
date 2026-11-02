package com.richzds.quiz.mapper;

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
