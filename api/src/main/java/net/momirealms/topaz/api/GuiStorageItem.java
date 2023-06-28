package net.momirealms.topaz.api;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.function.Function;

/**
 * 用于访问{@link Inventory}的物品。通过搜索物品所在的整个GUI，并获取字符组中的位置号来选择存储库中的槽位。
 * 例如，如果在GUI设置中有五个名为"s"的字符，并且玩家访问第二个物品，则会将其转换为存储库中的第二个槽位。
 */

public class GuiStorageItem extends GuiItem {
    private final Inventory storage;
    private final int invSlot;
    private Runnable applyStorage;
    private Function<ValidatorInfo, Boolean> itemValidator;

    /**
     * 用于访问{@link Inventory}的物品。
     * @param slotChar  在GUI设置字符串中要替换的字符。
     * @param storage   与该物品关联的{@link Inventory}。
     */
    public GuiStorageItem(char slotChar, Inventory storage) {
        this(slotChar, storage, -1);
    }

    /**
     * 用于访问{@link Inventory}中特定槽位的物品。
     * @param slotChar  在GUI设置字符串中要替换的字符。
     * @param storage   与该物品关联的{@link Inventory}。
     * @param invSlot   在{@link Inventory}中访问的槽位的索引。
     */
    public GuiStorageItem(char slotChar, Inventory storage, int invSlot) {
        this(slotChar, storage, invSlot, null, null);
    }


