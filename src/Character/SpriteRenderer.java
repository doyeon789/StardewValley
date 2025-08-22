package Character;

import java.awt.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import javax.swing.Timer;
import java.io.File;
import java.io.IOException;

public class SpriteRenderer {
    /** ========== 스프라이트 이미지 관련 ========== **/
    private BufferedImage spriteSheet;          /// 전체 스프라이트 시트 이미지 -> farmer
    private BufferedImage spriteSheetShirt;     /// 전체 스프라이트 시트 이미지 -> Shirt
    private BufferedImage spriteSheetPants;     /// 전체 스프라이트 시트 이미지 -> Pants
    private BufferedImage spriteSheetHair;      /// 전체 스프라이트 시트 이미지 -> Hair
    private BufferedImage baseFrame;            /// 현재 기본 프레임 (몸체)
    private BufferedImage armFrame;             /// 현재 팔 프레임
    private BufferedImage shirtFrame;           /// 현재 셔츠 프레임
    private BufferedImage pantsFrame;           /// 현재 바지 프레임
    private BufferedImage hairFrame;            /// 현재 머리카락 프레임

    /** ========== 색상 설정 (HSB) ========== **/
    private static final float HAIR_HUE = 0.08f;        // 머리카락 색조 (갈색)
    private static final float HAIR_SATURATION = 0.8f;  // 머리카락 채도
    private static final float HAIR_BRIGHTNESS = 0.6f;  // 머리카락 밝기

    private static final float PANTS_HUE = 0.65f;       // 바지 색조 (보라색)
    private static final float PANTS_SATURATION = 0.7f; // 바지 채도
    private static final float PANTS_BRIGHTNESS = 0.78f; // 바지 밝기

    /** ========== 위치 및 크기 정보 ========== **/
    private int x = 0;                          // 스프라이트의 X 좌표
    private int y = 0;                          // 스프라이트의 Y 좌표
    private int x_Base = 0;                     // 몸통 스프라이트의 X 좌표
    private int y_Base = 0;                     // 몸통 스프라이트의 Y 좌표
    private int x_Arm = 0;                      // 팔 스프라이트의 X 좌표
    private int y_Arm = 0;                      // 팔 스프라이트의 Y 좌표
    private int x_Shirt = 0;                    // 셔츠 스프라이트의 X 좌표
    private int y_Shirt = 0;                    // 셔츠 스프라이트의 Y 좌표
    private int x_Pants = 0;                    // 바지 스프라이트의 X 좌표
    private int y_Pants = 0;                    // 바지 스프라이트의 Y 좌표
    private int x_Hair = 0;                     // 머리카락 스프라이트의 X 좌표
    private int y_Hair = 0;                     // 머리카락 스프라이트의 Y 좌표

    private static final int SPRITE_WIDTH = 16;         // 개별 스프라이트의 가로 크기 (픽셀)
    private static final int SPRITE_HEIGHT = 32;        // 개별 스프라이트의 세로 크기 (픽셀)
    private static final int SPRITE_WIDTH_Shirt = 8;    // 개별 셔츠 스프라이트의 가로 크기 (픽셀)
    private static final int SPRITE_HEIGHT_Shirt = 8;   // 개별 셔츠 스프라이트의 세로 크기 (픽셀)
    private static final int SPRITE_WIDTH_Pants = 16;   // 개별 바지 스프라이트의 가로 크기 (픽셀)
    private static final int SPRITE_HEIGHT_Pants = 32;  // 개별 바지 스프라이트의 세로 크기 (픽셀)
    private static final int SPRITE_WIDTH_Hair = 16;    // 개별 머리 스프라이트의 가로 크기 (픽셀)
    private static final int SPRITE_HEIGHT_Hair = 32;   // 개별 머리 스프라이트의 세로 크기 (픽셀)

    private static final int SCALE = 3;          // 스프라이트 확대 비율

