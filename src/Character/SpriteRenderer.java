package Character;

import java.awt.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import javax.swing.Timer;
import java.io.File;
import java.io.IOException;

public class SpriteRenderer {
    /** ========== 스프라이트 이미지 관련 ========== **/
    private BufferedImage spriteSheet;          /// 전체 스프라이트 시트 이미지
    private BufferedImage baseFrame;            /// 현재 기본 프레임 (몸체)
    private BufferedImage armFrame;             /// 현재 팔 프레임

    /** ========== 위치 및 크기 정보 ========== **/
    private int x = 0;                          /// 스프라이트의 X 좌표
    private int y = 0;                          /// 스프라이트의 Y 좌표

    private static final int SPRITE_WIDTH = 16;  /// 개별 스프라이트의 가로 크기 (픽셀)
    private static final int SPRITE_HEIGHT = 32; /// 개별 스프라이트의 세로 크기 (픽셀)
    private static final int SCALE = 3;          /// 스프라이트 확대 비율

    /** ========== 애니메이션 시스템 ========== **/
    private Timer animationTimer;               /// 애니메이션 타이머 (190ms 간격)
    private boolean isAnimating = false;        /// 현재 애니메이션 중인지 여부
    private int currentAnimFrame = 0;           /// 현재 애니메이션 프레임 (0 또는 1)

    private int[] currentBaseFrames = {0, 0};   /// 현재 기본 애니메이션 프레임 배열 (몸체)
    private int[] currentArmFrames = {6, 6};    /// 현재 팔 애니메이션 프레임 배열
    private boolean currentFlipped = false;     /// 현재 좌우 반전 상태

    /** ========== 키 입력 상태 관리 ========== **/
    private boolean sPressed = false;           /// S키 (아래) 눌림 상태
    private boolean dPressed = false;           /// D키 (오른쪽) 눌림 상태
    private boolean aPressed = false;           /// A키 (왼쪽) 눌림 상태
    private boolean wPressed = false;           /// W키 (위) 눌림 상태

    /// 현재 활성화된 방향을 추적 (우선순위 시스템용)
    private String currentDirection = "";

    /// 생성자: 스프라이트 시트 로드 및 초기화
    /// 1. 스프라이트 시트 이미지 로드
    /// 2. 애니메이션 타이머 설정
    /// 3. 기본 프레임으로 초기화
    public SpriteRenderer() {
        loadSpriteSheet("resource/Characters/Farmer/farmer_base.png");
        setupAnimation();
        loadFrames(0, 6); // 기본값: 0번과 6번 프레임
    }

    /// 애니메이션 타이머 설정 (190ms 간격으로 프레임 전환)
    /// 애니메이션 중일 때만 프레임을 0과 1 사이에서 반복
    private void setupAnimation() {
        animationTimer = new Timer(190, e -> {
            if (isAnimating) {
                currentAnimFrame = (currentAnimFrame + 1) % 2; // 0과 1 사이를 반복
                updateCurrentFrame();
            }
        });
    }

    /// 현재 애니메이션 프레임에 따라 실제 스프라이트 프레임 업데이트
    /// currentAnimFrame 값에 따라 적절한 베이스/팔 프레임 선택
    private void updateCurrentFrame() {
        int baseFrame = currentBaseFrames[currentAnimFrame];
        int armFrame = currentArmFrames[currentAnimFrame];
        loadFrames(baseFrame, armFrame, currentFlipped);
    }

    /// 새로운 애니메이션 시작
    /// @param baseFrames 몸체 애니메이션 프레임 배열 (2개)
    /// @param armFrames 팔 애니메이션 프레임 배열 (2개)
    /// @param flipped 좌우 반전 여부
    private void startAnimation(int[] baseFrames, int[] armFrames, boolean flipped) {
        currentBaseFrames = baseFrames.clone();
        currentArmFrames = armFrames.clone();
        currentFlipped = flipped;
        currentAnimFrame = 0;
        isAnimating = true;

        updateCurrentFrame();
        animationTimer.start();
    }

    /// 애니메이션 중지
    /// 타이머를 멈추고 애니메이션 상태를 false로 설정
    private void stopAnimation() {
        isAnimating = false;
        animationTimer.stop();
    }

