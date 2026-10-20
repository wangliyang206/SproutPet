package com.sproutpet.pet.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * `OwnerLocalDataSource` 的本地持久化回归测试（`16 §三 K-4` / `16 §五 风险表`）。
 *
 * 重点守住两件「界面看不见但会静默出错」的事：
 * 1. **隐私同意态必须落盘** —— 只写内存标记的话，用户每次冷启都会被再弹一次门（或更糟：
 *    未同意就放行，直接踩 `00 §八` 红线 5）。
 * 2. **每日免费门票必须幂等** —— 重复调用不能无限发（`docs/00 §2.1` 每日 2 抽）。
 *
 * 测试策略：业务逻辑（键映射 / 幂等）用内存 DataStore，避免 JVM 侧 Windows 文件重命名抖动；
 * 落盘耐久性单独用一个「只写一次」的真实 DataStore 场景验证（DataStore 自身的原子写不重复测）。
 */
class OwnerLocalDataSourceTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    /** 内存 DataStore：只实现本类用到的 [data] 与 [updateData] */
    private class InMemoryDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow<Preferences>(
            androidx.datastore.preferences.core.emptyPreferences()
        )
        override val data: Flow<Preferences> = state

        override suspend fun updateData(
            transform: suspend (t: Preferences) -> Preferences
        ): Preferences {
            val updated = transform(state.value)
            state.value = updated
            return updated
        }
    }

    @Test
    fun `privacy is not agreed by default`() = runBlocking {
        val source = OwnerLocalDataSource(InMemoryDataStore())

        assertFalse(
            "全新安装必须未同意 —— 否则首启门形同虚设",
            source.readPrivacyAgreed()
        )
    }

    @Test
    fun `privacy agreement is idempotent`() = runBlocking {
        val source = OwnerLocalDataSource(InMemoryDataStore())

        source.setPrivacyAgreed()
        source.setPrivacyAgreed()
        source.setPrivacyAgreed()

        assertTrue(source.readPrivacyAgreed())
    }

    @Test
    fun `daily free tickets are granted once per day`() = runBlocking {
        val source = OwnerLocalDataSource(InMemoryDataStore())

        assertTrue("今天第一次应可领", source.claimDailyFreeTickets("2026-10-20"))
        assertFalse("同一天第二次不可再领", source.claimDailyFreeTickets("2026-10-20"))
        assertTrue("换一天又可领", source.claimDailyFreeTickets("2026-10-21"))
        assertFalse("新的一天领过之后仍不可重复", source.claimDailyFreeTickets("2026-10-21"))
    }

    @Test
    fun `privacy agreement survives a cold start`() = runBlocking {
        val file = File(tempFolder.root, "agree.preferences_pb")

        // 第一次运行：同意并落盘，然后释放 DataStore（模拟进程退出）
        val firstScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        OwnerLocalDataSource(
            PreferenceDataStoreFactory.create(scope = firstScope, produceFile = { file })
        ).setPrivacyAgreed()
        firstScope.cancel()

        // 冷启：同一文件重新打开，必须读回 true
        val secondScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val restored = OwnerLocalDataSource(
            PreferenceDataStoreFactory.create(scope = secondScope, produceFile = { file })
        ).readPrivacyAgreed()
        secondScope.cancel()

        assertTrue(
            "同意态必须落盘 —— 否则每次冷启都会重弹隐私门（或反之：未同意就放行）",
            restored
        )
    }
}
