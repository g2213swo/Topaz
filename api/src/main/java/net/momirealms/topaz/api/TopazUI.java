package net.momirealms.topaz.api;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Nameable;
import org.bukkit.Sound;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.DragType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryInteractEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.material.MaterialData;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 主要的库类，允许您创建和管理GUI。
 */
public class TopazUI implements Listener {

    private final static int[] ROW_WIDTHS = {3, 5, 9};
    private final static InventoryType[] INVENTORY_TYPES = {
            InventoryType.DISPENSER, // 3*3
            InventoryType.HOPPER, // 5*1
            InventoryType.CHEST // 9*x
    };

    private final static Map<String, TopazUI> GUI_MAP = new ConcurrentHashMap<>();
    private final static Map<UUID, ArrayDeque<TopazUI>> GUI_HISTORY = new ConcurrentHashMap<>();

    private final static Map<String, Pattern> PATTERN_CACHE = new HashMap<>();

    private final static boolean FOLIA;

    private static String DEFAULT_CLICK_SOUND;

    private final JavaPlugin plugin;
    private final GuiListener listener;
    private InventoryCreator creator;
    private String title;
    private boolean titleUpdated = false;
    private final char[] slots;
    private int width;
    private final GuiItem[] itemSlots;
    private final Map<Character, GuiItem> items = new ConcurrentHashMap<>();
    private InventoryType inventoryType;
    private final Map<UUID, Inventory> inventories = new ConcurrentHashMap<>();
    private InventoryHolder owner;
    private final Map<UUID, Integer> pageNumbers = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> pageAmounts = new ConcurrentHashMap<>();
    private GuiItem.Action outsideAction = click -> false;
    private CloseAction closeAction = close -> true;
    private String clickSound = getDefaultClickSound();
    private boolean silent = false;

    static {
        boolean folia;
        try {
            Class.forName("io.papermc.paper.threadedregions.scheduler.AsyncScheduler");
            folia = true;
        } catch (ClassNotFoundException e) {
            folia = false;
        }
        FOLIA = folia;

        // Sound names changed, make it compatible with both versions
        String clickSound = null;
        Map<String, String> clickSounds = new LinkedHashMap<>();
        clickSounds.put("UI_BUTTON_CLICK", "ui.button.click");
        clickSounds.put("CLICK", "random.click");
        for (Map.Entry<String, String> entry : clickSounds.entrySet()) {
            try {
                // Try to get sound enum to see if it exists
                Sound.valueOf(entry.getKey().toUpperCase(Locale.ROOT));
                // If it does use the sound key
                clickSound = entry.getValue();
                break;
            } catch (IllegalArgumentException ignored) {}
        }
        if (clickSound == null) {
            for (Sound sound : Sound.values()) {
                if (sound.name().contains("CLICK")) {
                    // Convert to sound key under the assumption that the enum name is just using underscores in the place of dots
                    clickSound = sound.name().toLowerCase(Locale.ROOT).replace('_', '.');
                    break;
                }
            }
        }
        setDefaultClickSound(clickSound);
    }

    /**
     * 使用特定的设置和一些物品创建一个新的GUI
     * @param plugin    您的插件
     * @param creator   用于创建后备库存的创建器
     * @param owner     持有此GUI的所有者，可使用{@link #get(InventoryHolder)}进行检索。
     *                  可以为<code>null</code>。
     * @param title     GUI的名称。这将是存储的标题。
     * @param rows      如何设置行。每个物品都会被分配一个字符。
     *                  空/缺失的物品将被填充为填充器。
     * @param items  GUI应该具有的{@link GuiItem}。您还可以稍后使用{@link #addItem(GuiItem)}。
     * @throws IllegalArgumentException 如果提供的行无法匹配到InventoryType，则抛出异常
     */
    public TopazUI(JavaPlugin plugin, InventoryCreator creator, InventoryHolder owner, String title, String[] rows, GuiItem... items) {
        this.plugin = plugin;
        this.creator = creator;
        this.owner = owner;
        this.title = title;
        this.listener = new GuiListener();

        width = ROW_WIDTHS[0];
        for (String row : rows) {
            if (row.length() > width) {
                width = row.length();
            }
        }
        for (int i = 0; i < ROW_WIDTHS.length && i < INVENTORY_TYPES.length; i++) {
            if (width < ROW_WIDTHS[i]) {
                width = ROW_WIDTHS[i];
            }
            if (width == ROW_WIDTHS[i]) {
                inventoryType = INVENTORY_TYPES[i];
                break;
            }
        }
        if (inventoryType == null) {
            throw new IllegalArgumentException("Could not match row setup to an inventory type!");
        }

        StringBuilder slotsBuilder = new StringBuilder();
        for (String row : rows) {
            if (row.length() < width) {
                double side = (width - row.length()) / 2.0;
                for (int i = 0; i < Math.floor(side); i++) {
                    slotsBuilder.append(" ");
                }
                slotsBuilder.append(row);
                for (int i = 0; i < Math.ceil(side); i++) {
                    slotsBuilder.append(" ");
                }
            } else if (row.length() == width) {
                slotsBuilder.append(row);
            } else {
                slotsBuilder.append(row, 0, width);
            }
        }
        slots = slotsBuilder.toString().toCharArray();
        itemSlots = new GuiItem[slots.length];

        addItems(items);
    }


    /**
     * 使用特定的设置和一些物品创建一个新的GUI
     * @param plugin    您的插件
     * @param owner     持有此GUI的所有者，可使用{@link #get(InventoryHolder)}进行检索。
     *                  可以为<code>null</code>。
     * @param title     GUI的名称。这将是存储的标题。
     * @param rows      如何设置行。每个物品都会被分配一个字符。
     *                  空/缺失的物品将被填充为填充器。
     * @param items  GUI应该具有的{@link GuiItem}。您还可以稍后使用{@link #addItem(GuiItem)}。
     * @throws IllegalArgumentException 如果提供的行无法匹配到InventoryType，则抛出异常
     */
    public TopazUI(JavaPlugin plugin, InventoryHolder owner, String title, String[] rows, GuiItem... items) {
        this(plugin, new InventoryCreator(
                        (gui, who, type) -> plugin.getServer().createInventory(new Holder(gui), type, gui.replaceVars(who, gui.getTitle())),
                        (gui, who, size) -> plugin.getServer().createInventory(new Holder(gui), size, gui.replaceVars(who, gui.getTitle()))),
                owner, title, rows, items);
    }

    /**
     * 创建一个新的最简单的GUI。它没有所有者，物品是可选的。
     * @param plugin    您的插件
     * @param title     GUI的名称。这将是存储的标题。
     * @param rows      如何设置行。每个物品都会被分配一个字符。
     *                  空/缺失的物品将被填充为填充器。
     * @param items  GUI应该具有的{@link GuiItem}。您还可以稍后使用{@link #addItem(GuiItem)}。
     * @throws IllegalArgumentException 如果提供的行无法匹配到InventoryType，则抛出异常
     */
    public TopazUI(JavaPlugin plugin, String title, String[] rows, GuiItem... items) {
        this(plugin, null, title, rows, items);
    }

