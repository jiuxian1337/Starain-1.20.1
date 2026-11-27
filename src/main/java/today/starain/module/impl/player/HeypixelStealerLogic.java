package today.starain.module.impl.player;

import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Heypixel 模式箱子小偷逻辑，对照 AntiCheatAddition 源码实现绕过：
 * - InventoryBatch (15 点击一批，仅材质变化时入队，同容器相邻点击计入间隔)
 * - AverageHeuristic (剔除 2 个离群值后的 SSE，分母为 misclicks + 1)
 * - Statistical / KS (剔除 2 个离群值后均匀检验 p-value < 0.5)
 * - PerfectExit (关箱距末次点击 > 70ms)
 * - Frequency (开箱至关箱总时长 > 350ms)
 * - MultiInteraction (跨箱全局生效：短距 > 75ms，跨距 > 125ms)
 */
final class HeypixelStealerLogic {
    private static final int ACA_BATCH_SIZE = 15;
    private static final double AVG_COEFF = 40000.0;
    private static final double AVG_BIAS = 25.0;
    private static final long PERFECT_EXIT_MS = 70L;
    private static final long FREQUENCY_MIN_MS = 350L;

    record Config(
            int aiMinDelay,
            int aiMaxDelay,
            String orderMode,
            boolean humanPause,
            boolean smartClose,
            boolean occasionalMisclick
    ) {
    }

    @FunctionalInterface
    interface ClickCallback {
        void click(int slotId, ClickType clickType);
    }

    private final Random random = new Random();

    // 镜像 ACA InventoryBatch（跨箱累计满 15 次有效且不同材质的点击后重置）
    private final List<Long> batchOffsets = new ArrayList<>();
    private int batchClickCount;
    private int batchMisclicks;
    private long lastBatchClickTimeMs;
    private Item lastBatchItem;
    private boolean hasBatchClickInCurrentChest;

    // 单箱交互状态
    private long openTimeMs;
    private long lastActionTimeMs;
    private long nextDelayMs;
    private long plannedCloseAtMs = -1L;

    private int lastSlot = -1;
    private Item lastItem;
    private int pendingSlot = -1;
    private boolean hasClickedAny;

    // 跨箱全局防 MultiInteraction 与 TSP 路径缓存
    private long lastGlobalClickTimeMs;
    private int lastGlobalSlot = -1;
    private final ArrayDeque<Integer> plannedPath = new ArrayDeque<>();

    /**
     * 打开新箱子时重置单箱交互状态（保留跨箱 15 点击 batch 与全局防连点状态）。
     */
    void onOpen() {
        long now = System.currentTimeMillis();
        openTimeMs = now;
        lastActionTimeMs = now;
        nextDelayMs = 0L;
        plannedCloseAtMs = -1L;
        lastSlot = -1;
        lastItem = null;
        pendingSlot = -1;
        hasClickedAny = false;
        hasBatchClickInCurrentChest = false;
        plannedPath.clear();
    }

    /**
     * 模块关闭时彻底清空单箱与跨箱统计状态。
     */
    void resetAll() {
        onOpen();
        batchOffsets.clear();
        batchClickCount = 0;
        batchMisclicks = 0;
        lastBatchClickTimeMs = 0L;
        lastBatchItem = null;
        lastGlobalClickTimeMs = 0L;
        lastGlobalSlot = -1;
    }

