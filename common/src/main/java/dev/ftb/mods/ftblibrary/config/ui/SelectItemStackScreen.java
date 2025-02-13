package dev.ftb.mods.ftblibrary.config.ui;

import com.google.common.base.Stopwatch;
import com.google.common.collect.Iterators;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.architectury.registry.registries.Registries;
import dev.ftb.mods.ftblibrary.FTBLibrary;
import dev.ftb.mods.ftblibrary.config.ConfigCallback;
import dev.ftb.mods.ftblibrary.config.IntConfig;
import dev.ftb.mods.ftblibrary.config.ItemStackConfig;
import dev.ftb.mods.ftblibrary.config.NBTConfig;
import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftblibrary.icon.Icons;
import dev.ftb.mods.ftblibrary.icon.ItemIcon;
import dev.ftb.mods.ftblibrary.ui.BaseScreen;
import dev.ftb.mods.ftblibrary.ui.BlankPanel;
import dev.ftb.mods.ftblibrary.ui.Button;
import dev.ftb.mods.ftblibrary.ui.GuiHelper;
import dev.ftb.mods.ftblibrary.ui.Panel;
import dev.ftb.mods.ftblibrary.ui.PanelScrollBar;
import dev.ftb.mods.ftblibrary.ui.SimpleTextButton;
import dev.ftb.mods.ftblibrary.ui.TextBox;
import dev.ftb.mods.ftblibrary.ui.Theme;
import dev.ftb.mods.ftblibrary.ui.Widget;
import dev.ftb.mods.ftblibrary.ui.WidgetLayout;
import dev.ftb.mods.ftblibrary.ui.WidgetType;
import dev.ftb.mods.ftblibrary.ui.input.Key;
import dev.ftb.mods.ftblibrary.ui.input.MouseButton;
import dev.ftb.mods.ftblibrary.util.TooltipList;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * @author LatvianModder
 */
public class SelectItemStackScreen extends BaseScreen {

	public static final ExecutorService ITEM_SEARCH = Executors.newSingleThreadExecutor(task -> {
		var thread = new Thread(task, "FTBLibrary-ItemSearch");
		thread.setDaemon(true);
		return thread;
	});

	public static final List<ItemSearchMode> modes = new ArrayList<>();

	static {
		modes.add(ItemSearchMode.ALL_ITEMS);
		modes.add(ItemSearchMode.INVENTORY);
	}

	private static ItemSearchMode activeMode = null;

	private class ItemStackButton extends Button {
		private final ItemStack stack;

		private ItemStackButton(Panel panel, ItemStack is) {
			super(panel, TextComponent.EMPTY, Icons.BARRIER);
			setSize(18, 18);
			stack = is;
			title = null;
			icon = ItemIcon.getItemIcon(is);
		}

		public boolean shouldAdd(String search, String mod) {
			if (search.isEmpty()) {
				return true;
			}

			if (!mod.isEmpty()) {
				return Registries.getId(stack.getItem(), Registry.ITEM_REGISTRY).getNamespace().contains(mod);
			}

			return stack.getHoverName().getString().toLowerCase().contains(search);
		}

		@Override
		public Component getTitle() {
			if (title == null) {
				title = stack.getHoverName();
			}

			return title;
		}

		@Override
		public void addMouseOverText(TooltipList list) {
		}

		@Override
		public WidgetType getWidgetType() {
			return stack.getItem() == current.getItem() && Objects.equals(stack.getTag(), current.getTag()) ? WidgetType.MOUSE_OVER : super.getWidgetType();
		}

		@Override
		public void drawBackground(PoseStack matrixStack, Theme theme, int x, int y, int w, int h) {
			(getWidgetType() == WidgetType.MOUSE_OVER ? Color4I.LIGHT_GREEN.withAlpha(70) : Color4I.BLACK.withAlpha(50)).draw(matrixStack, x, y, w, h);
		}

		@Override
		public void onClicked(MouseButton button) {
			playClickSound();
			current = stack.copy();
		}
	}

	private class ButtonSwitchMode extends Button {
		private final Iterator<ItemSearchMode> modeIterator = Iterators.cycle(modes);

		public ButtonSwitchMode(Panel panel) {
			super(panel);
			activeMode = modeIterator.next();
		}

		@Override
		public void drawIcon(PoseStack matrixStack, Theme theme, int x, int y, int w, int h) {
			activeMode.getIcon().draw(matrixStack, x, y, w, h);
		}

		@Override
		public Component getTitle() {
			return new TranslatableComponent("ftblibrary.select_item.list_mode");
		}

		@Override
		public void addMouseOverText(TooltipList list) {
			super.addMouseOverText(list);
			list.add(activeMode.getDisplayName().withStyle(ChatFormatting.GRAY).append(new TextComponent(" [" + panelStacks.widgets.size() + "]").withStyle(ChatFormatting.DARK_GRAY)));
		}

