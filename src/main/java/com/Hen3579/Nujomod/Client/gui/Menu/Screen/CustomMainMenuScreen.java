package com.Hen3579.Nujomod.Client.gui.Menu.Screen;
//我想通过VLCJ的方式在CustomMainMenuScreen.java中使用BACKGROUND_VIDEO作为视频背景

import com.Hen3579.Nujomod.Inits.InitSounds;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.realmsclient.RealmsMainScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.AccessibilityOptionsScreen;
import net.minecraft.client.gui.screens.LanguageSelectScreen;
import net.minecraft.client.gui.screens.OptionsScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraftforge.client.gui.ModListScreen;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLLoader;
import java.awt.image.BufferedImage;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.server.packs.resources.Resource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import com.mojang.blaze3d.platform.NativeImage;
import java.awt.image.DataBufferInt;
// 在import区块添加
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery;
import uk.co.caprica.vlcj.player.base.MediaPlayer;
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer;
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormat;
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormatCallback;
import java.awt.Canvas;


import static com.Hen3579.Nujomod.NujoBraincraft.MODID;

public class CustomMainMenuScreen extends Screen {

    private final int screenWidth;
    private final int screenHeight;
    private static final ResourceLocation MENU_CONTAINER = new ResourceLocation(MODID, "textures/gui/title/menu_container.png");
    // 在类顶部添加常量（请替换为实际尺寸）
    private static final int MENU_CONTAINER_WIDTH = 1020; // 改为实际宽度
    private static final int MENU_CONTAINER_HEIGHT = 1080; // 改为实际高度
    private float animationProgress = 0.0f;
    private long lastUpdateTime = System.currentTimeMillis();
    private long screenCreatedTime;
    private int MainbuttonContainerX;
    private int MainbuttonContainerY;

    // 新增按钮容器常量
    private int MAIN_BUTTON_CONTAINER_WIDTH;
    private int MAIN_BUTTON_CONTAINER_HEIGHT;

    // 在类顶部添加主标题常量（请替换为实际尺寸）

    private static final ResourceLocation MAIN_TITLE = new ResourceLocation(MODID, "textures/gui/title/main_title.png");
    private static final int MAIN_TITLE_WIDTH = 842; // 改为实际宽度
    private static final int MAIN_TITLE_HEIGHT = 193; // 改为实际高度
    // 在类顶部添加新常量
    // 新增退出按钮尺寸常量
    private static final float QUIT_BUTTON_SCALE = 0.12f; // 基于容器宽度的比例
    private static final ResourceLocation BUTTON_BG = new ResourceLocation(MODID, "textures/gui/title/buttons/mini_button_selected_hanging.png");
    private static final ResourceLocation QUIT_ICON = new ResourceLocation(MODID, "textures/gui/title/buttons/quit_common.png");
    private static final ResourceLocation QUIT_ICON_HOVER = new ResourceLocation(MODID, "textures/gui/title/buttons/quit_selected.png");
    private static final ResourceLocation ACCESSIBILITY_ICON = new ResourceLocation(MODID, "textures/gui/title/buttons/accessibility_common.png");
    private static final ResourceLocation ACCESSIBILITY_ICON_HOVER = new ResourceLocation(MODID, "textures/gui/title/buttons/accessibility_selected.png");

