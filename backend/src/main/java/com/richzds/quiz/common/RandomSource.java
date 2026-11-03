package com.richzds.quiz.common;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.stereotype.Component;

/**
 * 业务里的随机数（随机练习抽题、每日一练选题、选项打乱、规则卷抽题）都从这里取。
 * 平时就是 {@link ThreadLocalRandom}；演示数据模拟器运行时换成固定种子，同一份数据、同一个种子生成的结果完全一样。
 */
@Component
public class RandomSource {

    private volatile Random fixed;

    public Random current() {
        Random f = fixed;
        return f != null ? f : ThreadLocalRandom.current();
    }

    /** 之后都用这个种子的随机数（模拟器单线程运行时用）。 */
    public void useSeed(long seed) {
        fixed = new Random(seed);
    }
}
