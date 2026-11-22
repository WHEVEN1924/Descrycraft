package dev.descrycraft.descrycraft.client;

import dev.descrycraft.descrycraft.menu.ModuleWorkbenchMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * The bench screen.
 *
 * <p>Almost everything is the base class's work: it positions the slots, draws the item stacks and
 * handles the drag, and it already knows where the player inventory labels go. All this adds is
 * the panel behind them and readable label colours.
 */
public class ModuleWorkbenchScreen extends AbstractContainerScreen<ModuleWorkbenchMenu> {

    private static final Identifier BACKGROUND =
            Identifier.fromNamespaceAndPath("descrycraft", "textures/gui/module_workbench.png");

    /** 标题色，和面板边框的青色同一路。 */
    private static final int TITLE_COLOUR = 0xFF6FE3F5;
    /** 玩家物品栏标签，比标题暗一档，主次分明。 */
    private static final int INVENTORY_COLOUR = 0xFF8FA6B2;

    public ModuleWorkbenchScreen(ModuleWorkbenchMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, 176, 166);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        // The short blit overload assumes a 256x256 sheet - it forwards a hardcoded 256, 256 as the
        // texture size. This panel is 176x166, so that overload samples only the top-left 68% x 65%
        // of the image and stretches it across the whole quad: the screen comes out around 1.45x
        // too big and every slot well lands nowhere near its slot. Pass the real size explicitly.
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, x, y, 0.0F, 0.0F,
                this.imageWidth, this.imageHeight, this.imageWidth, this.imageHeight);
    }

    /**
     * 自己画标签，因为原版那套在这个面板上完全看不见。
     *
     * <p>{@code AbstractContainerScreen#extractLabels} 用的颜色是 {@code 0x404040} - 深灰，而且
     * 最后一个参数传的是 {@code false}，连投影都不带。这套配色是给原版那种浅米色 GUI 准备的，
     * 落到这张近黑的科技感面板上，标题和"物品栏"两个字就彻底糊进背景里了，看起来像整个界面
     * 一个字都没有。这里换成面板同色系的青与灰蓝，并打开投影。
     */
    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(this.font, this.title, this.titleLabelX, this.titleLabelY,
                TITLE_COLOUR, true);
        graphics.text(this.font, this.playerInventoryTitle, this.inventoryLabelX,
                this.inventoryLabelY, INVENTORY_COLOUR, true);
    }
}
