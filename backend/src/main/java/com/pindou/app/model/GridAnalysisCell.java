package com.pindou.app.model;

public class GridAnalysisCell {
    private int row;
    private int col;
    private String code;

    public GridAnalysisCell() {
    }

    public GridAnalysisCell(int row, int col, String code) {
        this.row = row;
        this.col = col;
        this.code = code;
    }

    public int getRow() {
        return row;
    }

    public void setRow(int row) {
        this.row = row;
    }

    public int getCol() {
        return col;
    }

    public void setCol(int col) {
        this.col = col;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }
}