    /// 스프라이트 시트 이미지 파일 로드
    /// @param imagePath 이미지 파일 경로
    private void loadSpriteSheet(String imagePath) {
        try {
            spriteSheet = ImageIO.read(new File(imagePath));
        } catch (IOException e) {
            System.err.println("스프라이트 시트를 로드할 수 없습니다: " + e.getMessage());
        }
    }

    /// 기본 프레임 로드 메서드 (좌우 반전 없음)
    /// @param baseFrameNum 몸체 프레임 번호
    /// @param armFrameNum 팔 프레임 번호
    private void loadFrames(int baseFrameNum, int armFrameNum) {
        loadFrames(baseFrameNum, armFrameNum, false);
    }

    /// 지정된 프레임 번호로 몸체와 팔 이미지 로드
    /// @param baseFrameNum 몸체 프레임 번호
    /// @param armFrameNum 팔 프레임 번호
    /// @param flipHorizontal 좌우 반전 여부
    private void loadFrames(int baseFrameNum, int armFrameNum, boolean flipHorizontal) {
        if (spriteSheet == null) return;

        int cols = spriteSheet.getWidth() / SPRITE_WIDTH;

        // 기본 프레임 추출 (몸체)
        int baseX = (baseFrameNum % cols) * SPRITE_WIDTH;
        int baseY = (baseFrameNum / cols) * SPRITE_HEIGHT;
        BufferedImage originalBase = spriteSheet.getSubimage(baseX, baseY, SPRITE_WIDTH, SPRITE_HEIGHT);

        // 팔 프레임 추출
        int armX = (armFrameNum % cols) * SPRITE_WIDTH;
        int armY = (armFrameNum / cols) * SPRITE_HEIGHT;
        BufferedImage originalArm = spriteSheet.getSubimage(armX, armY, SPRITE_WIDTH, SPRITE_HEIGHT);

        // 좌우 반전이 필요한 경우 (A키 입력시)
        if (flipHorizontal) {
            baseFrame = flipImageHorizontally(originalBase);
            armFrame = flipImageHorizontally(originalArm);
        } else {
            baseFrame = originalBase;
            armFrame = originalArm;
        }
    }

    /// 이미지를 좌우 반전시키는 메서드
    /// @param image 원본 이미지
    /// @return 좌우 반전된 이미지
    private BufferedImage flipImageHorizontally(BufferedImage image) {
        BufferedImage flipped = new BufferedImage(image.getWidth(), image.getHeight(), image.getType());
        Graphics2D g2d = flipped.createGraphics();
        g2d.drawImage(image, image.getWidth(), 0, -image.getWidth(), image.getHeight(), null);
        g2d.dispose();
        return flipped;
    }

    /// 현재 눌린 키들을 확인하고 우선순위에 따라 애니메이션 결정
    /// 우선순위: w(위) > s(아래) > a(왼쪽) > d(오른쪽)
    /// 방향이 바뀌었을 때만 애니메이션을 새로 시작하여 성능 최적화
    private void updateAnimationState() {
        String newDirection = "";

        // 우선순위: w > s > a > d (필요에 따라 변경 가능)
        if (wPressed) {
            newDirection = "w";
        } else if (sPressed) {
            newDirection = "s";
        } else if (aPressed) {
            newDirection = "a";
        } else if (dPressed) {
            newDirection = "d";
        }

        // 방향이 바뀌었거나 새로운 방향이 설정된 경우에만 애니메이션 업데이트
        if (!newDirection.equals(currentDirection)) {
            currentDirection = newDirection;

            if (newDirection.isEmpty()) {
                // 모든 키가 해제된 경우 - 기본 상태로
                stopAnimation();
                changeSprite(0, 6);
            } else {
                // 새로운 방향에 따라 애니메이션 시작
                switch (newDirection) {
                    case "s":   // 아래 방향: 몸(1,2), 팔(7,8)
                        startAnimation(new int[]{1, 2}, new int[]{7, 8}, false);
                        break;
                    case "d":   // 오른쪽 방향: 몸(19,20), 팔(23,24)
                        startAnimation(new int[]{19, 20}, new int[]{23, 24}, false);
                        break;
                    case "a":   // 왼쪽 방향: d와 같지만 좌우 반전
                        startAnimation(new int[]{19, 20}, new int[]{23, 24}, true);
                        break;
                    case "w":   // 위 방향: 몸(37,38), 팔(44,46)
                        startAnimation(new int[]{37, 38}, new int[]{44, 46}, false);
                        break;
                }
            }
        }
    }

