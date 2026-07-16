package com.Hen3579.Nujomod.Client.GameMode;

import net.minecraftforge.eventbus.api.Event;

public class GameModeSwitchEvent extends Event {
    private final GameModeState oldState;
    private final GameModeState newState;

    public GameModeSwitchEvent(GameModeState oldState, GameModeState newState) {
        this.oldState = oldState;
        this.newState = newState;
    }

    public GameModeState getOldState() {
        return oldState;
    }

    public GameModeState getNewState() {
        return newState;
    }
}
