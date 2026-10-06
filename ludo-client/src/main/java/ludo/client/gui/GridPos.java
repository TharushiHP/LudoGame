package ludo.client.gui;

/**
 * One square of the 15x15 board grid; rows and columns are numbered 0-14 from the top-left
 * (Value Object).
 */
public record GridPos(int row, int col) {

    /** The square a quarter turn clockwise round the board's centre square (7,7). */
    public GridPos rotateClockwise() {
        return new GridPos(col, BoardLayout.SIZE - 1 - row);
    }

    public GridPos rotateClockwise(int quarterTurns) {
        GridPos p = this;
        for (int i = 0; i < quarterTurns; i++)
            p = p.rotateClockwise();
        return p;
    }

    /** True if {@code other} touches this square, sideways or diagonally (a king's move in chess). */
    public boolean touches(GridPos other) {
        int dr = Math.abs(row - other.row);
        int dc = Math.abs(col - other.col);
        return dr <= 1 && dc <= 1 && (dr + dc) > 0;
    }

    public boolean isDiagonalTo(GridPos other) {
        return Math.abs(row - other.row) == 1 && Math.abs(col - other.col) == 1;
    }
}