    /**
     * 每 tick 驱动一次箱子拿取或智能关箱决策。
     */
    void tick(ChestMenu menu, Config cfg, double mouseCol, double mouseRow,
              ClickCallback onClick, Runnable onClose) {
        long now = System.currentTimeMillis();
        int containerSlots = menu.getRowCount() * 9;
        List<Integer> candidates = buildCandidates(menu, containerSlots);

        if (candidates.isEmpty()) {
            plannedPath.clear();
            handleEmptyChest(now, cfg, onClose);
            return;
        }

        plannedCloseAtMs = -1L;

        if (!candidates.contains(pendingSlot)) {
            pendingSlot = pickNextSlot(candidates, menu, cfg.orderMode(), mouseCol, mouseRow);
            nextDelayMs = computeNextDelay(lastSlot, pendingSlot, lastItem,
                    menu.getSlot(pendingSlot).getItem().getItem(), cfg);
        }

        if (now - lastActionTimeMs < nextDelayMs) {
            return;
        }

        // ACA InventoryMultiInteraction 跨箱全局检测：防止快速切箱时首击距上一箱末击过近
        if (lastSlot < 0 && lastGlobalSlot >= 0) {
            long globalNeed = slotDist(lastGlobalSlot, pendingSlot) >= 4.0 ? 185L : 115L;
            if (now - lastGlobalClickTimeMs < globalNeed) {
                return;
            }
        }

        // 低概率邻近空槽轻误触：提升人为真实感并在 AverageHeuristic 中降低 VL
        if (cfg.occasionalMisclick() && hasClickedAny && lastSlot >= 0 && random.nextDouble() < 0.04) {
            int mis = findNearbyEmptySlot(menu, containerSlots, lastSlot);
            if (mis >= 0) {
                batchMisclicks++;
                onClick.click(mis, ClickType.PICKUP);
                lastSlot = mis;
                lastItem = null;
                lastActionTimeMs = now;
                lastGlobalClickTimeMs = now;
                lastGlobalSlot = mis;
                nextDelayMs = computeNextDelay(lastSlot, pendingSlot, null,
                        menu.getSlot(pendingSlot).getItem().getItem(), cfg);
                return;
            }
        }

        int slotToTake = pendingSlot;
        ItemStack before = menu.getSlot(slotToTake).getItem();
        Item clickedItem = before.getItem();
        int beforeCount = before.getCount();

        onClick.click(slotToTake, ClickType.QUICK_MOVE);
        ItemStack after = menu.getSlot(slotToTake).getItem();
        boolean moved = after.isEmpty() || after.getCount() < beforeCount;

        if (moved) {
            recordBatchClick(now, clickedItem);
            lastItem = clickedItem;
        } else {
            // 未成功移出（如背包满或未点中），不假设服务端已更新 lastMaterialClicked
            lastItem = null;
        }

        if (!plannedPath.isEmpty() && plannedPath.peekFirst() == slotToTake) {
            plannedPath.pollFirst();
        }

        lastSlot = slotToTake;
        lastActionTimeMs = now;
        lastGlobalClickTimeMs = now;
        lastGlobalSlot = slotToTake;
        hasClickedAny = true;
        pendingSlot = -1;

        candidates.remove(Integer.valueOf(slotToTake));
        if (!candidates.isEmpty()) {
            pendingSlot = pickNextSlot(candidates, menu, cfg.orderMode(), mouseCol, mouseRow);
            nextDelayMs = computeNextDelay(lastSlot, pendingSlot, lastItem,
                    menu.getSlot(pendingSlot).getItem().getItem(), cfg);
        }
    }

    /**
     * 按 Inventory.java 的过滤条件镜像记录 batch：仅当材质变化时入队，满 15 次自动清空。
     */
    private void recordBatchClick(long now, Item item) {
        if (lastBatchItem != null && item == lastBatchItem) {
            return;
        }

        if (hasBatchClickInCurrentChest) {
            batchOffsets.add(Math.max(0L, now - lastBatchClickTimeMs));
        }

        lastBatchClickTimeMs = now;
        lastBatchItem = item;
        hasBatchClickInCurrentChest = true;
        batchClickCount++;

        if (batchClickCount >= ACA_BATCH_SIZE) {
            batchClickCount = 0;
            batchOffsets.clear();
            batchMisclicks = 0;
        }
    }

    private void handleEmptyChest(long now, Config cfg, Runnable onClose) {
        if (!cfg.smartClose()) {
            if (now - lastActionTimeMs >= Math.max(50, cfg.aiMinDelay())) {
                onClose.run();
            }
            return;
        }

        if (plannedCloseAtMs < 0L) {
            // PerfectExit: 关箱距末次有效点击需 > 70ms；Frequency: 开箱到关箱需 > 350ms
            long afterLastClick = hasClickedAny ? (PERFECT_EXIT_MS + 25L + random.nextInt(55)) : 0L;
            long afterOpen = FREQUENCY_MIN_MS + 30L + random.nextInt(60);
            plannedCloseAtMs = Math.max(lastActionTimeMs + afterLastClick, openTimeMs + afterOpen);
        }

        if (now >= plannedCloseAtMs) {
            plannedCloseAtMs = -1L;
            onClose.run();
        }
    }

    // ---- 顺序选择：TSP / Smart / Gaussian ----

