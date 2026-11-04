package adris.altoclef.multiversion;

import adris.altoclef.mixins.DrawableHelperInvoker;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import org.jetbrains.annotations.Nullable;

public class DrawContextWrapper {


    //#if MC >= 12001
    public static DrawContextWrapper of(GuiGraphics context) {
        if (context == null) return null;
        return new DrawContextWrapper(context);
    }
    private final GuiGraphics context;

    private DrawContextWrapper(GuiGraphics context) {
        this.context = context;
    }
    //#else
    //$$ public static DrawContextWrapper of(MatrixStack matrices) {
    //$$    if (matrices == null) return null;
    //$$    return new DrawContextWrapper(matrices);
    //$$ }
    //$$
    //$$ private final MatrixStack matrices;
    //$$ private final DrawableHelper helper;
    //$$ private DrawContextWrapper(MatrixStack matrices) {
    //$$        this.matrices = matrices;
    //$$        this.helper = new DrawableHelper(){};
    //$$ }
    //#endif

    private RenderType renderLayer = null;

    // used only 1.20.1 and later... can pass null in earlier versions
    public void setRenderLayer(RenderType renderLayer) {
        this.renderLayer = renderLayer;
    }

    public void fill(int x1, int y1, int x2, int y2, int color) {
        //#if MC >= 12001
        context.fill(renderLayer, x1, y1, x2, y2, color);
        //#else
        //$$  DrawableHelper.fill(matrices, x1, y1, x2, y2, color);
        //#endif
    }

    public void drawHorizontalLine(int x1, int x2, int y, int color) {
        //#if MC >= 12001
        context.hLine(renderLayer, x1, x2, y, color);
        //#else
        //$$ ((DrawableHelperInvoker) helper).invokeDrawHorizontalLine(matrices, x1, x2, y, color);
        //#endif
    }

    public void drawVerticalLine(int x, int y1, int y2, int color) {
        //#if MC >= 12001
        context.vLine(renderLayer, x, y1, y2, color);
        //#else
        //$$ ((DrawableHelperInvoker) helper).invokeDrawVerticalLine(matrices, x, y1, y2, color);
        //#endif
    }

    public void drawText(Font textRenderer, @Nullable String text, int x, int y, int color, boolean shadow) {
        //#if MC >= 12001
        context.drawString(textRenderer,text,x,y,color,shadow);
        //#else
        //$$ if (shadow) {
        //$$    textRenderer.drawWithShadow(matrices, text,x,y,color);
        //$$ } else {
        //$$    textRenderer.draw(matrices, text,x,y,color);
        //$$ }
        //#endif
    }


    public PoseStack getMatrices() {
        //#if MC >= 12001
        return context.pose();
        //#else
        //$$ return matrices;
        //#endif
    }

    public int getScaledWindowWidth() {
        //#if MC >= 12001
        return context.guiWidth();
        //#else
        //$$ return MinecraftClient.getInstance().getWindow().getScaledWidth();
        //#endif
    }

    public int getScaledWindowHeight() {
        //#if MC >= 12001
        return context.guiHeight();
        //#else
        //$$ return MinecraftClient.getInstance().getWindow().getScaledHeight();
        //#endif
    }


}
