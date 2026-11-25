package today.starain.module.impl.render;

import today.starain.Starain;
import today.starain.module.Category;
import today.starain.module.ClientModule;
import today.starain.value.impl.BooleanValue;
import today.starain.value.impl.NumberValue;

/**
 * 聊天渲染模块（从 HUD 中拆出）。关闭时完全回退到原版聊天（保留原版背景）。
 */
public class Chat extends ClientModule {

    private final BooleanValue background = new BooleanValue("Background", true, this);
    private final BooleanValue smooth = new BooleanValue("Smooth", false, this);
    private final BooleanValue chatHud = new BooleanValue("Chat HUD", true, this);
    private final NumberValue width = new NumberValue("Width", 80F, 400F, 320F, 1F, this, chatHud::get);
    private final NumberValue openHeight = new NumberValue("Open Height", 20F, 250F, 130F, 1F, this, chatHud::get);
    private final NumberValue closedHeight = new NumberValue("Closed Height", 20F, 250F, 130F, 1F, this, chatHud::get);
    private final NumberValue duration = new NumberValue("Duration", 1000F, 15000F, 5000F, 500F, this, chatHud::get);

    public Chat() {
        setName("Chat");
        setCategory(Category.RENDER);
    }

    private static Chat instance() {
        Starain starain = Starain.getInstance();
        if (starain == null || starain.getModuleManager() == null) {
            return null;
        }
        return starain.getModuleManager().getModule(Chat.class);
    }

    private static Chat active() {
        Chat chat = instance();
        return chat != null && chat.isEnabled() ? chat : null;
    }

    public static boolean isChatBackgroundEnabled() {
        Chat chat = active();
        return chat == null || chat.background.get();
    }

    public static boolean isSmoothChatEnabled() {
        Chat chat = active();
        return chat != null && chat.smooth.get();
    }

    public static boolean isChatHudEnabled() {
        Chat chat = active();
        return chat != null && chat.chatHud.get();
    }

    public static float getChatHudWidth() {
        Chat chat = instance();
        return chat == null ? 320F : chat.width.get().floatValue();
    }

    public static float getChatHudOpenHeight() {
        Chat chat = instance();
        return chat == null ? 130F : chat.openHeight.get().floatValue();
    }

    public static float getChatHudClosedHeight() {
        Chat chat = instance();
        return chat == null ? 130F : chat.closedHeight.get().floatValue();
    }

    public static float getChatHudDuration() {
        Chat chat = instance();
        return chat == null ? 5000F : chat.duration.get().floatValue();
    }
}
