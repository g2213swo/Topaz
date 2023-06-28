package net.momirealms.topaz.api;

import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 表示在 GUI 中会在绘制时查询所有数据的物品。
 */
public class DynamicGuiItem extends GuiItem {
    private Function<HumanEntity, GuiItem> query;

    private final Map<UUID, CacheEntry> cachedItems = new ConcurrentHashMap<>();

    /**
     * 表示在 GUI 中会在绘制时查询所有数据的物品。
     * @param slotChar 用于替换 GUI 设置字符串中的字符
     * @param query 查询物品数据的函数，应返回包含信息的物品
     */
    public DynamicGuiItem(char slotChar, Supplier<GuiItem> query) {
        this(slotChar, (h) -> query.get());
    }

    /**
     * 表示一个在 GUI 中的物品，当绘制时会查询其所有数据。
     * @param slotChar 用于替换 GUI 设置字符串中的字符
     * @param query 查询物品的数据，应返回一个包含信息并正确处理空玩家的物品
     */
    public DynamicGuiItem(char slotChar, Function<HumanEntity, GuiItem> query) {
        super(slotChar);
        this.query = query;
    }

    /**
     * 更新每个已缓存玩家的物品状态
     */
    public void update() {
        for (UUID playerId : new ArrayList<>(cachedItems.keySet())) {
            Player p = gui.getPlugin().getServer().getPlayer(playerId);
            if (p != null && p.isOnline()) {
                update(p);
            } else {
                cachedItems.remove(playerId);
            }
        }
    }

    /**
     * 更新特定玩家的此物品状态
     * @param player 要更新物品的玩家
     */
    public CacheEntry update(HumanEntity player) {
        CacheEntry cacheEntry = new CacheEntry(queryItem(player));
        if (cacheEntry.item instanceof DynamicGuiItem) {
            ((DynamicGuiItem) cacheEntry.item).update(player);
        } else if (cacheEntry.item instanceof GuiItemGroup) {
            TopazUI.updateItems(player, ((GuiItemGroup) cacheEntry.item).getItems());
        }
        cachedItems.put(player.getUniqueId(), cacheEntry);
        return cacheEntry;
    }

    /**
     * 设置GUI
     * @param gui  要设置的GUI
     */
    @Override
    public void setGui(TopazUI gui) {
        super.setGui(gui);
    }

    /**
     * 获取对应栏位的物品
     * @param who   看到此物品的玩家
     * @param slot  要获取物品的栏位
     * @return 物品或null
     */
    @Override
    public ItemStack getItem(HumanEntity who, int slot) {
        GuiItem item = getCachedItem(who);
        return item != null ? item.getItem(who, slot) : null;
    }

    /**
     * 获取此物品的动作
     * @param who   看到此物品的玩家
     * @return 动作或null
     */
    @Override
    public Action getAction(HumanEntity who) {
        GuiItem item = getCachedItem(who);
        return item != null ? item.getAction(who) : null;
    }

    /**
     * 获取此物品内容的查询
     * @return 物品内容查询
     */
    public Function<HumanEntity, GuiItem> getQuery() {
        return query;
    }


    /**
     * 设置此物品内容的查询
     * @param query 要设置的查询
     */
    public void setQuery(Function<HumanEntity, GuiItem> query) {
        this.query = query;
    }

    /**
     * 查询玩家的物品
     * @param who 玩家
     * @return GuiItem或null
     */
    public GuiItem queryItem(HumanEntity who) {
        GuiItem item = getQuery().apply(who);
        if (item != null) {
            item.setGui(gui);
            item.setSlots(slots);
        }
        return item;
    }

    /**
     * 获取缓存的物品，如果没有为该玩家缓存，则创建一个新物品。
     * 使用{@link #getLastCached(HumanEntity)}检查玩家是否有缓存的物品。
     * @param who 要获取物品的玩家
     * @return 当前缓存的物品
     */
    public GuiItem getCachedItem(HumanEntity who) {
        CacheEntry cached = cachedItems.get(who.getUniqueId());
        if (cached == null) {
            cached = update(who);
        }
        return cached.getItem();
    }


    /**
     * 从缓存中删除指定玩家的缓存物品。
     * @param who 要删除缓存物品的玩家
     * @return 被缓存的物品，如果没有被缓存则返回null
     */
    public GuiItem removeCachedItem(HumanEntity who) {
        CacheEntry cached = cachedItems.remove(who.getUniqueId());
        return cached != null ? cached.getItem() : null;
    }

    /**
     * 获取指定玩家上次缓存此物品的时间戳
     * @param who 要获取上次缓存时间的玩家
     * @return 上次缓存的时间戳，如果未缓存则返回-1
     */
    public long getLastCached(HumanEntity who) {
        CacheEntry cached = cachedItems.get(who.getUniqueId());
        return cached != null ? cached.getCreated() : -1;
    }

    public class CacheEntry {
        private final GuiItem item;
        private final long created = System.currentTimeMillis();

        CacheEntry(GuiItem item) {
            this.item = item;
        }

        public GuiItem getItem() {
            return item;
        }

        public long getCreated() {
            return created;
        }
    }

}