package MapLoad;

import javax.swing.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.File;
import java.util.*;
import java.util.List;

import Character.SpriteRenderer;
import Character.Camera;

public class TmxParser {
    /** ========== 내부 클래스 ========== **/
    static class Tileset {
        int firstGid;                           /// 타일셋의 시작 GID (Global ID)
        String name;                            /// 타일셋 이름
        int tileWidth;                          /// 개별 타일의 가로 크기
        int tileHeight;                         /// 개별 타일의 세로 크기
        int tileCount;                          /// 타일셋 내 총 타일 개수
        int columns;                            /// 타일셋의 열(column) 수
        String imagePath;                       /// 타일셋 이미지 파일 경로
        BufferedImage image;                    /// 로드된 타일셋 이미지

        Map<Integer, BufferedImage> tileCache = new HashMap<>(); /// 개별 타일 이미지 캐시
    }
    static class Layer {
        String name;                            /// 레이어 이름
        int width;                              /// 레이어 가로 크기 (타일 개수)
        int height;                             /// 레이어 세로 크기 (타일 개수)
        int[] data;                             /// 타일 GID 배열 데이터
        boolean visible = true;                 /// 레이어 표시 여부
    }

    /// 커스텀 렌더링 컴포넌트 (카메라 기반 렌더링)
    private class TileMapCanvas extends JComponent {
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            renderTileMapWithCamera(g);         /// 카메라를 적용한 타일맵 렌더링
        }
    }

    /** ========== 맵 정보 ========== **/
    private int mapWidth;                       /// 맵 가로 크기 (타일 개수)
    private int mapHeight;                      /// 맵 세로 크기 (타일 개수)
    private int tileWidth;                      /// 개별 타일의 가로 크기 (픽셀)
    private int tileHeight;                     /// 개별 타일의 세로 크기 (픽셀)
    private final List<Tileset> tilesets;             /// 타일셋 목록
    private final List<Layer> layers;                 /// 레이어 목록

    /// 모든 PNG 이미지를 저장하는 맵 (파일명 -> 이미지)
    private final Map<String, BufferedImage> preloadedImages;

    /// 성능 최적화를 위한 캐시
    private final Map<Integer, Tileset> gidToTilesetCache;     /// GID -> Tileset 매핑 캐시
    private final Map<Integer, BufferedImage> globalTileCache; /// GID -> 타일이미지 캐시

    /** ========== GUI 컴포넌트 ========== **/
    private final JFrame frame;                       /// 메인 윈도우
    private final TileMapCanvas canvas;               /// 타일맵 렌더링 캔버스

    /** ========== 게임 시스템 ========== **/
    private final SpriteRenderer sprite;              /// 플레이어 캐릭터 스프라이트
    private final Camera camera;                      /// 카메라 시스템

    private static final int TILE_SCALE = 3;   /// 타일 확대 비율

    /// 60 FPS 게임 루프 타이머
    private final Set<String> keysPressed = new HashSet<>();  /// 현재 눌린 키들
    private static final int GAME_FPS = 60;             /// 게임 FPS (초당 프레임)
    private static final int MOVE_SPEED = 5;            /// 픽셀 단위 이동 속도

    /// 맵 중앙 정렬을 위한 오프셋
    private int mapOffsetX = 0;
    private int mapOffsetY = 0;

    /** ========== 충돌 검사 시스템 ========== **/
    private Layer collisionLayer = null;
    private boolean showCollisionDebug = true;

    /// 1. 컬렉션 초기화
    /// 2. PNG 파일들을 미리 로3
    /// 3. 카메라와 스프라이트 초기화
    /// 4. GUI 초기화 및 키 이벤트 설정
    /// 5. 게임 루프 시작
    public TmxParser() {
        tilesets = new ArrayList<>();
        layers = new ArrayList<>();
        preloadedImages = new HashMap<>();
        gidToTilesetCache = new HashMap<>();
        globalTileCache = new HashMap<>();

        // 생성자에서 PNG 파일들을 미리 로드
        preloadAllPngImages();

        camera = new Camera(1200, 780);

        sprite = new SpriteRenderer();

        frame = new JFrame("TMX 타일맵 뷰어 (부드러운 이동)");
        frame.setResizable(false);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        canvas = new TileMapCanvas();
        canvas.setPreferredSize(new Dimension(1200, 780)); /// canvas 크기 지정
        canvas.setBackground(Color.BLACK);
        canvas.setOpaque(true);
        canvas.setFocusable(true);

        // 키 이벤트 핸들러
        canvas.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override
            public void keyPressed(java.awt.event.KeyEvent e) {
                String key = java.awt.event.KeyEvent.getKeyText(e.getKeyCode()).toLowerCase();
                keysPressed.add(key);
                sprite.handleKeyPressed(key);
            }

            @Override
            public void keyReleased(java.awt.event.KeyEvent e) {
                String key = java.awt.event.KeyEvent.getKeyText(e.getKeyCode()).toLowerCase();
                keysPressed.remove(key);
                sprite.handleKeyReleased(key);
            }
        });

        frame.add(canvas);

        // 게임 루프 타이머 시작
        startGameLoop();
    }

    /// 60 FPS로 동작하는 게임 루프 시작
    /// 매 프레임마다 이동 업데이트 및 화면 갱신
    private void startGameLoop() {
        /// 부드러운 이동을 위한 게임 루프 변수들
        javax.swing.Timer gameTimer = new javax.swing.Timer(1000 / GAME_FPS, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                updateMovement();
                canvas.repaint();
            }
        });
        gameTimer.start();
    }

    /// 현재 눌린 키들을 체크하여 연속적인 부드러운 이동 처리
    /// 맵 경계를 벗어나지 않도록 검사
    private void updateMovement() {
        if (keysPressed.isEmpty()) return;

        int newX = sprite.getX();
        int newY = sprite.getY();
        boolean moved = false;

        // 각 방향키에 대해 이동 처리
        if (keysPressed.contains("w")) {
            int testY = newY - MOVE_SPEED;
            if (isValidPlayerPosition(newX, testY)) {
                newY = testY;
                moved = true;
            }
        }
        if (keysPressed.contains("s")) {
            int testY = newY + MOVE_SPEED;
            if (isValidPlayerPosition(newX, testY)) {
                newY = testY;
                moved = true;
            }
        }
        if (keysPressed.contains("a")) {
            int testX = newX - MOVE_SPEED;
            if (isValidPlayerPosition(testX, newY)) {
                newX = testX;
                moved = true;
            }
        }
        if (keysPressed.contains("d")) {
            int testX = newX + MOVE_SPEED;
            if (isValidPlayerPosition(testX, newY)) {
                newX = testX;
                moved = true;
            }
        }

        // 맵 경계 체크 (기존 로직 유지)
        if (moved) {
            int mapPixelWidth = mapWidth * tileWidth * TILE_SCALE;
            int mapPixelHeight = mapHeight * tileHeight * TILE_SCALE;

            int minX = mapOffsetX;
            int minY = mapOffsetY;
            int maxX = mapOffsetX + mapPixelWidth - sprite.getWidth();
            int maxY = mapOffsetY + mapPixelHeight - sprite.getHeight();

            // 경계 내에서만 이동 허용
            if (newX >= minX && newX <= maxX && newY >= minY && newY <= maxY) {
                sprite.setPosition(newX, newY);
            }
        }
    }

    /// 맵 중앙 정렬 오프셋 계산
    private void calculateMapOffset() {
        int mapPixelWidth = mapWidth * tileWidth * TILE_SCALE;
        int mapPixelHeight = mapHeight * tileHeight * TILE_SCALE;

        // 맵이 화면보다 작은 경우 중앙 정렬
        if (mapPixelWidth < canvas.getWidth()) {
            mapOffsetX = (canvas.getWidth() - mapPixelWidth) / 2;
        } else {
            mapOffsetX = 0;
        }

        if (mapPixelHeight < canvas.getHeight()) {
            mapOffsetY = (canvas.getHeight() - mapPixelHeight) / 2;
        } else {
            mapOffsetY = 0;
        }
    }

    /// 자동으로 기본 TMX 파일 찾아서 로드하는 메소드
    public void loadDefaultTmxFile(String Mappath) {

        File file = new File(Mappath);
        if (file.exists()){
            if (loadTMX(Mappath)) {
                frame.setTitle("TMX 타일맵 뷰어 - " + file.getName());
                return;
            }
        }
        // resource 디렉토리에서 첫 번째 .tmx 파일 찾기
        File resourceDir = new File("resource");
        if (resourceDir.exists() && resourceDir.isDirectory()) {
            File[] tmxFiles = resourceDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".tmx"));
            if (tmxFiles != null && tmxFiles.length > 0) {
                String path = tmxFiles[0].getAbsolutePath();
                System.out.println("resource 디렉토리에서 TMX 파일 발견: " + path);
                if (loadTMX(path)) {
                    frame.setTitle("TMX 타일맵 뷰어 - " + tmxFiles[0].getName());
                    return;
                }
            }
        }

        System.out.println("기본 TMX 파일을 찾을 수 없습니다.");
    }

    /// 윈도우를 화면에 표시하고 키보드 포커스 설정
    public void show() {
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        // 창이 표시된 후 오프셋 재계산 (canvas 크기가 확정된 후)
        SwingUtilities.invokeLater(() -> {
            if (mapWidth > 0 && mapHeight > 0) {
                calculateMapOffset();
            }
            canvas.requestFocusInWindow();
            canvas.repaint();
        });
    }

    /// resource 디렉토리의 모든 PNG 파일을 재귀적으로 미리 로드
    /// 파일명과 확장자 없는 이름 두 가지로 저장하여 유연한 검색 지원
    private void preloadAllPngImages() {
        preloadedImages.clear();

        File resourceDir = new File("resource");
        if (!resourceDir.exists() || !resourceDir.isDirectory()) {
            File projectRoot = new File(System.getProperty("user.dir"));
            resourceDir = new File(projectRoot, "resource");

            if (!resourceDir.exists() || !resourceDir.isDirectory()) {
                System.err.println("resource 디렉토리를 찾을 수 없습니다: " + resourceDir.getAbsolutePath());
                return;
            }
        }

        System.out.println("resource 디렉토리에서 PNG 파일들을 재귀적으로 로드 중: " + resourceDir.getAbsolutePath());

        // 재귀적으로 PNG 파일 찾기
        List<File> pngFiles = new ArrayList<>();
        collectPngFiles(resourceDir, pngFiles);

        for (File pngFile : pngFiles) {
            try {
                BufferedImage image = ImageIO.read(pngFile);
                String fileName = pngFile.getName();
                String fileNameWithoutExt = fileName.substring(0, fileName.lastIndexOf('.'));

                // 확장자 포함/미포함 둘 다 저장
                preloadedImages.put(fileName, image);
                preloadedImages.put(fileNameWithoutExt, image);

            } catch (Exception e) {
                System.err.println("PNG 파일 로드 실패: " + pngFile.getName() + " - " + e.getMessage());
            }
        }

        System.out.println("총 " + pngFiles.size() + "개의 PNG 파일이 resource 디렉토리에서 로드되었습니다.");
    }

    /// 재귀적으로 PNG 파일 수집
    private void collectPngFiles(File dir, List<File> pngFiles) {
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                collectPngFiles(file, pngFiles); // 하위 폴더 재귀 탐색
            } else if (file.getName().toLowerCase().endsWith(".png")) {
                pngFiles.add(file);
            }
        }
    }

    /// 이미지 파일명으로부터 이미지 찾기 (다양한 방식으로 시도)
    /// 파일명, 확장자 제거, .png 추가 등 여러 방법으로 검색
    private BufferedImage findImageByName(String imagePath) {
        // 파일명만 추출
        String fileName = new File(imagePath).getName();

        // 확장자 포함해서 찾기
        BufferedImage image = preloadedImages.get(fileName);
        if (image != null) {
            return image;
        }

        // 확장자 제거해서 찾기
        if (fileName.contains(".")) {
            String nameWithoutExt = fileName.substring(0, fileName.lastIndexOf('.'));
            image = preloadedImages.get(nameWithoutExt);
            if (image != null) {
                return image;
            }
        }

        // 확장자가 없으면 .png를 붙여서 찾기
        if (!fileName.contains(".")) {
            image = preloadedImages.get(fileName + ".png");
            return image;
        }

        return null;
    }

    /// TMX 파일 로드 및 파싱
    /// 1. XML 문서 파싱
    /// 2. 맵 기본 정보 읽기
    /// 3. 타일셋 정보 파싱
    /// 4. 레이어 데이터 파싱
    /// 5. 카메라 및 플레이어 초기 설정
    /// 6. 캐시 시스템 구축
    public boolean loadTMX(String tmxPath) {
        try {
            // 기존 데이터 초기화
            tilesets.clear();
            layers.clear();
            gidToTilesetCache.clear();
            globalTileCache.clear();

            File tmxFile = new File(tmxPath);

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(tmxFile);
            doc.getDocumentElement().normalize();

            // 맵 속성 파싱
            Element mapElement = doc.getDocumentElement();
            mapWidth = Integer.parseInt(mapElement.getAttribute("width"));
            mapHeight = Integer.parseInt(mapElement.getAttribute("height"));
            tileWidth = Integer.parseInt(mapElement.getAttribute("tilewidth"));
            tileHeight = Integer.parseInt(mapElement.getAttribute("tileheight"));

            // 맵 중앙 정렬 오프셋 계산
            calculateMapOffset();

            // 카메라에 맵 크기 설정
            int mapPixelWidth = mapWidth * tileWidth * TILE_SCALE;
            int mapPixelHeight = mapHeight * tileHeight * TILE_SCALE;
            camera.setMapBounds(mapPixelWidth, mapPixelHeight);

            // 플레이어를 맵 중앙에 기본 배치 (나중에 setPlayerStartPosition으로 변경 가능)
            setPlayerStartPosition(10, 10);

            // 타일셋 파싱
            NodeList tilesetNodes = doc.getElementsByTagName("tileset");
            for (int i = 0; i < tilesetNodes.getLength(); i++) {
                Element tilesetElement = (Element) tilesetNodes.item(i);
                Tileset tileset = new Tileset();

                tileset.firstGid = Integer.parseInt(tilesetElement.getAttribute("firstgid"));
                tileset.name = tilesetElement.getAttribute("name");
                tileset.tileWidth = Integer.parseInt(tilesetElement.getAttribute("tilewidth"));
                tileset.tileHeight = Integer.parseInt(tilesetElement.getAttribute("tileheight"));
                tileset.tileCount = Integer.parseInt(tilesetElement.getAttribute("tilecount"));
                tileset.columns = Integer.parseInt(tilesetElement.getAttribute("columns"));

                // 이미지 경로 가져오기
                NodeList imageNodes = tilesetElement.getElementsByTagName("image");
                if (imageNodes.getLength() > 0) {
                    Element imageElement = (Element) imageNodes.item(0);
                    tileset.imagePath = imageElement.getAttribute("source");

                    // 미리 로드된 이미지에서 찾기
                    tileset.image = findImageByName(tileset.imagePath);

                    if (tileset.image != null) {
                        System.out.println("타일셋 이미지 연결됨: " + tileset.imagePath + " -> " + tileset.name);
                    } else {
                        System.err.println("타일셋 이미지를 찾을 수 없습니다: " + tileset.imagePath);
                    }
                }

                tilesets.add(tileset);
                System.out.println("타일셋 추가됨: " + tileset.name + " (GID: " + tileset.firstGid + ")");
            }

            // 레이어 파싱
            NodeList layerNodes = doc.getElementsByTagName("layer");
            for (int i = 0; i < layerNodes.getLength(); i++) {
                Element layerElement = (Element) layerNodes.item(i);
                Layer layer = new Layer();

                layer.name = layerElement.getAttribute("name");
                layer.width = Integer.parseInt(layerElement.getAttribute("width"));
                layer.height = Integer.parseInt(layerElement.getAttribute("height"));

                // 투명도 체크
                String opacity = layerElement.getAttribute("opacity");
                if (!opacity.isEmpty() && Float.parseFloat(opacity) == 0.0f) {
                    layer.visible = false;
                }

                // 데이터 파싱 (CSV 형식)
                NodeList dataNodes = layerElement.getElementsByTagName("data");
                if (dataNodes.getLength() > 0) {
                    Element dataElement = (Element) dataNodes.item(0);
                    String encoding = dataElement.getAttribute("encoding");

                    if ("csv".equals(encoding)) {
                        String csvData = dataElement.getTextContent().trim();
                        String[] values = csvData.split(",");
                        layer.data = new int[values.length];

                        for (int j = 0; j < values.length; j++) {
                            layer.data[j] = Integer.parseInt(values[j].trim());
                        }
                    }
                }

                layers.add(layer);
                System.out.println("레이어 추가됨: " + layer.name + " (" + layer.width + "x" + layer.height + ")");
            }

            // 타일셋 캐시 구축
            buildTilesetCache();

            // 타일 이미지 미리 캐싱 (백그라운드에서)
            preloadTileImages();

            setupCollisionLayer();

            // UI 업데이트
            SwingUtilities.invokeLater(() -> {
                canvas.revalidate();
                canvas.repaint();
                frame.pack();
            });

            return true;

        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(frame,
                    "TMX 파일 로드 중 오류 발생:\n" + e.getMessage(),
                    "로드 오류", JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    /// 타일셋 캐시 구축 (GID 범위별로 타일셋 매핑)
    /// 빠른 GID -> Tileset 검색을 위한 HashMap 생성
    private void buildTilesetCache() {
        for (Tileset tileset : tilesets) {
            for (int i = 0; i < tileset.tileCount; i++) {
                int gid = tileset.firstGid + i;
                gidToTilesetCache.put(gid, tileset);
            }
        }
        System.out.println("타일셋 캐시 구축 완료: " + gidToTilesetCache.size() + " entries");
    }

    /// 타일 이미지 미리 캐싱 (백그라운드 스레드에서 실행)
    /// 맵에서 실제 사용되는 타일들만 미리 생성하여 메모리에 캐시
    private void preloadTileImages() {
        new Thread(() -> {
            System.out.println("타일 이미지 캐싱 시작...");
            int cachedCount = 0;

            for (Layer layer : layers) {
                if (!layer.visible) continue;

                Set<Integer> uniqueGids = new HashSet<>();
                for (int gid : layer.data) {
                    if (gid > 0) uniqueGids.add(gid);
                }

                for (int gid : uniqueGids) {
                    BufferedImage tileImage = createTileImage(gid);
                    if (tileImage != null) {
                        globalTileCache.put(gid, tileImage);
                        cachedCount++;
                    }
                }
            }

            System.out.println("타일 이미지 캐싱 완료: " + cachedCount + " tiles");
            SwingUtilities.invokeLater(canvas::repaint);
        }).start();
    }

    /// 특정 GID에 해당하는 타일셋 찾기 (캐시 활용으로 최적화됨)
    private Tileset findTilesetForGid(int gid) {
        return gidToTilesetCache.get(gid);
    }

    /// 새 타일 이미지 생성 (캐시되지 않은 경우에만 호출)
    /// 타일셋에서 개별 타일을 추출하여 BufferedImage로 생성
    private BufferedImage createTileImage(int gid) {
        if (gid == 0) return null;

        Tileset tileset = findTilesetForGid(gid);
        if (tileset == null || tileset.image == null) return null;

        // 타일셋 내부 캐시 확인
        BufferedImage cachedTile = tileset.tileCache.get(gid);
        if (cachedTile != null) return cachedTile;

        int tileId = gid - tileset.firstGid;
        int tilesPerRow = tileset.columns;
        int tileX = (tileId % tilesPerRow) * tileset.tileWidth;
        int tileY = (tileId / tilesPerRow) * tileset.tileHeight;

        try {
            BufferedImage tileImage = tileset.image.getSubimage(tileX, tileY, tileset.tileWidth, tileset.tileHeight);
            // 타일셋 내부 캐시에 저장
            tileset.tileCache.put(gid, tileImage);
            return tileImage;
        } catch (Exception e) {
            return null;
        }
    }

    /// GID로부터 타일 이미지 가져오기 (캐시 우선 검색)
    /// 글로벌 캐시 → 생성 순으로 시도
    private BufferedImage getTileImage(int gid) {
        if (gid == 0) return null;

        // 글로벌 캐시에서 먼저 확인
        BufferedImage cachedImage = globalTileCache.get(gid);
        if (cachedImage != null) return cachedImage;

        // 캐시에 없으면 생성
        return createTileImage(gid);
    }

    /// 카메라 기반 최적화된 렌더링 시스템
    /// 1. 카메라 업데이트 (플레이어 추적)
    /// 2. 화면 범위 내 타일만 렌더링 (컬링)
    /// 3. 레이어별 타일 렌더링
    /// 4. 플레이어 렌더링 (좌표 변환)
    /// 5. UI 렌더링
    private void renderTileMapWithCamera(Graphics g) {
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        // 배경색
        g2d.setColor(Color.BLACK);
        g2d.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());

        int mapPixelWidth = mapWidth * tileWidth * TILE_SCALE;
        int mapPixelHeight = mapHeight * tileHeight * TILE_SCALE;

        // 맵이 화면보다 큰 경우에만 카메라 추적
        if (mapPixelWidth > canvas.getWidth() || mapPixelHeight > canvas.getHeight()) {
            // 카메라 업데이트
            camera.followPlayer(sprite);

            // 화면에 보이는 타일 범위 계산 (컬링 최적화)
            int scaledTileWidth = tileWidth * TILE_SCALE;
            int scaledTileHeight = tileHeight * TILE_SCALE;

            int startTileX = Math.max(0, camera.getX() / scaledTileWidth);
            int startTileY = Math.max(0, camera.getY() / scaledTileHeight);
            int endTileX = Math.min(mapWidth - 1, (camera.getX() + camera.getViewWidth()) / scaledTileWidth + 1);
            int endTileY = Math.min(mapHeight - 1, (camera.getY() + camera.getViewHeight()) / scaledTileHeight + 1);

            // 레이어별 렌더링
            for (Layer layer : layers) {
                if (!layer.visible) continue;

                for (int y = startTileY; y <= endTileY; y++) {
                    for (int x = startTileX; x <= endTileX; x++) {
                        int index = y * layer.width + x;
                        if (index >= layer.data.length) continue;

                        int gid = layer.data[index];
                        if (gid == 0) continue;

                        BufferedImage tileImage = getTileImage(gid);
                        if (tileImage != null) {
                            // 월드 좌표
                            int worldX = x * scaledTileWidth;
                            int worldY = y * scaledTileHeight;

                            // 화면 좌표로 변환
                            int screenX = camera.worldToScreenX(worldX);
                            int screenY = camera.worldToScreenY(worldY);

                            g2d.drawImage(tileImage, screenX, screenY, scaledTileWidth, scaledTileHeight, null);
                        }
                    }
                }
            }

            // 플레이어 렌더링 (카메라 좌표계)
            // 플레이어의 화면 좌표 계산
            int playerScreenX = camera.worldToScreenX(sprite.getX());
            int playerScreenY = camera.worldToScreenY(sprite.getY());

            // 원래 위치 저장
            int originalX = sprite.getX();
            int originalY = sprite.getY();

            // 화면 좌표로 설정하고 렌더링
            sprite.setPosition(playerScreenX, playerScreenY);
            sprite.render(g2d);

            // 원래 위치 복원
            sprite.setPosition(originalX, originalY);

        } else {
            // 맵이 화면보다 작은 경우 - 고정된 중앙 위치에서 렌더링
            int scaledTileWidth = tileWidth * TILE_SCALE;
            int scaledTileHeight = tileHeight * TILE_SCALE;

            // 레이어별 렌더링 (모든 타일 렌더링)
            for (Layer layer : layers) {
                if (!layer.visible) continue;

                for (int y = 0; y < mapHeight; y++) {
                    for (int x = 0; x < mapWidth; x++) {
                        int index = y * layer.width + x;
                        if (index >= layer.data.length) continue;

                        int gid = layer.data[index];
                        if (gid == 0) continue;

                        BufferedImage tileImage = getTileImage(gid);
                        if (tileImage != null) {
                            // 중앙 정렬된 위치에 타일 렌더링
                            int screenX = mapOffsetX + x * scaledTileWidth;
                            int screenY = mapOffsetY + y * scaledTileHeight;

                            g2d.drawImage(tileImage, screenX, screenY, scaledTileWidth, scaledTileHeight, null);
                        }
                    }
                }
            }

            // 플레이어 렌더링 (고정된 위치)
            sprite.render(g2d);
        }


        // 이동불가 영역 UI
        //renderCollisionDebug(g2d);
        // 정보 UI
        renderUI(g2d);
    }

    /// 플레이어 초기 위치 설정 (타일 좌표 기준)
    public void setPlayerStartPosition(int tileX, int tileY) {
        if (mapWidth > 0 && mapHeight > 0) {
            // 타일 좌표를 픽셀 좌표로 변환
            int pixelX = mapOffsetX + tileX * tileWidth * TILE_SCALE;
            int pixelY = mapOffsetY + tileY * tileHeight * TILE_SCALE;

            // 맵 경계 내에 있는지 확인
            int mapPixelWidth = mapWidth * tileWidth * TILE_SCALE;
            int mapPixelHeight = mapHeight * tileHeight * TILE_SCALE;

            int minX = mapOffsetX;
            int minY = mapOffsetY;
            int maxX = mapOffsetX + mapPixelWidth - sprite.getWidth();
            int maxY = mapOffsetY + mapPixelHeight - sprite.getHeight();

            // 경계 내로 제한
            pixelX = Math.max(minX, Math.min(maxX, pixelX));
            pixelY = Math.max(minY, Math.min(maxY, pixelY));

            sprite.setPosition(pixelX, pixelY);
            System.out.println("플레이어 위치 설정: 타일(" + tileX + ", " + tileY + ") -> 픽셀(" + pixelX + ", " + pixelY + ")");
        }
    }

    /// UI 렌더링 (상세한 디버그 정보 포함)
    private void renderUI(Graphics2D g2d) {
        // UI 배경 설정
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // UI 폰트 설정
        Font uiFont = new Font("Arial", Font.BOLD, 12);
        g2d.setFont(uiFont);

        // 메인 정보 패널 (왼쪽 상단)
        renderMainInfoPanel(g2d);

        // 맵 상세 정보 패널 (오른쪽 상단)
        renderMapDetailPanel(g2d);

        // 시스템 정보 패널 (왼쪽 하단)
        renderSystemInfoPanel(g2d);
    }

    /// 메인 정보 패널 (플레이어, 카메라, 기본 맵 정보)
    private void renderMainInfoPanel(Graphics2D g2d) {
        int panelWidth = 280;
        int panelHeight = 100;

        // 반투명 배경 패널
        g2d.setColor(new Color(0, 0, 0, 150));
        g2d.fillRect(10, 10, panelWidth, panelHeight);

        // 패널 테두리
        g2d.setColor(new Color(100, 100, 100));
        g2d.drawRect(10, 10, panelWidth, panelHeight);

        // UI 텍스트 색상
        g2d.setColor(Color.WHITE);

        int yOffset = 25;
        int lineHeight = 16;

        // 플레이어 위치 (픽셀)
        g2d.drawString(String.format("Player Position: (%d, %d)", sprite.getX(), sprite.getY()), 15, yOffset);
        yOffset += lineHeight;

        // 플레이어가 위치한 타일
        int playerTileX = (sprite.getX() - mapOffsetX) / (tileWidth * TILE_SCALE);
        int playerTileY = (sprite.getY() - mapOffsetY) / (tileHeight * TILE_SCALE);
        g2d.drawString(String.format("Player Tile: (%d, %d)", playerTileX, playerTileY), 15, yOffset);
        yOffset += lineHeight;

        // 카메라 위치
        g2d.drawString(String.format("Camera: (%d, %d)", camera.getX(), camera.getY()), 15, yOffset);
        yOffset += lineHeight;

        // 맵 크기 정보
        g2d.drawString(String.format("Map Size: %d x %d tiles", mapWidth, mapHeight), 15, yOffset);
        yOffset += lineHeight;

        // 타일 크기 정보
        g2d.drawString(String.format("Tile Size: %dx%d pixels", tileWidth, tileHeight), 15, yOffset);
    }

    /// 맵 상세 정보 패널 (오른쪽 상단)
    private void renderMapDetailPanel(Graphics2D g2d) {
        int panelWidth = 300;
        int panelHeight = 140;
        int panelX = canvas.getWidth() - panelWidth - 10;
        int panelY = 10;

        // 반투명 배경 패널
        g2d.setColor(new Color(0, 0, 0, 150));
        g2d.fillRect(panelX, panelY, panelWidth, panelHeight);

        // 패널 테두리
        g2d.setColor(new Color(100, 100, 100));
        g2d.drawRect(panelX, panelY, panelWidth, panelHeight);

        // UI 텍스트 색상
        g2d.setColor(Color.CYAN);

        int yOffset = panelY + 15;
        int lineHeight = 14;

        // 맵 픽셀 크기
        int mapPixelWidth = mapWidth * tileWidth * TILE_SCALE;
        int mapPixelHeight = mapHeight * tileHeight * TILE_SCALE;

        g2d.drawString(String.format("Map Pixel Size: %dx%d", mapPixelWidth, mapPixelHeight), panelX + 5, yOffset);
        yOffset += lineHeight;

        g2d.drawString(String.format("Canvas Size: %dx%d", canvas.getWidth(), canvas.getHeight()), panelX + 5, yOffset);
        yOffset += lineHeight;

        g2d.drawString(String.format("Map Offset: X=%d, Y=%d", mapOffsetX, mapOffsetY), panelX + 5, yOffset);
        yOffset += lineHeight;

        g2d.drawString(String.format("Tile Scale: x%d", TILE_SCALE), panelX + 5, yOffset);
        yOffset += lineHeight;

        // 타일셋 정보
        g2d.setColor(Color.YELLOW);
        g2d.drawString(String.format("Tilesets: %d loaded", tilesets.size()), panelX + 5, yOffset);
        yOffset += lineHeight;

        // 레이어 정보
        g2d.drawString(String.format("Layers: %d loaded", layers.size()), panelX + 5, yOffset);
        yOffset += lineHeight;

        // 캐시 정보
        g2d.setColor(Color.LIGHT_GRAY);
        g2d.drawString(String.format("Tileset Cache: %d entries", gidToTilesetCache.size()), panelX + 5, yOffset);
        yOffset += lineHeight;

        g2d.drawString(String.format("Tile Cache: %d entries", globalTileCache.size()), panelX + 5, yOffset);
        yOffset += lineHeight;

        g2d.drawString(String.format("Preloaded Images: %d files", preloadedImages.size()), panelX + 5, yOffset);
    }

    /// 시스템 정보 패널 (왼쪽 하단)
    private void renderSystemInfoPanel(Graphics2D g2d) {
        // 동적으로 패널 높이 계산 (타일셋 개수에 따라)
        int lineHeight = 14;
        int baseHeight = 40; // 기본 높이 (제목 + 여백)
        int tilesetHeight = tilesets.size() * lineHeight; // 타일셋들의 높이
        int keyHeight = keysPressed.isEmpty() ? 0 : lineHeight + 5; // 키 정보 높이

        int panelWidth = 400;
        int panelHeight = baseHeight + tilesetHeight + keyHeight;
        int panelX = 10;
        int panelY = canvas.getHeight() - panelHeight - 10;

        // 반투명 배경 패널
        g2d.setColor(new Color(0, 0, 0, 150));
        g2d.fillRect(panelX, panelY, panelWidth, panelHeight);

        // 패널 테두리
        g2d.setColor(new Color(100, 100, 100));
        g2d.drawRect(panelX, panelY, panelWidth, panelHeight);

        // UI 텍스트 색상
        g2d.setColor(Color.ORANGE);

        int yOffset = panelY + 15;

        // 타일셋 상세 정보 (모든 타일셋 표시)
        if (!tilesets.isEmpty()) {
            g2d.drawString("Tileset Details:", panelX + 5, yOffset);
            yOffset += lineHeight;

            // 모든 타일셋을 다 보여주기
            for (Tileset ts : tilesets) {
                g2d.setColor(Color.WHITE);
                g2d.drawString(String.format("  %s (GID:%d, %dx%d, %d tiles)",
                        ts.name, ts.firstGid, ts.tileWidth, ts.tileHeight, ts.tileCount), panelX + 10, yOffset);
                yOffset += lineHeight;
            }
        }

        // 현재 눌린 키 표시
        if (!keysPressed.isEmpty()) {
            yOffset += 5; // 약간의 여백
            g2d.setColor(Color.GREEN);
            g2d.drawString("Keys Pressed: " + String.join(", ", keysPressed), panelX + 5, yOffset);
        }
    }

    /// 충돌 레이어 설정 (TMX 로드 후 호출)
    private void setupCollisionLayer() {
        // "Buildings" 레이어를 충돌 레이어로 설정
        for (Layer layer : layers) {
            if ("Buildings".equalsIgnoreCase(layer.name)) {
                collisionLayer = layer;
                System.out.println("충돌 레이어 설정됨: " + layer.name);
                break;
            }
        }

        if (collisionLayer == null) {
            System.out.println("충돌 레이어를 찾을 수 없습니다. 모든 이동이 허용됩니다.");
        }
    }

    /// 특정 타일 위치에 충돌 객체가 있는지 확인
    /// @param tileX 타일 X 좌표
    /// @param tileY 타일 Y 좌표
    /// @return 충돌 객체가 있으면 true
    private boolean isTileBlocked(int tileX, int tileY) {
        if (collisionLayer == null) return false;

        // 맵 경계 체크
        if (tileX < 0 || tileY < 0 || tileX >= mapWidth || tileY >= mapHeight) {
            return true; // 맵 밖은 이동 불가
        }

        int index = tileY * collisionLayer.width + tileX;
        if (index >= collisionLayer.data.length) return true;

        int gid = collisionLayer.data[index];
        return gid != 0; // 0이 아니면 충돌 객체 존재
    }

    /// 픽셀 좌표에서 충돌 검사
    /// @param pixelX 픽셀 X 좌표
    /// @param pixelY 픽셀 Y 좌표
    /// @return 충돌하면 true
    private boolean isPixelBlocked(int pixelX, int pixelY) {
        // 맵 오프셋을 고려하여 타일 좌표로 변환
        int adjustedX = pixelX - mapOffsetX;
        int adjustedY = pixelY - mapOffsetY;

        if (adjustedX < 0 || adjustedY < 0) return true;

        int tileX = adjustedX / (tileWidth * TILE_SCALE);
        int tileY = adjustedY / (tileHeight * TILE_SCALE);

        return isTileBlocked(tileX, tileY);
    }

    /// 플레이어의 새로운 위치가 유효한지 검사 (히트박스 고려)
    /// @param newX 새로운 X 좌표
    /// @param newY 새로운 Y 좌표
    /// @return 이동 가능하면 true
    private boolean isValidPlayerPosition(int newX, int newY) {
        if (collisionLayer == null) return true;

        int playerWidth = sprite.getWidth();
        int playerHeight = sprite.getHeight();

        // 발의 중앙 지점만 검사
        int footCenterX = newX + playerWidth / 2;
        int footY = newY + playerHeight - 23;

        return !isPixelBlocked(footCenterX, footY);
    }

    /// 충돌 영역을 빨간색 반투명 박스로 표시하는 메서드
    private void renderCollisionDebug(Graphics2D g2d) {
        if (!showCollisionDebug || collisionLayer == null) return;

        // 반투명 빨간색 설정
        g2d.setColor(new Color(255, 0, 0, 120));

        int scaledTileWidth = tileWidth * TILE_SCALE;
        int scaledTileHeight = tileHeight * TILE_SCALE;

        // 맵이 화면보다 큰 경우 (카메라 사용)
        int mapPixelWidth = mapWidth * tileWidth * TILE_SCALE;
        int mapPixelHeight = mapHeight * tileHeight * TILE_SCALE;

        if (mapPixelWidth > canvas.getWidth() || mapPixelHeight > canvas.getHeight()) {
            // 화면에 보이는 타일 범위만 계산 (성능 최적화)
            int startTileX = Math.max(0, camera.getX() / scaledTileWidth);
            int startTileY = Math.max(0, camera.getY() / scaledTileHeight);
            int endTileX = Math.min(mapWidth - 1, (camera.getX() + camera.getViewWidth()) / scaledTileWidth + 1);
            int endTileY = Math.min(mapHeight - 1, (camera.getY() + camera.getViewHeight()) / scaledTileHeight + 1);

            // 화면에 보이는 충돌 타일만 렌더링
            for (int y = startTileY; y <= endTileY; y++) {
                for (int x = startTileX; x <= endTileX; x++) {
                    int index = y * collisionLayer.width + x;
                    if (index >= collisionLayer.data.length) continue;

                    int gid = collisionLayer.data[index];
                    if (gid != 0) { // 충돌 타일인 경우
                        // 월드 좌표
                        int worldX = x * scaledTileWidth;
                        int worldY = y * scaledTileHeight;

                        // 화면 좌표로 변환
                        int screenX = camera.worldToScreenX(worldX);
                        int screenY = camera.worldToScreenY(worldY);

                        // 빨간색 반투명 박스 그리기
                        g2d.fillRect(screenX, screenY, scaledTileWidth, scaledTileHeight);

                        // 테두리 그리기 (선택사항)
                        g2d.setColor(new Color(255, 0, 0, 200));
                        g2d.drawRect(screenX, screenY, scaledTileWidth, scaledTileHeight);
                        g2d.setColor(new Color(255, 0, 0, 120));
                    }
                }
            }
        } else {
            // 맵이 화면보다 작은 경우 (고정된 중앙 위치)
            for (int y = 0; y < mapHeight; y++) {
                for (int x = 0; x < mapWidth; x++) {
                    int index = y * collisionLayer.width + x;
                    if (index >= collisionLayer.data.length) continue;

                    int gid = collisionLayer.data[index];
                    if (gid != 0) { // 충돌 타일인 경우
                        // 중앙 정렬된 위치에 충돌 박스 렌더링
                        int screenX = mapOffsetX + x * scaledTileWidth;
                        int screenY = mapOffsetY + y * scaledTileHeight;

                        // 빨간색 반투명 박스 그리기
                        g2d.fillRect(screenX, screenY, scaledTileWidth, scaledTileHeight);

                        // 테두리 그리기 (선택사항)
                        g2d.setColor(new Color(255, 0, 0, 200));
                        g2d.drawRect(screenX, screenY, scaledTileWidth, scaledTileHeight);
                        g2d.setColor(new Color(255, 0, 0, 120));
                    }
                }
            }
        }
    }

    public Camera getCamera() { return camera; }           /// 카메라 객체 반환
    public SpriteRenderer getSprite() { return sprite; }   /// 스프라이트 객체 반환
    public int getMapWidth() { return mapWidth; }          /// 맵 가로 크기 반환 (타일 개수)
    public int getMapHeight() { return mapHeight; }        /// 맵 세로 크기 반환 (타일 개수)
    public int getTileWidth() { return tileWidth; }        /// 타일 가로 크기 반환 (픽셀)
    public int getTileHeight() { return tileHeight; }      /// 타일 세로 크기 반환 (픽셀)
    public int getMapOffsetX() { return mapOffsetX; }      /// 맵 X 오프셋 반환
    public int getMapOffsetY() { return mapOffsetY; }      /// 맵 Y 오프셋 반환
}