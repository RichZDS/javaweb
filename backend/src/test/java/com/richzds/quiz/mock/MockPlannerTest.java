package com.richzds.quiz.mock;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import com.richzds.quiz.mock.MockData.MockUser;
import com.richzds.quiz.mock.MockPlanner.DayPlan;
import com.richzds.quiz.mock.MockPlanner.Plan;
import com.richzds.quiz.mock.MockPlanner.UserPlan;

/** 计划只由数据和种子决定；weekdayWeights 只改变学习日落在星期几，每周总天数仍按 activeDaysPerWeek。 */
class MockPlannerTest {

    private static final ObjectMapper JSON = JsonMapper.builder().findAndAddModules().build();
    private static final LocalDate UNTIL = LocalDate.of(2026, 7, 11);

    private static MockData sample() {
        return MockData.load(new DefaultResourceLoader(), JSON, "classpath:mock-test/");
    }

    private static MockUser with(MockUser u, double perWeek, List<Double> weights, LocalDate registered) {
        return new MockUser(u.username(), u.nickname(), u.target(), registered, null, u.persona(), perWeek,
                u.questionsPerDay(), u.hours(), u.accuracy(), u.habits(), null, u.wrongRemoveThreshold(), u.avatar(),
                false, weights);
    }

    @Test
    void samePlanForTheSameSeed() {
        Plan a = MockPlanner.plan(sample(), UNTIL);
        Plan b = MockPlanner.plan(sample(), UNTIL);
        assertThat(a).isEqualTo(b);
        assertThat(a.plannedQuestions()).isPositive();
        UserPlan lin = a.users().get(0);
        UserPlan other = MockPlanner.planUser(8, 0, lin.user(), UNTIL);
        assertThat(other.days()).as("换种子计划就不同").isNotEqualTo(lin.days());
    }

    @Test
    void changingOneUserDoesNotShiftOthers() {
        MockData data = sample();
        UserPlan before = MockPlanner.planUser(data.seed(), 1, data.users().users().get(1), UNTIL);
        UserPlan again = MockPlanner.planUser(data.seed(), 1, data.users().users().get(1), UNTIL);
        assertThat(again).isEqualTo(before);
        assertThat(MockPlanner.planUser(data.seed(), 2, data.users().users().get(1), UNTIL).days())
                .as("每个人用自己的随机数（种子 + 序号）").isNotEqualTo(before.days());
    }

    @Test
    void weekdayWeightsPlaceStudyDaysButKeepTheWeeklyTotal() {
        MockUser base = sample().users().users().get(0);
        LocalDate from = LocalDate.of(2026, 1, 5); // 周一
        LocalDate to = from.plusWeeks(40).minusDays(1);
        MockUser weekend = with(base, 2, List.of(0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 1.0), from);
        UserPlan w = MockPlanner.planUser(1, 0, weekend, to);
        assertThat(w.days().keySet()).allMatch(d -> d.getDayOfWeek() == DayOfWeek.SATURDAY
                || d.getDayOfWeek() == DayOfWeek.SUNDAY);
        assertThat(w.days()).hasSize(80).as("每周 2 天、只在周六日：每个周末都学");

        MockUser tilted = with(base, 3, List.of(0.5, 0.5, 0.5, 0.5, 0.5, 2.25, 2.25), from);
        UserPlan t = MockPlanner.planUser(2, 0, tilted, to);
        assertThat(t.days().size() / 40.0).as("每周平均天数仍约等于 activeDaysPerWeek").isBetween(2.6, 3.4);
        long weekendDays = t.days().keySet().stream().filter(d -> d.getDayOfWeek().getValue() >= 6).count();
        assertThat(weekendDays).as("周末权重高，周末的学习日占多数").isGreaterThan(t.days().size() / 2);

        UserPlan flat = MockPlanner.planUser(3, 0, with(base, 3, null, from), to);
        assertThat(flat.days().size() / 40.0).as("不写 weekdayWeights 等于全是 1").isBetween(2.6, 3.4);
    }

    @Test
    void noPlanBeforeRegistrationOrAfterLastActiveDay() {
        Plan p = MockPlanner.plan(sample(), UNTIL);
        for (UserPlan u : p.users()) {
            for (Map.Entry<LocalDate, DayPlan> e : u.days().entrySet()) {
                assertThat(e.getKey()).isAfterOrEqualTo(u.user().registeredOn()).isBeforeOrEqualTo(u.lastDay());
                assertThat(e.getValue().questions()).isBetween(u.user().questionsPerDay().get(0),
                        u.user().questionsPerDay().get(1));
            }
        }
        UserPlan dropout = p.users().get(2);
        assertThat(dropout.lastDay()).isEqualTo(LocalDate.of(2026, 7, 5));
    }
}