    private static final ResourceLocation SETTINGS_ICON = new ResourceLocation(MODID, "textures/gui/title/buttons/settings_common.png");
    private static final ResourceLocation SETTINGS_ICON_HOVER = new ResourceLocation(MODID, "textures/gui/title/buttons/settings_selected.png");
    private static final ResourceLocation LANG_ICON = new ResourceLocation(MODID, "textures/gui/title/buttons/lang_common.png");
    private static final ResourceLocation LANG_ICON_HOVER = new ResourceLocation(MODID, "textures/gui/title/buttons/lang_selected.png");
    // 新增单人游戏按钮材质
    private static final ResourceLocation BUTTON_SINGLEPLAYER_COMMON = new ResourceLocation(MODID, "textures/gui/title/buttons/button_singleplayer_common.png");
    private static final ResourceLocation BUTTON_SINGLEPLAYER_HANGING = new ResourceLocation(MODID, "textures/gui/title/buttons/button_singleplayer_hanging.png");
    private static final ResourceLocation BUTTON_SINGLEPLAYER_SELECTED = new ResourceLocation(MODID, "textures/gui/title/buttons/button_singleplayer_selected.png");
  private static final ResourceLocation BUTTON_SINGLEPLAYER_TEXT_COMMON = new ResourceLocation(MODID, "textures/gui/title/buttons/button_singleplayer_text_common.png");
  private static final ResourceLocation BUTTON_SINGLEPLAYER_TEXT_HANGING = new ResourceLocation(MODID, "textures/gui/title/buttons/button_singleplayer_text_hanging.png");
    // 新增多人游戏按钮材质
    private static final ResourceLocation BUTTON_MULTIPLAYERS_COMMON = new ResourceLocation(MODID, "textures/gui/title/buttons/button_multiplayers_common.png");
  private static final ResourceLocation BUTTON_MULTIPLAYERS_HANGING = new ResourceLocation(MODID, "textures/gui/title/buttons/button_multiplayers_hanging.png");
  private static final ResourceLocation BUTTON_MULTIPLAYERS_SELECTED = new ResourceLocation(MODID, "textures/gui/title/buttons/button_multiplayers_selected.png");
  private static final ResourceLocation BUTTON_OTHERS_COMMON = new ResourceLocation(MODID, "textures/gui/title/buttons/button_others_common.png");
  private static final ResourceLocation BUTTON_OTHERS_HANGING = new ResourceLocation(MODID, "textures/gui/title/buttons/button_others_hanging.png");
  private static final ResourceLocation BUTTON_OTHERS_SELECTED = new ResourceLocation(MODID, "textures/gui/title/buttons/button_others_selected.png");
  private static final ResourceLocation  BUTTON_MULTIPLAYERS_TEXT_COMMON = new ResourceLocation(MODID, "textures/gui/title/buttons/button_multiplayers_text_common.png");
  private static final ResourceLocation BUTTON_MULTIPLAYERS_TEXT_HANGING = new ResourceLocation(MODID, "textures/gui/title/buttons/button_multiplayers_text_hanging.png");
    private static final ResourceLocation BUTTON_MODS_TEXT_COMMON = new ResourceLocation(MODID, "textures/gui/title/buttons/button_mods_text_common.png");
  private static final ResourceLocation BUTTON_MODS_TEXT_HANGING = new ResourceLocation(MODID, "textures/gui/title/buttons/button_mods_text_hanging.png");
  private static final ResourceLocation BUTTON_REALMS_TEXT_COMMON = new ResourceLocation(MODID, "textures/gui/title/buttons/button_realms_text_common.png");
  private static final ResourceLocation BUTTON_REALMS_TEXT_HANGING = new ResourceLocation(MODID, "textures/gui/title/buttons/button_realms_text_hanging.png");


  public static final ResourceLocation BACKGROUND = new ResourceLocation(MODID,"textures/gui/title/bg.png");
  public static final ResourceLocation BACKGROUND_VIDEO = new ResourceLocation(MODID,"videos/background_video.mp4");
  private SoundInstance currentMusic;

  private float animatedX; // 从局部变量提升为类字段
    private float alpha;     // 从局部变量提升为类字段
    // 新增字体大小和行间距控制参数
    private static final float DATE_TIME_FONT_SCALE = 3.6f;
    private static final int LINE_SPACING = 4;
    // 在类字段添加
    private EmbeddedMediaPlayer mediaPlayer;
    private Canvas videoCanvas;
    private int videoTextureID = -1;


