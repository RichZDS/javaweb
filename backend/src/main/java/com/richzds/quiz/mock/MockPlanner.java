package com.richzds.quiz.mock;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

import com.richzds.quiz.mock.MockData.MockUser;

/**
 * 只由数据和种子决定的计划：每个学员哪天学、学多少题、哪天考试。不碰数据库，预演（dry-run）打印的就是它。
 * 每个学员用自己的随机数（种子 + 序号），改一个人的数据不会让别人的计划跟着变。
 */
public final class MockPlanner {

    private MockPlanner() {
    }

    /** 某天的计划：做多少题、要不要考一场试。 */
    public record DayPlan(LocalDate day, int questions, boolean exam) {
    }

    /** 一个学员的计划；lastDay 是最后一个可能活动的日子（最后活跃日和结束日里早的那个）。 */
    public record UserPlan(int index, MockUser user, LocalDate lastDay, Map<LocalDate, DayPlan> days) {
    }

    public record Plan(LocalDate start, LocalDate until, List<UserPlan> users) {

        public long plannedQuestions() {
            return users.stream().flatMap(u -> u.days().values().stream()).mapToLong(DayPlan::questions).sum();
        }

        public long examDays() {
            return users.stream().flatMap(u -> u.days().values().stream()).filter(DayPlan::exam).count();
        }

        /** 每天学习的人数（只算有计划的天）。 */
        public Map<LocalDate, Integer> activeByDay() {
            Map<LocalDate, Integer> out = new TreeMap<>();
            for (LocalDate d = start; !d.isAfter(until); d = d.plusDays(1)) {
                out.put(d, 0);
            }
            for (UserPlan u : users) {
                for (LocalDate d : u.days().keySet()) {
                    out.merge(d, 1, Integer::sum);
                }
            }
            return out;
        }
    }

    public static Plan plan(MockData data, LocalDate until) {
        List<UserPlan> out = new ArrayList<>();
        List<MockUser> users = data.users().users();
        for (int i = 0; i < users.size(); i++) {
            out.add(planUser(data.seed(), i, users.get(i), until));
        }
        return new Plan(data.users().startDate(), until, out);
    }

    static UserPlan planUser(long seed, int index, MockUser u, LocalDate until) {
        Random r = new Random(seed ^ (0x9E3779B97F4A7C15L * (index + 1)));
        LocalDate last = u.lastActiveOn() == null || u.lastActiveOn().isAfter(until) ? until : u.lastActiveOn();
        double[] w = u.weights();
        double mean = 0;
        for (double x : w) {
            mean += x / 7;
        }
        double perDay = u.activeDaysPerWeek() / 7;
        double examChance = Math.min(1, u.habits().examsPerMonth() * 7 / (30 * Math.max(u.activeDaysPerWeek(), 0.1)));
        int min = u.questionsPerDay().get(0);
        int max = u.questionsPerDay().get(1);
        Map<LocalDate, DayPlan> days = new TreeMap<>();
        for (LocalDate d = u.registeredOn(); !d.isAfter(last); d = d.plusDays(1)) {
            double p = mean <= 0 ? 0 : Math.min(1, perDay * w[d.getDayOfWeek().getValue() - 1] / mean);
            double roll = r.nextDouble();
            int count = min + r.nextInt(max - min + 1);
            boolean exam = r.nextDouble() < examChance;
            if (roll < p) {
                days.put(d, new DayPlan(d, count, exam));
            }
        }
        return new UserPlan(index, u, last, days);
    }
}