    /// 화면에 스프라이트 렌더링 (몸체 + 팔을 레이어로 그리기)
    /// @param g2d Graphics2D 객체
    public void render(Graphics2D g2d) {
        if (baseFrame != null && armFrame != null) {
            g2d.drawImage(baseFrame, x, y,
                    SPRITE_WIDTH * SCALE, SPRITE_HEIGHT * SCALE, null);
            g2d.drawImage(armFrame, x, y,
                    SPRITE_WIDTH * SCALE, SPRITE_HEIGHT * SCALE, null);
        }
    }

    /// 스프라이트 위치 설정
    /// @param x X 좌표
    /// @param y Y 좌표
    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    /// 정적 스프라이트 변경 (애니메이션 없음)
    /// @param baseFrameNum 몸체 프레임 번호
    /// @param armFrameNum 팔 프레임 번호
    public void changeSprite(int baseFrameNum, int armFrameNum) {
        loadFrames(baseFrameNum, armFrameNum, false);
    }

    /// 정적 스프라이트 변경 (좌우 반전)
    /// @param baseFrameNum 몸체 프레임 번호
    /// @param armFrameNum 팔 프레임 번호
    public void changeSpriteFlipped(int baseFrameNum, int armFrameNum) {
        loadFrames(baseFrameNum, armFrameNum, true);
    }

    /// 키 눌림 처리 - 다중 키 입력 및 우선순위 지원
    /// @param keyInput 입력된 키 문자열 ("w", "a", "s", "d")
    public void handleKeyPressed(String keyInput) {
        boolean wasPressed = false;

        switch (keyInput.toLowerCase()) {
            case "s":   // 아래 방향키
                wasPressed = sPressed;
                sPressed = true;
                break;
            case "d":   // 오른쪽 방향키
                wasPressed = dPressed;
                dPressed = true;
                break;
            case "a":   // 왼쪽 방향키
                wasPressed = aPressed;
                aPressed = true;
                break;
            case "w":   // 위 방향키
                wasPressed = wPressed;
                wPressed = true;
                break;
        }

        // 이미 눌린 키가 아닌 경우에만 애니메이션 상태 업데이트
        if (!wasPressed) {
            updateAnimationState();
        }
    }

    /// 키 해제 처리 - 다른 키가 여전히 눌려있으면 해당 애니메이션 유지
    /// @param keyInput 해제된 키 문자열 ("w", "a", "s", "d")
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

        // 키가 해제될 때마다 현재 상태 업데이트
        updateAnimationState();

        // 모든 키가 해제된 경우 적절한 정지 모션 설정
        if (!sPressed && !dPressed && !aPressed && !wPressed) {
            // 마지막 방향에 따른 정지 모션 (선택사항)
            switch (keyInput.toLowerCase()) {
                case "s":   // 아래 방향 정지: 기본 상태
                    changeSprite(0, 6);
                    break;
                case "d":   // 오른쪽 방향 정지: 몸(18), 팔(24)
                    changeSprite(18, 24);
                    break;
                case "a":   // 왼쪽 방향 정지: 오른쪽과 같지만 반전
                    changeSpriteFlipped(18, 24);
                    break;
                case "w":   // 위 방향 정지: 몸(36), 팔(42)
                    changeSprite(36, 42);
                    break;
            }
        }
    }

    /** ========== Getter 메서드들 ========== **/
    public int getX() { return x; }                             /// X 좌표 반환
    public int getY() { return y; }                             /// Y 좌표 반환
    public int getWidth() { return SPRITE_WIDTH * SCALE; }      /// 스프라이트 실제 가로 크기 반환
    public int getHeight() { return SPRITE_HEIGHT * SCALE; }    /// 스프라이트 실제 세로 크기 반환
}