    public CustomMainMenuScreen(int width, int height) {
        super(Component.literal("Custom Main Menu"));
        this.screenWidth = width;
        this.screenHeight = height;
        this.screenCreatedTime = System.currentTimeMillis(); // 记录屏幕创建时间


    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // 获取原始纹理尺寸（需要知道图片实际尺寸）

        int textureWidth = 1920; // 替换为实际图片宽度
        int textureHeight = 1080; // 替换为实际图片高度

        // 计算原始图片比例
        float imageAspect = 1920f / 1080f; // 16:9
        float windowAspect = (float) screenWidth / screenHeight;
        // 计算保持宽高比的最小缩放比例
        float scaleX = (float) this.width / textureWidth;
        float scaleY = (float) this.height / textureHeight;
        float scale = Math.max(scaleX, scaleY);

        // 计算目标绘制尺寸
        int targetWidth = (int) (textureWidth * scale);
        int targetHeight = (int) (textureHeight * scale);


        // 绘制背景
        RenderSystem.setShaderTexture(0, BACKGROUND);
        // 计算需要裁剪的区域
        if (windowAspect > imageAspect) {
            // 窗口更宽，基于高度缩放
            int renderWidth = (int) (screenHeight * imageAspect);
            guiGraphics.blit(BACKGROUND,
                    (screenWidth - renderWidth)/2, 0,  // 居中裁剪左右
                    renderWidth, screenHeight,
                    0, 0,
                    1920, 1080,
                    1920, 1080
            );
        } else {
            // 窗口更高，基于宽度缩放
            int renderHeight = (int) (screenWidth / imageAspect);
            guiGraphics.blit(BACKGROUND,
                    0, (screenHeight - renderHeight)/2, // 居中裁剪上下
                    screenWidth, renderHeight,
                    0, 0,
                    1920, 1080,
                    1920, 1080
            );
        }



        // 绘制菜单容器
        RenderSystem.setShaderTexture(0, MENU_CONTAINER);
        int containerHeight = (windowAspect > imageAspect) ? screenHeight : (int) (screenWidth / imageAspect);
        float containerAspect = (float) 102.0f / 108.0f;
        int containerWidth = (int) (containerHeight * containerAspect); // 使用实际图片比例

        // 更新动画进度（1.5秒完成）
        long currentTime = System.currentTimeMillis();
        float deltaTimeSinceCreation = (currentTime - screenCreatedTime) / 1000f;
        lastUpdateTime = currentTime;

        // 完全重写动画进度计算
        if(deltaTimeSinceCreation >= 1.0f) {
            float effectiveTime = deltaTimeSinceCreation - 1.0f;
            animationProgress = Math.min(effectiveTime / 1.5f, 1.0f);
        } else {
            animationProgress = 0.0f; // 在4秒等待期内保持0
        }

        // 计算动画效果（缓入函数）
        float easedProgress = 1 - (float) Math.pow(1 - animationProgress, 4); // 4次方实现强缓出效果
        this.animatedX = -containerWidth * (1 - easedProgress);
        this.alpha = animationProgress * 0.9f;
        // 新增：更新所有CustomTextureButton的透明度
        this.children().forEach(widget -> {
            if (widget instanceof CustomTextureButton button) {
                button.setAlpha(alpha);
            }
        });

        // 应用不透明度
        guiGraphics.setColor(1, 1, 1, alpha);
        guiGraphics.blit(MENU_CONTAINER,
                (int)(animatedX), (screenHeight - containerHeight)/2, // 左对齐，垂直居中
                containerWidth, containerHeight,
                0, 0,
                MENU_CONTAINER_WIDTH, MENU_CONTAINER_HEIGHT, // 使用实际纹理尺寸
                MENU_CONTAINER_WIDTH, MENU_CONTAINER_HEIGHT
        );


        // 新增主标题绘制（位于菜单容器上层）
        RenderSystem.setShaderTexture(0, MAIN_TITLE);
        guiGraphics.setColor(1, 1, 1, alpha); // 使用相同的透明度
        int titleWidth = containerWidth - 20; // 容器宽度减20
        float titleAspect = MAIN_TITLE_HEIGHT / (float)MAIN_TITLE_WIDTH; // 原图比例
        int titleHeight = (int)(titleWidth * titleAspect); // 按比例计算高度

        // 修正后的坐标计算（使用动态尺寸）
        int titleX = (int)(-animatedX) + (containerWidth - titleWidth)/2; // 改为当前实际宽度
        int titleY = ((screenHeight - titleHeight)/2)/3; // 使用动态高度

        guiGraphics.blit(MAIN_TITLE,
                titleX, titleY,
                titleWidth, titleHeight,
                0, 0,
                MAIN_TITLE_WIDTH, MAIN_TITLE_HEIGHT,
                MAIN_TITLE_WIDTH, MAIN_TITLE_HEIGHT
        );
        // 新增：在动画计算后更新按钮容器位置
        MainbuttonContainerX = (int)(animatedX) + screenWidth *7/192; // 随菜单容器移动
        MainbuttonContainerY = (screenHeight - containerHeight)/2 + screenHeight/4; // 保持相对位置


        // 在动画绘制之后添加时间和日期显示
        renderDateTime(guiGraphics);

        // 重置颜色混合状态
        guiGraphics.setColor(1, 1, 1, 1);
        // 在 super.render 前添加版本信息渲染
        renderVersionInfo(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTicks);
    }
    private void renderVersionInfo(GuiGraphics guiGraphics) {
        // 获取版本信息
        String forgeVersion = ModList.get().getModContainerById("forge")
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("Unknown");

        String minecraftVersion = ModList.get().getModContainerById("minecraft")
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("Unknown");

        int modCount = ModList.get().size();

        // 左下角版本信息
        String leftInfo = String.format("Forge %s\nMinecraft %s\n%d Mods Loaded",
                forgeVersion, minecraftVersion, modCount);

        // 右下角版权信息（使用当前年份）
        String rightInfo = String.format("Original Work:@怒九笑\nCopyright Mojang AB %d.\nDo not Distribute",
                java.time.LocalDateTime.now().getYear());

        // 绘制多行文字（白色，小字号）
        int lineHeight = minecraft.font.lineHeight + 2; // 行高加2像素间距
        int startY = screenHeight - 10 - (lineHeight * 2); // 从底部向上计算起
        int rightStartY = screenHeight - 10 - (lineHeight * 2);

        for (String line : leftInfo.split("\n")) {
            guiGraphics.drawString(
                    minecraft.font,
                    line,
                    5, // 左边距5像素
                    startY,
                    0xecec53,
                    false
            );
            startY += lineHeight; // 下移一行
        }
        // 绘制右下角文字（灰色，小字号）

        for (String line : rightInfo.split("\n")) {
            int textWidth = minecraft.font.width(line); // 修正为当前行的宽度
            guiGraphics.drawString(
                    minecraft.font,
                    line,
                    screenWidth - textWidth - 5, // 正确的右对齐计算
                    rightStartY,
                    0xAAAAAA,
                    false
            );
            rightStartY += lineHeight;
        }


    }