    /** ========== 애니메이션 시스템 ========== **/
    private Timer animationTimer;               /// 애니메이션 타이머 (190ms 간격)
    private boolean isAnimating = false;        /// 현재 애니메이션 중인지 여부
    private int currentAnimFrame = 0;           /// 현재 애니메이션 프레임 인덱스
    private int maxAnimFrames = 6;              /// 현재 애니메이션의 최대 프레임 수

    private int[] currentBaseFrames = {0, 0};   /// 현재 기본 애니메이션 프레임 배열 (몸체)
    private int[] currentArmFrames = {6, 6};    /// 현재 팔 애니메이션 프레임 배열
    private int[] currentShirtFrames = {0, 0};  /// 현재 셔츠 애니메이션 프레임 배열
    private int[] currentPantsFrames = {0, 0};  /// 현재 바지 애니메이션 프레임 배열
    private int[] currentHairFrames = {65, 65}; /// 현재 머리 애니메이션 프레임 배열
    private boolean currentFlipped = false;     /// 현재 좌우 반전 상태

    /** ========== 프레임별 오프셋 배열 ========== **/
    private int[] frameOffsets_BaseX = {0};     // 몸통 X 오프셋 배열
    private int[] frameOffsets_BaseY = {0};     // 몸통 Y 오프셋 배열
    private int[] frameOffsets_ArmX = {0};      // 팔 X 오프셋 배열
    private int[] frameOffsets_ArmY = {0};      // 팔 Y 오프셋 배열
    private int[] frameOffsets_ShirtX = {12};   // 셔츠 X 오프셋 배열
    private int[] frameOffsets_ShirtY = {42};   // 셔츠 Y 오프셋 배열
    private int[] frameOffsets_PantsX = {0};    // 바지 X 오프셋 배열
    private int[] frameOffsets_PantsY = {0};    // 바지 Y 오프셋 배열
    private int[] frameOffsets_HairX = {0};     // 머리카락 X 오프셋 배열
    private int[] frameOffsets_HairY = {0};     // 머리카락 Y 오프셋 배열

    /** ========== 키 입력 상태 관리 ========== **/
    private boolean sPressed = false;           /// S키 (아래) 눌림 상태
    private boolean dPressed = false;           /// D키 (오른쪽) 눌림 상태
    private boolean aPressed = false;           /// A키 (왼쪽) 눌림 상태
    private boolean wPressed = false;           /// W키 (위) 눌림 상태

    /// 현재 활성화된 방향을 추적 (우선순위 시스템용)
    private String currentDirection = "";

    /// 마지막 방향을 기억 (키를 뗄 때 적절한 정지 모션을 위해)
    private String lastDirection = "s";

    /// 생성자: 스프라이트 시트 로드 및 초기화
    public SpriteRenderer() {
        loadSpriteSheet();
        setupAnimation();
        loadFrames(0, 6, 0, 0, 65); // 기본값: 몸(0), 팔(6), 셔츠(0), 바지(0), 머리카락(65)
        setOffsets(0,0,0,0,12, 42, 0, 0, 0, 0); // 초기 오프셋 설정
    }

    /// 스프라이트 시트 이미지 파일 로드
    private void loadSpriteSheet() {
        try {
            spriteSheet = ImageIO.read(new File("resource/Characters/Farmer/farmer_base.png"));
            spriteSheetShirt = ImageIO.read(new File("resource/Characters/Farmer/shirts.png"));
            spriteSheetPants = ImageIO.read(new File("resource/Characters/Farmer/pants.png"));
            spriteSheetHair = ImageIO.read(new File("resource/Characters/Farmer/hairstyles.png"));
        } catch (IOException e) {
            System.err.println("스프라이트 시트를 로드할 수 없습니다: " + e.getMessage());
        }
    }

