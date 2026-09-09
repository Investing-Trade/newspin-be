package org.gp.newspinbe.global.seed;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 시드 적재 설정. local/dev 프로필에서만 동작하며, 빈 DB일 때 1회만 적재한다.
 */
@ConfigurationProperties(prefix = "newspin.seed")
public record SeedProperties(boolean enabled, String dir) {

    public SeedProperties {
        if (dir == null || dir.isBlank()) {
            dir = "tools/seed/data";
        }
    }
}
