package com.microsoft.azure.webjobs.script.it.functions.dto;

public class Point {

    public Point() {}
    public Point(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public int getX() { return this.x; }
    public int getY() { return this.y; }
    public void setX(int x) { this.x = x; }
    public void setY(int y) { this.y = y; }

    private int x;
    private int y;
}
