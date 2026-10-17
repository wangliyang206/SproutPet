package com.sproutpet.pet.ui.inventory

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sproutpet.core.domain.item.ItemDefinition
import com.sproutpet.core.domain.item.ItemRegistry
import com.sproutpet.pet.R
import com.sproutpet.pet.data.local.OwnerLocalDataSource
import com.sproutpet.pet.data.repository.ItemRepository
import com.sproutpet.pet.data.repository.PetRepository
import com.sproutpet.pet.data.repository.UserRepository
import com.sproutpet.pet.domain.item.UseItemUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 背包页（docs/04 §2.4）：分类展示道具，点击使用。
 *
 * 货币从 [UserRepository] 取，库存从 [ItemRepository] 取，使用走 [UseItemUseCase]。
 */
@AndroidEntryPoint
class InventoryFragment : Fragment() {

    @Inject lateinit var userRepository: UserRepository
    @Inject lateinit var itemRepository: ItemRepository
    @Inject lateinit var petRepository: PetRepository
    @Inject lateinit var useItemUseCase: UseItemUseCase
    @Inject lateinit var ownerLocal: OwnerLocalDataSource

    private var ownerId: Long = 0L

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_inventory, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val rv = view.findViewById<RecyclerView>(R.id.rv_inventory)
        val tvGold = view.findViewById<TextView>(R.id.tv_gold)
        val tvGem = view.findViewById<TextView>(R.id.tv_gem)
        view.findViewById<TextView>(R.id.tv_back).setOnClickListener {
            parentFragmentManager.popBackStack()
        }
        view.findViewById<TextView>(R.id.tv_shop_enter).setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.scene_container, ShopFragment())
                .addToBackStack(null)
                .commit()
        }

        val adapter = InventoryAdapter { def -> useItem(def) }
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            ownerId = ownerLocal.getOrCreateOwnerId()
            userRepository.observeCurrent().collectLatest { user ->
                tvGold.text = "🪙 ${user?.gold ?: 0}"
                tvGem.text = "💎 ${user?.gem ?: 0}"
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            ownerId = ownerLocal.getOrCreateOwnerId()
            itemRepository.observeInventory(ownerId).collectLatest { items ->
                // 按 itemId 聚合数量（玩具多条记录合并显示总可用数）
                val grouped = items.groupBy { it.itemId }
                    .mapValues { (_, list) -> list.sumOf { it.count.coerceAtLeast(1) } }
                val defs = ItemRegistry.all.filter { (grouped[it.id] ?: 0) > 0 }
                adapter.submit(defs, grouped)
            }
        }
    }

    private fun useItem(def: ItemDefinition) {
        viewLifecycleOwner.lifecycleScope.launch {
            val mainPet = petRepository.observeHomePets(ownerId).first().firstOrNull()
            val result = useItemUseCase.execute(def.id, ownerId, mainPet?.id)
            when (result) {
                is UseItemUseCase.Result.Success ->
                    Toast.makeText(requireContext(), getString(R.string.inventory_used, def.name), Toast.LENGTH_SHORT).show()
                UseItemUseCase.Result.NotEnough ->
                    Toast.makeText(requireContext(), "数量不足", Toast.LENGTH_SHORT).show()
                UseItemUseCase.Result.PetRequired, UseItemUseCase.Result.PetNotFound ->
                    Toast.makeText(requireContext(), getString(R.string.inventory_no_pet), Toast.LENGTH_SHORT).show()
                UseItemUseCase.Result.ItemNotFound -> Unit
            }
        }
    }
}

/** 背包列表适配器 */
private class InventoryAdapter(
    private val onUse: (ItemDefinition) -> Unit
) : RecyclerView.Adapter<InventoryAdapter.VH>() {

    private var items: List<Pair<ItemDefinition, Int>> = emptyList()

    fun submit(defs: List<ItemDefinition>, counts: Map<String, Int>) {
        items = defs.map { it to (counts[it.id] ?: 0) }
        notifyDataSetChanged()
    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val icon: TextView = view.findViewById(R.id.tv_icon)
        val name: TextView = view.findViewById(R.id.tv_name)
        val desc: TextView = view.findViewById(R.id.tv_desc)
        val count: TextView = view.findViewById(R.id.tv_count)
        val action: Button = view.findViewById(R.id.btn_action)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_inventory, parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val (def, count) = items[position]
        holder.icon.text = iconFor(def.category)
        holder.name.text = def.name
        holder.desc.text = def.description
        holder.count.text = "×$count"
        holder.action.setText(R.string.inventory_use)
        holder.action.setOnClickListener { onUse(def) }
    }

    override fun getItemCount(): Int = items.size

    private fun iconFor(category: com.sproutpet.core.domain.item.ItemCategory): String = when (category) {
        com.sproutpet.core.domain.item.ItemCategory.FOOD -> "🍖"
        com.sproutpet.core.domain.item.ItemCategory.TOY -> "🧶"
        com.sproutpet.core.domain.item.ItemCategory.INCUBATION -> "⏳"
        com.sproutpet.core.domain.item.ItemCategory.TRAINING -> "🏋️"
        com.sproutpet.core.domain.item.ItemCategory.GROWTH -> "🌱"
        com.sproutpet.core.domain.item.ItemCategory.CONSUMABLE -> "🎁"
        com.sproutpet.core.domain.item.ItemCategory.SOCIAL -> "💐"
        com.sproutpet.core.domain.item.ItemCategory.DECORATION -> "🎨"
    }
}
