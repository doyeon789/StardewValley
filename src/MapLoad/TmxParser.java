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
import java.io.IOException;
import java.util.*;
import java.util.List;

import Character.SpriteRenderer;
import Character.Camera;

public class TmxParser {

    // 레이어 렌더링 순서 정의 (낮은 숫자가 먼저 렌더링)
    private static final Map<String, Integer> LAYER_ORDER = new HashMap<String, Integer>() {{
        put("Back", 0);
        put("Buildings", 1);
        put("Paths", 2);
        put("Front", 3);
        put("AlwaysFront", 4);
    }};

    static class Tileset {
        int firstGid;
        String name;
        int tileWidth, tileHeight, tileCount, columns;
        String imagePath;
        BufferedImage image;
        Map<Integer, BufferedImage> tileCache = new HashMap<>();
    }

    static class Layer {
        String name;
        String layerType; // 정규화된 레이어 타입 (BACK, BUILDINGS, 등)
        int renderOrder;  // 렌더링 순서
        int width, height;
        int[] data;
        boolean visible = true;
    }

    private class TileMapCanvas extends JComponent {
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            renderTileMapWithCamera(g);
        }
    }

    private int mapWidth, mapHeight, tileWidth, tileHeight;
    private final List<Tileset> tilesets = new ArrayList<>();
    private final List<Layer> layers = new ArrayList<>();
    private final Map<String, BufferedImage> preloadedImages = new HashMap<>();
    private final Map<Integer, Tileset> gidToTilesetCache = new HashMap<>();
    private final Map<Integer, BufferedImage> globalTileCache = new HashMap<>();

    private final JFrame frame;
    private final TileMapCanvas canvas;
    private final SpriteRenderer sprite;
    private final Camera camera;

    private static final int TILE_SCALE = 3;
    private static final int GAME_FPS = 60;
    private static final int MOVE_SPEED = 5;

    private final Set<String> keysPressed = new HashSet<>();
    private int mapOffsetX = 0, mapOffsetY = 0;
    private Map<Integer, BufferedImage> tileOverrides = new HashMap<>();

    // 맵 전환 시스템
    private static class MapTransition {
        String targetMapPath;
        int triggerTileX, triggerTileY;
        int destinationTileX, destinationTileY;

        MapTransition(String targetMapPath, int triggerX, int triggerY, int destX, int destY) {
            this.targetMapPath = targetMapPath;
            this.triggerTileX = triggerX;
            this.triggerTileY = triggerY;
            this.destinationTileX = destX;
            this.destinationTileY = destY;
        }
    }

    private String currentMapPath = "";
    private List<MapTransition> mapTransitions = new ArrayList<>();

    // 충돌 검사 시스템
    private Layer collisionLayer = null;
    private boolean showCollisionDebug = true;

    public TmxParser() {
        preloadAllPngImages();
        camera = new Camera(1200, 780);
        sprite = new SpriteRenderer();

        frame = new JFrame("TMX 타일맵 뷰어 (부드러운 이동)");
        frame.setResizable(false);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        canvas = new TileMapCanvas();
        canvas.setPreferredSize(new Dimension(1200, 780));
        canvas.setBackground(Color.BLACK);
        canvas.setOpaque(true);
        canvas.setFocusable(true);

        // 키 입력 처리
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
        startGameLoop();
    }

    private void startGameLoop() {
        javax.swing.Timer gameTimer = new javax.swing.Timer(1000 / GAME_FPS, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                updateMovement();
                canvas.repaint();
            }
        });
        gameTimer.start();
    }

    private void updateMovement() {
        if (keysPressed.isEmpty()) return;

        int newX = sprite.getX();
        int newY = sprite.getY();
        boolean moved = false;

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

        if (moved) {
            int mapPixelWidth = mapWidth * tileWidth * TILE_SCALE;
            int mapPixelHeight = mapHeight * tileHeight * TILE_SCALE;

            int minX = mapOffsetX;
            int minY = mapOffsetY;
            int maxX = mapOffsetX + mapPixelWidth - sprite.getWidth();
            int maxY = mapOffsetY + mapPixelHeight - sprite.getHeight();

            if (newX >= minX && newX <= maxX && newY >= minY && newY <= maxY) {
                sprite.setPosition(newX, newY);
                checkMapTransition(newX, newY);
            }
        }
    }

    private void calculateMapOffset() {
        int mapPixelWidth = mapWidth * tileWidth * TILE_SCALE;
        int mapPixelHeight = mapHeight * tileHeight * TILE_SCALE;

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

    public void loadDefaultTmxFile(String Mappath) {
        File file = new File(Mappath);
        if (file.exists()) {
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

    public void show() {
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        SwingUtilities.invokeLater(() -> {
            if (mapWidth > 0 && mapHeight > 0) {
                calculateMapOffset();
            }
            canvas.requestFocusInWindow();
            canvas.repaint();
        });
    }

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

        List<File> pngFiles = new ArrayList<>();
        collectPngFiles(resourceDir, pngFiles);

        for (File pngFile : pngFiles) {
            try {
                BufferedImage image = ImageIO.read(pngFile);
                String fileName = pngFile.getName();
                String fileNameWithoutExt = fileName.substring(0, fileName.lastIndexOf('.'));

                preloadedImages.put(fileName, image);
                preloadedImages.put(fileNameWithoutExt, image);

            } catch (Exception e) {
                System.err.println("PNG 파일 로드 실패: " + pngFile.getName() + " - " + e.getMessage());
            }
        }

        System.out.println("총 " + pngFiles.size() + "개의 PNG 파일이 resource 디렉토리에서 로드되었습니다.");
    }

    private void collectPngFiles(File dir, List<File> pngFiles) {
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                collectPngFiles(file, pngFiles);
            } else if (file.getName().toLowerCase().endsWith(".png")) {
                pngFiles.add(file);
            }
        }
    }

    private BufferedImage findImageByName(String imagePath) {
        String fileName = new File(imagePath).getName();

        BufferedImage image = preloadedImages.get(fileName);
        if (image != null) {
            return image;
        }

        if (fileName.contains(".")) {
            String nameWithoutExt = fileName.substring(0, fileName.lastIndexOf('.'));
            image = preloadedImages.get(nameWithoutExt);
            if (image != null) {
                return image;
            }
        }

        if (!fileName.contains(".")) {
            image = preloadedImages.get(fileName + ".png");
            return image;
        }

        return null;
    }

    /**
     * 레이어 이름을 정규화하고 렌더링 순서를 결정
     * AlwaysFront2 -> ALWAYSFRONT, Front3 -> FRONT 등으로 변환
     */
    private String normalizeLayerName(String layerName) {
        if (layerName == null) return "UNKNOWN";

        String normalized = layerName.toUpperCase();

        // 숫자 제거 (AlwaysFront2 -> ALWAYSFRONT)
        normalized = normalized.replaceAll("\\d+$", "");

        return normalized;
    }

    /**
     * 정규화된 레이어 이름으로 렌더링 순서 가져오기
     */
    private int getLayerRenderOrder(String normalizedLayerName) {
        return LAYER_ORDER.getOrDefault(normalizedLayerName, 999); // 알 수 없는 레이어는 맨 뒤
    }

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

            calculateMapOffset();

            int mapPixelWidth = mapWidth * tileWidth * TILE_SCALE;
            int mapPixelHeight = mapHeight * tileHeight * TILE_SCALE;
            camera.setMapBounds(mapPixelWidth, mapPixelHeight);

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

            // 레이어 파싱 및 정렬
            NodeList layerNodes = doc.getElementsByTagName("layer");
            for (int i = 0; i < layerNodes.getLength(); i++) {
                Element layerElement = (Element) layerNodes.item(i);
                Layer layer = new Layer();

                layer.name = layerElement.getAttribute("name");
                layer.layerType = normalizeLayerName(layer.name);
                layer.renderOrder = getLayerRenderOrder(layer.layerType);
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
                System.out.println("레이어 추가됨: " + layer.name + " -> " + layer.layerType +
                        " (순서: " + layer.renderOrder + ", " + layer.width + "x" + layer.height + ")");
            }

            // 레이어를 렌더링 순서대로 정렬
            layers.sort(Comparator.comparingInt(layer -> layer.renderOrder));
            System.out.println("레이어 정렬 완료 (렌더링 순서: Back -> Buildings -> Paths -> Front -> AlwaysFront)");

            buildTilesetCache();
            preloadTileImages();
            setupCollisionLayer();

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

    private void buildTilesetCache() {
        for (Tileset tileset : tilesets) {
            for (int i = 0; i < tileset.tileCount; i++) {
                int gid = tileset.firstGid + i;
                gidToTilesetCache.put(gid, tileset);
            }
        }
        System.out.println("타일셋 캐시 구축 완료: " + gidToTilesetCache.size() + " entries");
    }

    private void preloadTileImages() {
        new Thread(() -> {
            System.out.println("타일 이미지 캐싱 시작...");
            int cachedCount = 0;

            Set<Integer> uniqueGids = new HashSet<>();
            for (Layer layer : layers) {
                if (!layer.visible) continue;
                for (int gid : layer.data) if (gid > 0) uniqueGids.add(gid);
            }

            for (int gid : uniqueGids) {
                BufferedImage tileImage = createTileImage(gid);
                if (tileImage != null) {
                    globalTileCache.put(gid, tileImage);
                    cachedCount++;
                }
            }

            System.out.println("타일 이미지 캐싱 완료: " + cachedCount + " tiles");
            SwingUtilities.invokeLater(canvas::repaint);
        }).start();
    }

    private Tileset findTilesetForGid(int gid) {
        return gidToTilesetCache.get(gid);
    }

    private BufferedImage createTileImage(int gid) {
        if (gid == 0) return null;

        Tileset tileset = findTilesetForGid(gid);
        if (tileset == null || tileset.image == null) return null;

        BufferedImage cachedTile = tileset.tileCache.get(gid);
        if (cachedTile != null) return cachedTile;

        int tileId = gid - tileset.firstGid;
        int tilesPerRow = tileset.columns;
        int tileX = (tileId % tilesPerRow) * tileset.tileWidth;
        int tileY = (tileId / tilesPerRow) * tileset.tileHeight;

        try {
            BufferedImage tileImage = tileset.image.getSubimage(tileX, tileY, tileset.tileWidth, tileset.tileHeight);
            tileset.tileCache.put(gid, tileImage);
            return tileImage;
        } catch (Exception e) {
            return null;
        }
    }

    private BufferedImage getTileImage(int gid) {
        if (gid == 0) return null;

        BufferedImage cachedImage = globalTileCache.get(gid);
        if (cachedImage != null) return cachedImage;

        return createTileImage(gid);
    }

    /**
     * 레이어 순서에 따른 렌더링 시스템
     * 순서: Back -> Buildings -> Paths -> [플레이어] -> Front -> AlwaysFront
     */
    private void renderTileMapWithCamera(Graphics g) {
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        g2d.setColor(Color.BLACK);
        g2d.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());

        int mapPixelWidth = mapWidth * tileWidth * TILE_SCALE;
        int mapPixelHeight = mapHeight * tileHeight * TILE_SCALE;

        if (mapPixelWidth > canvas.getWidth() || mapPixelHeight > canvas.getHeight()) {
            camera.followPlayer(sprite);

            int scaledTileWidth = tileWidth * TILE_SCALE;
            int scaledTileHeight = tileHeight * TILE_SCALE;

            int startTileX = Math.max(0, camera.getX() / scaledTileWidth);
            int startTileY = Math.max(0, camera.getY() / scaledTileHeight);
            int endTileX = Math.min(mapWidth - 1, (camera.getX() + camera.getViewWidth()) / scaledTileWidth + 1);
            int endTileY = Math.min(mapHeight - 1, (camera.getY() + camera.getViewHeight()) / scaledTileHeight + 1);

            // 플레이어 뒤쪽 레이어들 렌더링 (Back, Buildings, Paths)
            renderLayersWithCamera(g2d, startTileX, startTileY, endTileX, endTileY, scaledTileWidth, scaledTileHeight, false);

            // 플레이어 렌더링
            renderPlayerWithCamera(g2d);

            // 플레이어 앞쪽 레이어들 렌더링 (Front, AlwaysFront)
            renderLayersWithCamera(g2d, startTileX, startTileY, endTileX, endTileY, scaledTileWidth, scaledTileHeight, true);
        } else {
            int scaledTileWidth = tileWidth * TILE_SCALE;
            int scaledTileHeight = tileHeight * TILE_SCALE;

            renderLayersFixed(g2d, scaledTileWidth, scaledTileHeight, false);
            sprite.render(g2d);
            renderLayersFixed(g2d, scaledTileWidth, scaledTileHeight, true);
        }

        renderUI(g2d);
    }

    /**
     * 카메라 모드에서 레이어 렌더링
     * @param frontLayersOnly true면 Front/AlwaysFront만, false면 Back/Buildings/Paths만
     */
    private void renderLayersWithCamera(Graphics2D g2d, int startTileX, int startTileY, int endTileX, int endTileY,
                                        int scaledTileWidth, int scaledTileHeight, boolean frontLayersOnly) {
        for (Layer layer : layers) {
            if (!layer.visible) continue;

            boolean isFrontLayer = layer.layerType.equals("FRONT") || layer.layerType.equals("ALWAYSFRONT");

            if (frontLayersOnly && !isFrontLayer) continue;
            if (!frontLayersOnly && isFrontLayer) continue;

            for (int y = startTileY; y <= endTileY; y++) {
                for (int x = startTileX; x <= endTileX; x++) {
                    int index = y * layer.width + x;
                    int gid = layer.data[index];
                    if (gid == 0) continue;
                    g2d.drawImage(globalTileCache.get(gid), camera.worldToScreenX(x * scaledTileWidth),
                            camera.worldToScreenY(y * scaledTileHeight), scaledTileWidth, scaledTileHeight, null);
                }
            }
        }
    }

    private void renderLayersFixed(Graphics2D g2d, int scaledTileWidth, int scaledTileHeight, boolean frontLayersOnly) {
        for (Layer layer : layers) {
            if (!layer.visible) continue;

            boolean isFrontLayer = layer.layerType.equals("FRONT") || layer.layerType.equals("ALWAYSFRONT");

            if (frontLayersOnly && !isFrontLayer) continue;
            if (!frontLayersOnly && isFrontLayer) continue;

            for (int y = 0; y < mapHeight; y++) {
                for (int x = 0; x < mapWidth; x++) {
                    int index = y * layer.width + x;
                    if (index >= layer.data.length) continue;

                    int gid = layer.data[index];
                    if (gid == 0) continue;

                    BufferedImage tileImage = getTileImage(gid);
                    if (tileImage != null) {
                        int screenX = mapOffsetX + x * scaledTileWidth;
                        int screenY = mapOffsetY + y * scaledTileHeight;
                        g2d.drawImage(tileImage, screenX, screenY, scaledTileWidth, scaledTileHeight, null);
                    }
                }
            }
        }
    }

    private void renderPlayerWithCamera(Graphics2D g2d) {
        int playerScreenX = camera.worldToScreenX(sprite.getX());
        int playerScreenY = camera.worldToScreenY(sprite.getY());

        int originalX = sprite.getX();
        int originalY = sprite.getY();

        sprite.setPosition(playerScreenX, playerScreenY);
        sprite.render(g2d);
        sprite.setPosition(originalX, originalY);
    }

    public void setPlayerStartPosition(int tileX, int tileY) {
        if (mapWidth > 0 && mapHeight > 0) {
            int pixelX = mapOffsetX + tileX * tileWidth * TILE_SCALE;
            int pixelY = mapOffsetY + tileY * tileHeight * TILE_SCALE;

            int mapPixelWidth = mapWidth * tileWidth * TILE_SCALE;
            int mapPixelHeight = mapHeight * tileHeight * TILE_SCALE;

            int minX = mapOffsetX;
            int minY = mapOffsetY;
            int maxX = mapOffsetX + mapPixelWidth - sprite.getWidth();
            int maxY = mapOffsetY + mapPixelHeight - sprite.getHeight();

            pixelX = Math.max(minX, Math.min(maxX, pixelX));
            pixelY = Math.max(minY, Math.min(maxY, pixelY));

            sprite.setPosition(pixelX, pixelY);
            System.out.println("플레이어 위치 설정: 타일(" + tileX + ", " + tileY + ") -> 픽셀(" + pixelX + ", " + pixelY + ")");
        }
    }

    private void renderUI(Graphics2D g2d) {
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        Font uiFont = new Font("Arial", Font.BOLD, 12);
        g2d.setFont(uiFont);

        renderMainInfoPanel(g2d);
        renderMapDetailPanel(g2d);
        renderSystemInfoPanel(g2d);
    }

    private void renderMainInfoPanel(Graphics2D g2d) {
        int panelWidth = 280;
        int panelHeight = 100;

        g2d.setColor(new Color(0, 0, 0, 150));
        g2d.fillRect(10, 10, panelWidth, panelHeight);

        g2d.setColor(new Color(100, 100, 100));
        g2d.drawRect(10, 10, panelWidth, panelHeight);

        g2d.setColor(Color.WHITE);

        int yOffset = 25;
        int lineHeight = 16;

        g2d.drawString(String.format("Player Position: (%d, %d)", sprite.getX(), sprite.getY()), 15, yOffset);
        yOffset += lineHeight;

        int playerTileX = (sprite.getX() - mapOffsetX) / (tileWidth * TILE_SCALE);
        int playerTileY = (sprite.getY() - mapOffsetY) / (tileHeight * TILE_SCALE);
        g2d.drawString(String.format("Player Tile: (%d, %d)", playerTileX, playerTileY), 15, yOffset);
        yOffset += lineHeight;

        g2d.drawString(String.format("Camera: (%d, %d)", camera.getX(), camera.getY()), 15, yOffset);
        yOffset += lineHeight;

        g2d.drawString(String.format("Map Size: %d x %d tiles", mapWidth, mapHeight), 15, yOffset);
        yOffset += lineHeight;

        g2d.drawString(String.format("Tile Size: %dx%d pixels", tileWidth, tileHeight), 15, yOffset);
    }

    private void renderMapDetailPanel(Graphics2D g2d) {
        int panelWidth = 300;
        int panelHeight = 120;
        int panelX = canvas.getWidth() - panelWidth - 10;
        int panelY = 10;

        g2d.setColor(new Color(0, 0, 0, 150));
        g2d.fillRect(panelX, panelY, panelWidth, panelHeight);

        g2d.setColor(new Color(100, 100, 100));
        g2d.drawRect(panelX, panelY, panelWidth, panelHeight);

        g2d.setColor(Color.CYAN);

        int yOffset = panelY + 15;
        int lineHeight = 14;

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

        g2d.setColor(Color.YELLOW);
        g2d.drawString(String.format("Tilesets: %d, Layers: %d", tilesets.size(), layers.size()), panelX + 5, yOffset);
        yOffset += lineHeight;

        g2d.setColor(Color.LIGHT_GRAY);
        g2d.drawString(String.format("Cache: %d tilesets, %d tiles", gidToTilesetCache.size(), globalTileCache.size()), panelX + 5, yOffset);
    }

    private void renderSystemInfoPanel(Graphics2D g2d) {
        int lineHeight = 14;
        int baseHeight = 40;
        int layerHeight = layers.size() * lineHeight;
        int keyHeight = keysPressed.isEmpty() ? 0 : lineHeight + 5;

        int panelWidth = 400;
        int panelHeight = baseHeight + layerHeight + keyHeight;
        int panelX = 10;
        int panelY = canvas.getHeight() - panelHeight - 10;

        g2d.setColor(new Color(0, 0, 0, 150));
        g2d.fillRect(panelX, panelY, panelWidth, panelHeight);

        g2d.setColor(new Color(100, 100, 100));
        g2d.drawRect(panelX, panelY, panelWidth, panelHeight);

        g2d.setColor(Color.ORANGE);

        int yOffset = panelY + 15;

        if (!layers.isEmpty()) {
            g2d.drawString("Layer Order (rendering sequence):", panelX + 5, yOffset);
            yOffset += lineHeight;

            for (Layer layer : layers) {
                g2d.setColor(Color.WHITE);
                g2d.drawString(String.format("  %s (%s) - Order: %d",
                        layer.name, layer.layerType, layer.renderOrder), panelX + 10, yOffset);
                yOffset += lineHeight;
            }
        }

        if (!keysPressed.isEmpty()) {
            yOffset += 5;
            g2d.setColor(Color.GREEN);
            g2d.drawString("Keys Pressed: " + String.join(", ", keysPressed), panelX + 5, yOffset);
        }
    }

    private void setupCollisionLayer() {
        for (Layer layer : layers) {
            if ("BUILDINGS".equals(layer.layerType)) {
                collisionLayer = layer;
                System.out.println("충돌 레이어 설정됨: " + layer.name + " (" + layer.layerType + ")");
                break;
            }
        }

        if (collisionLayer == null) {
            System.out.println("충돌 레이어를 찾을 수 없습니다. 모든 이동이 허용됩니다.");
        }
    }

    private boolean isTileBlocked(int tileX, int tileY) {
        if (collisionLayer == null) return false;

        if (tileX < 0 || tileY < 0 || tileX >= mapWidth || tileY >= mapHeight) {
            return true;
        }

        int index = tileY * collisionLayer.width + tileX;
        if (index >= collisionLayer.data.length) return true;

        int gid = collisionLayer.data[index];
        return gid != 0;
    }

    private boolean isPixelBlocked(int pixelX, int pixelY) {
        int adjustedX = pixelX - mapOffsetX;
        int adjustedY = pixelY - mapOffsetY;

        if (adjustedX < 0 || adjustedY < 0) return true;

        int tileX = adjustedX / (tileWidth * TILE_SCALE);
        int tileY = adjustedY / (tileHeight * TILE_SCALE);

        return isTileBlocked(tileX, tileY);
    }

    private boolean isValidPlayerPosition(int newX, int newY) {
        if (collisionLayer == null) return true;

        int playerWidth = sprite.getWidth();
        int playerHeight = sprite.getHeight();

        // 플레이어 히트박스 (발 부분 중심)
        int hitboxTopOffset = playerHeight - 23;
        int hitboxBottomOffset = playerHeight - 1;
        int hitboxLeftOffset = 4;
        int hitboxRightOffset = playerWidth - 4;

        int hitboxLeft = newX + hitboxLeftOffset;
        int hitboxRight = newX + hitboxRightOffset;
        int hitboxTop = newY + hitboxTopOffset;
        int hitboxBottom = newY + hitboxBottomOffset;

        int[][] corners = {
                {hitboxLeft, hitboxBottom},
                {hitboxRight, hitboxBottom},
                {hitboxLeft, hitboxTop},
                {hitboxRight, hitboxTop}
        };

        for (int[] corner : corners) {
            if (isPixelBlocked(corner[0], corner[1])) return false;
        }
        return true;
    }

    // 맵 전환 관련 메서드들
    public void addMapTransition(String fromMap, int triggerX, int triggerY,
                                 String toMap, int destX, int destY) {
        mapTransitions.add(new MapTransition(toMap, triggerX, triggerY, destX, destY));
        System.out.println("맵 전환 추가: " + fromMap + "(" + triggerX + "," + triggerY +
                ") -> " + toMap + "(" + destX + "," + destY + ")");
    }

    private void checkMapTransition(int playerX, int playerY) {
        int playerTileX = (playerX - mapOffsetX) / (tileWidth * TILE_SCALE);
        int playerTileY = (playerY - mapOffsetY) / (tileHeight * TILE_SCALE);

        for (MapTransition transition : mapTransitions) {
            if (playerTileX == transition.triggerTileX &&
                    playerTileY == transition.triggerTileY) {

                System.out.println("맵 전환 트리거 발동: (" + playerTileX + "," + playerTileY +
                        ") -> " + transition.targetMapPath);

                switchToMap(transition.targetMapPath,
                        transition.destinationTileX,
                        transition.destinationTileY);
                break;
            }
        }
    }

    private void switchToMap(String targetMapPath, int destinationTileX, int destinationTileY) {
        System.out.println("맵 전환 시작: " + currentMapPath + " -> " + targetMapPath);

        if (loadTMX(targetMapPath)) {
            currentMapPath = targetMapPath;
            setPlayerStartPosition(destinationTileX, destinationTileY);

            String mapName = extractMapName(targetMapPath);
            frame.setTitle("TMX 타일맵 뷰어 - " + mapName);

            System.out.println("맵 전환 완료: " + mapName +
                    " 위치(" + destinationTileX + "," + destinationTileY + ")");

            SwingUtilities.invokeLater(() -> {
                canvas.revalidate();
                canvas.repaint();
            });

        } else {
            System.err.println("맵 전환 실패: " + targetMapPath);
        }
    }

    // 다중 TMX 파일 미리 로드
    public void preloadMultipleTmxFiles(String[] tmxPaths) {
        System.out.println("=== 다중 TMX 파일 미리 로드 시작 ===");

        for (String tmxPath : tmxPaths) {
            File file = new File(tmxPath);
            if (file.exists()) {
                System.out.println("미리 로드 중: " + tmxPath);
                preloadTmxData(tmxPath);
            } else {
                System.err.println("파일을 찾을 수 없음: " + tmxPath);
            }
        }

        System.out.println("=== 다중 TMX 파일 미리 로드 완료 ===");
        System.out.println("총 캐시된 타일 이미지: " + globalTileCache.size());
    }

    private void preloadTmxData(String tmxPath) {
        try {
            List<Tileset> tempTilesets = new ArrayList<>();
            List<Layer> tempLayers = new ArrayList<>();

            File tmxFile = new File(tmxPath);

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(tmxFile);
            doc.getDocumentElement().normalize();

            // 맵 속성 파싱
            Element mapElement = doc.getDocumentElement();
            int tempMapWidth = Integer.parseInt(mapElement.getAttribute("width"));
            int tempMapHeight = Integer.parseInt(mapElement.getAttribute("height"));
            int tempTileWidth = Integer.parseInt(mapElement.getAttribute("tilewidth"));
            int tempTileHeight = Integer.parseInt(mapElement.getAttribute("tileheight"));

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

                NodeList imageNodes = tilesetElement.getElementsByTagName("image");
                if (imageNodes.getLength() > 0) {
                    Element imageElement = (Element) imageNodes.item(0);
                    tileset.imagePath = imageElement.getAttribute("source");
                    tileset.image = findImageByName(tileset.imagePath);

                    if (tileset.image != null) {
                        System.out.println("  타일셋 이미지 연결됨: " + tileset.imagePath);
                    } else {
                        System.err.println("  타일셋 이미지를 찾을 수 없습니다: " + tileset.imagePath);
                    }
                }

                tempTilesets.add(tileset);
            }

            // 레이어 파싱
            NodeList layerNodes = doc.getElementsByTagName("layer");
            for (int i = 0; i < layerNodes.getLength(); i++) {
                Element layerElement = (Element) layerNodes.item(i);
                Layer layer = new Layer();

                layer.name = layerElement.getAttribute("name");
                layer.layerType = normalizeLayerName(layer.name);
                layer.renderOrder = getLayerRenderOrder(layer.layerType);
                layer.width = Integer.parseInt(layerElement.getAttribute("width"));
                layer.height = Integer.parseInt(layerElement.getAttribute("height"));

                String opacity = layerElement.getAttribute("opacity");
                if (!opacity.isEmpty() && Float.parseFloat(opacity) == 0.0f) {
                    layer.visible = false;
                }

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

                tempLayers.add(layer);
            }

            cacheTilesFromLayers(tempTilesets, tempLayers);

            System.out.println("  완료: " + extractMapName(tmxPath) + " (" + tempTilesets.size() +
                    " tilesets, " + tempLayers.size() + " layers)");

        } catch (Exception e) {
            System.err.println("TMX 미리 로드 실패: " + tmxPath + " - " + e.getMessage());
        }
    }

    private void cacheTilesFromLayers(List<Tileset> tempTilesets, List<Layer> tempLayers) {
        Map<Integer, Tileset> tempGidToTilesetCache = new HashMap<>();
        for (Tileset tileset : tempTilesets) {
            for (int i = 0; i < tileset.tileCount; i++) {
                int gid = tileset.firstGid + i;
                tempGidToTilesetCache.put(gid, tileset);
            }
        }

        int cachedCount = 0;

        for (Layer layer : tempLayers) {
            if (!layer.visible) continue;

            Set<Integer> uniqueGids = new HashSet<>();
            for (int gid : layer.data) {
                if (gid > 0) uniqueGids.add(gid);
            }

            for (int gid : uniqueGids) {
                if (!globalTileCache.containsKey(gid)) {
                    BufferedImage tileImage = createTileImageFromTilesets(gid, tempGidToTilesetCache);
                    if (tileImage != null) {
                        globalTileCache.put(gid, tileImage);
                        cachedCount++;
                    }
                }
            }
        }

        System.out.println("    새로 캐시된 타일: " + cachedCount + "개");
    }

    private BufferedImage createTileImageFromTilesets(int gid, Map<Integer, Tileset> tempGidToTilesetCache) {
        if (gid == 0) return null;

        if (tileOverrides.containsKey(gid)) {
            return tileOverrides.get(gid);
        }

        Tileset tileset = tempGidToTilesetCache.get(gid);
        if (tileset == null || tileset.image == null) return null;

        int tileId = gid - tileset.firstGid;
        int tilesPerRow = tileset.columns;
        int tileX = (tileId % tilesPerRow) * tileset.tileWidth;
        int tileY = (tileId / tilesPerRow) * tileset.tileHeight;

        try {
            return tileset.image.getSubimage(tileX, tileY, tileset.tileWidth, tileset.tileHeight);
        } catch (Exception e) {
            return null;
        }
    }

    // Getter 메서드들
    public void setCurrentMapPath(String mapPath) {
        this.currentMapPath = mapPath;
    }

    public String extractMapName(String filePath) {
        String fileName = filePath.substring(filePath.lastIndexOf('/') + 1);
        if (fileName.contains(".")) {
            fileName = fileName.substring(0, fileName.lastIndexOf('.'));
        }
        return fileName;
    }

    public Camera getCamera() { return camera; }
    public SpriteRenderer getSprite() { return sprite; }
    public int getMapWidth() { return mapWidth; }
    public int getMapHeight() { return mapHeight; }
    public int getTileWidth() { return tileWidth; }
    public int getTileHeight() { return tileHeight; }
    public int getMapOffsetX() { return mapOffsetX; }
    public int getMapOffsetY() { return mapOffsetY; }
    public JFrame getFrame() { return frame; }
    public TileMapCanvas getCanvas() { return canvas; }

    public void clearCurrentMap() {
        tilesets.clear();
        layers.clear();
        gidToTilesetCache.clear();
        globalTileCache.clear();

        mapWidth = 0;
        mapHeight = 0;
        tileWidth = 0;
        tileHeight = 0;

        collisionLayer = null;

        SwingUtilities.invokeLater(() -> {
            canvas.revalidate();
            canvas.repaint();
        });
    }
}