    private int pickNextSlot(List<Integer> candidates, ChestMenu menu, String orderMode,
                             double mouseCol, double mouseRow) {
        if (candidates.size() == 1) {
            return candidates.get(0);
        }
        if ("Gaussian".equalsIgnoreCase(orderMode)) {
            return candidates.get(sampleGaussianIndex(candidates));
        }
        if ("TSP".equalsIgnoreCase(orderMode)) {
            while (!plannedPath.isEmpty() && !candidates.contains(plannedPath.peekFirst())) {
                plannedPath.pollFirst();
            }
            if (plannedPath.isEmpty()) {
                planTspPath(candidates, menu, mouseCol, mouseRow);
            }
            if (!plannedPath.isEmpty()) {
                return plannedPath.peekFirst();
            }
        }

        int bestSlot = candidates.get(0);
        double bestScore = Double.MAX_VALUE;
        for (int slot : candidates) {
            double score;
            if (lastSlot < 0) {
                score = Math.hypot((slot % 9) - mouseCol, (slot / 9.0) - mouseRow)
                        + Math.abs(random.nextGaussian()) * 0.85;
            } else {
                double dist = slotDist(lastSlot, slot);
                score = dist;
                if (dist < 4.0) {
                    score -= 1.2;
                }
                Item item = menu.getSlot(slot).getItem().getItem();
                if (lastItem != null && item == lastItem && dist < 5.0) {
                    score -= 0.8;
                }
                score += Math.abs(random.nextGaussian()) * 0.75;
            }
            if (score < bestScore) {
                bestScore = score;
                bestSlot = slot;
            }
        }
        return bestSlot;
    }

    private void planTspPath(List<Integer> candidates, ChestMenu menu, double mouseCol, double mouseRow) {
        plannedPath.clear();
        int k = candidates.size();
        if (k == 0) {
            return;
        }
        if (k == 1) {
            plannedPath.add(candidates.get(0));
            return;
        }

        Item[] items = new Item[k];
        double[] startCost = new double[k];
        for (int i = 0; i < k; i++) {
            int slot = candidates.get(i);
            items[i] = menu.getSlot(slot).getItem().getItem();
            if (lastSlot >= 0) {
                double d = slotDist(lastSlot, slot);
                boolean same = lastItem != null && items[i] == lastItem;
                startCost[i] = transitionTickCost(d, same) + d * 3.0 + Math.abs(random.nextGaussian()) * 2.0;
            } else {
                double mouseDist = Math.hypot((slot % 9) - mouseCol, (slot / 9.0) - mouseRow);
                startCost[i] = mouseDist * 18.0 + Math.abs(random.nextGaussian()) * 2.5;
            }
        }

        double[][] cost = new double[k][k];
        for (int i = 0; i < k; i++) {
            for (int j = i + 1; j < k; j++) {
                double d = slotDist(candidates.get(i), candidates.get(j));
                boolean same = items[i] == items[j];
                double w = transitionTickCost(d, same) + d * 3.0 + Math.abs(random.nextGaussian()) * 2.0;
                cost[i][j] = w;
                cost[j][i] = w;
            }
        }

        int[] order = k <= 10 ? solveHeldKarp(startCost, cost, k) : solveGreedyTwoOpt(startCost, cost, k);
        for (int idx : order) {
            plannedPath.add(candidates.get(idx));
        }
    }

    private static double transitionTickCost(double dist, boolean sameMat) {
        if (sameMat && dist < 4.0) {
            return 90.0;
        }
        if (dist < 4.0) {
            return 100.0;
        }
        if (sameMat) {
            return 150.0;
        }
        return 200.0;
    }

    private static int[] solveHeldKarp(double[] startCost, double[][] cost, int k) {
        int fullMask = 1 << k;
        double[][] dp = new double[fullMask][k];
        int[][] parent = new int[fullMask][k];
        for (int m = 0; m < fullMask; m++) {
            Arrays.fill(dp[m], Double.POSITIVE_INFINITY);
            Arrays.fill(parent[m], -1);
        }
        for (int i = 0; i < k; i++) {
            dp[1 << i][i] = startCost[i];
        }

        for (int mask = 1; mask < fullMask; mask++) {
            for (int last = 0; last < k; last++) {
                if ((mask & (1 << last)) == 0) continue;
                double curCost = dp[mask][last];
                if (Double.isInfinite(curCost)) continue;
                for (int next = 0; next < k; next++) {
                    if ((mask & (1 << next)) != 0) continue;
                    int nextMask = mask | (1 << next);
                    double nextCost = curCost + cost[last][next];
                    if (nextCost < dp[nextMask][next]) {
                        dp[nextMask][next] = nextCost;
                        parent[nextMask][next] = last;
                    }
                }
            }
        }

        int finalMask = fullMask - 1;
        int bestLast = 0;
        double bestTotal = Double.POSITIVE_INFINITY;
        for (int i = 0; i < k; i++) {
            if (dp[finalMask][i] < bestTotal) {
                bestTotal = dp[finalMask][i];
                bestLast = i;
            }
        }

        int[] order = new int[k];
        int mask = finalMask;
        int cur = bestLast;
        for (int pos = k - 1; pos >= 0; pos--) {
            order[pos] = cur;
            int prev = parent[mask][cur];
            mask ^= (1 << cur);
            cur = prev;
        }
        return order;
    }

