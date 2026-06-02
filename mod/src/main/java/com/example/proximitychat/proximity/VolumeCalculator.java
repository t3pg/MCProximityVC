package com.example.proximitychat.proximity;

import com.example.proximitychat.config.ProximityConfig;

public class VolumeCalculator {

    public static int calculate(float distance, ProximityConfig config) {
        float min = config.getMinDistance();
        float max = config.getMaxDistance();
        int maxVol = config.getMaxVolume();
        int minVol = config.getMinVolume();

        if (distance <= min) return maxVol;
        if (distance >= max) return minVol;

        float raw = switch (config.getFalloffType()) {
            case "QUADRATIC" -> {
                float ratio = 1.0f - (distance - min) / (max - min);
                yield maxVol * ratio * ratio;
            }
            case "STEPPED" -> {
                float step = max / 4.0f;
                int tier = (int) ((distance - min) / step);
                yield maxVol * (1.0f - tier / 4.0f);
            }
            default -> maxVol * (1.0f - (distance - min) / (max - min)); // LINEAR
        };

        return Math.max(minVol, Math.min(maxVol, (int) raw));
    }
}
