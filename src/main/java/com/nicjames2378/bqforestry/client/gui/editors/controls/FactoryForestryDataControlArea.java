package com.nicjames2378.bqforestry.client.gui.editors.controls;

import betterquesting.api2.client.gui.misc.GuiRectangle;
import betterquesting.api2.client.gui.misc.IGuiRect;
import betterquesting.api2.client.gui.panels.CanvasEmpty;
import betterquesting.api2.client.gui.panels.IGuiCanvas;
import betterquesting.api2.client.gui.panels.IGuiPanel;
import betterquesting.api2.utils.QuestTranslation;
import com.nicjames2378.bqforestry.BQ_Forestry;

public class FactoryForestryDataControlArea {
    private IGuiPanel[] panels;
    private IGuiCanvas parent;

    private int x, y, w;
    private int height;

    private String titleText = "TITLE_NOT_SET";

    private int LAYOUT_SIZE_X = -1;
    private int LAYOUT_SIZE_Y = -1;
    private int LAYOUT_COLUMNS = -1;

    private int currentColumn = 0;
    private int currentRow = 0;

    public FactoryForestryDataControlArea(IGuiCanvas parent, int x, int y, int w) {
        this.parent = parent;
        this.x = x;
        this.y = y;
        this.w = w;
    }

    public IGuiPanel[] getPanels() {
        return this.panels;
    }

    public FactoryForestryDataControlArea setPanels(IGuiPanel[] panels) {
        this.panels = panels;
        return this;
    }

    public FactoryForestryDataControlArea setTitle(String title, boolean isLangKey) {
        titleText = isLangKey ? QuestTranslation.translate(title) : title;
        return this;
    }

    public GuiRectangle getNextRect() {
        return new GuiRectangle(getNextX(), getNextY() + 16, LAYOUT_SIZE_X, LAYOUT_SIZE_Y);
    }

    public int getHeight() {
        return height + 16;
    }

    private int getNextX() {
        int parentHalfWidth = parent.getTransform().getWidth() / 2;
        int factoryHalfWidth = (LAYOUT_COLUMNS * LAYOUT_SIZE_X) / 2;

        return (parentHalfWidth - factoryHalfWidth) + LAYOUT_SIZE_X * getCurrentColumn();
    }

    private int getNextY() {
        return LAYOUT_SIZE_Y * currentRow;
    }

    private int getCurrentColumn() {
        if (currentColumn > LAYOUT_COLUMNS - 1) {
            currentColumn = 0;
            currentRow++;
        }

        int temp = currentColumn; // Return current value
        currentColumn++;
        return temp;
    }

    public FactoryForestryDataControlArea setLayout(int columns, int sizeY) {
        if (this.LAYOUT_COLUMNS != -1) {
            BQ_Forestry.debug("Layout already set!");
            return this;
        }

        this.LAYOUT_SIZE_X = w / columns;
        this.LAYOUT_SIZE_Y = sizeY;
        // Make the columns count 0-based, while allowing people to specify it as a 1-based int
        this.LAYOUT_COLUMNS = columns;

        return this;
    }

    public CanvasEmpty buildCanvas() {
        // Create the Canvas
        // Add it to it's parent
        // Add it's title
        // Add other Panels

        height = 16 + (panels.length / LAYOUT_COLUMNS + (panels.length % LAYOUT_COLUMNS == 0 ? 0 : 1)) * LAYOUT_SIZE_Y;
        IGuiRect rect = new GuiRectangle(x, y, w, height);

        CanvasEmpty built = new CanvasEmpty(rect);
        parent.addPanel(built);

//        PanelTextBox tbx = new PanelTextBox(new GuiTransform(GuiAlign.TOP_CENTER, new GuiPadding(0, 0, 0, -16), 0), titleText).setAlignment(1).setColor(PresetColor.TEXT_HEADER.getColor());
//        built.addPanel(tbx);

        for (IGuiPanel panel : panels) {
            built.addPanel(panel);
        }

        return built;
    }
}