    /// 애니메이션 타이머 설정 (130ms 간격으로 프레임 전환)
    private void setupAnimation() {
        animationTimer = new Timer(130, e -> { //130
            if (isAnimating) {
                currentAnimFrame = (currentAnimFrame + 1) % maxAnimFrames;
                updateCurrentFrame();
            }
        });
    }

    // mark

    /// 현재 애니메이션 프레임에 따라 실제 스프라이트 프레임 업데이트
    private void updateCurrentFrame() {
        // 각 배열의 길이를 체크하여 안전하게 인덱스 접근
        int baseFrame = currentAnimFrame < currentBaseFrames.length ?
                currentBaseFrames[currentAnimFrame] :
                currentBaseFrames[currentBaseFrames.length - 1];

        int armFrame = currentAnimFrame < currentArmFrames.length ?
                currentArmFrames[currentAnimFrame] :
                currentArmFrames[currentArmFrames.length - 1];

        int shirtFrame = currentAnimFrame < currentShirtFrames.length ?
                currentShirtFrames[currentAnimFrame] :
                currentShirtFrames[currentShirtFrames.length - 1];

        int pantsFrame = currentAnimFrame < currentPantsFrames.length ?
                currentPantsFrames[currentAnimFrame] :
                currentPantsFrames[currentPantsFrames.length - 1];

        int hairFrame = currentAnimFrame < currentHairFrames.length ?
                currentHairFrames[currentAnimFrame] :
                currentHairFrames[currentHairFrames.length - 1];

        loadFrames(baseFrame, armFrame, shirtFrame, pantsFrame, hairFrame, currentFlipped);
    }

    /// 기본 프레임 로드 메서드
    private void loadFrames(int baseFrameNum, int armFrameNum, int shirtFrameNum, int pantsFrameNum, int hairFrameNum) {
        loadFrames(baseFrameNum, armFrameNum, shirtFrameNum, pantsFrameNum, hairFrameNum, false);
    }

    /// 지정된 프레임 번호로 모든 스프라이트 이미지 로드
    private void loadFrames(int baseFrameNum, int armFrameNum, int shirtFrameNum, int pantsFrameNum, int hairFrameNum, boolean flipHorizontal) {
        // 몸체와 팔 로드
        if (spriteSheet != null) {
            int cols = spriteSheet.getWidth() / SPRITE_WIDTH;

            // 기본 프레임 추출 (몸체)
            int baseX = (baseFrameNum % cols) * SPRITE_WIDTH;
            int baseY = (baseFrameNum / cols) * SPRITE_HEIGHT;
            BufferedImage originalBase = spriteSheet.getSubimage(baseX, baseY, SPRITE_WIDTH, SPRITE_HEIGHT);

            // 팔 프레임 추출
            int armX = (armFrameNum % cols) * SPRITE_WIDTH;
            int armY = (armFrameNum / cols) * SPRITE_HEIGHT;
            BufferedImage originalArm = spriteSheet.getSubimage(armX, armY, SPRITE_WIDTH, SPRITE_HEIGHT);

            // 좌우 반전 처리
            if (flipHorizontal) {
                baseFrame = flipImageHorizontally(originalBase);
                armFrame = flipImageHorizontally(originalArm);
            } else {
                baseFrame = originalBase;
                armFrame = originalArm;
            }
        }

        // 셔츠 로드 (원본 색상 그대로 사용)
        loadClothingFrame(spriteSheetShirt, shirtFrameNum, SPRITE_WIDTH_Shirt, SPRITE_HEIGHT_Shirt,
                flipHorizontal, "셔츠", (frame) -> shirtFrame = frame);

        // 바지 로드
        loadClothingFrame(spriteSheetPants, pantsFrameNum, SPRITE_WIDTH_Pants, SPRITE_HEIGHT_Pants,
                flipHorizontal, "바지", (frame) -> pantsFrame = applyHSBColor(frame, PANTS_HUE, PANTS_SATURATION, PANTS_BRIGHTNESS));

        // 머리카락 로드
        loadClothingFrame(spriteSheetHair, hairFrameNum, SPRITE_WIDTH_Hair, SPRITE_HEIGHT_Hair,
                flipHorizontal, "머리카락", (frame) -> hairFrame = applyHSBColor(frame, HAIR_HUE, HAIR_SATURATION, HAIR_BRIGHTNESS));
    }

