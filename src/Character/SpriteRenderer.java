package Character;

import java.awt.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import javax.swing.Timer;
import java.io.File;
import java.io.IOException;

public class SpriteRenderer {
    // 스프라이트 관련 변수
    private BufferedImage spriteSheet;
    private BufferedImage baseFrame;  // 현재 기본 프레임
    private BufferedImage armFrame;   // 현재 팔 프레임

    // 위치 정보
    private int x = 100;
    private int y = 100;

    // 스프라이트 설정
    private static final int SPRITE_WIDTH = 16;
    private static final int SPRITE_HEIGHT = 32;
    private static final int SCALE = 4;

    // 애니메이션 관련 변수
    private Timer animationTimer;
    private boolean isAnimating = false;
    private int currentAnimFrame = 0; // 0 또는 1 (첫 번째 또는 두 번째 프레임)

    // 현재 애니메이션 프레임 번호들
    private int[] currentBaseFrames = {0, 0}; // 기본 상태
    private int[] currentArmFrames = {6, 6};  // 기본 상태
    private boolean currentFlipped = false;

    // 현재 상태 추적
    private boolean sPressed = false;
    private boolean dPressed = false;
    private boolean aPressed = false;
    private boolean wPressed = false;

    public SpriteRenderer() {
        loadSpriteSheet("resource/Characters/Farmer/farmer_base.png");
        setupAnimation();
        loadFrames(0, 6); // 기본값: 0번과 6번 프레임
    }

    private void setupAnimation() {
        animationTimer = new Timer(190, e -> {
            if (isAnimating) {
                currentAnimFrame = (currentAnimFrame + 1) % 2; // 0과 1 사이를 반복
                updateCurrentFrame();
            }
        });
    }

    private void updateCurrentFrame() {
        int baseFrame = currentBaseFrames[currentAnimFrame];
        int armFrame = currentArmFrames[currentAnimFrame];
        loadFrames(baseFrame, armFrame, currentFlipped);
    }

    private void startAnimation(int[] baseFrames, int[] armFrames, boolean flipped) {
        currentBaseFrames = baseFrames.clone();
        currentArmFrames = armFrames.clone();
        currentFlipped = flipped;
        currentAnimFrame = 0;
        isAnimating = true;

        updateCurrentFrame();
        animationTimer.start();
    }

    private void stopAnimation() {
        isAnimating = false;
        animationTimer.stop();
    }

    private void loadSpriteSheet(String imagePath) {
        try {
            spriteSheet = ImageIO.read(new File(imagePath));
            System.out.println("스프라이트 시트 로드 완료: " + imagePath);
        } catch (IOException e) {
            System.err.println("스프라이트 시트를 로드할 수 없습니다: " + e.getMessage());
            createTestSpriteSheet();
        }
    }

