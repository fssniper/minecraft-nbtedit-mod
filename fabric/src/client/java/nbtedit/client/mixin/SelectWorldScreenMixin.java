package nbtedit.client.mixin;

import java.io.IOException;
import nbtedit.NBTEdit;
import nbtedit.client.screen.AccentButton;
import nbtedit.client.screen.WorldBrowserScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelStorageSource.LevelStorageAccess;
import net.minecraft.world.level.storage.LevelSummary;
import net.minecraft.world.level.validation.ContentValidationException;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SelectWorldScreen.class)
public abstract class SelectWorldScreenMixin extends Screen {
	@Shadow
	private @Nullable WorldSelectionList list;

	@Shadow
	protected @Nullable EditBox searchBox;

	@Unique
	private @Nullable Button nbtedit$button;

	private SelectWorldScreenMixin(Component title) {
		super(title);
	}

	@Inject(method = "init", at = @At("TAIL"))
	private void nbtedit$addButton(CallbackInfo info) {
		Button button = new AccentButton(20, 20, Component.literal("{}"), ignored -> this.nbtedit$openBrowser());
		button.setTooltip(Tooltip.create(Component.translatable("nbtedit.button.tooltip")));
		this.nbtedit$button = this.addRenderableWidget(button);
		this.nbtedit$button.active = this.list != null && this.list.getSelectedOpt().isPresent();
		this.nbtedit$placeButton();
	}

	@Inject(method = "repositionElements", at = @At("TAIL"))
	private void nbtedit$repositionButton(CallbackInfo info) {
		this.nbtedit$placeButton();
	}

	@Unique
	private void nbtedit$placeButton() {
		if (this.nbtedit$button == null) {
			return;
		}

		if (this.searchBox == null) {
			this.nbtedit$button.setPosition(6, 6);
			return;
		}

		this.nbtedit$button.setPosition(this.searchBox.getX() + this.searchBox.getWidth() + 4, this.searchBox.getY());
	}

	@Inject(method = "updateButtonStatus", at = @At("TAIL"))
	private void nbtedit$updateButton(@Nullable LevelSummary summary, CallbackInfo info) {
		if (this.nbtedit$button != null) {
			this.nbtedit$button.active = summary != null && !summary.isLocked();
		}
	}

	@Unique
	private void nbtedit$openBrowser() {
		WorldSelectionList worldList = this.list;
		if (worldList == null) {
			return;
		}

		worldList.getSelectedOpt().ifPresent(entry -> {
			String levelId = entry.getLevelSummary().getLevelId();

			LevelStorageAccess access;
			try {
				access = this.minecraft.getLevelSource().validateAndCreateAccess(levelId);
			} catch (IOException | ContentValidationException e) {
				NBTEdit.LOGGER.error("Failed to access level {}", levelId, e);
				SystemToast.onWorldAccessFailure(this.minecraft, levelId);
				return;
			}

			this.minecraft.gui.setScreen(new WorldBrowserScreen(access, () -> {
				access.safeClose();
				worldList.returnToScreen();
			}));
		});
	}
}
