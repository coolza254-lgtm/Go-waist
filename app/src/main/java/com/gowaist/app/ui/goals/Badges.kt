package com.gowaist.app.ui.goals

import com.gowaist.app.R
import com.gowaist.core.goals.BadgeKey

fun badgeTitleRes(b: BadgeKey): Int = when (b) {
    BadgeKey.FIRST_RUN -> R.string.badge_first_run
    BadgeKey.FIRST_SESSION -> R.string.badge_first_session
    BadgeKey.FIRST_PB -> R.string.badge_first_pb
    BadgeKey.PB_10 -> R.string.badge_pb_10
    BadgeKey.STREAK_7 -> R.string.badge_streak_7
    BadgeKey.STREAK_30 -> R.string.badge_streak_30
    BadgeKey.RUN_5K -> R.string.badge_run_5k
    BadgeKey.RUN_10K -> R.string.badge_run_10k
    BadgeKey.RUN_HALF -> R.string.badge_run_half
    BadgeKey.TOTAL_100K -> R.string.badge_total_100k
    BadgeKey.SESSIONS_10 -> R.string.badge_sessions_10
    BadgeKey.SESSIONS_50 -> R.string.badge_sessions_50
    BadgeKey.CHAIN_LEVEL_UP -> R.string.badge_chain_up
    BadgeKey.FIRST_GOAL -> R.string.badge_first_goal
    BadgeKey.EARLY_BIRD -> R.string.badge_early_bird
}

fun badgeDescRes(b: BadgeKey): Int = when (b) {
    BadgeKey.FIRST_RUN -> R.string.badge_first_run_desc
    BadgeKey.FIRST_SESSION -> R.string.badge_first_session_desc
    BadgeKey.FIRST_PB -> R.string.badge_first_pb_desc
    BadgeKey.PB_10 -> R.string.badge_pb_10_desc
    BadgeKey.STREAK_7 -> R.string.badge_streak_7_desc
    BadgeKey.STREAK_30 -> R.string.badge_streak_30_desc
    BadgeKey.RUN_5K -> R.string.badge_run_5k_desc
    BadgeKey.RUN_10K -> R.string.badge_run_10k_desc
    BadgeKey.RUN_HALF -> R.string.badge_run_half_desc
    BadgeKey.TOTAL_100K -> R.string.badge_total_100k_desc
    BadgeKey.SESSIONS_10 -> R.string.badge_sessions_10_desc
    BadgeKey.SESSIONS_50 -> R.string.badge_sessions_50_desc
    BadgeKey.CHAIN_LEVEL_UP -> R.string.badge_chain_up_desc
    BadgeKey.FIRST_GOAL -> R.string.badge_first_goal_desc
    BadgeKey.EARLY_BIRD -> R.string.badge_early_bird_desc
}

fun badgeEmoji(b: BadgeKey): String = when (b) {
    BadgeKey.FIRST_RUN -> "👟"
    BadgeKey.FIRST_SESSION -> "💧"
    BadgeKey.FIRST_PB -> "🏅"
    BadgeKey.PB_10 -> "🎯"
    BadgeKey.STREAK_7 -> "🔥"
    BadgeKey.STREAK_30 -> "🌋"
    BadgeKey.RUN_5K -> "🥉"
    BadgeKey.RUN_10K -> "🥈"
    BadgeKey.RUN_HALF -> "🥇"
    BadgeKey.TOTAL_100K -> "💯"
    BadgeKey.SESSIONS_10 -> "💪"
    BadgeKey.SESSIONS_50 -> "🦾"
    BadgeKey.CHAIN_LEVEL_UP -> "⬆️"
    BadgeKey.FIRST_GOAL -> "🏆"
    BadgeKey.EARLY_BIRD -> "🐦"
}
