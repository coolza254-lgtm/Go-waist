package com.gowaist.core.model

/** How a single set of an exercise is measured. */
enum class TrackingType {
    /** Repetitions only. */
    REPS,

    /** Seconds held (plank, L-sit). */
    HOLD,

    /** Repetitions with extra load (backpack, vest). */
    WEIGHTED,

    /** Repetitions with assistance (band, reduced body weight). */
    ASSISTED,

    /** Distance and/or time based drills. */
    CARDIO,
}

enum class MovementPattern { PUSH, PULL, LEGS, CORE, FULL_BODY, SKILL }

enum class Muscle(val isUpper: Boolean) {
    CHEST(true),
    SHOULDERS(true),
    TRICEPS(true),
    BACK(true),
    BICEPS(true),
    GLUTES(false),
    LEGS(false),
    CALVES(false),
    ABS(true),
    OBLIQUES(true),
    LOWER_BACK(true),
}

enum class Equipment { NONE, BAR, BAND, RINGS, DIP_BARS }

enum class SetType { WARMUP, NORMAL, DROP, FAILURE }

/** Kind of a scheduled day in a training plan. */
enum class PlanDayType(val isRun: Boolean, val isBodyweight: Boolean) {
    RUN_EASY(true, false),
    RUN_TEMPO(true, false),
    RUN_INTERVAL(true, false),
    RUN_LONG(true, false),
    BW_PUSH(false, true),
    BW_PULL(false, true),
    BW_LEGS(false, true),
    BW_CORE(false, true),
    BW_FULL(false, true),
    BW_UPPER(false, true),
    BW_LOWER(false, true),
    REST(false, false);

    /** Days that load the legs heavily and should not sit right before a long run. */
    val isHeavyLegs: Boolean get() = this == BW_LEGS || this == BW_LOWER || this == BW_FULL
}

enum class PlanDayStatus { PENDING, DONE, PARTIAL, MISSED, OVER, REST }

enum class GoalType {
    RUN_DISTANCE,
    RUN_COUNT,
    RUN_5K_TIME,
    BW_SESSIONS,
    EXERCISE_MAX_REPS,
    EXERCISE_HOLD,
    EXERCISE_TOTAL_REPS,
    STREAK_DAYS,
    BODY_WEIGHT,
    WAIST,
}

enum class GoalPeriod { WEEK, MONTH, CUSTOM }

enum class GoalStatus { ACTIVE, ACHIEVED, EXPIRED }

enum class PrType {
    MAX_REPS,
    LONGEST_HOLD,
    MAX_VOLUME_SESSION,
    BEST_SET,
    MAX_ADDED_WEIGHT,
    EST_1RM,
    RUN_LONGEST,
    RUN_FASTEST_PACE,
    RUN_1K,
    RUN_5K,
    RUN_10K,
    RUN_HALF,
}
