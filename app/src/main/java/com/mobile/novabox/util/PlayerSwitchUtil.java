package com.mobile.novabox.util;

import java.util.Set;

/**
 * 播放内核自动切换工具。
 *
 * 4 档 PLAY_TYPE:0=EXO硬解, 1=EXO软解, 2=IJK硬解, 3=IJK软解。
 * 播放失败时按固定顺序 0→1→2→3 逐个尝试其余三个内核,跳过当前与已尝试过的,
 * 四个播放场景(点播/直播/本地/OpenList)共用同一套逻辑。
 */
public class PlayerSwitchUtil {

    /**
     * 计算下一个待尝试的内核档位。
     *
     * @param currentType 当前正在使用的内核档位(0~3)
     * @param tried       已尝试过的内核档位集合(方法内会把当前内核加入其中)
     * @return 下一个要尝试的内核档位;返回 -1 表示其余三个内核都试过了
     */
    public static int nextPlayerType(int currentType, Set<Integer> tried) {
        if (currentType < 0 || currentType > 3) currentType = 2;
        tried.add(currentType);
        for (int i = 0; i <= 3; i++) {
            if (!tried.contains(i)) return i;
        }
        return -1;
    }

    /**
     * 兼容历史 PLAY_TYPE 编码(老 1=IJK, 老 2=EXO),归一化为 4 档新编码。
     * 老 0(系统播放器)按项目现状保留为 EXO硬解。
     */
    public static int normalizePlayType(int playType) {
        if (playType == 1) return 2;         // 老 IJK -> IJK硬解
        if (playType == 2) return 0;         // 老 EXO -> EXO硬解
        if (playType < 0 || playType > 3) return 2; // 兜底 IJK硬解
        return playType;
    }

    /**
     * 根据内核档位返回对应的 IJK 解码标记。
     * 仅 IJK 软解(3)需要"软解码",其余档位统一"硬解码"(EXO 路径不消费该字段)。
     */
    public static String ijkCodeFor(int playType) {
        return playType == 3 ? "软解码" : "硬解码";
    }
}
