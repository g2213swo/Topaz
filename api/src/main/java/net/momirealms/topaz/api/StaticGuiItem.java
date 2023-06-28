package net.momirealms.topaz.api;

import org.bukkit.entity.HumanEntity;
import org.bukkit.inventory.ItemStack;

/**
 * 表示GUI中的静态简单物品，可以为其分配操作。
 * 如果您希望在单击时更改物品，您需要自己进行处理。
 */
public class StaticGuiItem extends GuiItem {
    private ItemStack item;
    private int number;
    private String[] text;

    /**
     * 表示GUI中的物品
     * @param slotChar  在GUI设置字符串中要替换的字符
     * @param item      此物品显示的物品
     * @param number    数量，1将不显示数量
     * @param action    当玩家点击此物品时要执行的操作
     * @param text      要显示在此物品上的文本，占位符将自动替换，有关占位符变量的列表，请参阅{@link TopazUI#replaceVars}。
     *                  空文本字符串也会被过滤掉，如果要添加空行，请使用一个空格！<br>
     *                  如果未设置/为空，则将使用项的默认名称。
     * @throws IllegalArgumentException 如果数量低于1或高于最大堆叠数量（当前为64）
     */
    public StaticGuiItem(char slotChar, ItemStack item, int number, Action action, String... text) throws IllegalArgumentException {
        super(slotChar, action);
        this.item = item;
        this.text = text;
        setNumber(number);
    }

    /**
     * 表示GUI中的物品
     * @param slotChar  在GUI设置字符串中要替换的字符
     * @param item      此物品显示的物品
     * @param action    当玩家点击此物品时要执行的操作
     * @param text      要显示在此物品上的文本，占位符将自动替换，有关占位符变量的列表，请参阅{@link TopazUI#replaceVars}。
     *                  空文本字符串也会被过滤掉，如果要添加空行，请使用一个空格！<br>
     *                  如果未设置/为空，则将使用项的默认名称。
     */
    public StaticGuiItem(char slotChar, ItemStack item, Action action, String... text) {
        this(slotChar, item, item != null ? item.getAmount() : 1, action, text);
    }


    /**
     * 表示GUI中没有任何点击操作的物品
     * @param slotChar  在GUI设置字符串中要替换的字符
     * @param item      此物品显示的物品
     * @param text      要显示在此物品上的文本，占位符将自动替换，有关占位符变量的列表，请参阅{@link TopazUI#replaceVars}。
     *                  空文本字符串也会被过滤掉，如果要添加空行，请使用一个空格！<br>
     *                  如果未设置/为空，则将使用项的默认名称。
     */
    public StaticGuiItem(char slotChar, ItemStack item, String... text) {
        this(slotChar, item, item != null ? item.getAmount() : 1, null, text);
    }


    /**
     * 设置此物品显示的物品
     * @param item  应该由此物品显示的物品
     */
    public void setItem(ItemStack item) {
        this.item = item;
    }

    /**
     * 获取此物品显示的原始物品，它是通过构造函数传递或使用{@link #setItem(ItemStack)}设置的。
     * 此物品不会应用数量或文本！使用{@link #getItem(HumanEntity, int)}来获取带有数量和文本的物品！
     * @return  原始物品
     */
    public ItemStack getRawItem() {
        return item;
    }

    @Override
    public ItemStack getItem(HumanEntity who, int slot) {
        if (item == null) {
            return null;
        }
        ItemStack clone = item.clone();
        gui.setItemText(who, clone, getText());
        if (number > 0 && number <= 64) {
            clone.setAmount(number);
        }
        return clone;
    }


    /**
     * 设置该物品的显示文本。如果这是一个空数组，将显示该项的名称。
     * @param text  要显示在此物品上的文本，占位符将自动替换，有关占位符变量的列表，请参阅{@link TopazUI#replaceVars}。
     *              空文本字符串也会被过滤掉，如果要添加空行，请使用一个空格！<br>
     *              如果未设置/为空，则将使用项的默认名称。
     */
    public void setText(String... text) {
        this.text = text;
    }

    /**
     * 获取此物品显示的文本
     * @return  显示在此物品上的文本
     */
    public String[] getText() {
        return text;
    }

    /**
     * 设置此物品应显示的数字（通过项的数量）
     * @param number    数字，1表示不显示数字
     * @return          如果设置了数字，则返回<code>true</code>；如果数字小于1或大于64，则返回<code>false</code>
     */
    public boolean setNumber(int number) {
        if (number < 1 || number > 64) {
            this.number = 1;
            return false;
        }
        this.number = number;
        return true;
    }

    /**
     * 获取此物品应显示的数字
     * @return 当前物品的数字（项数量）
     */
    public int getNumber() {
        return number;
    }

}
