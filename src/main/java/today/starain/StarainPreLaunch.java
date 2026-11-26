package today.starain;

import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import today.starain.utils.render.skia.SkijaNatives;

/**
 * Fabric 的 preLaunch 入口点：在 Minecraft 任何类被加载之前执行。
 * <p>
 * 必须走这个入口点，而不是 {@code Bootstrap}：{@code MixinMinecraft} 会在
 * {@code Minecraft.<init>} 的 {@code resizeDisplay} 阶段就创建 Skia 表面，
 * 那时 {@code Bootstrap}（在 {@code <init>} 的 TAIL）还没跑，Skija 会先按 linux/arm64 去抽原生库然后崩。
 */
public class StarainPreLaunch implements PreLaunchEntrypoint {

    @Override
    public void onPreLaunch() {
        SkijaNatives.prepare();
    }
}
