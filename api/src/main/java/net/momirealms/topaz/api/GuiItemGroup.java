package net.momirealms.topaz.api;

import org.bukkit.entity.HumanEntity;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * 表示多个物品的组。默认情况下，将使用左对齐。
 */
public class GuiItemGroup extends GuiItem {
    private List<GuiItem> items = new ArrayList<>();
    private GuiItem filler = null;
    private Alignment alignment = Alignment.LEFT;

    /**
     * 创建一个物品组
     *
     * @param slotChar 要替换的 GUI 设置字符串中的字符
     * @param items 组中的物品
     */
    public GuiItemGroup(char slotChar, GuiItem... items) {
        super(slotChar, null);
        setAction(click -> {
            GuiItem guiItem = getItem(click.getSlot(), click.getGui().getPageNumber(click.getWhoClicked()));
            if (guiItem != null && guiItem.getAction(click.getRawEvent().getWhoClicked()) != null) {
                return guiItem.getAction(click.getWhoClicked()).onClick(click);
            }
            return true;
        });
        Collections.addAll(this.items, items);
    }

    @Override
    public ItemStack getItem(HumanEntity who, int slot) {
        GuiItem guiItem = getItem(slot, gui.getPageNumber(who));
        if (guiItem != null) {
            return guiItem.getItem(who, slot);
        }
        return null;
    }

    @Override
    public void setGui(TopazUI gui) {
        super.setGui(gui);
        for (GuiItem guiItem : items) {
            if (guiItem != null) {
                guiItem.setGui(gui);
            }
        }
        if (filler != null) {
            filler.setGui(gui);
        }
    }

    @Override
    public void setSlots(int[] slots) {
        super.setSlots(slots);
        for (GuiItem guiItem : items) {
            if (guiItem != null) {
                guiItem.setSlots(slots);
            }
        }
    }

    /**
     * 向该组中添加一个物品
     *
     * @param guiItem 要添加的物品
     */
    public void addItems(GuiItem guiItem) {
        items.add(guiItem);
        if (guiItem != null) {
            guiItem.setGui(gui);
            guiItem.setSlots(slots);
        }
    }

    /**
     * 向该组添加物品
     *
     * @param items 要添加的物品
     */
    public void addItems(GuiItem... items) {
        for (GuiItem guiItem : items) {
            addItems(guiItem);
        }
    }

    /**
     * 向该组添加物品
     *
     * @param items 要添加的物品
     */
    public void addItems(Collection<GuiItem> items) {
        for (GuiItem item : items) {
            addItems(item);
        }
    }

    /**
     * 获取特定槽位的物品
     *
     * @param slot 要获取物品的槽位
     * @return 该槽位上的 GuiItem，如果不存在则返回 <code>null</code>
     */
    public GuiItem getItem(int slot) {
        return getItem(slot, 0);
    }

    /**
     * 获取特定页面的特定槽位上的物品
     *
     * @param slot       要获取物品的槽位
     * @param pageNumber GUI 所在的页面编号
     * @return 该槽位上的 GuiItem，如果不存在则返回 <code>null</code>
     */
    public GuiItem getItem(int slot, int pageNumber) {
        if (items.isEmpty()) {
            return null;
        }
        int index = getSlotIndex(slot, slots.length < items.size() ? pageNumber : 0);
        if (index > -1) {
            if (alignment == Alignment.LEFT) {
                if (index < items.size()) {
                    return items.get(index);
                }
            } else {
                int lineWidth = getLineWidth(slot);
                int linePosition = getLinePosition(slot);
                if (items.size() - index > lineWidth - linePosition) {
                    return items.get(index);
                }
                int rest = items.size() - (index - linePosition);
                int blankBefore = alignment == Alignment.CENTER ? (lineWidth - rest) / 2 : lineWidth - rest;
                if (linePosition < blankBefore || index - blankBefore >= items.size()) {
                    return filler;
                }
                return items.get(index - blankBefore);
            }
        }
        return filler;
    }


    /**
     * 获取槽位所在行的宽度
     *
     * @param slot 槽位
     * @return 该行在此组的 GUI 设置中的宽度
     */
    private int getLineWidth(int slot) {
        int width = gui.getWidth();
        int row = slot / width;

        int amount = 0;
        for (int s : slots) {
            if (s >= row * width && s < (row + 1) * width) {
                amount++;
            }
        }
        return amount;
    }

    /**
     * 获取槽位在其所在行中的位置
     *
     * @param slot 槽位 ID
     * @return 行位置，如果不在其行中则返回 -1
     */
    private int getLinePosition(int slot) {
        int width = gui.getWidth();
        int row = slot / width;

        int position = -1;
        for (int s : slots) {
            if (s >= row * width && s < (row + 1) * width) {
                position++;
                if (s == slot) {
                    return position;
                }
            }
        }
        return position;
    }

    /**
     * 获取该组的所有物品。此列表是不可修改的，可以使用 {@link #addItems(GuiItem)}
     * 和 {@link #clearItems()} 方法来修改该组中的物品。
     *
     * @return 该组中所有物品的不可修改列表
     */
    public List<GuiItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    /**
     * 清除该组中的所有物品
     */
    public void clearItems() {
        items.clear();
    }

    /**
     * 设置空槽位的填充物品
     *
     * @param item 填充物品的 ItemStack
     */
    public void setFiller(ItemStack item) {
        filler = new StaticGuiItem(' ', item, " ");
        filler.setGui(gui);
    }


    /**
     * 设置空槽位的填充物品
     *
     * @param filler 填充物品的 GuiItem
     */
    public void setFiller(GuiItem filler) {
        this.filler = filler;
        if (filler != null) {
            filler.setGui(gui);
        }
    }

    /**
     * 获取填充物品
     *
     * @return 填充物品
     */
    public GuiItem getFiller() {
        return filler;
    }

    /**
     * 获取该组的大小
     *
     * @return 该组物品的数量
     */
    public int size() {
        return items.size();
    }

    /**
     * 设置该组中物品的对齐方式
     *
     * @param alignment 对齐方式
     */
    public void setAlignment(Alignment alignment) {
        this.alignment = alignment;
    }

    /**
     * 获取该组中物品的对齐方式
     *
     * @return 对齐方式
     */
    public Alignment getAlignment() {
        return alignment;
    }

    public enum Alignment {
        LEFT,
        CENTER,
        RIGHT
    }
}
