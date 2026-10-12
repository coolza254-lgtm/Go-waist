package com.gowaist.app.domain

import android.content.Context
import com.gowaist.app.data.repo.GoalRepository
import com.gowaist.app.widget.WidgetUpdater
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Updates goal progress and the home-screen widget after runs or body data change. */
@Singleton
class ActivityCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val goals: GoalRepository,
) {
    suspend fun afterChange() {
        goals.evaluate()
        WidgetUpdater.update(context)
    }
}
