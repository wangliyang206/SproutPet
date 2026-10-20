package com.sproutpet.pet.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.sproutpet.core.db.SproutPetDatabase
import com.sproutpet.core.db.entity.InventoryItemDao
import com.sproutpet.core.db.entity.PetDao
import com.sproutpet.core.db.entity.UserDao
import com.sproutpet.core.domain.pet.PetRendererFactory
import com.sproutpet.pet.data.repository.BlindBoxRepository
import com.sproutpet.pet.data.repository.BlindBoxRepositoryImpl
import com.sproutpet.pet.data.repository.FusionRepository
import com.sproutpet.pet.data.repository.FusionRepositoryImpl
import com.sproutpet.pet.data.repository.IncubationRepository
import com.sproutpet.pet.data.repository.IncubationRepositoryImpl
import com.sproutpet.pet.data.repository.ItemRepository
import com.sproutpet.pet.data.repository.ItemRepositoryImpl
import com.sproutpet.pet.data.repository.PairingRepository
import com.sproutpet.pet.data.repository.PairingRepositoryImpl
import com.sproutpet.pet.data.repository.PetRepository
import com.sproutpet.pet.data.repository.PetRepositoryImpl
import com.sproutpet.pet.data.repository.UserRepository
import com.sproutpet.pet.data.repository.UserRepositoryImpl
import com.sproutpet.pet.pet.renderer.frame.FramePetRendererFactory
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * app 层依赖装配（docs/04 Phase 1）。
 *
 * core 的 [com.sproutpet.core.di.AppModule] 只提供 DB/网络；
 * DAO、DataStore、仓库与渲染工厂属于 app 层装配，放这里。
 */
@Module
@InstallIn(SingletonComponent::class)
object SceneProvidesModule {

    @Provides
    @Singleton
    fun providePetDao(db: SproutPetDatabase): PetDao = db.petDao()

    @Provides
    @Singleton
    fun provideUserDao(db: SproutPetDatabase): UserDao = db.userDao()

    @Provides
    @Singleton
    fun provideInventoryItemDao(db: SproutPetDatabase): InventoryItemDao = db.inventoryItemDao()

    @Provides
    @Singleton
    fun providePairingDao(db: SproutPetDatabase): com.sproutpet.core.db.entity.PairingDao = db.pairingDao()

    /** 本地偏好（ownerId / 盲盒保底计数），文件 sproutpet_local.preferences_pb */
    @Provides
    @Singleton
    fun provideLocalDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile("sproutpet_local") }
        )
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SceneBindsModule {

    @Binds
    @Singleton
    abstract fun bindPetRepository(impl: PetRepositoryImpl): PetRepository

    @Binds
    @Singleton
    abstract fun bindBlindBoxRepository(impl: BlindBoxRepositoryImpl): BlindBoxRepository

    @Binds
    @Singleton
    abstract fun bindIncubationRepository(impl: IncubationRepositoryImpl): IncubationRepository

    @Binds
    @Singleton
    abstract fun bindUserRepository(impl: UserRepositoryImpl): UserRepository

    @Binds
    @Singleton
    abstract fun bindItemRepository(impl: ItemRepositoryImpl): ItemRepository

    @Binds
    @Singleton
    abstract fun bindFusionRepository(impl: FusionRepositoryImpl): FusionRepository

    @Binds
    @Singleton
    abstract fun bindPairingRepository(impl: PairingRepositoryImpl): PairingRepository

    /** 渲染工厂：Phase 1 帧动画占位；Phase 3 切 Rive 只改这一个绑定 */
    @Binds
    @Singleton
    abstract fun bindPetRendererFactory(impl: FramePetRendererFactory): PetRendererFactory
}
