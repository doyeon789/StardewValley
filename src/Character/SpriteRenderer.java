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
    private BufferedImage SpriteSheetPants;     /// 전체 스프라이트 시트 이미지 -> Pants
    private BufferedImage baseFrame;            /// 현재 기본 프레임 (몸체)
    private BufferedImage armFrame;             /// 현재 팔 프레임
    private BufferedImage shirtFrame;           /// 현재 셔츠 프레임
    private BufferedImage pantsFrame;           /// 현재 바지 프레임

    /** ========== 위치 및 크기 정보 ========== **/
    private int x = 0;                          // 스프라이트의 X 좌표
    private int y = 0;                          // 스프라이트의 Y 좌표
    private int x_Shirt = 0;                    // 셔츠 스프라이트의 X 좌표
    private int y_Shirt = 0;                    // 셔츠 스프라이트의 Y 좌표
    private int x_Pants = 0;                    // 바지 스프라이트의 X 좌표
    private int y_Pants = 0;                    // 바지 스프라이트의 Y 좌표

    private static final int SPRITE_WIDTH = 16;         // 개별 스프라이트의 가로 크기 (픽셀)
    private static final int SPRITE_HEIGHT = 32;        // 개별 스프라이트의 세로 크기 (픽셀)
    private static final int SPRITE_WIDTH_Shirt = 8;    // 개별 셔츠 스프라이트의 가로 크기 (픽셀)
    private static final int SPRITE_HEIGHT_Shirt = 8;   // 개별 셔츠 스프라이트의 세로 크기 (픽셀)
    private static final int SPRITE_WIDTH_Pants = 16;   // 개별 바지 스프라이트의 가로 크기 (픽셀)
    private static final int SPRITE_HEIGHT_Pants = 32;  // 개별 바지 스프라이트의 세로 크기 (픽셀)

    private static final int SCALE = 3;          // 스프라이트 확대 비율

    /** ========== 애니메이션 시스템 ========== **/
    private Timer animationTimer;               /// 애니메이션 타이머 (190ms 간격)
    private boolean isAnimating = false;        /// 현재 애니메이션 중인지 여부
    private int currentAnimFrame = 0;           /// 현재 애니메이션 프레임 (0 또는 1)

    private int[] currentBaseFrames = {0, 0};   /// 현재 기본 애니메이션 프레임 배열 (몸체)
    private int[] currentArmFrames = {6, 6};    /// 현재 팔 애니메이션 프레임 배열
    private int[] currentShirtFrames = {0, 0};  /// 현재 셔츠 애니메이션 프레임 배열
    private int[] currentPantsFrames = {0, 0};  /// 현재 바지 애니메이션 프레임 배열
    private boolean currentFlipped = false;     /// 현재 좌우 반전 상태

    /** ========== 키 입력 상태 관리 ========== **/
    private boolean sPressed = false;           /// S키 (아래) 눌림 상태
    private boolean dPressed = false;           /// D키 (오른쪽) 눌림 상태
    private boolean aPressed = false;           /// A키 (왼쪽) 눌림 상태
    private boolean wPressed = false;           /// W키 (위) 눌림 상태

    /// 현재 활성화된 방향을 추적 (우선순위 시스템용)
    private String currentDirection = "";

    /// 생성자: 스프라이트 시트 로드 및 초기화
    public SpriteRenderer() {
        loadSpriteSheet("resource/Characters/Farmer/farmer_base.png");
        loadShirtSpriteSheet("resource/Characters/Farmer/shirts.png");
        loadPantsSpriteSheet("resource/Characters/Farmer/pants.png");
        setupAnimation();
        loadFrames(0, 6, 0, 0); // 기본값: 몸(0), 팔(6), 셔츠(0), 바지(0)
    }

    /// 애니메이션 타이머 설정 (190ms 간격으로 프레임 전환)
    private void setupAnimation() {
        animationTimer = new Timer(190, e -> {
            if (isAnimating) {
                currentAnimFrame = (currentAnimFrame + 1) % 2; // 0과 1 사이를 반복
                updateCurrentFrame();
            }
        });
    }

    /// 현재 애니메이션 프레임에 따라 실제 스프라이트 프레임 업데이트
    private void updateCurrentFrame() {
        int baseFrame = currentBaseFrames[currentAnimFrame];
        int armFrame = currentArmFrames[currentAnimFrame];
        int shirtFrame = currentShirtFrames[currentAnimFrame];
        int pantsFrame = currentPantsFrames[currentAnimFrame];
        loadFrames(baseFrame, armFrame, shirtFrame, pantsFrame, currentFlipped);
    }

    /// 새로운 애니메이션 시작
    private void startAnimation(int[] baseFrames, int[] armFrames, int[] shirtFrames, int[] pantsFrames, boolean flipped) {
        currentBaseFrames = baseFrames.clone();
        currentArmFrames = armFrames.clone();
        currentShirtFrames = shirtFrames.clone();
        currentPantsFrames = pantsFrames.clone();
        currentFlipped = flipped;
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

    /// 스프라이트 시트 이미지 파일 로드
    private void loadSpriteSheet(String imagePath) {
        try {
            spriteSheet = ImageIO.read(new File(imagePath));
        } catch (IOException e) {
            System.err.println("스프라이트 시트를 로드할 수 없습니다: " + e.getMessage());
        }
    }

    /// 셔츠 스프라이트 시트 이미지 파일 로드
    private void loadShirtSpriteSheet(String imagePath) {
        try {
            spriteSheetShirt = ImageIO.read(new File(imagePath));
        } catch (IOException e) {
            System.err.println("셔츠 스프라이트 시트를 로드할 수 없습니다: " + e.getMessage());
        }
    }

    /// 바지 스프라이트 시트 이미지 파일 로드
    private void loadPantsSpriteSheet(String imagePath) {
        try {
            SpriteSheetPants = ImageIO.read(new File(imagePath));
        } catch (IOException e) {
            System.err.println("바지 스프라이트 시트를 로드할 수 없습니다: " + e.getMessage());
        }
    }

    /// 기본 프레임 로드 메서드
    private void loadFrames(int baseFrameNum, int armFrameNum, int shirtFrameNum, int pantsFrameNum) {
        loadFrames(baseFrameNum, armFrameNum, shirtFrameNum, pantsFrameNum, false);
    }

    /// 지정된 프레임 번호로 모든 스프라이트 이미지 로드
    private void loadFrames(int baseFrameNum, int armFrameNum, int shirtFrameNum, int pantsFrameNum, boolean flipHorizontal) {
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

        // 셔츠 로드
        if (spriteSheetShirt != null) {
            try {
                int shirtCols = spriteSheetShirt.getWidth() / SPRITE_WIDTH_Shirt;
                int shirtX = (shirtFrameNum % shirtCols) * SPRITE_WIDTH_Shirt;
                int shirtY = (shirtFrameNum / shirtCols) * SPRITE_HEIGHT_Shirt;
                BufferedImage originalShirt = spriteSheetShirt.getSubimage(shirtX, shirtY, SPRITE_WIDTH_Shirt, SPRITE_HEIGHT_Shirt);

                if (flipHorizontal) {
                    shirtFrame = flipImageHorizontally(originalShirt);
                } else {
                    shirtFrame = originalShirt;
                }
            } catch (Exception e) {
                System.err.println("셔츠 프레임 로드 실패: " + e.getMessage());
            }
        }

        // 바지 로드
        if (SpriteSheetPants != null) {
            try {
                int pantsCols = SpriteSheetPants.getWidth() / SPRITE_WIDTH_Pants;
                int pantsX = (pantsFrameNum % pantsCols) * SPRITE_WIDTH_Pants;
                int pantsY = (pantsFrameNum / pantsCols) * SPRITE_HEIGHT_Pants;
                BufferedImage originalPants = SpriteSheetPants.getSubimage(pantsX, pantsY, SPRITE_WIDTH_Pants, SPRITE_HEIGHT_Pants);

                if (flipHorizontal) {
                    pantsFrame = flipImageHorizontally(originalPants);
                } else {
                    pantsFrame = originalPants;
                }
            } catch (Exception e) {
                System.err.println("바지 프레임 로드 실패: " + e.getMessage());
            }
        }
    }

    /// 이미지를 좌우 반전시키는 메서드
    private BufferedImage flipImageHorizontally(BufferedImage image) {
        BufferedImage flipped = new BufferedImage(image.getWidth(), image.getHeight(), image.getType());
        Graphics2D g2d = flipped.createGraphics();
        g2d.drawImage(image, image.getWidth(), 0, -image.getWidth(), image.getHeight(), null);
        g2d.dispose();
        return flipped;
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
            currentDirection = newDirection;

            if (newDirection.isEmpty()) {
                // 모든 키가 해제된 경우 - 기본 상태로
                stopAnimation();
                changeSprite(0, 6, 0, 0);
            } else {
                // 새로운 방향에 따라 애니메이션 시작
                switch (newDirection) {
                    case "s":   // 아래 방향
                        startAnimation(new int[]{1, 2}, new int[]{7, 8}, new int[]{0, 0}, new int[]{1, 2}, false);
                        break;
                    case "d":   // 오른쪽 방향
                        startAnimation(new int[]{19, 20}, new int[]{23, 24}, new int[]{1, 1}, new int[]{19, 20}, false);
                        break;
                    case "a":   // 왼쪽 방향: 오른쪽과 같지만 좌우 반전
                        startAnimation(new int[]{19, 20}, new int[]{23, 24}, new int[]{1, 1}, new int[]{19, 20}, true);
                        break;
                    case "w":   // 위 방향
                        startAnimation(new int[]{37, 38}, new int[]{44, 46}, new int[]{3, 3}, new int[]{37, 38}, false);
                        break;
                }
            }
        }
    }

    /// 화면에 스프라이트 렌더링 (몸체 + 바지 + 셔츠 + 팔을 레이어로 그리기)
    public void render(Graphics2D g2d) {
        // 1. 몸체 렌더링 (가장 아래)
        if (baseFrame != null) {
            g2d.drawImage(baseFrame, x, y,
                    SPRITE_WIDTH * SCALE, SPRITE_HEIGHT * SCALE, null);
        }

        // 2. 바지 렌더링 (몸체 위에)
        if (pantsFrame != null) {
            g2d.drawImage(pantsFrame, x + x_Pants, y + y_Pants,
                    SPRITE_WIDTH_Pants * SCALE, SPRITE_HEIGHT_Pants * SCALE, null);
        }

        // 3. 셔츠 렌더링 (바지 위에)
        if (shirtFrame != null) {
            g2d.drawImage(shirtFrame, x + x_Shirt, y + y_Shirt,
                    SPRITE_WIDTH_Shirt * SCALE, SPRITE_HEIGHT_Shirt * SCALE, null);
        }

        // 4. 팔 렌더링 (가장 앞)
        if (armFrame != null) {
            g2d.drawImage(armFrame, x, y,
                    SPRITE_WIDTH * SCALE, SPRITE_HEIGHT * SCALE, null);
        }
    }

    /// 스프라이트 위치 설정
    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    /// 셔츠 위치 오프셋 설정
    public void setShirtOffset(int offsetX, int offsetY) {
        this.x_Shirt = offsetX;
        this.y_Shirt = offsetY;
    }

    /// 바지 위치 오프셋 설정
    public void setPantsOffset(int offsetX, int offsetY) {
        this.x_Pants = offsetX;
        this.y_Pants = offsetY;
    }

    /// 정적 스프라이트 변경 (애니메이션 없음)
    public void changeSprite(int baseFrameNum, int armFrameNum, int shirtFrameNum, int pantsFrameNum) {
        loadFrames(baseFrameNum, armFrameNum, shirtFrameNum, pantsFrameNum, false);
    }

    /// 정적 스프라이트 변경 (좌우 반전)
    public void changeSpriteFlipped(int baseFrameNum, int armFrameNum, int shirtFrameNum, int pantsFrameNum) {
        loadFrames(baseFrameNum, armFrameNum, shirtFrameNum, pantsFrameNum, true);
    }

    /// 키 눌림 처리
    public void handleKeyPressed(String keyInput) {
        boolean wasPressed = false;

        switch (keyInput.toLowerCase()) {
            case "s":
                wasPressed = sPressed;
                sPressed = true;
                break;
            case "d":
                wasPressed = dPressed;
                dPressed = true;
                break;
            case "a":
                wasPressed = aPressed;
                aPressed = true;
                break;
            case "w":
                wasPressed = wPressed;
                wPressed = true;
                break;
        }

        if (!wasPressed) {
            updateAnimationState();
        }
    }

    /// 키 해제 처리
    public void handleKeyReleased(String keyInput) {
        switch (keyInput.toLowerCase()) {
            case "s":
                sPressed = false;
                break;
            case "d":
                dPressed = false;
                break;
            case "a":
                aPressed = false;
                break;
            case "w":
                wPressed = false;
                break;
        }

        updateAnimationState();

        // 모든 키가 해제된 경우 적절한 정지 모션 설정
        if (!sPressed && !dPressed && !aPressed && !wPressed) {
            switch (keyInput.toLowerCase()) {
                case "s":   // 아래 방향 정지
                    changeSprite(0, 6, 0, 0);
                    break;
                case "d":   // 오른쪽 방향 정지
                    changeSprite(18, 24, 1, 18);
                    break;
                case "a":   // 왼쪽 방향 정지
                    changeSpriteFlipped(18, 24, 1, 18);
                    break;
                case "w":   // 위 방향 정지
                    changeSprite(36, 42, 3, 36);
                    break;
            }
        }
    }

    /** ========== Getter 메서드들 ========== **/
    public int getX() { return x; }
    public int getY() { return y; }
    public int getWidth() { return SPRITE_WIDTH * SCALE; }
    public int getHeight() { return SPRITE_HEIGHT * SCALE; }
    public String getCurrentDirection() { return currentDirection; }
}