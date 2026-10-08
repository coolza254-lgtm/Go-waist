package com.gowaist.app.di

import android.content.Context
import androidx.room.Room
import com.gowaist.app.data.db.GoWaistDatabase
import com.gowaist.app.data.db.Migrations
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): GoWaistDatabase =
        Room.databaseBuilder(context, GoWaistDatabase::class.java, "gowaist.db")
            .addMigrations(*Migrations.ALL)
            .build()

    @Provides fun runDao(db: GoWaistDatabase) = db.runDao()
    @Provides fun exerciseDao(db: GoWaistDatabase) = db.exerciseDao()
    @Provides fun chainDao(db: GoWaistDatabase) = db.chainDao()
    @Provides fun templateDao(db: GoWaistDatabase) = db.templateDao()
    @Provides fun sessionDao(db: GoWaistDatabase) = db.sessionDao()
    @Provides fun planDao(db: GoWaistDatabase) = db.planDao()
    @Provides fun goalDao(db: GoWaistDatabase) = db.goalDao()
    @Provides fun bodyDao(db: GoWaistDatabase) = db.bodyDao()
    @Provides fun recordDao(db: GoWaistDatabase) = db.recordDao()
    @Provides fun suggestionDao(db: GoWaistDatabase) = db.suggestionDao()

    @Provides
    @Singleton
    @ApplicationScope
    fun appScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