    /// 의류(셔츠, 바지, 머리카락) 프레임 로드를 위한 공통 메서드
    private void loadClothingFrame(BufferedImage spriteSheet, int frameNum, int spriteWidth, int spriteHeight,
                                   boolean flipHorizontal, String itemName, FrameSetter frameSetter) {
        if (spriteSheet != null) {
            try {
                int cols = spriteSheet.getWidth() / spriteWidth;
                int x = (frameNum % cols) * spriteWidth;
                int y = (frameNum / cols) * spriteHeight;
                BufferedImage originalFrame = spriteSheet.getSubimage(x, y, spriteWidth, spriteHeight);

                BufferedImage processedFrame = flipHorizontal ?
                        flipImageHorizontally(originalFrame) : originalFrame;

                frameSetter.setFrame(processedFrame);
            } catch (Exception e) {
                System.err.println(itemName + " 프레임 로드 실패: " + e.getMessage());
            }
        }
    }

    /// 프레임 설정을 위한 함수형 인터페이스
    @FunctionalInterface
    private interface FrameSetter {
        void setFrame(BufferedImage frame);
    }

    /// 새로운 애니메이션 시작
    private void startAnimation(int[] baseFrames, int[] armFrames, int[] shirtFrames, int[] pantsFrames, int[] hairFrames, boolean flipped) {
        currentBaseFrames = baseFrames.clone();
        currentArmFrames = armFrames.clone();
        currentShirtFrames = shirtFrames.clone();
        currentPantsFrames = pantsFrames.clone();
        currentHairFrames = hairFrames.clone();
        currentFlipped = flipped;

        maxAnimFrames = Math.max(Math.max(Math.max(Math.max(baseFrames.length, armFrames.length),
                shirtFrames.length), pantsFrames.length), hairFrames.length);

        currentAnimFrame = 0;
        isAnimating = true;

        updateCurrentFrame();
        animationTimer.start();
    }

    private void startAnimationWithOffsets(int[] baseFrames, int[] armFrames, int[] shirtFrames,
                                           int[] pantsFrames, int[] hairFrames, boolean flipped,
                                           int[] baseX, int[] baseY, int[] armX, int[] armY,
                                           int[] shirtX, int[] shirtY, int[] pantsX, int[] pantsY,
                                           int[] hairX, int[] hairY) {
        // 기존 애니메이션 설정
        currentBaseFrames = baseFrames.clone();
        currentArmFrames = armFrames.clone();
        currentShirtFrames = shirtFrames.clone();
        currentPantsFrames = pantsFrames.clone();
        currentHairFrames = hairFrames.clone();
        currentFlipped = flipped;

        // 프레임별 오프셋 설정
        setFrameOffsets(baseX, baseY, armX, armY, shirtX, shirtY, pantsX, pantsY, hairX, hairY);

        maxAnimFrames = Math.max(Math.max(Math.max(Math.max(baseFrames.length, armFrames.length),
                shirtFrames.length), pantsFrames.length), hairFrames.length);

        currentAnimFrame = 0;
        isAnimating = true;

        updateCurrentFrame();
        animationTimer.start();
    }

    /// 애니메이션 중지
    private void stopAnimation() {
        isAnimating = false;
        animationTimer.stop();
    }

    /// 이미지를 좌우 반전시키는 메서드
    private BufferedImage flipImageHorizontally(BufferedImage image) {
        BufferedImage flipped = new BufferedImage(image.getWidth(), image.getHeight(), image.getType());
        Graphics2D g2d = flipped.createGraphics();
        g2d.drawImage(image, image.getWidth(), 0, -image.getWidth(), image.getHeight(), null);
        g2d.dispose();
        return flipped;
    }