    private static int[] solveGreedyTwoOpt(double[] startCost, double[][] cost, int k) {
        boolean[] used = new boolean[k];
        int[] order = new int[k];

        int first = 0;
        for (int i = 1; i < k; i++) {
            if (startCost[i] < startCost[first]) {
                first = i;
            }
        }
        order[0] = first;
        used[first] = true;

        for (int step = 1; step < k; step++) {
            int prev = order[step - 1];
            int best = -1;
            double bestEdge = Double.POSITIVE_INFINITY;
            for (int i = 0; i < k; i++) {
                if (!used[i] && cost[prev][i] < bestEdge) {
                    bestEdge = cost[prev][i];
                    best = i;
                }
            }
            order[step] = best;
            used[best] = true;
        }

        for (int iter = 0; iter < 40; iter++) {
            boolean improved = false;
            for (int a = 0; a < k - 1; a++) {
                for (int b = a + 1; b < k; b++) {
                    double beforeLeft = a == 0 ? startCost[order[0]] : cost[order[a - 1]][order[a]];
                    double afterLeft = a == 0 ? startCost[order[b]] : cost[order[a - 1]][order[b]];
                    double beforeRight = b == k - 1 ? 0.0 : cost[order[b]][order[b + 1]];
                    double afterRight = b == k - 1 ? 0.0 : cost[order[a]][order[b + 1]];
                    if (afterLeft + afterRight < beforeLeft + beforeRight - 1e-6) {
                        for (int l = a, r = b; l < r; l++, r--) {
                            int tmp = order[l];
                            order[l] = order[r];
                            order[r] = tmp;
                        }
                        improved = true;
                    }
                }
            }
            if (!improved) {
                break;
            }
        }
        return order;
    }

    private int sampleGaussianIndex(List<Integer> candidates) {
        int size = candidates.size();
        double meanIndex = (size - 1) * 0.5;
        if (lastSlot >= 0) {
            int nearestIdx = 0;
            double nearestDist = Double.MAX_VALUE;
            for (int i = 0; i < size; i++) {
                double d = slotDist(lastSlot, candidates.get(i));
                if (d < nearestDist) {
                    nearestDist = d;
                    nearestIdx = i;
                }
            }
            meanIndex = nearestIdx;
        }
        double sigma = Math.max(1.0, size / 3.0);
        int idx = (int) Math.round(meanIndex + random.nextGaussian() * sigma);
        return Math.max(0, Math.min(size - 1, idx));
    }

    // ---- 50ms Tick 对齐延迟计算 + 全程方差建立 ----

    private long computeNextDelay(int fromSlot, int toSlot, Item prevItem, Item nextItem, Config cfg) {
        int aiMin = Math.max(50, cfg.aiMinDelay());
        int aiMax = Math.max(aiMin + 60, cfg.aiMaxDelay());
        double dist = fromSlot < 0 ? 2.0 : slotDist(fromSlot, toSlot);
        boolean sameMat = prevItem != null && nextItem != null && prevItem == nextItem;

        // 开箱首击无同箱间隔检测，使用高斯采样快速起步
        if (fromSlot < 0) {
            int firstBase = ((Math.max(aiMin, 80) + 49) / 50) * 50;
            long firstTick = sampleGaussianTick(firstBase, 25.0, firstBase, firstBase + 100);
            return Math.max(58L, firstTick - 12L);
        }

        // 同材质不入队 InventoryBatch，跳过多余停顿档但严格遵守距离防线
        if (sameMat) {
            if (dist < 4.0) {
                int sameShort = ((Math.max(aiMin, 90) + 49) / 50) * 50;
                return Math.max(88L, sameShort - 12L);
            }
            int sameLong = ((Math.max(aiMin + 45, 140) + 49) / 50) * 50;
            return Math.max(138L, sameLong - 12L);
        }

        int floor = dist < 4.0 ? Math.max(aiMin, 90) : Math.max(aiMin + 65, 160);
        int baseTickMs = ((floor + 49) / 50) * 50;
        int maxCapTick = Math.max(((aiMax + 49) / 50) * 50 + 50, baseTickMs + 150);

        long chosenTickMs;
        boolean willRecordOffset = hasBatchClickInCurrentChest && (lastBatchItem == null || nextItem != lastBatchItem);
        if (cfg.humanPause() && willRecordOffset) {
            chosenTickMs = pickSafeTickDelay(baseTickMs, maxCapTick);
        } else {
            chosenTickMs = sampleBimodalGaussianTick(baseTickMs, maxCapTick);
        }

        // 返回值略低于目标 50ms tick 档位，防止 ±3ms 系统抖动把 100ms 推成 150ms
        return Math.max(Math.max(58L, floor - 12L), chosenTickMs - 12L);
    }