		@Override
		public void onClicked(MouseButton button) {
			playClickSound();
			activeMode = modeIterator.next();
			panelStacks.refreshWidgets();
		}
	}

	private abstract class ButtonStackConfig extends Button {
		public ButtonStackConfig(Panel panel, Component title, Icon icon) {
			super(panel, title, icon);
		}

		@Override
		public WidgetType getWidgetType() {
			return current.isEmpty() ? WidgetType.DISABLED : super.getWidgetType();
		}
	}

	private class ButtonEditData extends Button {
		public ButtonEditData(Panel panel) {
			super(panel, TextComponent.EMPTY, Icons.BUG);
		}

		@Override
		public void drawIcon(PoseStack matrixStack, Theme theme, int x, int y, int w, int h) {
			matrixStack.pushPose();
			matrixStack.translate(x + w / 2D, y + h / 2D, 100);

			if (w != 16 || h != 16) {
				int s = Math.min(w, h);
				matrixStack.scale(s / 16F, s / 16F, s / 16F);
			}

			GuiHelper.drawItem(matrixStack, current, 0, true, null);
			matrixStack.popPose();
		}

		@Override
		public Component getTitle() {
			return current.getHoverName();
		}

		@Override
		public void onClicked(MouseButton button) {
			playClickSound();
			var c = new NBTConfig();

			EditConfigFromStringScreen.open(c, current.save(new CompoundTag()), config.defaultValue.save(new CompoundTag()), accepted -> {
				if (accepted) {
					current = ItemStack.of(c.value);
				}

				run();
			});
		}
	}

	private class ButtonCount extends ButtonStackConfig {
		public ButtonCount(Panel panel) {
			super(panel, new TranslatableComponent("ftblibrary.select_item.count"), ItemIcon.getItemIcon(Items.PAPER));
		}

		@Override
		public void onClicked(MouseButton button) {
			playClickSound();
			var c = new IntConfig(0, current.getMaxStackSize());
			EditConfigFromStringScreen.open(c, current.getCount(), config.defaultValue.getCount(), accepted -> {
				if (accepted) {
					current.setCount(c.value);
				}

				run();
			});
		}
	}

	private class ButtonNBT extends ButtonStackConfig {
		public ButtonNBT(Panel panel) {
			super(panel, new TranslatableComponent("ftblibrary.select_item.nbt"), ItemIcon.getItemIcon(Items.NAME_TAG));
		}

		@Override
		public void onClicked(MouseButton button) {
			playClickSound();
			var c = new NBTConfig();
			EditConfigFromStringScreen.open(c, current.getTag(), config.defaultValue.getTag(), accepted -> {
				if (accepted) {
					current.setTag(c.value);
				}

				run();
			});
		}
	}

	private class ButtonCaps extends ButtonStackConfig {
		public ButtonCaps(Panel panel) {
			super(panel, new TranslatableComponent("ftblibrary.select_item.caps"), ItemIcon.getItemIcon(Items.ANVIL));
		}

		@Override
		public void onClicked(MouseButton button) {
			playClickSound();

			final var nbt = current.save(new CompoundTag());
			var c = new NBTConfig();

			EditConfigFromStringScreen.open(c, (CompoundTag) nbt.get("ForgeCaps"), (CompoundTag) config.defaultValue.save(new CompoundTag()).get("ForgeCaps"), accepted -> {
				if (accepted) {
					if (c.value == null || c.value.isEmpty()) {
						nbt.remove("ForgeCaps");
					} else {
						nbt.put("ForgeCaps", c.value);
					}

					current = ItemStack.of(nbt);
				}

				SelectItemStackScreen.this.run();
			});
		}
	}

	public List<Widget> getItems(String search, Panel panel) {
		var timer = Stopwatch.createStarted();

		// sanity check, just in case
		if (activeMode == null) {
			return Collections.emptyList();
		}

		var items = activeMode.getAllItems();
		List<Widget> widgets = new ArrayList<>(search.isEmpty() ? items.size() + 1 : 64);

		var mod = "";

		if (search.startsWith("@")) {
			mod = search.substring(1);
		}

		var button = new ItemStackButton(panel, ItemStack.EMPTY);

		if (config.allowEmpty && button.shouldAdd(search, mod)) {
			widgets.add(new ItemStackButton(panel, ItemStack.EMPTY));
		}

		for (var stack : items) {
			if (!stack.isEmpty()) {
				button = new ItemStackButton(panel, stack);

				if (button.shouldAdd(search, mod)) {
					widgets.add(button);
					var j = widgets.size() - 1;
					button.setPos(1 + (j % 9) * 19, 1 + (j / 9) * 19);
				}
			}
		}

		FTBLibrary.LOGGER.info("Done updating item list in {}μs!", timer.stop().elapsed(TimeUnit.MICROSECONDS));
		return widgets;
	}