    /**
     * 创建一个新的没有所有者的GUI，具有特定的设置和一些物品
     * @param plugin    您的插件
     * @param owner     持有此GUI的所有者，可使用{@link #get(InventoryHolder)}进行检索。
     *                  可以为<code>null</code>。
     * @param title     GUI的名称。这将是存储的标题。
     * @param rows      如何设置行。每个物品都会被分配一个字符。
     *                  空/缺失的物品将被填充为填充器。
     * @param items  GUI应该具有的{@link GuiItem}。您还可以稍后使用{@link #addItem(GuiItem)}。
     * @throws IllegalArgumentException 如果提供的行无法匹配到InventoryType，则抛出异常
     */
    public TopazUI(JavaPlugin plugin, InventoryHolder owner, String title, String[] rows, Collection<GuiItem> items) {
        this(plugin, owner, title, rows);
        addItems(items);
    }


    /**
     * 直接设置特定槽位的物品
     * @param slot      要添加的槽位
     * @param item   要添加的{@link GuiItem}
     * @throws IllegalArgumentException 如果提供的槽位小于0或等于/超过可用槽位数量，则抛出异常
     * @throws IllegalStateException 如果物品已经添加到GUI中，则抛出异常
     */
    public void setItem(int slot, GuiItem item) {
        if (slot < 0 || slot >= itemSlots.length) {
            // throw new IllegalArgumentException("Provided slots is outside available slots! (" + itemSlots.length + ")");
            throw new IllegalArgumentException("提供的槽位超出可用槽位范围！（" + itemSlots.length + "）");
        }
        if (item.getSlots().length > 0 || item.getGui() != null) {
            //throw new IllegalStateException("Item was already added to a gui!");
            throw new IllegalStateException("物品已经添加到GUI中！");
        }
        item.setSlots(new int[] {slot});
        item.setGui(this);
        itemSlots[slot] = item;
    }

    /**
     * 使用物品的槽位字符和GUI设置字符串直接将物品添加到GUI中
     * @param item   要添加的{@link GuiItem}
     */
    public void addItem(GuiItem item) {
        if (item.getSlots().length > 0 || item.getGui() != null) {
            //throw new IllegalStateException("Item was already added to a gui!");
            throw new IllegalStateException("物品已经添加到GUI中！");
        }
        items.put(item.getSlotChar(), item);
        item.setGui(this);
        int[] slots = getSlots(item.getSlotChar());
        item.setSlots(slots);
        for (int slot : slots) {
            itemSlots[slot] = item;
        }
    }