    // 新增日期时间渲染方法
    private void renderDateTime(GuiGraphics guiGraphics) {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        String timeText = now.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
        String dateText = now.format(java.time.format.DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String weekText = now.format(java.time.format.DateTimeFormatter.ofPattern("EEE", java.util.Locale.US));

        // 获取字体尺寸
        int containerCenterX = screenWidth * 146 / 192;
        int y = screenHeight / 3;

        // 绘制时间（2倍大小）
        // 使用自定义字体大小渲染时间
        renderTextWithScale(guiGraphics, timeText, containerCenterX, y, DATE_TIME_FONT_SCALE,true);

        // 计算缩放后的行高
        int scaledLineHeight = (int)(minecraft.font.lineHeight * DATE_TIME_FONT_SCALE) + LINE_SPACING;

        // 计算日期和星期的组合宽度（包含间距）
        int dateWidth = minecraft.font.width(dateText);
        int weekWidth = minecraft.font.width(weekText);
        int totalWidth = dateWidth + 4 + (int)(weekWidth * 1.0f);
        // 计算组合元素的起始X坐标（实现整体居中）
        int startX = containerCenterX - totalWidth / 2;
        // 分别渲染日期和星期（左对齐模式）
        renderTextWithScale(guiGraphics, dateText, startX, y + scaledLineHeight, 1.0f, false);
        renderTextWithScale(guiGraphics, weekText,
                startX + dateWidth + 4, // 日期后加4像素间距
                y + scaledLineHeight,
                1.0f, false);
    }
    private void renderTextWithScale(GuiGraphics guiGraphics, String text, int x, int y, float scale, boolean centerAligned) {
        int textWidth = minecraft.font.width(text);
        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(scale, scale, 1.0f);
        int renderX = centerAligned ? (int)(x / scale - textWidth / 2) : (int)(x / scale);
        guiGraphics.drawString(
                minecraft.font,
                Component.literal(text).withStyle(net.minecraft.ChatFormatting.WHITE),
                renderX,
                (int)(y / scale),
                0xFFFFFF,
                false
        );
        guiGraphics.pose().popPose();
    }

    @Override
    protected void init() {
        super.init();

        // 初始化视频抓取器

        // 这个调用会保留原版按钮的初始化逻辑
        // 如果要添加自定义按钮，在此处添加
        // 例如：
        // this.addRenderableWidget(new Button(...));
        this.MAIN_BUTTON_CONTAINER_WIDTH = (this.width) * 80 /192;
        this.MAIN_BUTTON_CONTAINER_HEIGHT  = MAIN_BUTTON_CONTAINER_WIDTH / 2;

        // 新增位置计算逻辑
        float windowAspect = (float) screenWidth / screenHeight;
        float imageAspect = 1920f / 1080f;
        int containerHeight = (windowAspect > imageAspect) ? screenHeight : (int) (screenWidth / imageAspect);
        float containerAspect = 102.0f / 108.0f;
        int containerWidth = (int) (containerHeight * containerAspect);

        // 计算新的容器位置
       this.MainbuttonContainerX = screenWidth *7/192; // 水平居中偏移
        this.MainbuttonContainerY = screenHeight /2; // 位于屏幕3/4高度处

        // 初始化时设置基准位置（具体动画位置在render()中更新）
        MainbuttonContainerX = screenWidth *7/192;
        MainbuttonContainerY = screenHeight /2;


        //MenuContainer入场动画
       this.animationProgress = 0.0f;
       this.lastUpdateTime = System.currentTimeMillis();

        // 计算按钮容器位置
        int buttonContainerX = MainbuttonContainerX;
        int buttonContainerY = MainbuttonContainerY;

        // 单人游戏按钮
        int singlePlayerBtnWidth = MAIN_BUTTON_CONTAINER_WIDTH * 5 / 8;
        int singlePlayerBtnHeight = MAIN_BUTTON_CONTAINER_HEIGHT * 5 / 8;
        this.addRenderableWidget(new CustomTextureButton(
                buttonContainerX,
                buttonContainerY,
                singlePlayerBtnWidth,
                singlePlayerBtnHeight,
                Component.empty(),
                button -> {
                    // 处理单人游戏逻辑
                    minecraft.setScreen(new net.minecraft.client.gui.screens.worldselection.SelectWorldScreen(this));
                },
                BUTTON_SINGLEPLAYER_COMMON,
                BUTTON_SINGLEPLAYER_HANGING,
                BUTTON_SINGLEPLAYER_SELECTED,
                BUTTON_SINGLEPLAYER_TEXT_COMMON,
                BUTTON_SINGLEPLAYER_TEXT_HANGING,
                0.4f,
                true

        ));

        // 多人游戏按钮
        int multiPlayersBtnWidth = MAIN_BUTTON_CONTAINER_WIDTH * 25 / 80;
        int multiPlayersBtnHeight = MAIN_BUTTON_CONTAINER_HEIGHT * 25 / 40;
        this.addRenderableWidget(new CustomTextureButton(
                buttonContainerX + MAIN_BUTTON_CONTAINER_WIDTH - multiPlayersBtnWidth,
                buttonContainerY,
                multiPlayersBtnWidth,
                multiPlayersBtnHeight,
                Component.empty(),
                button -> {
                    // 处理多人游戏逻辑
                    minecraft.setScreen(new net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen(this));
                },
                BUTTON_MULTIPLAYERS_COMMON,
                BUTTON_MULTIPLAYERS_HANGING,
                BUTTON_MULTIPLAYERS_SELECTED,
                BUTTON_MULTIPLAYERS_TEXT_COMMON,
                BUTTON_MULTIPLAYERS_TEXT_HANGING,
                0.4f,
                false
        ));

        // 管理Mods按钮
        int otherBtnWidth = MAIN_BUTTON_CONTAINER_WIDTH * 38 / 80;
        int otherBtnHeight = MAIN_BUTTON_CONTAINER_HEIGHT / 4;
        this.addRenderableWidget(new CustomTextureButton(
                buttonContainerX,
                buttonContainerY + singlePlayerBtnHeight + 10, // 下方间隔10像素
                otherBtnWidth,
                otherBtnHeight,
                Component.empty(),
                button -> {
                    // 处理管理Mods逻辑
                    // 这里需要替换为实际打开Mod管理界面的代码
                    minecraft.setScreen(new ModListScreen(this));
                },
                BUTTON_OTHERS_COMMON,
                BUTTON_OTHERS_HANGING,
                BUTTON_OTHERS_SELECTED,
                BUTTON_MODS_TEXT_COMMON,
                BUTTON_MODS_TEXT_HANGING,
                0.4f,
                false
        ));

        // 管理Realms按钮
        this.addRenderableWidget(new CustomTextureButton(
                buttonContainerX + MAIN_BUTTON_CONTAINER_WIDTH - otherBtnWidth,
                buttonContainerY + singlePlayerBtnHeight + 10, // 下方间隔10像素
                otherBtnWidth,
                otherBtnHeight,
                Component.empty(),
                button -> {
                    // 处理管理Realms逻辑
                    // 修正类引用
                    minecraft.setScreen(new RealmsMainScreen(this));
                },
                BUTTON_OTHERS_COMMON,
                BUTTON_OTHERS_HANGING,
                BUTTON_OTHERS_SELECTED,
                BUTTON_REALMS_TEXT_COMMON,
                BUTTON_REALMS_TEXT_HANGING,
                0.4f,
                false
        ));



        // 计算动态位置和尺寸
        int buttonBgWidth = (int)(this.width * (10.0f / 192));
        int buttonBgHeight = (int)(this.height * (10.0f / 108));
        int buttonX = (int)(this.width * (187.0f / 192)) - buttonBgWidth; // 192-5=187
        int buttonY = (int)(this.height * (15.0f / 108)) - buttonBgHeight; //
        int spacing = buttonBgWidth / 3; // 间距

        this.addRenderableWidget(new HoverableButton(
                buttonX - 3*buttonBgWidth - 3*spacing,
                buttonY,
                buttonBgWidth, buttonBgHeight,
                Component.empty(),
                button -> minecraft.setScreen(new LanguageSelectScreen(this,minecraft.options,minecraft.getLanguageManager() ))
                ,BUTTON_BG,
                LANG_ICON,
                LANG_ICON_HOVER,1.0f));

        this.addRenderableWidget(new HoverableButton(
                buttonX - 2*buttonBgWidth - 2*spacing,
                buttonY,
                buttonBgWidth, buttonBgHeight,
                Component.empty(),
                button -> minecraft.setScreen(new OptionsScreen(this, minecraft.options)),
                BUTTON_BG,
                SETTINGS_ICON,
                SETTINGS_ICON_HOVER,1.0f
        ));

        this.addRenderableWidget(new HoverableButton(
                buttonX - buttonBgWidth - spacing, // 退出按钮左侧间隔半个按钮宽度
                buttonY, // 保持与退出按钮相同的Y坐标
                buttonBgWidth, buttonBgHeight, // 动态按钮背景尺寸,
                Component.empty(),
                button -> minecraft.setScreen(new AccessibilityOptionsScreen(this, minecraft.options)),
                BUTTON_BG,
                ACCESSIBILITY_ICON,
                ACCESSIBILITY_ICON_HOVER,1.0f
        ));

        // 添加右上角退出按钮
        this.addRenderableWidget(new HoverableButton(
                buttonX, buttonY, // 动态计算的位置
                buttonBgWidth, buttonBgHeight, // 动态按钮背景尺寸
                Component.empty(),
                button -> minecraft.stop(),
                BUTTON_BG,
                QUIT_ICON,
                QUIT_ICON_HOVER,1.0f
        ));


    }
    @Override
    public void removed() {
        stopMusic();

        super.removed();
    }


    private void stopMusic() {
        if (currentMusic != null) {
            minecraft.getSoundManager().stop(currentMusic);
            currentMusic = null;
        }
    }
    // 添加自定义按钮类（放在文件末尾，类内部）
    private class HoverableButton extends Button {

        protected final ResourceLocation bgTexture;  // 从private改为protected
        protected final ResourceLocation iconTexture; // 从private改为protected
        private final ResourceLocation hoverIconTexture;
        private final float alpha; // 新增透明度参数

        public HoverableButton(int x, int y, int width, int height, Component message, OnPress onPress,
                               ResourceLocation bg, ResourceLocation icon, ResourceLocation hoverIcon, float alpha) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
            this.bgTexture = bg;
            this.iconTexture = icon;
            this.hoverIconTexture = hoverIcon;
            this.alpha = alpha; // 默认透明度为1.0
        }

        @Override
        public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            float currentAlpha = CustomMainMenuScreen.this.alpha;
            // 计算图标动态尺寸
            guiGraphics.setColor(1, 1, 1, alpha); // 应用透明度
            int iconWidth = (int)(width * (4.8f / 10)); // 按钮宽度的48%
            int iconHeight = (int)(height * (4.8f / 10)); // 按钮高度的48%

            if (isHovered()) {
                // 绘制背景
                guiGraphics.setColor(1, 1, 1, 0.6f);
                guiGraphics.blit(bgTexture,
                        getX(), getY(),
                        width, height, // 使用按钮实际尺寸
                        0, 0, // 使用完整的纹理区域
                        20, 20, // 原始纹理尺寸
                        20, 20);
                guiGraphics.setColor(1, 1, 1, 1);

                // 绘制悬停图标（居中）
                guiGraphics.blit(hoverIconTexture,
                        getX() + (width - iconWidth)/2,
                        getY() + (height - iconHeight)/2,
                        iconWidth, iconHeight,
                        0, 0,
                        iconWidth, iconHeight,
                        iconWidth, iconHeight);
            } else {
                // 绘制普通状态图标（居中）
                guiGraphics.blit(iconTexture,
                        getX() + (width - iconWidth)/2,
                        getY() + (height - iconHeight)/2,
                        iconWidth, iconHeight,
                        0, 0,
                        iconWidth, iconHeight,
                        iconWidth, iconHeight);
            }
        }
    }

    private class CustomTextureButton extends Button {
        private final boolean isSingleplayerButton; // 新增标识字段

        private final ResourceLocation commonTexture;
        private final ResourceLocation hangingTexture;
        private final ResourceLocation selectedTexture;
        // 移除animatedX和alpha字段
        private final int originalX; // 新增原始X坐标存储

        private final ResourceLocation textCommonTexture;
        private final ResourceLocation textHoverTexture;
        private final float textScale;
        private Minecraft minecraft;


        public CustomTextureButton(int x, int y, int width, int height, Component message, OnPress onPress,
                                   ResourceLocation common, ResourceLocation hanging, ResourceLocation selected,
                                   ResourceLocation textCommon, ResourceLocation textHover, float textScale,
                                   boolean isSingleplayerButton) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION);            this.commonTexture = common;
            this.hangingTexture = hanging;
            this.selectedTexture = selected;
            this.textCommonTexture = textCommon;
            this.textHoverTexture = textHover;
            this.textScale = textScale;
            this.originalX = x;
            this.isSingleplayerButton = isSingleplayerButton;
        }

        @Override
        public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            // 从父屏幕获取动画参数
            float currentAnimatedX = CustomMainMenuScreen.this.animatedX;
            float currentAlpha = CustomMainMenuScreen.this.alpha;

            // 更新按钮位置
            this.setX((int)(originalX + currentAnimatedX));
            guiGraphics.setColor(1, 1, 1, currentAlpha);

            // 新状态判断逻辑
            // 通过实例方法访问minecraft字段
            ResourceLocation texture = commonTexture;
            if (isActive() && isHovered()) {
                // 直接使用继承自Widget父类的minecraft字段
                boolean isPressed = this.minecraft != null
                        && this.minecraft.mouseHandler.isLeftPressed()
                        && isMouseOver(mouseX, mouseY);
                texture = isPressed ? selectedTexture : hangingTexture;
            }

            guiGraphics.setColor(1, 1, 1, 1);
            guiGraphics.blit(texture,
                    getX(), getY(),
                    width, height,
                    0, 0,
                    width, height,
                    width, height);
            guiGraphics.setColor(1, 1, 1, 1); // 重置颜色状态

            // 新增文本贴图渲染
            ResourceLocation textTexture = isHoveredOrFocused() ? textHoverTexture : textCommonTexture;
            float textHeight = height * textScale;
            float textWidth = textHeight * (getTextureWidth(textTexture) / (float)getTextureHeight(textTexture));

            guiGraphics.blit(textTexture,
                    (int)(getX() + (width - textWidth)/2),  // 水平居中
                    (int)(getY() + (height - textHeight)/2), // 垂直居中
                    (int)textWidth, (int)textHeight,
                    0, 0,
                    (int)textWidth, (int)textHeight,
                    (int)textWidth, (int)textHeight);
        }
        private int getTextureHeight(ResourceLocation texture) {
            // 根据实际纹理尺寸调整，与getTextureWidth对应
            if(texture.getPath().contains("singleplayer_text")) return 23;
            if(texture.getPath().contains("multiplayers_text")) return 20;
            if(texture.getPath().contains("mods_text")) return 12;
            if(texture.getPath().contains("realms_text")) return 10;
            return 60; // 默认值
        }
        private int getTextureWidth(ResourceLocation texture) {
            // 需要根据实际纹理尺寸调整，这里假设已知道实际尺寸
            if(texture.getPath().contains("singleplayer_text")) return 70;
            if(texture.getPath().contains("multiplayers_text")) return 38;
            if(texture.getPath().contains("mods_text")) return 43;
            if(texture.getPath().contains("realms_text")) return 51;
            return 200; // 默认值
        }
        @Override
        public void onPress() {
            // 添加调试信息
            System.out.println("DEBUG: 按钮类型 - " + (this.isSingleplayerButton ? "单人游戏" : "其他"));
            System.out.println("DEBUG: minecraft实例 - " + (this.minecraft != null ? "存在" : "空"));

            // 添加音效播放逻辑
            if (this.minecraft != null && this.minecraft.player != null) {
                System.out.println("DEBUG: 正在播放音效 - " +
                        (this.isSingleplayerButton ? "SINGLEPLAYER_START" : "UI_BUTTON_CLICK"));

                // 使用新字段进行判断
                if (this.isSingleplayerButton) {
                    System.out.println("DEBUG: 正在尝试播放单人游戏音效");
                    this.minecraft.player.playSound(
                            InitSounds.SINGLEPLAYER_START.get(),
                            1.0f, 1.0f
                    );
                } else {
                    this.minecraft.player.playSound(
                            SoundEvents.UI_BUTTON_CLICK.value(),
                            1.0f,
                            1.0f
                    );
                }
            }
            super.onPress();
        }
    }
    // 扩展自定义按钮类
}