    /**
     * 用于访问{@link Inventory}中特定槽位的物品。
     * @param slotChar      在GUI设置字符串中要替换的字符。
     * @param storage       与该物品关联的{@link Inventory}。
     * @param invSlot       在{@link Inventory}中访问的槽位的索引。
     * @param applyStorage  应用此物品表示的存储的操作。
     * @param itemValidator 对于不适用于该槽位的物品应返回<code>false</code>。
     *                      如果存储直接连接，则可以为null。
     */
    public GuiStorageItem(char slotChar, Inventory storage, int invSlot, Runnable applyStorage, Function<ValidatorInfo, Boolean> itemValidator) {
        super(slotChar, null);
        this.invSlot = invSlot;
        this.applyStorage = applyStorage;
        this.itemValidator = itemValidator;
        setAction(click -> {
            if (getStorageSlot(click.getWhoClicked(), click.getSlot()) < 0) {
                return true;
            }
            ItemStack storageItem = getStorageItem(click.getWhoClicked(), click.getSlot());
            ItemStack slotItem = click.getRawEvent().getView().getTopInventory().getItem(click.getSlot());
            if (slotItem == null && storageItem != null && storageItem.getType() != Material.AIR
                    || storageItem == null && slotItem != null && slotItem.getType() != Material.AIR
                    || storageItem != null && !storageItem.equals(slotItem)) {
                gui.draw(click.getWhoClicked(), false);
                return true;
            }

            if (!(click.getRawEvent() instanceof InventoryClickEvent event)) {
                // 仅处理单击事件，拖拽事件单独处理
                return true;
            }

            ItemStack movedItem = null;
            switch (event.getAction()) {
                case NOTHING:
                case CLONE_STACK:
                    return false;
                case MOVE_TO_OTHER_INVENTORY:
                    if (event.getRawSlot() < click.getRawEvent().getView().getTopInventory().getSize()) {
                        // 从存储中移动

                        // 检查是否有空间（当前不支持更高级的检查）
                        if (click.getRawEvent().getView().getBottomInventory().firstEmpty() == -1) {
                            // 没有空槽位，取消
                            return true;
                        }
                        movedItem = null;
                    } else {
                        // 移动到存储

                        // 检查是否有空间（当前不支持更高级的检查）
                        if (click.getRawEvent().getView().getTopInventory().firstEmpty() == -1) {
                            // 没有空槽位，取消
                            return true;
                        }
                        movedItem = event.getCurrentItem();
                    }
                    // 更新GUI以避免显示错误
                    gui.runTask(gui::draw);
                    break;
                case HOTBAR_MOVE_AND_READD:
                case HOTBAR_SWAP:
                    int button = event.getHotbarButton();
                    if (button < 0) {
                        return true;
                    }
                    ItemStack hotbarItem = click.getRawEvent().getView().getBottomInventory().getItem(button);
                    if (hotbarItem != null) {
                        movedItem = hotbarItem.clone();
                    }
                    break;
                case PICKUP_ONE:
                case DROP_ONE_SLOT:
                    if (event.getCurrentItem() != null) {
                        movedItem = event.getCurrentItem().clone();
                        movedItem.setAmount(movedItem.getAmount() - 1);
                    }
                    break;
                case DROP_ALL_SLOT:
                    movedItem = null;
                    break;
                case PICKUP_HALF:
                    if (event.getCurrentItem() != null) {
                        movedItem = event.getCurrentItem().clone();
                        movedItem.setAmount(movedItem.getAmount() / 2);
                    }
                    break;
                case PLACE_SOME:
                    if (event.getCurrentItem() == null) {
                        if (event.getCursor() != null) {
                            movedItem = event.getCursor().clone();
                        }
                    } else {
                        movedItem = event.getCurrentItem().clone();
                        int newAmount = movedItem.getAmount() + (event.getCursor() != null ? event.getCursor().getAmount() : 0);
                        if (newAmount < movedItem.getMaxStackSize()) {
                            movedItem.setAmount(newAmount);
                        } else {
                            movedItem.setAmount(movedItem.getMaxStackSize());
                        }
                    }
                    break;
                case PLACE_ONE:
                    if (event.getCursor() != null) {
                        if (event.getCurrentItem() == null) {
                            movedItem = event.getCursor().clone();
                            movedItem.setAmount(1);
                        } else {
                            movedItem = event.getCursor().clone();
                            movedItem.setAmount(event.getCurrentItem().getAmount() + 1);
                        }
                    }
                    break;
                case PLACE_ALL:
                    if (event.getCursor() != null) {
                        movedItem = event.getCursor().clone();
                        if (event.getCurrentItem() != null && event.getCurrentItem().getAmount() > 0) {
                            movedItem.setAmount(event.getCurrentItem().getAmount() + movedItem.getAmount());
                        }
                    }
                    break;
                case PICKUP_ALL:
                case SWAP_WITH_CURSOR:
                    if (event.getCursor() != null) {
                        movedItem = event.getCursor().clone();
                    }
                    break;
                case COLLECT_TO_CURSOR:
                    if (event.getCursor() == null
                            || event.getCurrentItem() != null && event.getCurrentItem().getType() != Material.AIR) {
                        return true;
                    }
                    gui.simulateCollectToCursor(click);
                    return false;
                default:
                    click.getRawEvent().getWhoClicked().sendMessage(ChatColor.RED + "不支持动作" + event.getAction() + "！抱歉：(");
                    return true;
            }
            return !setStorageItem(click.getWhoClicked(), click.getSlot(), movedItem);
        });
        this.storage = storage;
    }


    @Override
    public ItemStack getItem(HumanEntity who, int slot) {
        int index = getStorageSlot(who, slot);
        if (index > -1 && index < storage.getSize()) {
            return storage.getItem(index);
        }
        return null;
    }

    /**
     * 获取与该物品关联的{@link Inventory}。
     * @return 与该物品关联的{@link Inventory}。
     */
    public Inventory getStorage() {
        return storage;
    }


    /**
     * 获取与InventoryGui槽位对应的存储库槽位索引
     * @param player    使用GUI的玩家
     * @param slot      GUI中的槽位
     * @return          存储库槽位的索引，如果超出存储库范围则返回<code>-1</code>
     */
    private int getStorageSlot(HumanEntity player, int slot) {
        int index = invSlot != -1 ? invSlot : getSlotIndex(slot, gui.getPageNumber(player));
        if (index < 0 || index >= storage.getSize()) {
            return -1;
        }
        return index;
    }