    private int[] getSlots(char slotChar) {
        ArrayList<Integer> slotList = new ArrayList<>();
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == slotChar) {
                slotList.add(i);
            }
        }
        return slotList.stream().mapToInt(Integer::intValue).toArray();
    }


    /**
     * 使用一种快速方法创建并添加{@link StaticGuiItem}。
     * @param slotChar  根据GUI设置字符串指定物品位置的字符
     * @param item      应该显示的物品
     * @param action    当玩家点击此物品时要运行的{@link GuiItem.Action}
     * @param text      要显示在此物品上的文本，占位符将自动替换，
     *                  有关占位符变量的列表，请参见{@link TopazUI#replaceVars}。
     *                  空文本字符串也将被过滤掉，如果要添加空行，请使用单个空格！
     *                  如果未设置/为空，将使用项目的默认名称
     */
    public void addItem(char slotChar, ItemStack item, GuiItem.Action action, String... text) {
        addItem(new StaticGuiItem(slotChar, item, action, text));
    }

    /**
     * 创建并添加一个没有操作的{@link StaticGuiItem}。
     * @param slotChar  根据GUI设置字符串指定物品位置的字符
     * @param item      应该显示的物品
     * @param text      要显示在此物品上的文本，占位符将自动替换，
     *                  有关占位符变量的列表，请参见{@link TopazUI#replaceVars}。
     *                  空文本字符串也将被过滤掉，如果要添加空行，请使用单个空格！
     *                  如果未设置/为空，将使用项目的默认名称
     */
    public void addItem(char slotChar, ItemStack item, String... text) {
        addItem(new StaticGuiItem(slotChar, item, null, text));
    }

    /**
     * 使用一种快速方法创建并添加{@link StaticGuiItem}。
     * @param slotChar      根据GUI设置字符串指定物品位置的字符
     * @param materialData  此物品物品的{@link MaterialData}
     * @param action        当玩家点击此物品时要运行的{@link GuiItem.Action}
     * @param text          要显示在此物品上的文本，占位符将自动替换，
     *                      有关占位符变量的列表，请参见{@link TopazUI#replaceVars}。
     *                      空文本字符串也将被过滤掉，如果要添加空行，请使用单个空格！
     *                      如果未设置/为空，将使用项目的默认名称
     */
    public void addItem(char slotChar, MaterialData materialData, GuiItem.Action action, String... text) {
        addItem(slotChar, materialData.toItemStack(1), action, text);
    }


    /**
     * 创建并添加一个{@link StaticGuiItem}。
     * @param slotChar  根据GUI设置字符串指定物品位置的字符
     * @param material  物品应具有的{@link Material}
     * @param data      此物品的材料数据的<code>byte</code>表示
     * @param action    当玩家点击此物品时要运行的{@link GuiItem.Action}
     * @param text      要显示在此物品上的文本，占位符将自动替换，
     *                  有关占位符变量的列表，请参见{@link TopazUI#replaceVars}。
     *                  空文本字符串也将被过滤掉，如果要添加空行，请使用单个空格！
     *                  如果未设置/为空，将使用项目的默认名称
     */
    public void addItem(char slotChar, Material material, byte data, GuiItem.Action action, String... text) {
        addItem(slotChar, new MaterialData(material, data), action, text);
    }

    /**
     * 创建并添加一个{@link StaticGuiItem}。
     * @param slotChar  根据GUI设置字符串指定物品位置的字符
     * @param material  物品应具有的{@link Material}
     * @param action    当玩家点击此物品时要运行的{@link GuiItem.Action}
     * @param text      要显示在此物品上的文本，占位符将自动替换，
     *                  有关占位符变量的列表，请参见{@link TopazUI#replaceVars}。
     *                  空文本字符串也将被过滤掉，如果要添加空行，请使用单个空格！
     *                  如果未设置/为空，将使用项目的默认名称
     */
    public void addItem(char slotChar, Material material, GuiItem.Action action, String... text) {
        addItem(slotChar, material, (byte) 0, action, text);
    }


    /**
     * 使用其槽字符位置将多个物品添加到GUI中。
     * @param items   要添加的{@link GuiItem}物品
     */
    public void addItems(GuiItem... items) {
        for (GuiItem item : items) {
            addItem(item);
        }
    }

    /**
     * 使用其槽字符位置将多个物品添加到GUI中。
     * @param items   要添加的{@link GuiItem}物品
     */
    public void addItems(Collection<GuiItem> items) {
        for (GuiItem item : items) {
            addItem(item);
        }
    }


    /**
     * 从GUI中移除特定物品。
     * @param item   要移除的物品
     * @return GUI是否包含此物品，并且是否已被移除
     */
    public boolean removeItem(GuiItem item) {
        boolean removed = items.remove(item.getSlotChar(), item);
        for (int slot : item.getSlots()) {
            if (itemSlots[slot] == item) {
                itemSlots[slot] = null;
                removed = true;
            }
        }
        return removed;
    }


    /**
     * 从GUI中的所有槽位中移除当前分配给特定槽位字符的物品
     * @param slotChar  槽位字符
     * @return 该槽位中的物品，如果没有则返回<code>null</code>
     */
    public GuiItem removeItem(char slotChar) {
        GuiItem item = getItem(slotChar);
        if (item != null) {
            removeItem(item);
        }
        return item;
    }

    /**
     * 从特定槽位中移除当前存在的物品。不会从其他槽位中移除该物品
     * @param slot  槽位
     * @return 该槽位中的物品，如果没有则返回<code>null</code>
     */
    public GuiItem removeItem(int slot) {
        if (slot < 0 || slot >= itemSlots.length) {
            return null;
        }
        GuiItem item = itemSlots[slot];
        itemSlots[slot] = null;
        return item;
    }

    /**
     * 设置空槽位的填充物品
     * @param item  填充物品的物品
     */
    public void setFiller(ItemStack item) {
        addItem(new StaticGuiItem(' ', item, " "));
    }

    /**
     * 获取填充物品
     * @return  空槽位的填充物品
     */
    public GuiItem getFiller() {
        return items.get(' ');
    }


    /**
     * 获取此GUI所在的页面编号。从零开始计数。仅影响组物品。
     * @param player    要查询页面编号的玩家
     * @return 页面编号
     */
    public int getPageNumber(@NotNull HumanEntity player) {
        return pageNumbers.getOrDefault(player.getUniqueId(), 0);
    }

    /**
     * 设置此GUI所在的页面编号，对所有玩家有效。从零开始计数。仅影响组物品。
     * @param pageNumber 要设置的页面编号
     */
    public void setPageNumber(int pageNumber) {
        for (UUID playerId : inventories.keySet()) {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player != null) {
                setPageNumber(player, pageNumber);
            }
        }
    }

    /**
     * 为玩家设置此GUI所在的页面编号，从零开始计数。仅影响组物品。
     * @param player        要设置页面编号的玩家
     * @param pageNumber    要设置的页面编号
     */
    public void setPageNumber(HumanEntity player, int pageNumber) {
        setPageNumberInternal(player, pageNumber);
        draw(player, false);
    }

    private void setPageNumberInternal(HumanEntity player, int pageNumber) {
        pageNumbers.put(player.getUniqueId(), Math.max(pageNumber, 0));
    }

    /**
     * 获取特定玩家在此GUI中的页面数量
     * @param player    要查询页面数量的玩家
     * @return 页面数量
     */
    public int getPageAmount(@NotNull HumanEntity player) {
        return pageAmounts.getOrDefault(player.getUniqueId(), 1);
    }


    /**
     * 为特定玩家设置GUI的页面数量
     * @param player        要查询页面数量的玩家
     * @param pageAmount    页面数量
     */
    private void setPageAmount(HumanEntity player, int pageAmount) {
        pageAmounts.put(player.getUniqueId(), pageAmount);
    }

    private void calculatePageAmount(HumanEntity player) {
        int pageAmount = 0;
        for (GuiItem item : items.values()) {
            int amount = calculateItemSize(player, item);
            if (amount > 0 && (pageAmount - 1) * item.getSlots().length < amount && item.getSlots().length > 0) {
                pageAmount = (int) Math.ceil((double) amount / item.getSlots().length);
            }
        }
        setPageAmount(player, pageAmount);
        if (getPageNumber(player) >= pageAmount) {
            setPageNumberInternal(player, Math.min(0, pageAmount - 1));
        }
    }

    private int calculateItemSize(HumanEntity player, GuiItem item) {
        if (item instanceof GuiItemGroup) {
            return ((GuiItemGroup) item).size();
        } else if (item instanceof GuiStorageItem) {
            return ((GuiStorageItem) item).getStorage().getSize();
        } else if (item instanceof DynamicGuiItem) {
            return calculateItemSize(player, ((DynamicGuiItem) item).getCachedItem(player));
        }
        return 0;
    }

    /**
     * 将此GUI显示给玩家
     * @param player    要显示GUI的玩家
     */
    public void show(HumanEntity player) {
        show(player, true);
    }


    /**
     * 将此GUI显示给玩家
     * @param player    要显示GUI的玩家
     * @param checkOpen 是否检查此GUI是否已经打开
     */
    public void show(HumanEntity player, boolean checkOpen) {
        // 将物品绘制到一个Inventory中，如果标题已更新，则在存在的情况下也强制重新创建Inventory
        draw(player, true, titleUpdated);
        if (titleUpdated || !checkOpen || !this.equals(getOpen(player))) {
            InventoryType type = player.getOpenInventory().getType();
            if (type != InventoryType.CRAFTING && type != InventoryType.CREATIVE) {
                // 如果玩家已经打开了一个GUI，我们假设该调用是来自该GUI。
                // 为了避免在InventoryClickEvent监听器中关闭它（这将导致错误），
                // 我们延迟一刻钟后再打开，以在处理完事件后运行
                runTask(player, () -> {
                    Inventory inventory = getInventory(player);
                    if (inventory != null) {
                        addHistory(player, this);
                        player.openInventory(inventory);
                    }
                });
            } else {
                Inventory inventory = getInventory(player);
                if (inventory != null) {
                    clearHistory(player);
                    addHistory(player, this);
                    player.openInventory(inventory);
                }
            }
        }
        // 重置指示标题是否已更改的字段
        titleUpdated = false;
    }

    /**
     * 构建GUI
     */
    public void build() {
        build(owner);
    }

    /**
     * 设置GUI的拥有者并构建它
     * @param owner     拥有GUI的 {@link InventoryHolder}
     */
    public void build(InventoryHolder owner) {
        setOwner(owner);
        listener.registerListeners();
    }

    /**
     * 绘制GUI中的物品。可以用于手动刷新GUI。更新任何动态物品。
     */
    public void draw() {
        for (UUID playerId : inventories.keySet()) {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player != null) {
                runTaskOrNow(player, () -> draw(player));
            }
        }
    }


    /**
     * 绘制GUI中的物品。可以用于手动刷新GUI。更新任何动态物品。
     * @param who 要绘制GUI的对象
     */
    public void draw(HumanEntity who) {
        draw(who, true);
    }

    /**
     * 绘制GUI中的物品。可以用于手动刷新GUI。
     * @param who           要绘制GUI的对象
     * @param updateDynamic 更新动态物品
     */
    public void draw(HumanEntity who, boolean updateDynamic) {
        draw(who, updateDynamic, false);
    }

    /**
     * 绘制GUI中的物品。可以用于手动刷新GUI。
     * @param who               要绘制GUI的对象
     * @param updateDynamic     更新动态物品
     * @param recreateInventory 重新创建inventory
     */
    public void draw(HumanEntity who, boolean updateDynamic, boolean recreateInventory) {
        if (updateDynamic) {
            updateItems(who, items.values());
        }
        calculatePageAmount(who);
        Inventory inventory = getInventory(who);
        if (inventory == null || recreateInventory) {
            build();
            if (slots.length != inventoryType.getDefaultSize()) {
                inventory = getInventoryCreator().getSizeCreator().create(this, who, slots.length);
            } else {
                inventory = getInventoryCreator().getTypeCreator().create(this, who, inventoryType);
            }
            inventories.put(who != null ? who.getUniqueId() : null, inventory);
        } else {
            inventory.clear();
        }
        for (int i = 0; i < inventory.getSize(); i++) {
            GuiItem item = getItem(i);
            if (item == null) {
                item = getFiller();
            }
            if (item != null) {
                inventory.setItem(i, item.getItem(who, i));
            }
        }
    }

    /**
     * 在下一个刻度上调度一个任务在 {@link HumanEntity}/主线程上运行
     * @param entity 要调度任务的 HumanEntity
     * @param task 要运行的任务
     */
    protected void runTask(HumanEntity entity, Runnable task) {
        if (FOLIA) {
            entity.getScheduler().run(plugin, st -> task.run(), null);
        } else {
            plugin.getServer().getScheduler().runTask(plugin, task);
        }
    }

    /**
     * 在全局区域/主线程上调度一个任务在下一个刻度运行
     * @param task 要运行的任务
     */
    protected void runTask(Runnable task) {
        if (FOLIA) {
            plugin.getServer().getGlobalRegionScheduler().run(plugin, st -> task.run());
        } else {
            plugin.getServer().getScheduler().runTask(plugin, task);
        }
    }

    /**
     * 在下一个刻度上调度一个任务在 {@link HumanEntity} 上运行
     * 如果当前线程已经是正确的线程，则立即执行
     * @param entity 要调度任务的 HumanEntity
     * @param task 要运行的任务
     */
    protected void runTaskOrNow(HumanEntity entity, Runnable task) {
        if (FOLIA) {
            if (plugin.getServer().isOwnedByCurrentRegion(entity)) {
                task.run();
            } else {
                entity.getScheduler().run(plugin, st -> task.run(), null);
            }
        } else {
            if (plugin.getServer().isPrimaryThread()) {
                task.run();
            } else {
                plugin.getServer().getScheduler().runTask(plugin, task);
            }
        }
    }

    /**
     * 更新物品集合中的所有动态物品。
     * @param who       要更新物品的玩家
     * @param items  要更新的物品集合
     */
    public static void updateItems(HumanEntity who, Collection<GuiItem> items) {
        for (GuiItem item : items) {
            if (item instanceof DynamicGuiItem) {
                ((DynamicGuiItem) item).update(who);
            } else if (item instanceof GuiItemGroup) {
                updateItems(who, ((GuiItemGroup) item).getItems());
            }
        }
    }

    /**
     * 关闭所有正在查看此 GUI 的玩家的 GUI
     */
    public void close() {
        close(true);
    }

    /**
     * 关闭所有正在查看此 GUI 的玩家的 GUI
     * @param clearHistory  是否完全关闭 GUI（通过清除历史记录）
     */
    public void close(boolean clearHistory) {
        for (Inventory inventory : inventories.values()) {
            for (HumanEntity viewer : new ArrayList<>(inventory.getViewers())) {
                close(viewer, clearHistory);
            }
        }
    }

    /**
     * 关闭特定玩家正在查看的 GUI
     * @param viewer    正在查看的玩家
     */
    public void close(HumanEntity viewer) {
        close(viewer, true);
    }

    /**
     * 关闭特定玩家正在查看的 GUI
     * @param viewer        正在查看的玩家
     * @param clearHistory  是否完全关闭 GUI（通过清除历史记录）
     */
    public void close(HumanEntity viewer, boolean clearHistory) {
        if (clearHistory) {
            clearHistory(viewer);
        }
        viewer.closeInventory();
    }

    /**
     * 销毁此 GUI。这将取消注册所有监听器并从 GUI_MAP 中移除它
     */
    public void destroy() {
        destroy(true);
    }

    private void destroy(boolean closeInventories) {
        if (closeInventories) {
            close();
        }
        for (Inventory inventory : inventories.values()) {
            inventory.clear();
        }
        inventories.clear();
        pageNumbers.clear();
        pageAmounts.clear();
        listener.unregisterListeners();
        removeFromMap();
    }


    /**
     * 将新的历史记录条目添加到历史记录末尾
     * @param player    要添加历史记录条目的玩家
     * @param gui       要添加到历史记录的 GUI
     */
    public static void addHistory(HumanEntity player, TopazUI gui) {
        GUI_HISTORY.putIfAbsent(player.getUniqueId(), new ArrayDeque<>());
        Deque<TopazUI> history = getHistory(player);
        if (history.peekLast() != gui) {
            history.add(gui);
        }
    }

    /**
     * 获取玩家的历史记录
     * @param player    要获取历史记录的玩家
     * @return          历史记录作为 TopazUI 的双向队列；返回空队列而不是 <code>null</code>！
     */
    public static Deque<TopazUI> getHistory(HumanEntity player) {
        return GUI_HISTORY.getOrDefault(player.getUniqueId(), new ArrayDeque<>());
    }

    /**
     * 在历史记录中返回上一个条目
     * @param player    要显示上一个 GUI 的玩家
     * @return          如果存在要显示的 GUI，则返回 <code>true</code>；否则返回 <code>false</code>
     */
    public static boolean goBack(HumanEntity player) {
        Deque<TopazUI> history = getHistory(player);
        history.pollLast();
        if (history.isEmpty()) {
            return false;
        }
        TopazUI previous = history.peekLast();
        if (previous != null) {
            previous.show(player, false);
        }
        return true;
    }

    /**
     * 清除玩家的历史记录
     * @param player    要清除历史记录的玩家
     * @return          历史记录
     */
    public static Deque<TopazUI> clearHistory(HumanEntity player) {
        Deque<TopazUI> previous = GUI_HISTORY.remove(player.getUniqueId());
        return previous != null ? previous : new ArrayDeque<>();
    }

    /**
     * 获取拥有此 GUI 的插件。应该是创建此 GUI 的插件。
     * @return 拥有此 GUI 的插件
     */
    public JavaPlugin getPlugin() {
        return plugin;
    }


    /**
     * 获取用于为此 GUI 创建自定义背包的辅助类。
     * 默认情况下，简单地使用 {@link org.bukkit.Bukkit#createInventory(InventoryHolder, int, String)}。
     * @return 所使用的背包创建器实例
     */
    public InventoryCreator getInventoryCreator() {
        return creator;
    }

    /**
     * 设置用于为此 GUI 创建自定义背包的辅助类。
     * 可用于创建更特殊的背包。
     * 默认情况下，简单地使用 {@link org.bukkit.Bukkit#createInventory(InventoryHolder, int, String)}。
     * 应返回一个能够容纳指定大小的容器背包。特殊背包可能会导致问题。
     * @param creator 新的背包创建器实例
     */
    public void setInventoryCreator(InventoryCreator creator) {
        this.creator = creator;
    }

    /**
     * 获取特定插槽的物品
     * @param slot  要获取物品的插槽
     * @return      GuiItem，如果插槽为空/没有物品，则返回 <code>null</code>
     */
    public GuiItem getItem(int slot) {
        return slot < 0 || slot >= itemSlots.length ? null : itemSlots[slot];
    }

    /**
     * 根据字符获取物品
     * @param c 要获取物品的字符
     * @return  GuiItem，如果没有对应的物品，则返回 <code>null</code>
     */
    public GuiItem getItem(char c) {
        return items.get(c);
    }


    /**
     * 获取此 GUI 的所有物品。该集合是不可修改的，使用 addItem 和 removeItem 方法修改此 GUI 中的物品。
     * @return 此 GUI 中所有物品的不可修改集合
     */
    public Collection<GuiItem> getItems() {
        return Collections.unmodifiableCollection(items.values());
    }

    /**
     * 设置此 GUI 的所有者。将删除先前的赋值。
     * @param owner GUI 的所有者
     */
    public void setOwner(InventoryHolder owner) {
        removeFromMap();
        this.owner = owner;
        if (owner instanceof Entity) {
            GUI_MAP.put(((Entity) owner).getUniqueId().toString(), this);
        } else if (owner instanceof BlockState) {
            GUI_MAP.put(((BlockState) owner).getLocation().toString(), this);
        }
    }

    /**
     * 获取此 GUI 的所有者。如果 GUI 没有所有者，则为 null。
     * @return 此 GUI 的 InventoryHolder
     */
    public InventoryHolder getOwner() {
        return owner;
    }


    /**
     * 检查此 GUI 的所有者是真实的还是虚假的
     * @return 如果所有者是真实的世界 InventoryHolder，则返回 <code>true</code>；如果为 null，则返回 <code>false</code>
     */
    public boolean hasRealOwner() {
        return owner != null;
    }

    /**
     * 获取当玩家在背包外部点击时运行的动作
     * @return 点击背包外部时运行的动作；可以为 null
     */
    public GuiItem.Action getOutsideAction() {
        return outsideAction;
    }

    /**
     * 设置当玩家在背包外部点击时运行的动作
     * @param outsideAction 当玩家在背包外部点击时运行的动作；可以为 null
     */
    public void setOutsideAction(GuiItem.Action outsideAction) {
        this.outsideAction = outsideAction;
    }

    /**
     * 获取当关闭此 GUI 时运行的动作
     * @return 当玩家关闭此背包时运行的动作；可以为 null
     */
    public CloseAction getCloseAction() {
        return closeAction;
    }

    /**
     * 设置当关闭此 GUI 时运行的动作；如果 GUI 应返回上一层，则应返回 true
     * @param closeAction 当玩家关闭此背包时运行的动作；可以为 null
     */
    public void setCloseAction(CloseAction closeAction) {
        this.closeAction = closeAction;
    }

    /**
     * 获取非静音 GUI 使用的点击声音，如果未设置特定的声音
     * @return 默认的点击声音，如果未设置则返回 null
     */
    public static String getDefaultClickSound() {
        return DEFAULT_CLICK_SOUND;
    }

    /**
     * 设置非静音 GUI 使用的点击声音，如果未设置特定的声音
     * @param defaultClickSound 默认的点击声音，如果设置为 null，则不播放声音
     */
    public static void setDefaultClickSound(String defaultClickSound) {
        DEFAULT_CLICK_SOUND = defaultClickSound;
    }


    /**
     * 设置在 GUI 中点击按钮（未阻止物品被取走的按钮）时播放的声音。
     * 填充物将不会播放点击声音。
     * @return 要播放的声音的键
     */
    public String getClickSound() {
        return clickSound;
    }

    /**
     * 设置在 GUI 中点击按钮（未阻止物品被取走的按钮）时播放的声音。
     * 填充物将不会播放点击声音。
     * @param soundKey  要播放的声音的键，如果为 null，则不播放声音（与 {@link #setSilent(boolean)} 相同效果）
     */
    public void setClickSound(String soundKey) {
        clickSound = soundKey;
    }

    /**
     * 获取此 GUI 在与能发出声音的物品交互时是否发出声音
     * @return 是否在与物品交互时发出声音
     */
    public boolean isSilent() {
        return silent;
    }

    /**
     * 设置此 GUI 在与能发出声音的物品交互时是否发出声音
     * @param silent 是否在与物品交互时发出声音
     */
    public void setSilent(boolean silent) {
        this.silent = silent;
    }

    private void removeFromMap() {
        if (owner instanceof Entity) {
            GUI_MAP.remove(((Entity) owner).getUniqueId().toString(), this);
        } else if (owner instanceof BlockState) {
            GUI_MAP.remove(((BlockState) owner).getLocation().toString(), this);
        }
    }


    /**
     * 获取注册到 InventoryHolder 的 GUI
     * @param holder    要获取 GUI 的 InventoryHolder
     * @return          注册到该 InventoryHolder 的 TopazUI，如果没有注册则返回 <code>null</code>
     */
    public static TopazUI get(InventoryHolder holder) {
        if (holder instanceof Entity) {
            return GUI_MAP.get(((Entity) holder).getUniqueId().toString());
        } else if (holder instanceof BlockState) {
            return GUI_MAP.get(((BlockState) holder).getLocation().toString());
        }
        return null;
    }

    /**
     * 获取玩家当前打开的 GUI
     * @param player    要获取 GUI 的玩家
     * @return          玩家当前打开的 TopazUI
     */
    public static TopazUI getOpen(HumanEntity player) {
        return getHistory(player).peekLast();
    }

    /**
     * 获取 GUI 的标题
     * @return  GUI 的标题
     */
    public String getTitle() {
        return title;
    }

    /**
     * 设置 GUI 的标题
     * @param title 要设置的 GUI 标题
     */
    public void setTitle(String title) {
        this.title = title;
        this.titleUpdated = true;
    }

    /**
     * 播放点击音效，例如当物品作为按钮时
     */
    public void playClickSound() {
        if (isSilent() || clickSound == null) return;
        for (Inventory inventory : inventories.values()) {
            for (HumanEntity humanEntity : inventory.getViewers()) {
                if (humanEntity instanceof Player) {
                    ((Player) humanEntity).playSound(humanEntity.getEyeLocation(), getClickSound(), 1, 1);
                }
            }
        }
    }

    /**
     * 获取背包。包范围，因为它只应由 TopazUI.Holder 使用
     * @return GUI 生成的背包
     */
    Inventory getInventory() {
        return getInventory(null);
    }

    /**
     * 获取特定玩家的背包
     * @param who 玩家，如果为 null，则尝试返回首次创建的背包；如果没有创建，则返回 null
     * @return GUI 生成的背包，如果找不到则返回 null
     */
    private Inventory getInventory(HumanEntity who) {
        return who != null ? inventories.get(who.getUniqueId()) : (inventories.isEmpty() ? null : inventories.values().iterator().next());
    }

    /**
     * 获取 GUI 的横向格子数
     * @return GUI 的宽度
     */
    int getWidth() {
        return width;
    }


    /**
     * 处理与此 GUI 中的插槽的交互
     * @param event     触发交互的事件
     * @param clickType 点击类型
     * @param slot      插槽
     * @param cursor    光标上的物品
     * @return 结果点击对象
     */
    private GuiItem.Click handleInteract(InventoryInteractEvent event, ClickType clickType, int slot, ItemStack cursor) {
        GuiItem.Action action = null;
        GuiItem item = null;
        if (slot >= 0) {
            item = getItem(slot);
            if (item != null) {
                action = item.getAction(event.getWhoClicked());
            }
        } else if (slot == -999) {
            action = outsideAction;
        } else {
            if (event instanceof InventoryClickEvent) {
                // 点击既不是顶部背包也不是外部
                // 例如，点击在底部背包中
                if (((InventoryClickEvent) event).getAction() == InventoryAction.COLLECT_TO_CURSOR) {
                    GuiItem.Click click = new GuiItem.Click(this, slot, clickType, cursor, null, event);
                    simulateCollectToCursor(click);
                    return click;
                } else if (((InventoryClickEvent) event).getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
                    // 这是我们无法处理的动作，取消事件
                    event.setCancelled(true);
                }
            }
            return null;
        }
        try {
            GuiItem.Click click = new GuiItem.Click(this, slot, clickType, cursor, item, event);
            if (action == null || action.onClick(click)) {
                event.setCancelled(true);
                if (event.getWhoClicked() instanceof Player) {
                    ((Player) event.getWhoClicked()).updateInventory();
                }
            }
            if (action != null) {
                // 假设发生了某些变化，重新绘制所有当前显示的背包
                for (UUID playerId : inventories.keySet()) {
                    if (!event.getWhoClicked().getUniqueId().equals(playerId)) {
                        Player player = plugin.getServer().getPlayer(playerId);
                        if (player != null) {
                            draw(player, false);
                        }
                    }
                }
                return click;
            }
        } catch (Throwable t) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player) {
                ((Player) event.getWhoClicked()).updateInventory();
            }
