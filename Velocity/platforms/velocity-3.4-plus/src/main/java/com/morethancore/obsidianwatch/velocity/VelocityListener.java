package com.morethancore.obsidianwatch.velocity;

import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PostLoginEvent;

public final class VelocityListener {
    private final ObsidianWatchVelocityService service;

    public VelocityListener(ObsidianWatchVelocityService service) {
        this.service = service;
    }

    @Subscribe(order = PostOrder.LATE)
    public void onPostLogin(PostLoginEvent event) {
        service.handlePostLogin(event.getPlayer());
    }
}