    /**
     * 获取与InventoryGui槽位对应的存储库中的物品
     * @param slot  GUI中的槽位
     * @return      {@link ItemStack}，如果槽位超出物品大小范围则返回<code>null</code>
     * @deprecated 使用{@link #getStorageItem(HumanEntity, int)}
     */
    @Deprecated
    public ItemStack getStorageItem(int slot) {
        return getStorageItem(null, slot);
    }

    /**
     * 获取与InventoryGui槽位对应的存储库中的物品
     * @param player    使用GUI的玩家
     * @param slot      GUI中的槽位
     * @return          {@link ItemStack}，如果槽位超出物品大小范围则返回<code>null</code>
     */
    public ItemStack getStorageItem(HumanEntity player, int slot) {
        int index = getStorageSlot(player, slot);
        if (index == -1) {
            return null;
        }
        return storage.getItem(index);
    }


    /**
     * 将与InventoryGui槽位对应的存储库中的物品设置为指定的{@link ItemStack}。
     * @param slot  GUI中的槽位
     * @param item  要设置的{@link ItemStack}
     * @return      如果设置成功则返回<code>true</code>；如果槽位不在该存储库范围内则返回<code>false</code>
     * @deprecated 使用{@link #setStorageItem(HumanEntity, int, ItemStack)}
     */
    @Deprecated
    public boolean setStorageItem(int slot, ItemStack item) {
        return setStorageItem(null, slot, item);
    }

    /**
     * 将与InventoryGui槽位对应的存储库中的物品设置为指定的{@link ItemStack}。
     * @param player    使用GUI的玩家
     * @param slot      GUI中的槽位
     * @param item      要设置的{@link ItemStack}
     * @return      如果设置成功则返回<code>true</code>；如果槽位不在该存储库范围内则返回<code>false</code>
     */
    public boolean setStorageItem(HumanEntity player, int slot, ItemStack item) {
        int index = getStorageSlot(player, slot);
        if (index == -1) {
            return false;
        }
        if (!validateItem(slot, item)) {
            return false;
        }
        storage.setItem(index, item);
        if (applyStorage != null) {
            applyStorage.run();
        }
        return true;
    }

    /**
     * 获取应用存储的操作
     * @return 应用存储的操作，可能为null
     */
    public Runnable getApplyStorage() {
        return applyStorage;
    }


    /**
     * 设置应用存储的操作。
     * 如果存储直接由真实的存储库支持，则不需要设置。
     * @param applyStorage  应用存储的操作；如果不需要执行任何操作，则可以为null
     */
    public void setApplyStorage(Runnable applyStorage) {
        this.applyStorage = applyStorage;
    }

    /**
     * 获取物品验证器
     * @return  物品验证器
     */
    public Function<ValidatorInfo, Boolean> getItemValidator() {
        return itemValidator;
    }

    /**
     * 设置一个函数，用于验证物品是否适合放置在槽位中
     * @param itemValidator 物品验证器，接受一个{@link ValidatorInfo}参数，对于适合放置在槽位中的物品返回<code>true</code>，对于不适合放置在槽位中的物品返回<code>false</code>
     */
    public void setItemValidator(Function<ValidatorInfo, Boolean> itemValidator) {
        this.itemValidator = itemValidator;
    }


    /**
     * 验证是否可以将物品放置在具有在{@link #setItemValidator(Function)}中设置的物品验证器的槽位中
     * @param slot  要测试的槽位
     * @param item  要测试的物品
     * @return      <code>true</code>表示物品可以放置在该槽位中，<code>false</code>表示物品不可以放置在该槽位中
     */
    public boolean validateItem(int slot, ItemStack item) {
        return itemValidator == null || itemValidator.apply(new ValidatorInfo(this, slot, item));
    }

    public static class ValidatorInfo {
        private final GuiItem guiItem;
        private final int slot;
        private final ItemStack item;

        public ValidatorInfo(GuiItem guiItem, int slot, ItemStack item) {
            this.item = item;
            this.slot = slot;
            this.guiItem = guiItem;
        }

        public GuiItem getGuiItem() {
            return guiItem;
        }

        public int getSlot() {
            return slot;
        }

        public ItemStack getItem() {
            return item;
        }
    }

}