    private void createTestSpriteSheet() {
        spriteSheet = new BufferedImage(SPRITE_WIDTH * 8, SPRITE_HEIGHT * 8,
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = spriteSheet.createGraphics();

        // 0번 프레임 (빨간색)
        g2d.setColor(Color.RED);
        g2d.fillRect(0, 0, SPRITE_WIDTH, SPRITE_HEIGHT);
        g2d.setColor(Color.WHITE);
        g2d.drawString("0", 5, 20);

        // 6번 프레임 (파란색)
        g2d.setColor(Color.BLUE);
        g2d.fillRect(SPRITE_WIDTH * 6, 0, SPRITE_WIDTH, SPRITE_HEIGHT);
        g2d.setColor(Color.WHITE);
        g2d.drawString("6", SPRITE_WIDTH * 6 + 5, 20);

        g2d.dispose();
    }

    private void loadFrames(int baseFrameNum, int armFrameNum) {
        loadFrames(baseFrameNum, armFrameNum, false);
    }

    private void loadFrames(int baseFrameNum, int armFrameNum, boolean flipHorizontal) {
        if (spriteSheet == null) return;

        int cols = spriteSheet.getWidth() / SPRITE_WIDTH;

        // 기본 프레임 추출
        int baseX = (baseFrameNum % cols) * SPRITE_WIDTH;
        int baseY = (baseFrameNum / cols) * SPRITE_HEIGHT;
        BufferedImage originalBase = spriteSheet.getSubimage(baseX, baseY, SPRITE_WIDTH, SPRITE_HEIGHT);

        // 팔 프레임 추출
        int armX = (armFrameNum % cols) * SPRITE_WIDTH;
        int armY = (armFrameNum / cols) * SPRITE_HEIGHT;
        BufferedImage originalArm = spriteSheet.getSubimage(armX, armY, SPRITE_WIDTH, SPRITE_HEIGHT);

        // 좌우 반전이 필요한 경우
        if (flipHorizontal) {
            baseFrame = flipImageHorizontally(originalBase);
            armFrame = flipImageHorizontally(originalArm);
        } else {
            baseFrame = originalBase;
            armFrame = originalArm;
        }
    }

    private BufferedImage flipImageHorizontally(BufferedImage image) {
        BufferedImage flipped = new BufferedImage(image.getWidth(), image.getHeight(), image.getType());
        Graphics2D g2d = flipped.createGraphics();
        g2d.drawImage(image, image.getWidth(), 0, -image.getWidth(), image.getHeight(), null);
        g2d.dispose();
        return flipped;
    }

    // 0번과 6번 프레임을 겹쳐서 그리는 메서드
    public void render(Graphics2D g2d) {
        if (baseFrame != null && armFrame != null) {
            // 0번 프레임 먼저 그리기
            g2d.drawImage(baseFrame, x, y,
                    SPRITE_WIDTH * SCALE, SPRITE_HEIGHT * SCALE, null);

            // 6번 프레임을 겹쳐서 그리기
            g2d.drawImage(armFrame, x, y,
                    SPRITE_WIDTH * SCALE, SPRITE_HEIGHT * SCALE, null);
        }
    }

    // 위치 설정 메서드들
    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    // 스프라이트 프레임 변경 메서드
    public void changeSprite(int baseFrameNum, int armFrameNum) {
        loadFrames(baseFrameNum, armFrameNum, false);
    }

    public void changeSpriteFlipped(int baseFrameNum, int armFrameNum) {
        loadFrames(baseFrameNum, armFrameNum, true);
    }

    // 키보드 입력에 따른 스프라이트 변경 메서드
    public void handleKeyPressed(String keyInput) {
        switch (keyInput.toLowerCase()) {
            case "s":
                if (!sPressed) {
                    sPressed = true;
                    startAnimation(new int[]{1, 2}, new int[]{7, 8}, false); // s 누를 때: 몸(1,2), 팔(7,8)
                }
                break;
            case "d":
                if (!dPressed) {
                    dPressed = true;
                    startAnimation(new int[]{19, 20}, new int[]{23, 24}, false); // d 누를 때: 몸(19,20), 팔(23,24)
                }
                break;
            case "a":
                if (!aPressed) {
                    aPressed = true;
                    startAnimation(new int[]{19, 20}, new int[]{23, 24}, true); // a 누를 때: d와 같지만 좌우 반전
                }
                break;
            case "w":
                if (!wPressed) {
                    wPressed = true;
                    startAnimation(new int[]{37, 38}, new int[]{44, 46}, false); // w 누를 때: 몸(37,38), 팔(44,46)
                }
                break;
        }
    }

    public void handleKeyReleased(String keyInput) {
        switch (keyInput.toLowerCase()) {
            case "s":
                sPressed = false;
                stopAnimation();
                changeSprite(0, 6); // s 손 뗄 때: 기본 상태
                break;
            case "d":
                dPressed = false;
                stopAnimation();
                changeSprite(18, 24); // d 손 뗄 때: 몸(18), 팔(24)
                break;
            case "a":
                aPressed = false;
                stopAnimation();
                changeSpriteFlipped(18, 24); // a 손 뗄 때: d와 같지만 좌우 반전
                break;
            case "w":
                wPressed = false;
                stopAnimation();
                changeSprite(36, 42); // w 손 뗄 때: 몸(36), 팔(42)
                break;
        }
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getWidth() {
        return SPRITE_WIDTH * SCALE;
    }

    public int getHeight() {
        return SPRITE_HEIGHT * SCALE;
    }
}