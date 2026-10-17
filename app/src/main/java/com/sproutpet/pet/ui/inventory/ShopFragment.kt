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
import com.sproutpet.core.domain.item.ItemCategory
import com.sproutpet.core.domain.item.ItemDefinition
import com.sproutpet.core.domain.item.ItemRegistry
import com.sproutpet.pet.R
import com.sproutpet.pet.data.local.OwnerLocalDataSource
import com.sproutpet.pet.data.repository.ItemRepository
import com.sproutpet.pet.data.repository.UserRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 商店页（docs/04 §2.4）：金币/钻石购买道具。
 *
 * 商品列表来自 [ItemRegistry] 中带 `shopPriceGold` / `shopPriceGem` 的道具。
 */
@AndroidEntryPoint
class ShopFragment : Fragment() {

    @Inject lateinit var userRepository: UserRepository
    @Inject lateinit var itemRepository: ItemRepository
    @Inject lateinit var ownerLocal: OwnerLocalDataSource

    private var ownerId: Long = 0L

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_shop, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val rv = view.findViewById<RecyclerView>(R.id.rv_shop)
        view.findViewById<TextView>(R.id.tv_back).setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        val shopItems = ItemRegistry.all.filter { it.shopPriceGold != null || it.shopPriceGem != null }
        val adapter = ShopAdapter(shopItems) { def -> buy(def) }
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            ownerId = ownerLocal.getOrCreateOwnerId()
        }
    }

    private fun buy(def: ItemDefinition) {
        viewLifecycleOwner.lifecycleScope.launch {
            val user = userRepository.current() ?: return@launch
            val priceGold = def.shopPriceGold
            val priceGem = def.shopPriceGem
            val canAfford = (priceGold != null && user.gold >= priceGold) ||
                    (priceGem != null && user.gem >= priceGem)
            if (!canAfford) {
                Toast.makeText(requireContext(), getString(R.string.shop_not_enough), Toast.LENGTH_SHORT).show()
                return@launch
            }
            // 优先金币支付
            if (priceGold != null) {
                userRepository.addGold(-priceGold)
            } else if (priceGem != null) {
                userRepository.addGem(-priceGem)
            }
            itemRepository.acquire(ownerId, def.id, 1)
            Toast.makeText(requireContext(), getString(R.string.shop_bought, def.name), Toast.LENGTH_SHORT).show()
        }
    }
}

private class ShopAdapter(
    private val items: List<ItemDefinition>,
    private val onBuy: (ItemDefinition) -> Unit
) : RecyclerView.Adapter<ShopAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val icon: TextView = view.findViewById(R.id.tv_icon)
        val name: TextView = view.findViewById(R.id.tv_name)
        val desc: TextView = view.findViewById(R.id.tv_desc)
        val price: TextView = view.findViewById(R.id.tv_count)
        val action: Button = view.findViewById(R.id.btn_action)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_inventory, parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val def = items[position]
        holder.icon.text = iconFor(def.category)
        holder.name.text = def.name
        holder.desc.text = def.description
        val priceText = when {
            def.shopPriceGold != null -> "🪙 ${def.shopPriceGold}"
            def.shopPriceGem != null -> "💎 ${def.shopPriceGem}"
            else -> ""
        }
        holder.price.text = priceText
        holder.action.setText(R.string.shop_buy)
        holder.action.setOnClickListener { onBuy(def) }
    }

    override fun getItemCount(): Int = items.size

    private fun iconFor(category: ItemCategory): String = when (category) {
        ItemCategory.FOOD -> "🍖"
        ItemCategory.TOY -> "🧶"
        ItemCategory.INCUBATION -> "⏳"
        ItemCategory.TRAINING -> "🏋️"
        ItemCategory.GROWTH -> "🌱"
        ItemCategory.CONSUMABLE -> "🎁"
        ItemCategory.SOCIAL -> "💐"
        ItemCategory.DECORATION -> "🎨"
    }
}
