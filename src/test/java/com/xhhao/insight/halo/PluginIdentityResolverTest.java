package com.xhhao.insight.halo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class PluginIdentityResolverTest {

    @Test
    void indexesMetadataNamesCaseInsensitivelyWithoutGuessingAliases() {
        PluginRuntimeInfo photos = new PluginRuntimeInfo(
            "PluginPhotos", "图库", "1.0.0", true, "STARTED", null);
        var index = new PluginIdentityResolver().index(List.of(photos));

        assertThat(index.get("pluginphotos")).isSameAs(photos);
        assertThat(index).doesNotContainKey("photo");
    }
}
