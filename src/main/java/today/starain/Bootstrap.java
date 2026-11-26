package today.starain;

import today.starain.utils.render.skia.SkijaNatives;

// 这是个跳板类 因为mixin不能混indy 这个类可以
// mixin初始化调用这个类 这个类去调用主类初始化
public class Bootstrap {
    public Bootstrap(String string) {
        launch();
    }

    private void launch() {
        // 必须在任何 Skija 类被加载之前执行：安卓缺少 OperatingSystem.ANDROID，
        // 得先把原生库释放出来并写好 skija.library.path。
        SkijaNatives.prepare();
        Starain.newInstance();
    }
}