//            plugin.getLogger().log(Level.SEVERE, "Exception while trying to run action for click on "
//                    + (item != null ? item.getClass().getSimpleName() : "empty item")
//                    + " in slot " + slot + " of " + getTitle() + " GUI!");
            plugin.getLogger().log(Level.SEVERE, "尝试运行在 "
                    + (item != null ? item.getClass().getSimpleName() : "空物品")
                    + " 中的插槽 " + slot + " 的 " + getTitle() + " GUI 上的点击动作时发生异常！");
            t.printStackTrace();
        }
        return null;
    }


    private abstract class UnregisterableListener implements Listener {
        private final List<UnregisterableListener> listeners;
        private boolean listenersRegistered = false;

        private UnregisterableListener() {
            List<UnregisterableListener> listeners = new ArrayList<>();
            for (Class<?> innerClass : getClass().getDeclaredClasses()) {
                if (UnregisterableListener.class.isAssignableFrom(innerClass)) {
                    try {
                        UnregisterableListener listener = ((Class<? extends UnregisterableListener>) innerClass).getDeclaredConstructor(getClass()).newInstance(this);
                        if (!(listener instanceof OptionalListener) || ((OptionalListener) listener).isCompatible()) {
                            listeners.add(listener);
                        }
                    } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                        e.printStackTrace();
                    }
                }
            }
            this.listeners = Collections.unmodifiableList(listeners);
        }

        protected void registerListeners() {
            if (listenersRegistered) {
                return;
            }
            plugin.getServer().getPluginManager().registerEvents(this, plugin);
            for (UnregisterableListener listener : listeners) {
                listener.registerListeners();
            }
            listenersRegistered = true;
        }

        protected void unregisterListeners() {
            HandlerList.unregisterAll(this);
            for (UnregisterableListener listener : listeners) {
                listener.unregisterListeners();
            }
            listenersRegistered = false;
        }
    }

    private abstract class OptionalListener extends UnregisterableListener {
        private boolean isCompatible() {
            try {
                getClass().getMethods();
                getClass().getDeclaredMethods();
                return true;
            } catch (NoClassDefFoundError e) {
                return false;
            }
        }
    }

    /**
     * TopazUI 所需的所有监听器
     */
    private class GuiListener extends UnregisterableListener {

        @EventHandler(ignoreCancelled = true)
        private void onInventoryClick(InventoryClickEvent event) {
            if (event.getInventory().equals(getInventory(event.getWhoClicked()))) {

                int slot = -1;
                if (event.getRawSlot() < event.getView().getTopInventory().getSize()) {
                    slot = event.getRawSlot();
                } else if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
                    slot = event.getInventory().firstEmpty();
                }

                // 缓存原始光标
                ItemStack originalCursor = event.getCursor() != null ? event.getCursor().clone() : null;

                // 转发点击事件
                GuiItem.Click click = handleInteract(event, event.getClick(), slot, event.getCursor());

                // 如有必要，更新光标位置
                if (click != null && (originalCursor == null || !originalCursor.equals(click.getCursor()))) {
                    event.setCursor(click.getCursor());
                }
            } else if (hasRealOwner() && owner.equals(event.getInventory().getHolder())) {
                // 点击了同一所有者的不同 GUI 的底层背包
                // 假设底层背包发生了更改，重新绘制 GUI
                runTask(TopazUI.this::draw);
            }
        }

        @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
        public void onInventoryDrag(InventoryDragEvent event) {
            Inventory inventory = getInventory(event.getWhoClicked());
            if (event.getInventory().equals(inventory)) {
                // 如果只在一个插槽上进行拖动，则将其视为使用物品进行点击，并进行处理。
                if (event.getRawSlots().size() == 1) {
                    int slot = event.getRawSlots().iterator().next();
                    if (slot < event.getView().getTopInventory().getSize()) {
                        GuiItem.Click click = handleInteract(
                                event,
                                // 将拖动类型映射到导致它的按钮
                                event.getType() == DragType.SINGLE ? ClickType.RIGHT : ClickType.LEFT,
                                slot,
                                event.getOldCursor()
                        );

                        // 如有必要，更新光标位置
                        if (click != null && !event.getOldCursor().equals(click.getCursor())) {
                            event.setCursor(click.getCursor());
                        }
                    }
                    return;
                }

                int rest = 0;
                Map<Integer, ItemStack> resetSlots = new HashMap<>();
                for (Map.Entry<Integer, ItemStack> items : event.getNewItems().entrySet()) {
                    if (items.getKey() < inventory.getSize()) {
                        GuiItem item = getItem(items.getKey());
                        if (!(item instanceof GuiStorageItem)
                                || !((GuiStorageItem) item).setStorageItem(event.getWhoClicked(), items.getKey(), items.getValue())) {
                            ItemStack slotItem = event.getInventory().getItem(items.getKey());
                            if (!items.getValue().isSimilar(slotItem)) {
                                rest += items.getValue().getAmount();
                            } else if (slotItem != null) {
                                rest += items.getValue().getAmount() - slotItem.getAmount();
                            }
                            // items.getValue().setAmount(0); // 无法更改结果物品 :/
                            resetSlots.put(items.getKey(), event.getInventory().getItem(items.getKey())); // reset them manually
                        }
                    }
                }

                runTask(event.getWhoClicked(), () -> {
                    for (Map.Entry<Integer, ItemStack> items : resetSlots.entrySet()) {
                        event.getView().getTopInventory().setItem(items.getKey(), items.getValue());
                    }
                });

                if (rest > 0) {
                    int cursorAmount = event.getCursor() != null ? event.getCursor().getAmount() : 0;
                    if (!event.getOldCursor().isSimilar(event.getCursor())) {
                        event.setCursor(event.getOldCursor());
                        cursorAmount = 0;
                    }
                    int newCursorAmount = cursorAmount + rest;
                    if (newCursorAmount <= event.getCursor().getMaxStackSize()) {
                        event.getCursor().setAmount(newCursorAmount);
                    } else {
                        event.getCursor().setAmount(event.getCursor().getMaxStackSize());
                        ItemStack add = event.getCursor().clone();
                        int addAmount = newCursorAmount - event.getCursor().getMaxStackSize();
                        if (addAmount > 0) {
                            add.setAmount(addAmount);
                            for (ItemStack drop : event.getWhoClicked().getInventory().addItem(add).values()) {
                                event.getWhoClicked().getLocation().getWorld().dropItem(event.getWhoClicked().getLocation(), drop);
                            }
                        }
                    }
                }
            }
        }

        @EventHandler(priority = EventPriority.MONITOR)
        public void onInventoryClose(InventoryCloseEvent event) {
            Inventory inventory = getInventory(event.getPlayer());
            if (event.getInventory().equals(inventory)) {
                // 返回上一层。检查玩家是否在 GUI 中且具有历史记录。
                if (TopazUI.this.equals(getOpen(event.getPlayer()))) {
                    if (closeAction == null || closeAction.onClose(new Close(event.getPlayer(), TopazUI.this, event))) {
                        goBack(event.getPlayer());
                    } else {
                        clearHistory(event.getPlayer());
                    }
                }
                if (inventories.size() <= 1) {
                    destroy(false);
                } else {
                    inventory.clear();
                    for (HumanEntity viewer : inventory.getViewers()) {
                        if (viewer != event.getPlayer()) {
                            viewer.closeInventory();
                        }
                    }
                    inventories.remove(event.getPlayer().getUniqueId());
                    pageAmounts.remove(event.getPlayer().getUniqueId());
                    pageNumbers.remove(event.getPlayer().getUniqueId());
                    for (GuiItem item : getItems()) {
                        if (item instanceof DynamicGuiItem) {
                            ((DynamicGuiItem) item).removeCachedItem(event.getPlayer());
                        }
                    }
                }
            }
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onInventoryMoveItem(InventoryMoveItemEvent event) {
            if (hasRealOwner() && (owner.equals(event.getDestination().getHolder()) || owner.equals(event.getSource().getHolder()))) {
                runTask(TopazUI.this::draw);
            }
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onDispense(BlockDispenseEvent event) {
            if (hasRealOwner() && owner.equals(event.getBlock().getState())) {
                runTask(TopazUI.this::draw);
            }
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onBlockBreak(BlockBreakEvent event) {
            if (hasRealOwner() && owner.equals(event.getBlock().getState())) {
                destroy();
            }
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onEntityDeath(EntityDeathEvent event) {
            if (hasRealOwner() && owner.equals(event.getEntity())) {
                destroy();
            }
        }

        @EventHandler(priority = EventPriority.MONITOR)
        public void onPluginDisable(PluginDisableEvent event) {
            if (event.getPlugin() == plugin) {
                destroy();
            }
        }

        /**
         * 在旧版本中不可用的事件，因此请使用单独的监听器...
         */
        protected class ItemSwapGuiListener extends OptionalListener {

            @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
            public void onInventoryMoveItem(PlayerSwapHandItemsEvent event) {
                Inventory inventory = getInventory(event.getPlayer());
                if (event.getPlayer().getOpenInventory().getTopInventory().equals(inventory)) {
                    event.setCancelled(true);
                }
            }
        }
    }

    /**
     * 用于 GUI 的虚拟 InventoryHolder
     */
    public static class Holder implements InventoryHolder {
        private TopazUI gui;

        public Holder(TopazUI gui) {
            this.gui = gui;
        }

        @Override
        public Inventory getInventory() {
            return gui.getInventory();
        }

        public TopazUI getGui() {
            return gui;
        }
    }

    public static interface CloseAction {

        /**
         * 当玩家关闭 GUI 时执行。
         * @param close 关闭对象，保存有关此关闭的信息
         * @return 关闭后是否应返回上一层
         */
        boolean onClose(Close close);

    }

    public static class Close {
        private final HumanEntity player;
        private final TopazUI gui;
        private final InventoryCloseEvent event;

        public Close(HumanEntity player, TopazUI gui, InventoryCloseEvent event) {
            this.player = player;
            this.gui = gui;
            this.event = event;
        }

        public HumanEntity getPlayer() {
            return player;
        }

        public TopazUI getGui() {
            return gui;
        }

        public InventoryCloseEvent getEvent() {
            return event;
        }
    }

    /**
     * 使用显示名称和描述设置物品的文本。
     * 同时替换文本中的占位符，并过滤掉空行。
     * 使用一个空格创建空行。
     * @param item      要设置文本的 {@link ItemStack}
     * @param text      要设置的文本行
     * @deprecated 使用 {@link #setItemText(HumanEntity, ItemStack, String...)} 方法
     */
    @Deprecated
    public void setItemText(ItemStack item, String... text) {
        setItemText(null, item, text);
    }

    /**
     * 使用显示名称和描述设置物品的文本。
     * 同时替换文本中的占位符，并过滤掉空行。
     * 使用一个空格创建空行。
     * @param player    查看 GUI 的玩家
     * @param item      要设置文本的 {@link ItemStack}
     * @param text      要设置的文本行
     */
    public void setItemText(HumanEntity player, ItemStack item, String... text) {
        if (item != null && text != null && text.length > 0) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                String combined = replaceVars(player, Arrays.stream(text)
                        .map(s -> s == null ? " " : s)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.joining("\n")));
                String[] lines = combined.split("\n");
                if (text[0] != null) {
                    meta.setDisplayName(lines[0]);
                }
                if (lines.length > 1) {
                    meta.setLore(Arrays.asList(Arrays.copyOfRange(lines, 1, lines.length)));
                } else {
                    meta.setLore(null);
                }
                item.setItemMeta(meta);
            }
        }
    }

    /**
     * 使用与 GUI 状态相关的值替换文本中的一些占位符。替换颜色代码。<br>
     * 占位符包括：<br>
     * <code>%plugin%</code>    - 此 GUI 所属插件的名称。<br>
     * <code>%owner%</code>     - 此 GUI 的所有者名称。当所有者为 null 时，将为空字符串。<br>
     * <code>%title%</code>     - 此 GUI 的标题。<br>
     * <code>%page%</code>      - 此 GUI 所在的当前页数。<br>
     * <code>%nextpage%</code>  - 下一页。如果没有下一页，则为 "none"。<br>
     * <code>%prevpage%</code>  - 上一页。如果没有上一页，则为 "none"。<br>
     * <code>%pages%</code>     - 此 GUI 的总页数。
     * @param text          要替换占位符的文本
     * @param replacements  附加的替换值。i = 占位符, i+1 = 替换值
     * @return      替换了所有占位符的文本
     * @deprecated 使用 {@link #replaceVars(HumanEntity, String, String...)} 方法
     */
    @Deprecated
    public String replaceVars(String text, String... replacements) {
        return replaceVars(null, text, replacements);
    }

    /**
     * 根据GUI的状态，替换一些占位符的值。替换颜色代码。<br>
     * 占位符包括：<br>
     * <code>%plugin%</code>    - 此GUI所属的插件的名称。<br>
     * <code>%owner%</code>     - 此GUI的所有者的名称。如果所有者为null，则为空字符串。<br>
     * <code>%title%</code>     - 此GUI的标题。<br>
     * <code>%page%</code>      - 此GUI所在的当前页面。<br>
     * <code>%nextpage%</code>  - 下一页。如果没有下一页，则为"none"。<br>
     * <code>%prevpage%</code>  - 上一页。如果没有上一页，则为"none"。<br>
     * <code>%pages%</code>     - 此GUI的页面数量。
     * @param player        查看GUI的玩家
     * @param text          需要替换占位符的文本
     * @param replacements  额外的替换项。i = 占位符，i+1 = 替换项
     * @return      所有占位符都被替换后的文本
     */
    public String replaceVars(HumanEntity player, String text, String... replacements) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            map.putIfAbsent(replacements[i], replacements[i + 1]);
        }

        map.putIfAbsent("plugin", plugin.getName());
        try {
            map.putIfAbsent("owner", owner instanceof Nameable ? ((Nameable) owner).getCustomName() : "");
        } catch (NoSuchMethodError | NoClassDefFoundError e) {
            map.putIfAbsent("owner", owner instanceof Entity ? ((Entity) owner).getCustomName() : "");
        }
        map.putIfAbsent("title", title);
        map.putIfAbsent("page", String.valueOf(getPageNumber(player) + 1));
        map.putIfAbsent("nextpage", getPageNumber(player) + 1 < getPageAmount(player) ? String.valueOf(getPageNumber(player) + 2) : "none");
        map.putIfAbsent("prevpage", getPageNumber(player) > 0 ? String.valueOf(getPageNumber(player)) : "none");
        map.putIfAbsent("pages", String.valueOf(getPageAmount(player)));

        return ChatColor.translateAlternateColorCodes('&', replace(text, map));
    }

    /**
     * 在字符串中替换占位符
     * @param string        需要进行替换的字符串
     * @param replacements  用于替换占位符的内容。第n个索引是占位符，第n+1个是值。
     * @return 所有占位符都被替换后的字符串（使用配置的占位符前缀和后缀）
     */
    private String replace(String string, Map<String, String> replacements) {
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            String placeholder = "%" + entry.getKey() + "%";
            Pattern pattern = PATTERN_CACHE.get(placeholder);
            if (pattern == null) {
                PATTERN_CACHE.put(placeholder, pattern = Pattern.compile(placeholder, Pattern.LITERAL));
            }
            string = pattern.matcher(string).replaceAll(Matcher.quoteReplacement(entry.getValue() != null ? entry.getValue() : "null"));
        }
        return string;
    }

    /**
     * 模拟收集到光标的操作，同时考虑到不能被修改的物品
     * @param click 触发这个操作的点击事件
     */
    void simulateCollectToCursor(GuiItem.Click click) {
        if (!(click.getRawEvent() instanceof InventoryClickEvent)) {
            // 只有点击事件才能触发集合到光标
            return;
        }
        InventoryClickEvent event = (InventoryClickEvent) click.getRawEvent();

        ItemStack newCursor = click.getCursor().clone();

        boolean itemInGui = false;
        for (int i = 0; i < click.getRawEvent().getView().getTopInventory().getSize(); i++) {
            if (i != event.getRawSlot()) {
                ItemStack viewItem = click.getRawEvent().getView().getTopInventory().getItem(i);
                if (newCursor.isSimilar(viewItem)) {
                    itemInGui = true;
                }
                GuiItem item = getItem(i);
                if (item instanceof GuiStorageItem) {
                    GuiStorageItem storageItem = (GuiStorageItem) item;
                    ItemStack otherStorageItem = storageItem.getStorageItem(click.getWhoClicked(), i);
                    if (addToStack(newCursor, otherStorageItem)) {
                        if (otherStorageItem.getAmount() == 0) {
                            otherStorageItem = null;
                        }
                        storageItem.setStorageItem(i, otherStorageItem);
                        if (newCursor.getAmount() == newCursor.getMaxStackSize()) {
                            break;
                        }
                    }
                }
            }
        }

        if (itemInGui) {
            event.setCurrentItem(null);
            click.getRawEvent().setCancelled(true);
            if (click.getRawEvent().getWhoClicked() instanceof Player) {
                ((Player) click.getRawEvent().getWhoClicked()).updateInventory();
            }

            if (click.getItem() instanceof GuiStorageItem) {
                ((GuiStorageItem) click.getItem()).setStorageItem(click.getWhoClicked(), click.getSlot(), null);
            }

            if (newCursor.getAmount() < newCursor.getMaxStackSize()) {
                Inventory bottomInventory = click.getRawEvent().getView().getBottomInventory();
                for (ItemStack bottomIem : bottomInventory) {
                    if (addToStack(newCursor, bottomIem)) {
                        if (newCursor.getAmount() == newCursor.getMaxStackSize()) {
                            break;
                        }
                    }
                }
            }
            event.setCursor(newCursor);
            draw();
        }
    }

    /**
     * 添加物品到ItemStack中，直到达到最大堆叠数
     * @param item  基础物品
     * @param add   要添加的物品
     * @return <code>true</code> 代表堆叠完成; <code>false</code> 代表堆叠无法堆叠
     */
    private static boolean addToStack(ItemStack item, ItemStack add) {
        if (item.isSimilar(add)) {
            int newAmount = item.getAmount() + add.getAmount();
            if (newAmount >= item.getMaxStackSize()) {
                item.setAmount(item.getMaxStackSize());
                add.setAmount(newAmount - item.getAmount());
            } else {
                item.setAmount(newAmount);
                add.setAmount(0);
            }
            return true;
        }
        return false;
    }

    public static class InventoryCreator {
        private final CreatorImplementation<InventoryType> typeCreator;
        private final CreatorImplementation<Integer> sizeCreator;

        /**
         * 一个新的库存创建者，它能够根据类型和大小创建界面。
         * <br><br>
         * 默认情况下，创建者的实现如下：
         * <pre>
         * typeCreator = (gui, who, type) -> plugin.getServer().createInventory(new Holder(gui), type, gui.replaceVars(who, title));
         * sizeCreator = (gui, who, size) -> plugin.getServer().createInventory(new Holder(gui), size, gui.replaceVars(who, title));
         * </pre>
         * @param typeCreator 创建者的类型
         * @param sizeCreator 创建者的大小
         */
        public InventoryCreator(CreatorImplementation<InventoryType> typeCreator, CreatorImplementation<Integer> sizeCreator) {
            this.typeCreator = typeCreator;
            this.sizeCreator = sizeCreator;
        }

        public CreatorImplementation<InventoryType> getTypeCreator() {
            return typeCreator;
        }

        public CreatorImplementation<Integer> getSizeCreator() {
            return sizeCreator;
        }

        public interface CreatorImplementation<T> {
            /**
             * 创建一个新的背包界面
             * @param gui   TopazUI示例
             * @param who   谁打开了这个背包界面
             * @param t     背包界面的类型或大小
             * @return      创建的背包界面
             */
            Inventory create(TopazUI gui, HumanEntity who, T t);
        }
    }
}