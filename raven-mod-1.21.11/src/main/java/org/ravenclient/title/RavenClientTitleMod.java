package org.ravenclient.title;

import net.fabricmc.api.ClientModInitializer;

/**
 * Client entrypoint. Loads the RavenClient config so the title screen mixin
 * knows whether to show the custom menu.
 */
public class RavenClientTitleMod implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        RavenConfig.load();
    }
}