	private final ItemStackConfig config;
	private final ConfigCallback callback;
	private ItemStack current;
	private final Button buttonCancel, buttonAccept;
	private final Panel panelStacks;
	private final PanelScrollBar scrollBar;
	private final TextBox searchBox;
	private final Panel tabs;
	public long update = Long.MAX_VALUE;

	public SelectItemStackScreen(ItemStackConfig c, ConfigCallback cb) {
		setSize(211, 150);
		config = c;
		callback = cb;
		current = config.value.isEmpty() ? ItemStack.EMPTY : config.value.copy();

		var bsize = width / 2 - 10;

		buttonCancel = new SimpleTextButton(this, new TranslatableComponent("gui.cancel"), Icon.EMPTY) {
			@Override
			public void onClicked(MouseButton button) {
				playClickSound();
				callback.save(false);
			}

			@Override
			public boolean renderTitleInCenter() {
				return true;
			}
		};

		buttonCancel.setPosAndSize(8, height - 24, bsize, 16);

		buttonAccept = new SimpleTextButton(this, new TranslatableComponent("gui.accept"), Icon.EMPTY) {
			@Override
			public void onClicked(MouseButton button) {
				playClickSound();
				config.setCurrentValue(current);
				callback.save(true);
			}

			@Override
			public boolean renderTitleInCenter() {
				return true;
			}
		};

		buttonAccept.setPosAndSize(width - bsize - 8, height - 24, bsize, 16);

		panelStacks = new BlankPanel(this) {
			@Override
			public void addWidgets() {
				update = System.currentTimeMillis() + 100L;
			}

			@Override
			public void drawBackground(PoseStack matrixStack, Theme theme, int x, int y, int w, int h) {
				theme.drawPanelBackground(matrixStack, x, y, w, h);
			}
		};

		panelStacks.setPosAndSize(9, 24, 9 * 19 + 1, 5 * 19 + 1);

		scrollBar = new PanelScrollBar(this, panelStacks);
		scrollBar.setCanAlwaysScroll(true);
		scrollBar.setScrollStep(20);

		searchBox = new TextBox(this) {
			@Override
			public void onTextChanged() {
				panelStacks.refreshWidgets();
			}
		};

		searchBox.setPosAndSize(8, 7, width - 16, 12);
		searchBox.ghostText = I18n.get("gui.search_box");
		searchBox.setFocused(true);

		tabs = new Panel(this) {
			@Override
			public void addWidgets() {
				add(new ButtonSwitchMode(this));
				add(new ButtonEditData(this));

				if (!config.singleItemOnly) {
					add(new ButtonCount(this));
				}

				add(new ButtonNBT(this));
				add(new ButtonCaps(this));
			}

			@Override
			public void alignWidgets() {
				for (var widget : widgets) {
					widget.setSize(20, 20);
				}

				setHeight(align(WidgetLayout.VERTICAL));
			}
		};

		tabs.setPosAndSize(-19, 8, 20, 0);

		updateItemWidgets(Collections.emptyList());
	}

	private void updateItemWidgets(List<Widget> items) {
		panelStacks.widgets.clear();
		panelStacks.addAll(items);
		scrollBar.setPosAndSize(panelStacks.posX + panelStacks.width + 6, panelStacks.posY - 1, 16, panelStacks.height + 2);
		scrollBar.setValue(0);
		scrollBar.setMaxValue(1 + Mth.ceil(panelStacks.widgets.size() / 9F) * 19);
	}

	@Override
	public void addWidgets() {
		add(tabs);
		add(panelStacks);
		add(scrollBar);
		add(searchBox);
		add(buttonCancel);
		add(buttonAccept);
	}

	@Override
	public void onClosed() {
		super.onClosed();
	}

	@Override
	public boolean onClosedByKey(Key key) {
		if (super.onClosedByKey(key)) {
			callback.save(false);
			return false;
		}

		return false;
	}

	@Override
	public void drawBackground(PoseStack matrixStack, Theme theme, int x, int y, int w, int h) {
		super.drawBackground(matrixStack, theme, x, y, w, h);

		var now = System.currentTimeMillis();

		if (now >= update) {
			update = Long.MAX_VALUE;
			CompletableFuture.supplyAsync(() -> this.getItems(searchBox.getText().toLowerCase(), panelStacks), ITEM_SEARCH)
					.thenAcceptAsync(this::updateItemWidgets, Minecraft.getInstance());
		}
	}

	@Override
	public boolean doesGuiPauseGame() {
		var screen = getPrevScreen();
		return screen != null && screen.isPauseScreen();
	}
}