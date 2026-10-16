package com.sproutpet.core.di

import android.content.Context
import androidx.room.Room
import com.sproutpet.core.db.DbMigrations
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
    fun provideDatabase(@ApplicationContext context: Context): SproutPetDatabase {
        val builder = Room.databaseBuilder(context, SproutPetDatabase::class.java, DB_NAME)
            .addMigrations(*DbMigrations.ALL)

        // ⚠️ 仅 DEBUG 兜底，正式包绝不挂载。
        //
        // 正式包缺失迁移时必须**崩溃（fail fast）**，而不是静默清库：
        // 清空用户数据是不可逆的（族谱/灵血/世代无法重建），
        // 更危险的是它会把「字段没落库」这类事故伪装成「数据正常」——
        // 清库后老行没了，读取端看不到异常，问题被藏起来直到上线。
        //
        // 若将来新增迁移，务必同步补 DbMigrations 与迁移测试（见 docs/03 §5）。
        if (com.sproutpet.core.BuildConfig.DEBUG) {
            builder.fallbackToDestructiveMigration()
        }
        return builder.build()
    }

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
