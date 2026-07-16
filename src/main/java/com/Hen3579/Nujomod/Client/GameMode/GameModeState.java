package com.Hen3579.Nujomod.Client.GameMode;

public enum GameModeState {
    VANILLA("原版", "vanilla"),
    COMBAT("战斗", "combat"),
    BUILD("建造", "build");

    private final String displayName;
    private final String id;

    GameModeState(String displayName, String id) {
        this.displayName = displayName;
        this.id = id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getId() {
        return id;
    }

    public GameModeState next() {
        GameModeState[] values = values();
        return values[(this.ordinal() + 1) % values.length];
    }
}
