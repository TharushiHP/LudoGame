package ludo.game;

public class MoveResult {

    private final boolean moved;
    private final boolean captured;
    private final boolean reachedHome;
    private final boolean landedOnMystery;
    private final String message;

    private MoveResult(Builder builder) {
        this.moved = builder.moved;
        this.captured = builder.captured;
        this.reachedHome = builder.reachedHome;
        this.landedOnMystery = builder.landedOnMystery;
        this.message = builder.message;
    }

    public boolean isMoved()          { return moved; }
    public boolean isCaptured()       { return captured; }
    public boolean isReachedHome()    { return reachedHome; }
    public boolean isLandedOnMystery(){ return landedOnMystery; }
    public String getMessage()        { return message; }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private boolean moved;
        private boolean captured;
        private boolean reachedHome;
        private boolean landedOnMystery;
        private String message = "";

        public Builder moved(boolean val)           { moved = val; return this; }
        public Builder captured(boolean val)        { captured = val; return this; }
        public Builder reachedHome(boolean val)     { reachedHome = val; return this; }
        public Builder landedOnMystery(boolean val) { landedOnMystery = val; return this; }
        public Builder message(String val)          { message = val; return this; }

        public MoveResult build() {
            return new MoveResult(this);
        }
    }
}