    /// HSB 색상을 적용하여 이미지를 색칠하는 메서드
    private BufferedImage applyHSBColor(BufferedImage originalImage, float hue, float saturation, float brightness) {
        if (originalImage == null) return null;

        BufferedImage coloredImage = new BufferedImage(originalImage.getWidth(), originalImage.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Color targetColor = Color.getHSBColor(hue, saturation, brightness);

        for (int x = 0; x < originalImage.getWidth(); x++) {
            for (int y = 0; y < originalImage.getHeight(); y++) {
                int pixel = originalImage.getRGB(x, y);
                int alpha = (pixel >> 24) & 0xff;

                if (alpha > 0) { // 투명하지 않은 픽셀만 처리
                    // 원본 픽셀의 밝기를 가져옴
                    int red = (pixel >> 16) & 0xff;
                    int green = (pixel >> 8) & 0xff;
                    int blue = pixel & 0xff;
                    float[] hsb = Color.RGBtoHSB(red, green, blue, null);

                    // 원본의 밝기와 타겟 밝기를 조합
                    float finalBrightness = brightness * hsb[2];
                    Color finalColor = Color.getHSBColor(hue, saturation, finalBrightness);

                    // 알파 채널을 유지하면서 새로운 색상 적용
                    int newRGB = (alpha << 24) | (finalColor.getRGB() & 0xffffff);
                    coloredImage.setRGB(x, y, newRGB);
                } else {
                    // 투명한 픽셀은 그대로 유지
                    coloredImage.setRGB(x, y, pixel);
                }
            }
        }

        return coloredImage;
    }

    /// 현재 눌린 키들을 확인하고 우선순위에 따라 애니메이션 결정
    private void updateAnimationState() {
        String newDirection = "";

        // 우선순위: w > s > a > d
        if (wPressed) {
            newDirection = "w";
        } else if (sPressed) {
            newDirection = "s";
        } else if (aPressed) {
            newDirection = "a";
        } else if (dPressed) {
            newDirection = "d";
        }

        // 방향이 바뀌었을 때만 애니메이션 업데이트
        if (!newDirection.equals(currentDirection)) {
            if (!newDirection.isEmpty()) {
                lastDirection = newDirection; // 마지막 방향 저장
            }
            currentDirection = newDirection;

            if (newDirection.isEmpty()) {
                // 모든 키가 해제된 경우 - 마지막 방향에 따른 정지 모션
                stopAnimation();
                setIdleState(lastDirection);
            } else {
                // 새로운 방향에 따라 애니메이션 시작
                setMovementState(newDirection);
            }
        }
    }

    /// 정지 상태 설정
    private void setIdleState(String direction) {
        frameOffsets_BaseX = new int[]{0};
        frameOffsets_BaseY = new int[]{0};
        frameOffsets_ArmX = new int[]{0};
        frameOffsets_ArmY = new int[]{0};
        frameOffsets_ShirtX = new int[]{12};   // 셔츠의 기본 X 오프셋
        frameOffsets_ShirtY = new int[]{42};   // 셔츠의 기본 Y 오프셋
        frameOffsets_PantsX = new int[]{0};
        frameOffsets_PantsY = new int[]{0};
        frameOffsets_HairX = new int[]{0};
        frameOffsets_HairY = new int[]{0};

        switch (direction) {
            case "s":   // 아래 방향 정지
                changeSprite(0, 6, 0, 0, 65);
                setOffsets(0,0,0,0,12, 42, 0, 0, 0, 0);
                break;
            case "d":   // 오른쪽 방향 정지
                changeSprite(18, 24, 32, 120, 73);
                setOffsets(0,0,0,0,12, 42, 0, 0, 0, 0);
                break;
            case "a":   // 왼쪽 방향 정지
                changeSpriteFlipped(18, 24, 32, 120, 73);
                setOffsets(0,0,0,0,12, 42, 0, 0, 0, 0);
                break;
            case "w":   // 위 방향 정지
                changeSprite(36, 42, 96, 240, 81);
                setOffsets(0,0,0,0,12, 42, 0, 0, 0, 0);
                break;
        }
    }

    /// 이동 상태 설정
    private void setMovementState(String direction) {
        switch (direction) {
            case "s":   // 아래 방향 - 완벽
                startAnimation(new int[]{0, 1, 54, 1, 0, 2, 55, 2},
                        new int[]{6, 7, 60, 7, 6, 8, 61, 8},
                        new int[]{0, 0},
                        new int[]{0 , 1 , 540 , 1 , 0 , 2  , 541 , 2},
                        new int[]{65, 65}, false);
                setOffsets(0,0,0,0,12, 42, 0, 0, 0, 0);
                break;
            case "d":   // 오른쪽 방향
                startAnimationWithOffsets(
                        // 프레임 번호들
                        new int[]{18, 56, 41, 18, 57, 23},
                        new int[]{24, 62, 29, 24, 63, 47},
                        new int[]{32, 32},
                        new int[]{120, 363, 245, 120, 363, 125},
                        new int[]{73, 73}, false,
                        // 프레임별 오프셋
                        new int[]{0, 0, 0, 0, 0, 0},            // 몸통 X 오프셋 (6프레임)
                        new int[]{0, -6, 0, 0, -6, 0},          /// 몸통 Y 오프셋
                        new int[]{0, 0, 0, 0, 0, 0},            // 팔 X 오프셋
                        new int[]{0, -6, 0, 0, -6, 0},          /// 팔 Y 오프셋
                        new int[]{12, 12, 12, 12, 12, 12},      // 셔츠 X 오프셋
                        new int[]{42, 39, 45, 42, 39, 45},      /// 셔츠 Y 오프셋
                        new int[]{0, 0, 0, 0, 0, 0},            // 바지 X 오프셋
                        new int[]{0, -6, 0, 0, -6, 0},          /// 바지 Y 오프셋
                        new int[]{0, 0, 0, 0, 0, 0},            // 머리카락 X 오프셋
                        new int[]{0, -3, 3, 0, -3, 3}           /// 머리카락 Y 오프셋
                );
                break;
            case "a":   // 왼쪽 방향: 오른쪽과 같지만 좌우 반전
                startAnimationWithOffsets(
                        // 프레임 번호들
                        new int[]{18, 56, 41, 18, 57, 23},
                        new int[]{24, 62, 29, 24, 63, 47},
                        new int[]{32, 32},
                        new int[]{120, 363, 245, 120, 363, 125},
                        new int[]{73, 73}, false,
                        // 프레임별 오프셋
                        new int[]{0, 0, 0, 0, 0, 0},            // 몸통 X 오프셋 (6프레임)
                        new int[]{0, -6, 0, 0, -6, 0},          /// 몸통 Y 오프셋
                        new int[]{0, 0, 0, 0, 0, 0},            // 팔 X 오프셋
                        new int[]{0, -6, 0, 0, -6, 0},          /// 팔 Y 오프셋
                        new int[]{12, 12, 12, 12, 12, 12},      // 셔츠 X 오프셋
                        new int[]{42, 39, 45, 42, 39, 45},      /// 셔츠 Y 오프셋
                        new int[]{0, 0, 0, 0, 0, 0},            // 바지 X 오프셋
                        new int[]{0, -6, 0, 0, -6, 0},          /// 바지 Y 오프셋
                        new int[]{0, 0, 0, 0, 0, 0},            // 머리카락 X 오프셋
                        new int[]{0, -3, 3, 0, -3, 3}           /// 머리카락 Y 오프셋
                );
                break;
            case "w":   // 위 방향 - 8프레임 애니메이션
                startAnimation(new int[]{36, 38, 59, 38, 36, 37, 58, 37},
                        new int[]{42, 65, 65, 42, 64, 64},
                        new int[]{96, 96},
                        new int[]{240, 241, 364, 241, 240, 242, 365, 342},
                        new int[]{81, 81}, false);
                setOffsets(0,0,0,0,12, 42, 0, 0, 0, 0);
                break;
        }
    }

    /// 모든 오프셋을 한 번에 설정하는 편의 메서드
    private void setOffsets(int baseX, int baseY, int armX, int armY, int shirtX, int shirtY, int pantsX, int pantsY, int hairX, int hairY) {
        this.x_Base = baseX;        // 몸통 오프셋 설정
        this.y_Base = baseY;

        this.x_Arm = armX;          // 팔 오프셋 설정
        this.y_Arm = armY;

        this.x_Shirt = shirtX;      // 셔츠 오프셋 설정
        this.y_Shirt = shirtY;

        this.x_Pants = pantsX;      // 바지 오프셋 설정
        this.y_Pants = pantsY;

        this.x_Hair = hairX;        // 머리카락 오프셋 설정
        this.y_Hair = hairY;
    }

    /// 프레임별 오프셋을 설정하는 메서드
    private void setFrameOffsets(int[] baseX, int[] baseY, int[] armX, int[] armY,
                                 int[] shirtX, int[] shirtY, int[] pantsX, int[] pantsY,
                                 int[] hairX, int[] hairY) {
        frameOffsets_BaseX = baseX.clone();
        frameOffsets_BaseY = baseY.clone();
        frameOffsets_ArmX = armX.clone();
        frameOffsets_ArmY = armY.clone();
        frameOffsets_ShirtX = shirtX.clone();
        frameOffsets_ShirtY = shirtY.clone();
        frameOffsets_PantsX = pantsX.clone();
        frameOffsets_PantsY = pantsY.clone();
        frameOffsets_HairX = hairX.clone();
        frameOffsets_HairY = hairY.clone();
    }

    /// 현재 프레임에 맞는 오프셋을 안전하게 가져오는 메서드
    private int getFrameOffset(int[] offsetArray, int frameIndex) {
        if (offsetArray.length == 0) return 0;
        return frameIndex < offsetArray.length ? offsetArray[frameIndex] : offsetArray[offsetArray.length - 1];
    }

    /// 화면에 스프라이트 렌더링 (몸체 + 바지 + 셔츠 + 팔 + 머리카락을 레이어로 그리기)
    public void render(Graphics2D g2d) {
        // 현재 애니메이션 프레임에 해당하는 오프셋 계산
        int baseOffsetX = getFrameOffset(frameOffsets_BaseX, currentAnimFrame);
        int baseOffsetY = getFrameOffset(frameOffsets_BaseY, currentAnimFrame);
        int armOffsetX = getFrameOffset(frameOffsets_ArmX, currentAnimFrame);
        int armOffsetY = getFrameOffset(frameOffsets_ArmY, currentAnimFrame);
        int shirtOffsetX = getFrameOffset(frameOffsets_ShirtX, currentAnimFrame);
        int shirtOffsetY = getFrameOffset(frameOffsets_ShirtY, currentAnimFrame);
        int pantsOffsetX = getFrameOffset(frameOffsets_PantsX, currentAnimFrame);
        int pantsOffsetY = getFrameOffset(frameOffsets_PantsY, currentAnimFrame);
        int hairOffsetX = getFrameOffset(frameOffsets_HairX, currentAnimFrame);
        int hairOffsetY = getFrameOffset(frameOffsets_HairY, currentAnimFrame);

        // 1. 몸체 렌더링 (가장 아래) - 프레임별 오프셋 적용
        if (baseFrame != null) {
            g2d.drawImage(baseFrame, x + baseOffsetX, y + baseOffsetY,
                    SPRITE_WIDTH * SCALE, SPRITE_HEIGHT * SCALE, null);
        }

        // 2. 바지 렌더링 (몸체 위에) - 프레임별 오프셋 적용
        if (pantsFrame != null) {
            g2d.drawImage(pantsFrame, x + pantsOffsetX, y + pantsOffsetY,
                    SPRITE_WIDTH_Pants * SCALE, SPRITE_HEIGHT_Pants * SCALE, null);
        }

        // 3. 셔츠 렌더링 (바지 위에) - 프레임별 오프셋 적용
        if (shirtFrame != null) {
            g2d.drawImage(shirtFrame, x + shirtOffsetX, y + shirtOffsetY,
                    SPRITE_WIDTH_Shirt * SCALE, SPRITE_HEIGHT_Shirt * SCALE, null);
        }

        // 4. 팔 렌더링 (셔츠 위에) - 프레임별 오프셋 적용
        if (armFrame != null) {
            g2d.drawImage(armFrame, x + armOffsetX, y + armOffsetY,
                    SPRITE_WIDTH * SCALE, SPRITE_HEIGHT * SCALE, null);
        }

        // 5. 머리카락 (가장 앞) - 프레임별 오프셋 적용
        if (hairFrame != null) {
            g2d.drawImage(hairFrame, x + hairOffsetX, y + hairOffsetY,
                    SPRITE_WIDTH_Hair * SCALE, SPRITE_HEIGHT_Hair * SCALE, null);
        }
    }

    /// 스프라이트 위치 설정
    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    /// 정적 스프라이트 변경 (애니메이션 없음)
    public void changeSprite(int baseFrameNum, int armFrameNum, int shirtFrameNum, int pantsFrameNum, int hairFrameNum) {
        loadFrames(baseFrameNum, armFrameNum, shirtFrameNum, pantsFrameNum, hairFrameNum, false);
    }

    /// 정적 스프라이트 변경 (좌우 반전)
    public void changeSpriteFlipped(int baseFrameNum, int armFrameNum, int shirtFrameNum, int pantsFrameNum, int hairFrameNum) {
        loadFrames(baseFrameNum, armFrameNum, shirtFrameNum, pantsFrameNum, hairFrameNum, true);
    }

    /// 키 눌림 처리
    public void handleKeyPressed(String keyInput) {
        boolean wasPressed = isKeyPressed(keyInput);

        switch (keyInput.toLowerCase()) {
            case "s": sPressed = true; break;
            case "d": dPressed = true; break;
            case "a": aPressed = true; break;
            case "w": wPressed = true; break;
        }

        if (!wasPressed) {
            updateAnimationState();
        }
    }

    /// 키 해제 처리
    public void handleKeyReleased(String keyInput) {
        switch (keyInput.toLowerCase()) {
            case "s": sPressed = false; break;
            case "d": dPressed = false; break;
            case "a": aPressed = false; break;
            case "w": wPressed = false; break;
        }

        updateAnimationState();
    }

    /// 키가 눌린 상태인지 확인
    private boolean isKeyPressed(String keyInput) {
        switch (keyInput.toLowerCase()) {
            case "s": return sPressed;
            case "d": return dPressed;
            case "a": return aPressed;
            case "w": return wPressed;
            default: return false;
        }
    }

    /// 리소스 정리
    public void dispose() {
        if (animationTimer != null && animationTimer.isRunning()) {
            animationTimer.stop();
        }
    }

    /** ========== Getter 메서드들 ========== **/
    public int getX() { return x; }
    public int getY() { return y; }
    public int getWidth() { return SPRITE_WIDTH * SCALE; }
    public int getHeight() { return SPRITE_HEIGHT * SCALE; }
    public String getCurrentDirection() { return currentDirection; }
    public String getLastDirection() { return lastDirection; }
    public boolean isAnimating() { return isAnimating; }
}