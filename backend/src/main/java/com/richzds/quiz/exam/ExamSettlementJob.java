package com.richzds.quiz.exam;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.richzds.quiz.entity.ExamRecord;

/**
 * 每 30 秒结算一次已过截止时间的考试，App 关掉也能出成绩。
 * test 配置里关掉定时触发（app.exam.auto-settle=false），测试直接调 {@link #run()} 并配合可调时钟。
 */
@Component
public class ExamSettlementJob {

    private static final Logger log = LoggerFactory.getLogger(ExamSettlementJob.class);

    private final ExamSettler settler;
    private final boolean enabled;

    /** 演示数据模拟器运行时（app.mock.enabled）关掉定时触发：过期考试由模拟器推进时钟后调 {@link #run()} 结算。 */
    public ExamSettlementJob(ExamSettler settler, @Value("${app.exam.auto-settle:true}") boolean enabled,
                             @Value("${app.mock.enabled:false}") boolean mock) {
        this.settler = settler;
        this.enabled = enabled && !mock;
    }

    @Scheduled(fixedDelay = 30_000, initialDelay = 30_000)
    public void scheduled() {
        if (enabled) {
            run();
        }
    }

    /** 结算全部到时的考试，返回这次结算的场数。单条失败只记日志，不影响其他。 */
    public int run() {
        int settled = 0;
        for (ExamRecord r : settler.expired(null)) {
            try {
                if (settler.settle(r.getUserId(), r.getId(), ExamSettler.REASON_TIMEOUT)) {
                    settled++;
                }
            } catch (RuntimeException e) {
                log.error("考试记录 {} 自动结算失败", r.getId(), e);
            }
        }
        if (settled > 0) {
            log.info("自动结算了 {} 场到时的考试", settled);
        }
        return settled;
    }
}
