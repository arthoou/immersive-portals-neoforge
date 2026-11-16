package qouteall.imm_ptl.peripheral.wand;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.CreativeModeTab.Output;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import qouteall.imm_ptl.core.IPCGlobal;
import qouteall.imm_ptl.core.IPMcHelper;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.block_manipulation.BlockManipulationServer;
import qouteall.q_misc_util.platform.ClientPlatform;
import qouteall.q_misc_util.platform.Platform;

public class PortalWandItem extends Item {
   public static final PortalWandItem instance = new PortalWandItem(
      new Properties().setId(ResourceKey.create(Registries.ITEM, McHelper.newResourceLocation("immersive_portals:portal_wand")))
   );
   public static final Codec<PortalWandItem.Mode> MODE_CODEC = Codec.STRING.xmap(PortalWandItem.Mode::fromStr, PortalWandItem.Mode::toStr);
   public static final DataComponentType<PortalWandItem.Mode> COMPONENT_TYPE = DataComponentType.<PortalWandItem.Mode>builder().persistent(MODE_CODEC).build();
   private static boolean instructionInformed = false;

   public static void init() {
      Platform.get().registerBuiltIn(BuiltInRegistries.DATA_COMPONENT_TYPE, McHelper.newResourceLocation("iportal:portal_wand_data"), COMPONENT_TYPE);
      Platform.get()
         .onAttackBlock(
            (player, world, hand, pos, direction) -> player.getMainHandItem().getItem() == instance ? InteractionResult.FAIL : InteractionResult.PASS
         );
      BlockManipulationServer.canDoCrossPortalInteractionEvent.register(p -> p.getMainHandItem().getItem() != instance);
   }

   public static void initClient() {
      ClientPlatform.get().onClientTickEnd(client -> {
         if (client.player != null) {
            ItemStack itemStack = client.player.getMainHandItem();
            if (itemStack.getItem() == instance) {
               updateDisplay(itemStack);
            } else {
               ClientPortalWandPortalCreation.clearCursorPointing();
            }
         }

         ClientPortalWandPortalDrag.tick();
      });
      IPCGlobal.CLIENT_CLEANUP_EVENT.register(ClientPortalWandPortalCreation::reset);
      IPCGlobal.CLIENT_CLEANUP_EVENT.register(ClientPortalWandPortalDrag::reset);
      IPCGlobal.CLIENT_CLEANUP_EVENT.register(ClientPortalWandPortalCopy::reset);
   }

   public static void addIntoCreativeTag(Output entries) {
      ItemStack w1 = new ItemStack(instance);
      w1.set(COMPONENT_TYPE, PortalWandItem.Mode.CREATE_PORTAL);
      entries.accept(w1);
      ItemStack w2 = new ItemStack(instance);
      w2.set(COMPONENT_TYPE, PortalWandItem.Mode.DRAG_PORTAL);
      entries.accept(w2);
      ItemStack w3 = new ItemStack(instance);
      w3.set(COMPONENT_TYPE, PortalWandItem.Mode.COPY_PORTAL);
      entries.accept(w3);
   }

   public PortalWandItem(Properties properties) {
      super(properties);
   }

   @Environment(EnvType.CLIENT)
   public static void onClientLeftClick(LocalPlayer player, ItemStack itemStack) {
      if (player.isShiftKeyDown()) {
         showSettings(player);
      } else {
         PortalWandItem.Mode mode = itemStack.getOrDefault(COMPONENT_TYPE, PortalWandItem.Mode.FALLBACK);
         switch (mode) {
            case CREATE_PORTAL:
               ClientPortalWandPortalCreation.onLeftClick();
               break;
            case DRAG_PORTAL:
               ClientPortalWandPortalDrag.onLeftClick();
               break;
            case COPY_PORTAL:
               ClientPortalWandPortalCopy.onLeftClick();
         }
      }
   }

   @Override
   public InteractionResult use(Level world, Player player, InteractionHand hand) {
      ItemStack itemStack = player.getItemInHand(hand);
      PortalWandItem.Mode mode = itemStack.getOrDefault(COMPONENT_TYPE, PortalWandItem.Mode.FALLBACK);
      if (player.isShiftKeyDown() && !world.isClientSide() && !PortalWandInteraction.isDragging((ServerPlayer)player)) {
         PortalWandItem.Mode nextMode = mode.next();
         itemStack.set(COMPONENT_TYPE, nextMode);
         return InteractionResult.SUCCESS.heldItemTransformedTo(itemStack);
      }

      if (!player.isShiftKeyDown() && world.isClientSide()) {
         this.onUseClient(mode);
      }

      return super.use(world, player, hand);
   }

   @Environment(EnvType.CLIENT)
   private void onUseClient(PortalWandItem.Mode mode) {
      switch (mode) {
         case CREATE_PORTAL:
            ClientPortalWandPortalCreation.onRightClick();
            break;
         case DRAG_PORTAL:
            ClientPortalWandPortalDrag.onRightClick();
            break;
         case COPY_PORTAL:
            ClientPortalWandPortalCopy.onRightClick();
      }
   }

