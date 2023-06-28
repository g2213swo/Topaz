package net.momirealms.topaz.api;

import org.bukkit.entity.HumanEntity;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryInteractEvent;
import org.bukkit.inventory.ItemStack;

/**
 * 在GUI中表示一个物品
 */
public abstract class GuiItem {
    private final char slotChar;
    private Action action;
    protected int[] slots = new int[0];
    protected TopazUI gui;

    /**
     * 在GUI中表示一个物品
     *
     * @param slotChar 要替换的GUI设置字符串中的字符
     * @param action   当玩家点击此物品时要执行的动作
     */
    public GuiItem(char slotChar, Action action) {
        this.slotChar = slotChar;
        setAction(action);
    }

    /**
     * 在GUI中表示一个没有任何点击动作的物品
     *
     * @param slotChar 要替换的GUI设置字符串中的字符
     */
    public GuiItem(char slotChar) {
        this(slotChar, null);
    }

    /**
     * 获取与此物品对应的GUI设置中的字符
     *
     * @return 字符
     */
    public char getSlotChar() {
        return slotChar;
    }

    /**
     * 获取在特定页面上此物品显示的物品
     *
     * @param who  查看页面的玩家
     * @param slot 要获取物品的槽位
     * @return 作为此物品显示的ItemStack
     */
    public abstract ItemStack getItem(HumanEntity who, int slot);


    /**
     * 获取在点击该物品时执行的动作
     *
     * @param who 查看页面的玩家
     * @return 要执行的动作
     */
    public Action getAction(HumanEntity who) {
        return action;
    }

    /**
     * 设置在点击该物品时要执行的动作
     *
     * @param action 要执行的动作。{@link Action#onClick} 方法应返回点击事件是否应该被取消
     */
    public void setAction(Action action) {
        this.action = action;
    }

    /**
     * 获取该物品所显示槽位的索引
     *
     * @return 该槽位的索引数组
     */
    public int[] getSlots() {
        return slots;
    }

    /**
     * 设置该物品所显示的槽位的ID
     *
     * @param slots 该物品所显示的槽位的ID数组
     */
    public void setSlots(int[] slots) {
        this.slots = slots;
    }

    /**
     * 获取该槽位在物品显示槽位列表中的索引
     *
     * @param slot 槽位的ID
     * @return 该ID在槽位列表中的索引，如果不存在则返回-1
     */
    public int getSlotIndex(int slot) {
        return getSlotIndex(slot, 0);
    }


    /**
     * 获取该槽位在物品显示槽位列表中的索引
     *
     * @param slot       槽位的ID
     * @param pageNumber GUI所在的页面编号
     * @return 该ID在槽位列表中的索引，如果不存在则返回-1
     */
    public int getSlotIndex(int slot, int pageNumber) {
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == slot) {
                return i + slots.length * pageNumber;
            }
        }
        return -1;
    }

    /**
     * 设置该物品所属的GUI
     *
     * @param gui 该物品所在的GUI
     */
    public void setGui(TopazUI gui) {
        this.gui = gui;
    }

    /**
     * 获取该物品所属的GUI
     *
     * @return 该物品所在的GUI
     */
    public TopazUI getGui() {
        return gui;
    }

    public interface Action {

        /**
         * 当玩家点击物品时执行
         *
         * @param click 包含有关点击的信息的Click类
         * @return 点击事件是否应取消
         */
        boolean onClick(Click click);

    }


    public static class Click {
        private final TopazUI gui;
        private final int slot;
        private final ClickType clickType;
        private ItemStack cursor;
        private final GuiItem item;
        private final InventoryInteractEvent event;

        public Click(TopazUI gui, int slot, ClickType clickType, ItemStack cursor, GuiItem item, InventoryInteractEvent event) {
            this.gui = gui;
            this.slot = slot;
            this.clickType = clickType;
            this.cursor = cursor;
            this.item = item;
            this.event = event;
        }

        /**
         * 获取被点击的GUI槽位
         *
         * @return 被点击的槽位
         */
        public int getSlot() {
            return slot;
        }

        /**
         * 获取被点击的物品
         *
         * @return 被点击的GuiItem
         */
        public GuiItem getItem() {
            return item;
        }

        /**
         * 获取点击类型
         *
         * @return 点击类型
         */
        public ClickType getType() {
            return clickType;
        }

        /**
         * 获取光标上的物品
         *
         * @return 点击发生时光标上的物品
         */
        public ItemStack getCursor() {
            return cursor;
        }

        /**
         * 设置点击后的光标上的物品
         *
         * @param cursor 新的光标上的物品
         */
        public void setCursor(ItemStack cursor) {
            this.cursor = cursor;
        }

        /**
         * 获取点击物品的玩家
         *
         * @return 点击的玩家
         */
        public HumanEntity getWhoClicked() {
            return event.getWhoClicked();
        }

        /**
         * 获取与该点击相关的InventoryInteractEvent事件
         *
         * @return 与该点击相关的InventoryInteractEvent事件
         */
        public InventoryInteractEvent getRawEvent() {
            return event;
        }

        public TopazUI getGui() {
            return gui;
        }
    }
}