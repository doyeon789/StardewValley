package Character;

public class Camera {
    /** ========== 카메라 위치 및 뷰포트 정보 ========== **/
    private int x, y;                           /// 카메라의 월드 좌표 (카메라가 바라보는 월드 위치)
    private int viewWidth, viewHeight;          /// 뷰포트 크기 (화면에 보이는 영역의 가로/세로 크기)
    private int mapWidth, mapHeight;            /// 현재 맵의 전체 크기 (픽셀 단위)

    /** ========== 카메라 이동 제한 범위 ========== **/
    private int minX, minY, maxX, maxY;         /// 카메라 이동 한계 (맵 경계를 벗어나지 않도록 제한)

    /// 생성자: 뷰포트 크기를 설정하고 카메라를 원점에 초기화
    /// @param viewWidth 화면에 보이는 영역의 가로 크기
    /// @param viewHeight 화면에 보이는 영역의 세로 크기
    public Camera(int viewWidth, int viewHeight) {
        this.viewWidth = viewWidth;
        this.viewHeight = viewHeight;
        this.x = 0;
        this.y = 0;
    }

    /// 맵 크기를 설정하고 카메라 이동 한계를 계산
    /// 카메라가 맵 경계를 벗어나지 않도록 최소/최대 이동 범위 설정
    /// @param mapWidth 맵의 전체 가로 크기 (픽셀)
    /// @param mapHeight 맵의 전체 세로 크기 (픽셀)
    public void setMapBounds(int mapWidth, int mapHeight) {
        this.mapWidth = mapWidth;
        this.mapHeight = mapHeight;

        // 카메라 이동 한계 계산
        minX = 0;                                       /// 최소 X 좌표 (맵 왼쪽 끝)
        minY = 0;                                       /// 최소 Y 좌표 (맵 위쪽 끝)
        maxX = Math.max(0, mapWidth - viewWidth);       /// 최대 X 좌표 (맵이 뷰포트보다 클 때만)
        maxY = Math.max(0, mapHeight - viewHeight);     /// 최대 Y 좌표 (맵이 뷰포트보다 클 때만)

        // 현재 카메라 위치를 한계 내로 조정
        clampCamera();
    }

    /// 플레이어를 화면 중앙에 유지하도록 카메라 업데이트
    /// 플레이어의 중심점이 화면 중앙에 오도록 카메라 위치 계산
    /// @param player 추적할 플레이어 스프라이트 객체
    public void followPlayer(SpriteRenderer player) {
        // 플레이어를 화면 중앙에 위치시키기 위한 카메라 위치 계산
        int targetX = player.getX() + player.getWidth() / 2 - viewWidth / 2;
        int targetY = player.getY() + player.getHeight() / 2 - viewHeight / 2;

        // 새로운 카메라 위치 설정
        this.x = targetX;
        this.y = targetY;

        // 카메라를 맵 경계 내로 제한
        clampCamera();
    }

    /// 카메라 위치를 맵 경계 내로 제한하는 내부 메서드
    /// setMapBounds()에서 계산된 min/max 값을 사용하여 범위 제한
    private void clampCamera() {
        if (x < minX) x = minX;     /// 왼쪽 경계 체크
        if (y < minY) y = minY;     /// 위쪽 경계 체크
        if (x > maxX) x = maxX;     /// 오른쪽 경계 체크
        if (y > maxY) y = maxY;     /// 아래쪽 경계 체크
    }

    /** ========== 좌표 변환 메서드들 ========== **/

    /// 월드 좌표를 화면 좌표로 변환
    /// 월드의 절대 좌표를 현재 카메라 기준의 상대 좌표로 변환
    /// @param worldX 월드 상의 X 좌표
    /// @return 화면 상의 X 좌표
    public int worldToScreenX(int worldX) {
        return worldX - x;
    }

    /// 월드 좌표를 화면 좌표로 변환 (Y축)
    /// @param worldY 월드 상의 Y 좌표
    /// @return 화면 상의 Y 좌표
    public int worldToScreenY(int worldY) {
        return worldY - y;
    }

    /// 화면 좌표를 월드 좌표로 변환
    /// 마우스 클릭 등 화면 상의 위치를 월드 좌표로 변환할 때 사용
    /// @param screenX 화면 상의 X 좌표
    /// @return 월드 상의 X 좌표
    public int screenToWorldX(int screenX) {
        return screenX + x;
    }

    /// 화면 좌표를 월드 좌표로 변환 (Y축)
    /// @param screenY 화면 상의 Y 좌표
    /// @return 월드 상의 Y 좌표
    public int screenToWorldY(int screenY) {
        return screenY + y;
    }

    /** ========== 렌더링 최적화 관련 ========== **/

    /// 특정 객체가 현재 카메라 뷰포트 내에 있는지 확인
    /// 화면에 보이지 않는 객체는 렌더링하지 않도록 컬링에 사용
    /// @param worldX 객체의 월드 X 좌표
    /// @param worldY 객체의 월드 Y 좌표
    /// @param width 객체의 가로 크기
    /// @param height 객체의 세로 크기
    /// @return 뷰포트 내에 있으면 true, 밖에 있으면 false
    public boolean isInView(int worldX, int worldY, int width, int height) {
        int screenX = worldToScreenX(worldX);
        int screenY = worldToScreenY(worldY);

        return screenX + width >= 0 && screenX <= viewWidth &&
                screenY + height >= 0 && screenY <= viewHeight;
    }

    /** ========== Getter 메서드들 ========== **/
    public int getX() { return x; }                     /// 카메라 X 좌표 반환
    public int getY() { return y; }                     /// 카메라 Y 좌표 반환
    public int getViewWidth() { return viewWidth; }     /// 뷰포트 가로 크기 반환
    public int getViewHeight() { return viewHeight; }   /// 뷰포트 세로 크기 반환
    public int getMapWidth() { return mapWidth; }       /// 맵 가로 크기 반환
    public int getMapHeight() { return mapHeight; }     /// 맵 세로 크기 반환

    /** ========== 경계 상태 확인 메서드들 ========== **/
    /// UI나 특별한 효과를 위해 카메라가 맵 경계에 닿았는지 확인
    public boolean isAtLeftEdge() { return x <= minX; }     /// 왼쪽 경계에 닿았는지 확인
    public boolean isAtRightEdge() { return x >= maxX; }    /// 오른쪽 경계에 닿았는지 확인
    public boolean isAtTopEdge() { return y <= minY; }      /// 위쪽 경계에 닿았는지 확인
    public boolean isAtBottomEdge() { return y >= maxY; }   /// 아래쪽 경계에 닿았는지 확인

    /// 디버그 정보를 문자열로 반환
    /// 개발 중 카메라 상태를 확인하기 위한 디버그용 메서드
    /// @return 카메라의 현재 상태 정보를 담은 문자열
    public String getDebugInfo() {
        return String.format("Camera: (%d,%d) | Bounds: (%d,%d) to (%d,%d) | Map: %dx%d",
                x, y, minX, minY, maxX, maxY, mapWidth, mapHeight);
    }
}