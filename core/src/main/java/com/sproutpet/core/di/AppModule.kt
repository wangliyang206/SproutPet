package com.sproutpet.core.di

import android.content.Context
import androidx.room.Room
import com.sproutpet.core.db.SproutPetDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

/**
 * 全局依赖注入配置。
 *
 * 所有 @Singleton 级别的实例在此集中提供，
 * 便于统一控制生命周期与替换实现。
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /** 数据库名 */
    private const val DB_NAME = "sproutpet.db"

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SproutPetDatabase =
        Room.databaseBuilder(context, SproutPetDatabase::class.java, DB_NAME)
            // 开发期允许破坏性迁移；上线前必须改为 addMigrations(...)
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    @Singleton
    fun provideOkHttp(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = if (com.sproutpet.core.BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .addInterceptor(logging)
            // 重要：不做 TrustAllCerts，使用系统默认证书校验
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(
        client: OkHttpClient,
        @Named("baseUrl") baseUrl: String
    ): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    @Provides
    @Singleton
    @Named("baseUrl")
    fun provideBaseUrl(): String = "https://api.sproutpet.example.com/"
}