   @Environment(EnvType.CLIENT)
   @Override
   public void appendHoverText(
      ItemStack stack, TooltipContext tooltipContext, TooltipDisplay tooltipDisplay, Consumer<Component> tooltip, TooltipFlag tooltipFlag
   ) {
      super.appendHoverText(stack, tooltipContext, tooltipDisplay, tooltip, tooltipFlag);
      tooltip.accept(
         Component.translatable(
            "imm_ptl.wand.item_desc_1",
            Minecraft.getInstance().options.keyShift.getTranslatedKeyMessage(),
            Minecraft.getInstance().options.keyUse.getTranslatedKeyMessage()
         )
      );
      tooltip.accept(
         Component.translatable(
            "imm_ptl.wand.item_desc_2",
            Minecraft.getInstance().options.keyShift.getTranslatedKeyMessage(),
            Minecraft.getInstance().options.keyAttack.getTranslatedKeyMessage()
         )
      );
   }

   @Override
   public Component getName(ItemStack stack) {
      PortalWandItem.Mode mode = stack.getOrDefault(COMPONENT_TYPE, PortalWandItem.Mode.FALLBACK);
      MutableComponent baseText = Component.translatable("item.immersive_portals.portal_wand");
      return baseText.append(Component.literal(" : ")).append(mode.getText().withStyle(ChatFormatting.GOLD));
   }

   public static void showSettings(Player player) {
      player.displayClientMessage(Component.translatable("imm_ptl.wand.settings_1"), false);
      player.displayClientMessage(Component.translatable("imm_ptl.wand.settings_alignment"), false);
      int[] alignments = new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 16, 32, 64};
      List<MutableComponent> alignmentSettingTexts = new ArrayList<>();

      for (int alignment : alignments) {
         MutableComponent textWithCommand = IPMcHelper.getTextWithCommand(
            Component.literal("1/" + alignment), "/imm_ptl_client_debug wand set_cursor_alignment " + alignment
         );
         alignmentSettingTexts.add(textWithCommand);
      }

      alignmentSettingTexts.add(
         IPMcHelper.getTextWithCommand(Component.translatable("imm_ptl.wand.no_alignment"), "/imm_ptl_client_debug wand set_cursor_alignment 0")
      );
      player.displayClientMessage(alignmentSettingTexts.stream().reduce(Component.literal(""), (a, b) -> a.append(" ").append(b)), false);
      player.displayClientMessage(Component.translatable("imm_ptl.wand.settings_2", Minecraft.getInstance().options.keyChat.getTranslatedKeyMessage()), false);
   }

   @Environment(EnvType.CLIENT)
   private static void updateDisplay(ItemStack itemStack) {
      PortalWandItem.Mode mode = itemStack.getOrDefault(COMPONENT_TYPE, PortalWandItem.Mode.FALLBACK);
      switch (mode) {
         case CREATE_PORTAL:
            ClientPortalWandPortalCreation.updateDisplay();
            break;
         case DRAG_PORTAL:
            ClientPortalWandPortalDrag.updateDisplay();
            break;
         case COPY_PORTAL:
            ClientPortalWandPortalCopy.updateDisplay();
      }
   }

   @Environment(EnvType.CLIENT)
   public static void clientRender(
      LocalPlayer player, ItemStack itemStack, PoseStack poseStack, BufferSource bufferSource, double camX, double camY, double camZ
   ) {
      if (!instructionInformed) {
         instructionInformed = true;
      }

      PortalWandItem.Mode mode = itemStack.getOrDefault(COMPONENT_TYPE, PortalWandItem.Mode.FALLBACK);
      switch (mode) {
         case CREATE_PORTAL:
            ClientPortalWandPortalCreation.render(poseStack, bufferSource, camX, camY, camZ);
            break;
         case DRAG_PORTAL:
            ClientPortalWandPortalDrag.render(poseStack, bufferSource, camX, camY, camZ);
            break;
         case COPY_PORTAL:
            ClientPortalWandPortalCopy.render(poseStack, bufferSource, camX, camY, camZ);
      }
   }

   public enum Mode {
      CREATE_PORTAL,
      DRAG_PORTAL,
      COPY_PORTAL;

      public static final PortalWandItem.Mode FALLBACK = CREATE_PORTAL;

      public static PortalWandItem.Mode fromTag(CompoundTag tag) {
         String mode = tag.getStringOr("mode", "");
         return fromStr(mode);
      }

      public static PortalWandItem.Mode fromStr(String mode) {
         return switch (mode) {
            case "create_portal" -> CREATE_PORTAL;
            case "drag_portal" -> DRAG_PORTAL;
            case "copy_portal" -> COPY_PORTAL;
            default -> FALLBACK;
         };
      }

      public PortalWandItem.Mode next() {
         return switch (this) {
            case CREATE_PORTAL -> DRAG_PORTAL;
            case DRAG_PORTAL -> COPY_PORTAL;
            case COPY_PORTAL -> CREATE_PORTAL;
         };
      }

      public CompoundTag toTag() {
         CompoundTag tag = new CompoundTag();
         String modeString = this.toStr();
         tag.putString("mode", modeString);
         return tag;
      }

      public String toStr() {
         return switch (this) {
            case CREATE_PORTAL -> "create_portal";
            case DRAG_PORTAL -> "drag_portal";
            case COPY_PORTAL -> "copy_portal";
         };
      }

      public MutableComponent getText() {
         return switch (this) {
            case CREATE_PORTAL -> Component.translatable("imm_ptl.wand.mode.create_portal");
            case DRAG_PORTAL -> Component.translatable("imm_ptl.wand.mode.drag_portal");
            case COPY_PORTAL -> Component.translatable("imm_ptl.wand.mode.copy_portal");
         };
      }
   }
}