    /**
     * 50ms 量化感知 + 全程主动方差建立：
     * 保证每个 batch 至少存在 3 个高位样本（>= base + 100ms），
     * 防止 removeOutliers(2) 剔除最远 2 个点后剩余 SSE 坍缩触发 averageheuristic。
     */
    private long pickSafeTickDelay(int baseTickMs, int maxCapTick) {
        int size = batchOffsets.size();
        int highCount = 0;
        for (long v : batchOffsets) {
            if (v >= baseTickMs + 85L) {
                highCount++;
            }
        }

        long highA = Math.min(maxCapTick, baseTickMs + 100L);
        long highB = Math.min(maxCapTick, baseTickMs + 150L);
        EvalResult baseEval = evalBatchWith(baseTickMs);

        // 从第 3 个间隔起主动建立高位分离样本，或当 SSE/VL 进入危险区时强制拉高
        boolean needHighForOutliers = size >= 2 && highCount < 3 && (size >= 4 || random.nextDouble() < 0.45);
        boolean baseUnsafe = size >= 2 && (baseEval.sse < 6500.0 || baseEval.vl > -8.0);

        if (needHighForOutliers || baseUnsafe) {
            EvalResult evalA = evalBatchWith(highA);
            EvalResult evalB = evalBatchWith(highB);
            boolean aKsSafe = size + 1 < 6 || evalA.ksP < 0.40;
            boolean bKsSafe = size + 1 < 6 || evalB.ksP < 0.40;

            if (aKsSafe && (!bKsSafe || (evalA.vl <= -8.0 && evalA.sse >= 6500.0 && random.nextDouble() < 0.7))) {
                return highA;
            }
            if (bKsSafe && (evalB.sse >= evalA.sse || !aKsSafe)) {
                return highB;
            }
            return evalB.sse >= evalA.sse ? highB : highA;
        }

        // 已具备安全方差时：用双峰高斯优先跑高速低档，仅在安全且远离均匀分布时采纳
        for (int attempt = 0; attempt < 6; attempt++) {
            long cand = sampleBimodalGaussianTick(baseTickMs, maxCapTick);
            if (cand != baseTickMs && size >= 1 && Math.abs(batchOffsets.get(size - 1) - cand) < 25L) {
                continue;
            }
            EvalResult eval = evalBatchWith(cand);
            if (size + 1 < 4 || (eval.sse >= 6500.0 && eval.vl <= -8.0 && (size + 1 < 7 || eval.ksP < 0.40))) {
                return cand;
            }
        }

        // 兜底挑选最高安全档
        long[] fallback = {baseTickMs, Math.min(maxCapTick, baseTickMs + 50L), highA, highB};
        long bestTick = highA;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (long cand : fallback) {
            EvalResult eval = evalBatchWith(cand);
            double score = eval.sse - (eval.ksP >= 0.45 ? 10000.0 : 0.0);
            if (score > bestScore) {
                bestScore = score;
                bestTick = cand;
            }
        }
        return bestTick;
    }

    private long sampleBimodalGaussianTick(int baseTickMs, int maxCapTick) {
        double anchor = random.nextDouble() < 0.70 ? baseTickMs : Math.min(maxCapTick, baseTickMs + 100.0);
        return sampleGaussianTick(anchor, 25.0, baseTickMs, maxCapTick);
    }

    private long sampleGaussianTick(double mean, double sigma, int minTickMs, int maxTickMs) {
        double raw = mean + random.nextGaussian() * sigma;
        long aligned = Math.round(raw / 50.0) * 50L;
        return Math.max(minTickMs, Math.min(maxTickMs, aligned));
    }

    private record EvalResult(double sse, double vl, double ksP) {
    }

    private EvalResult evalBatchWith(long quantizedCandidateMs) {
        long[] sim = new long[batchOffsets.size() + 1];
        for (int i = 0; i < batchOffsets.size(); i++) {
            sim[i] = batchOffsets.get(i);
        }
        sim[sim.length - 1] = quantizedCandidateMs;

        long[] filtered = removeOutliers2(sim);
        double avg = average(filtered);
        double sse = sse(filtered, avg);
        double multiplier = avgMultiplier(avg / 50.0);
        double vl = AVG_COEFF / (sse + 1.0) * multiplier / (batchMisclicks + 1.0) - AVG_BIAS;
        double ksP = ksUniformPValue(filtered);
        return new EvalResult(sse, vl, ksP);
    }

    // ---- 数学工具（严格对照 ACA DataUtil + KolmogorovSmirnov） ----

    private static long[] removeOutliers2(long[] data) {
        if (data.length <= 2) {
            return data;
        }
        double mean = average(data);
        Long[] boxed = new Long[data.length];
        for (int i = 0; i < data.length; i++) {
            boxed[i] = data[i];
        }
        Arrays.sort(boxed, (a, b) -> Double.compare(Math.abs(a - mean), Math.abs(b - mean)));
        long[] result = new long[data.length - 2];
        for (int i = 0; i < result.length; i++) {
            result[i] = boxed[i];
        }
        return result;
    }

    private static double average(long[] data) {
        if (data.length == 0) {
            return 0.0;
        }
        double s = 0.0;
        for (long v : data) {
            s += v;
        }
        return s / data.length;
    }

    private static double sse(long[] data, double ref) {
        double s = 0.0;
        for (long v : data) {
            double d = v - ref;
            s += d * d;
        }
        return s;
    }

    private static double avgMultiplier(double avgTicks) {
        return -1.3 * Math.tanh(0.15 * avgTicks - 1.0) + 1.8;
    }

    private static double ksUniformPValue(long[] data) {
        if (data.length < 2) {
            return 0.0;
        }
        long min = data[0];
        long max = data[0];
        for (long v : data) {
            if (v < min) min = v;
            if (v > max) max = v;
        }

        double[] norm;
        if (min == max) {
            norm = new double[data.length];
        } else {
            double range = (double) (max - min);
            norm = new double[data.length];
            for (int i = 0; i < data.length; i++) {
                norm[i] = (data[i] - min) / range;
            }
            Arrays.sort(norm);
        }

        int n = norm.length;
        double d = 0.0;
        for (int i = 0; i < n; i++) {
            double up = Math.abs((i + 1.0) / n - norm[i]);
            double down = Math.abs(norm[i] - i / (double) n);
            if (up > d) d = up;
            if (down > d) d = down;
        }

        double sqrtN = Math.sqrt(n);
        double lambda = (sqrtN + 0.12 + 0.11 / sqrtN) * d;
        if (lambda < 1e-2) {
            return 1.0;
        }

        double sum = 0.0;
        for (int j = 1; j <= 1000; j++) {
            double sign = (j & 1) == 1 ? 1.0 : -1.0;
            double term = 2.0 * sign * Math.exp(-2.0 * j * j * lambda * lambda);
            sum += term;
            if (Math.abs(term) < 1e-12) {
                break;
            }
        }
        return Math.max(0.0, Math.min(1.0, sum));
    }

    // ---- 辅助 ----

    private List<Integer> buildCandidates(ChestMenu menu, int containerSlots) {
        List<Integer> list = new ArrayList<>();
        for (int i = 0; i < containerSlots; i++) {
            ItemStack s = menu.getSlot(i).getItem();
            if (!s.isEmpty() && ChestStealer.isItemUseful(s) && ChestStealer.isBestItemInChest(menu, s)) {
                list.add(i);
            }
        }
        return list;
    }

    private int findNearbyEmptySlot(ChestMenu menu, int containerSlots, int origin) {
        List<Integer> empty = new ArrayList<>();
        for (int i = 0; i < containerSlots; i++) {
            if (i != origin && menu.getSlot(i).getItem().isEmpty() && slotDist(origin, i) < 2.5) {
                empty.add(i);
            }
        }
        return empty.isEmpty() ? -1 : empty.get(random.nextInt(empty.size()));
    }

    private static double slotDist(int a, int b) {
        return Math.hypot(a % 9 - b % 9, a / 9 - b / 9);
    }
}
