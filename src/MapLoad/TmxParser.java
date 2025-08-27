package MapLoad;

import javax.swing.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.File;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import javax.swing.Timer;
import java.util.stream.Collectors;

import Character.SpriteRenderer;
import Character.Camera;

public class TmxParser {
    // Constants
    private static final Map<String, Integer> LAYER_ORDER = Map.of(
            "BACK", 0,
            "BUILDINGS", 1,
            "PATHS", 2,
            "FRONT", 3,
            "ALWAYSFRONT", 4
    );

    private static final int TILE_SCALE = 3;
    private static final int GAME_FPS = 60;
    private static final int MOVE_SPEED = 5;

    // Inner Classes
    static class Tileset {
        int firstGid, tileWidth, tileHeight, tileCount, columns;
        String name, imagePath;
        BufferedImage image;
        final Map<Integer, BufferedImage> tileCache = new HashMap<>();
    }

    static class Layer {
        String name, layerType;
        int renderOrder, width, height;
        int[] data;
        boolean visible = true;
    }

    private static class MapTransition {
        final String targetMapPath;
        final int triggerTileX, triggerTileY, destinationTileX, destinationTileY;

        MapTransition(String targetMapPath, int triggerX, int triggerY, int destX, int destY) {
            this.targetMapPath = targetMapPath;
            this.triggerTileX = triggerX;
            this.triggerTileY = triggerY;
            this.destinationTileX = destX;
            this.destinationTileY = destY;
        }
    }

    public static class PathTileCustomization {
        final String imagePath;
        final int targetTileIndex, tileWidth, tileHeight, offsetX, offsetY;
        final RenderMode renderMode;
        final boolean isGrass;

        public enum RenderMode { STRETCH, ASPECT_FIT, ASPECT_FILL, ORIGINAL_SIZE, CENTER }

        PathTileCustomization(String imagePath, int targetTileIndex, int tileWidth, int tileHeight,
                              RenderMode renderMode, boolean isGrass) {
            this(imagePath, targetTileIndex, tileWidth, tileHeight, renderMode, 0, 0, isGrass);
        }

        PathTileCustomization(String imagePath, int targetTileIndex, int tileWidth, int tileHeight,
                              RenderMode renderMode, int offsetX, int offsetY, boolean isGrass) {
            this.imagePath = imagePath;
            this.targetTileIndex = targetTileIndex;
            this.tileWidth = tileWidth;
            this.tileHeight = tileHeight;
            this.renderMode = renderMode;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.isGrass = isGrass;
        }
    }

    private class TileMapCanvas extends JComponent {
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            renderTileMapWithCamera(g);
        }
    }

    // Fields
    private int mapWidth, mapHeight, tileWidth, tileHeight;
    private int mapOffsetX = 0, mapOffsetY = 0;

    private final List<Tileset> tilesets = new ArrayList<>();
    private final List<Layer> layers = new ArrayList<>();
    private final Map<String, BufferedImage> preloadedImages = new ConcurrentHashMap<>();
    private final Map<Integer, Tileset> gidToTilesetCache = new HashMap<>();
    private final Map<Integer, BufferedImage> globalTileCache = new ConcurrentHashMap<>();
    private final Map<Integer, PathTileCustomization> pathTileCustomizations = new HashMap<>();
    private final Map<String, BufferedImage> customPathImages = new HashMap<>();
    private final Set<String> keysPressed = new HashSet<>();
    private final List<MapTransition> mapTransitions = new ArrayList<>();

    private final JFrame frame;
    private final TileMapCanvas canvas;
    private final SpriteRenderer sprite;
    private final Camera camera;
    private final GrassRenderer grassRenderer;

    private String currentMapPath = "";
    private Layer collisionLayer = null;

    public TmxParser() {
        preloadAllPngImages();
        camera = new Camera(1200, 780);
        sprite = new SpriteRenderer();
        grassRenderer = new GrassRenderer(customPathImages);

        frame = new JFrame("TMX 타일맵 뷰어 (부드러운 이동)");
        frame.setResizable(false);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        canvas = new TileMapCanvas();
        canvas.setPreferredSize(new Dimension(1200, 780));
        canvas.setBackground(Color.BLACK);
        canvas.setOpaque(true);
        canvas.setFocusable(true);

        setupKeyListener();
        frame.add(canvas);
        startGameLoop();
    }

    private void setupKeyListener() {
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
    }

    private void startGameLoop() {
        Timer gameTimer = new Timer(1000 / GAME_FPS, e -> {
            updateMovement();
            canvas.repaint();
        });
        gameTimer.start();
    }

    private void updateMovement() {
        if (keysPressed.isEmpty()) return;

        int newX = sprite.getX();
        int newY = sprite.getY();
        boolean moved = false;

        if (keysPressed.contains("w") && isValidPlayerPosition(newX, newY - MOVE_SPEED)) {
            newY -= MOVE_SPEED;
            moved = true;
        }
        if (keysPressed.contains("s") && isValidPlayerPosition(newX, newY + MOVE_SPEED)) {
            newY += MOVE_SPEED;
            moved = true;
        }
        if (keysPressed.contains("a") && isValidPlayerPosition(newX - MOVE_SPEED, newY)) {
            newX -= MOVE_SPEED;
            moved = true;
        }
        if (keysPressed.contains("d") && isValidPlayerPosition(newX + MOVE_SPEED, newY)) {
            newX += MOVE_SPEED;
            moved = true;
        }

        if (moved) {
            validateAndSetPlayerPosition(newX, newY);
        }
    }

    private void validateAndSetPlayerPosition(int newX, int newY) {
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

    private void calculateMapOffset() {
        int mapPixelWidth = mapWidth * tileWidth * TILE_SCALE;
        int mapPixelHeight = mapHeight * tileHeight * TILE_SCALE;

        mapOffsetX = mapPixelWidth < canvas.getWidth() ? (canvas.getWidth() - mapPixelWidth) / 2 : 0;
        mapOffsetY = mapPixelHeight < canvas.getHeight() ? (canvas.getHeight() - mapPixelHeight) / 2 : 0;
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
        File resourceDir = findResourceDirectory();
        if (resourceDir == null) return;

        System.out.println("resource 디렉토리에서 PNG 파일들을 재귀적으로 로드 중: " + resourceDir.getAbsolutePath());

        List<File> pngFiles = new ArrayList<>();
        collectPngFiles(resourceDir, pngFiles);
        pngFiles.parallelStream().forEach(this::loadPngFile);

        System.out.println("총 " + pngFiles.size() + "개의 PNG 파일이 resource 디렉토리에서 로드되었습니다.");
    }

    private File findResourceDirectory() {
        File resourceDir = new File("resource");
        if (resourceDir.exists() && resourceDir.isDirectory()) {
            return resourceDir;
        }

        File projectRoot = new File(System.getProperty("user.dir"));
        resourceDir = new File(projectRoot, "resource");

        if (resourceDir.exists() && resourceDir.isDirectory()) {
            return resourceDir;
        }

        System.err.println("resource 디렉토리를 찾을 수 없습니다: " + resourceDir.getAbsolutePath());
        return null;
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

    private void loadPngFile(File pngFile) {
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

    private BufferedImage findImageByName(String imagePath) {
        String fileName = new File(imagePath).getName();

        BufferedImage image = preloadedImages.get(fileName);
        if (image != null) return image;

        if (fileName.contains(".")) {
            String nameWithoutExt = fileName.substring(0, fileName.lastIndexOf('.'));
            image = preloadedImages.get(nameWithoutExt);
            if (image != null) return image;
        }

        if (!fileName.contains(".")) {
            return preloadedImages.get(fileName + ".png");
        }

        return null;
    }

    public boolean loadTMX(String tmxPath) {
        try {
            clearExistingData();

            File tmxFile = new File(tmxPath);
            Document doc = parseXmlDocument(tmxFile);

            Element mapElement = doc.getDocumentElement();
            parseMapProperties(mapElement);
            parseTilesets(doc);
            parseLayers(doc);

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

    private void clearExistingData() {
        tilesets.clear();
        layers.clear();
        gidToTilesetCache.clear();
        globalTileCache.clear();
        grassRenderer.clearCache();
    }

    private Document parseXmlDocument(File tmxFile) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(tmxFile);
        doc.getDocumentElement().normalize();
        return doc;
    }

    private void parseMapProperties(Element mapElement) {
        mapWidth = Integer.parseInt(mapElement.getAttribute("width"));
        mapHeight = Integer.parseInt(mapElement.getAttribute("height"));
        tileWidth = Integer.parseInt(mapElement.getAttribute("tilewidth"));
        tileHeight = Integer.parseInt(mapElement.getAttribute("tileheight"));

        calculateMapOffset();

        int mapPixelWidth = mapWidth * tileWidth * TILE_SCALE;
        int mapPixelHeight = mapHeight * tileHeight * TILE_SCALE;
        camera.setMapBounds(mapPixelWidth, mapPixelHeight);

        setPlayerStartPosition(10, 10);
    }

    private void parseTilesets(Document doc) {
        NodeList tilesetNodes = doc.getElementsByTagName("tileset");
        for (int i = 0; i < tilesetNodes.getLength(); i++) {
            Element tilesetElement = (Element) tilesetNodes.item(i);
            Tileset tileset = createTileset(tilesetElement);
            tilesets.add(tileset);
            System.out.println("타일셋 추가됨: " + tileset.name + " (GID: " + tileset.firstGid + ")");
        }
    }

    private Tileset createTileset(Element tilesetElement) {
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
                System.out.println("타일셋 이미지 연결됨: " + tileset.imagePath + " -> " + tileset.name);
            } else {
                System.err.println("타일셋 이미지를 찾을 수 없습니다: " + tileset.imagePath);
            }
        }

        return tileset;
    }

    private void parseLayers(Document doc) {
        NodeList layerNodes = doc.getElementsByTagName("layer");
        for (int i = 0; i < layerNodes.getLength(); i++) {
            Element layerElement = (Element) layerNodes.item(i);
            Layer layer = createLayer(layerElement);
            layers.add(layer);
            System.out.println("레이어 추가됨: " + layer.name + " -> " + layer.layerType);
        }

        layers.sort(Comparator.comparingInt(layer -> layer.renderOrder));
        System.out.println("레이어 정렬 완료");
    }

    private Layer createLayer(Element layerElement) {
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
                parseLayerData(layer, dataElement.getTextContent().trim());
            }
        }

        return layer;
    }

    private void parseLayerData(Layer layer, String csvData) {
        String[] values = csvData.split(",");
        layer.data = new int[values.length];

        for (int j = 0; j < values.length; j++) {
            layer.data[j] = Integer.parseInt(values[j].trim());
        }
    }

    private String normalizeLayerName(String layerName) {
        if (layerName == null) return "UNKNOWN";
        return layerName.toUpperCase().replaceAll("\\d+$", "");
    }

    private int getLayerRenderOrder(String normalizedLayerName) {
        return LAYER_ORDER.getOrDefault(normalizedLayerName, 999);
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
            loadCustomPathImages();
            int cachedCount = cacheAllVisibleTiles();
            System.out.println("타일 이미지 캐싱 완료: " + cachedCount + " tiles");
            SwingUtilities.invokeLater(canvas::repaint);
        }).start();
    }

    private int cacheAllVisibleTiles() {
        Set<Integer> uniqueGids = new HashSet<>();
        for (Layer layer : layers) {
            if (!layer.visible) continue;
            for (int gid : layer.data) {
                if (gid > 0) uniqueGids.add(gid);
            }
        }

        int cachedCount = 0;
        for (int gid : uniqueGids) {
            BufferedImage tileImage = createTileImage(gid);
            if (tileImage != null) {
                globalTileCache.put(gid, tileImage);
                cachedCount++;
            }
        }

        return cachedCount;
    }

    private Tileset findTilesetForGid(int gid) {
        return gidToTilesetCache.get(gid);
    }

    private BufferedImage createTileImage(int gid) {
        if (gid == 0) return null;

        if (pathTileCustomizations.containsKey(gid)) {
            BufferedImage customTile = createCustomPathTileImage(gid);
            if (customTile != null) return customTile;
        }

        Tileset tileset = findTilesetForGid(gid);
        if (tileset == null || tileset.image == null) return null;

        BufferedImage cachedTile = tileset.tileCache.get(gid);
        if (cachedTile != null) return cachedTile;

        return extractTileFromTileset(tileset, gid);
    }

    private BufferedImage extractTileFromTileset(Tileset tileset, int gid) {
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
        return cachedImage != null ? cachedImage : createTileImage(gid);
    }

    private void renderTileMapWithCamera(Graphics g) {
        Graphics2D g2d = setupGraphics(g);

        int mapPixelWidth = mapWidth * tileWidth * TILE_SCALE;
        int mapPixelHeight = mapHeight * tileHeight * TILE_SCALE;

        if (mapPixelWidth > canvas.getWidth() || mapPixelHeight > canvas.getHeight()) {
            renderCameraMode(g2d);
        } else {
            renderFixedMode(g2d);
        }

        renderUI(g2d);
    }

    private Graphics2D setupGraphics(Graphics g) {
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g2d.setColor(Color.BLACK);
        g2d.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
        return g2d;
    }

    private void renderCameraMode(Graphics2D g2d) {
        camera.followPlayer(sprite);

        int scaledTileWidth = tileWidth * TILE_SCALE;
        int scaledTileHeight = tileHeight * TILE_SCALE;

        Rectangle visibleBounds = calculateVisibleTileBounds(scaledTileWidth, scaledTileHeight);

        renderLayersWithCamera(g2d, visibleBounds, scaledTileWidth, scaledTileHeight, false);
        renderPlayerWithCamera(g2d);
        renderLayersWithCamera(g2d, visibleBounds, scaledTileWidth, scaledTileHeight, true);
    }

    private Rectangle calculateVisibleTileBounds(int scaledTileWidth, int scaledTileHeight) {
        int startTileX = Math.max(0, camera.getX() / scaledTileWidth);
        int startTileY = Math.max(0, camera.getY() / scaledTileHeight);
        int endTileX = Math.min(mapWidth - 1, (camera.getX() + camera.getViewWidth()) / scaledTileWidth + 1);
        int endTileY = Math.min(mapHeight - 1, (camera.getY() + camera.getViewHeight()) / scaledTileHeight + 1);

        return new Rectangle(startTileX, startTileY, endTileX - startTileX + 1, endTileY - startTileY + 1);
    }

    private void renderFixedMode(Graphics2D g2d) {
        int scaledTileWidth = tileWidth * TILE_SCALE;
        int scaledTileHeight = tileHeight * TILE_SCALE;

        renderLayersFixed(g2d, scaledTileWidth, scaledTileHeight, false);
        sprite.render(g2d);
        renderLayersFixed(g2d, scaledTileWidth, scaledTileHeight, true);
    }

    private void renderLayersWithCamera(Graphics2D g2d, Rectangle bounds, int scaledTileWidth,
                                        int scaledTileHeight, boolean frontLayersOnly) {
        for (Layer layer : layers) {
            if (!layer.visible) continue;

            boolean isFrontLayer = layer.layerType.equals("FRONT") || layer.layerType.equals("ALWAYSFRONT");
            if (frontLayersOnly != isFrontLayer) continue;

            renderLayerTiles(g2d, layer, bounds, scaledTileWidth, scaledTileHeight, true);
        }
    }

    private void renderLayersFixed(Graphics2D g2d, int scaledTileWidth, int scaledTileHeight, boolean frontLayersOnly) {
        Rectangle fullBounds = new Rectangle(0, 0, mapWidth, mapHeight);

        for (Layer layer : layers) {
            if (!layer.visible) continue;

            boolean isFrontLayer = layer.layerType.equals("FRONT") || layer.layerType.equals("ALWAYSFRONT");
            if (frontLayersOnly != isFrontLayer) continue;

            renderLayerTiles(g2d, layer, fullBounds, scaledTileWidth, scaledTileHeight, false);
        }
    }

    private void renderLayerTiles(Graphics2D g2d, Layer layer, Rectangle bounds,
                                  int scaledTileWidth, int scaledTileHeight, boolean useCamera) {
        for (int y = bounds.y; y < bounds.y + bounds.height && y < mapHeight; y++) {
            for (int x = bounds.x; x < bounds.x + bounds.width && x < mapWidth; x++) {
                int index = y * layer.width + x;
                if (index >= layer.data.length) continue;

                int gid = layer.data[index];
                if (gid == 0) continue;

                BufferedImage tileImage = globalTileCache.get(gid);
                if (tileImage == null) continue;

                int screenX, screenY;
                if (useCamera) {
                    screenX = camera.worldToScreenX(x * scaledTileWidth);
                    screenY = camera.worldToScreenY(y * scaledTileHeight);
                } else {
                    screenX = mapOffsetX + x * scaledTileWidth;
                    screenY = mapOffsetY + y * scaledTileHeight;
                }

                if (pathTileCustomizations.containsKey(gid)) {
                    renderCustomPathTile(g2d, tileImage, screenX, screenY,
                            scaledTileWidth, scaledTileHeight, gid, x, y);
                } else {
                    g2d.drawImage(tileImage, screenX, screenY, scaledTileWidth, scaledTileHeight, null);
                }
            }
        }
    }

    private void renderCustomPathTile(Graphics2D g2d, BufferedImage tileImage,
                                      int screenX, int screenY, int tileWidth, int tileHeight,
                                      int gid, int tileX, int tileY) {
        PathTileCustomization customization = pathTileCustomizations.get(gid);
        if (customization == null) {
            g2d.drawImage(tileImage, screenX, screenY, tileWidth, tileHeight, null);
            return;
        }

        if (customization.isGrass) {
            grassRenderer.renderGrassTile(g2d, screenX, screenY, tileWidth, tileHeight, gid, tileX, tileY, customization);
            return;
        }

        renderCustomTileWithMode(g2d, tileImage, screenX, screenY, tileWidth, tileHeight, customization);
    }

    private void renderCustomTileWithMode(Graphics2D g2d, BufferedImage tileImage,
                                          int screenX, int screenY, int tileWidth, int tileHeight,
                                          PathTileCustomization customization) {
        int originalWidth = tileImage.getWidth();
        int originalHeight = tileImage.getHeight();

        int renderX = screenX + customization.offsetX;
        int renderY = screenY + customization.offsetY;
        int renderWidth = tileWidth;
        int renderHeight = tileHeight;

        switch (customization.renderMode) {
            case STRETCH:
                g2d.drawImage(tileImage, renderX, renderY, renderWidth, renderHeight, null);
                break;

            case ASPECT_FIT:
                double scale = Math.min((double) tileWidth / originalWidth, (double) tileHeight / originalHeight);
                renderWidth = (int) (originalWidth * scale);
                renderHeight = (int) (originalHeight * scale);
                renderX = screenX + (tileWidth - renderWidth) / 2 + customization.offsetX;
                renderY = screenY + (tileHeight - renderHeight) / 2 + customization.offsetY;
                g2d.drawImage(tileImage, renderX, renderY, renderWidth, renderHeight, null);
                break;

            case ASPECT_FILL:
                scale = Math.max((double) tileWidth / originalWidth, (double) tileHeight / originalHeight);
                renderWidth = (int) (originalWidth * scale);
                renderHeight = (int) (originalHeight * scale);
                renderX = screenX + (tileWidth - renderWidth) / 2 + customization.offsetX;
                renderY = screenY + (tileHeight - renderHeight) / 2 + customization.offsetY;
                g2d.drawImage(tileImage, renderX, renderY, renderWidth, renderHeight, null);
                break;

            case ORIGINAL_SIZE:
                renderWidth = originalWidth;
                renderHeight = originalHeight;
                g2d.drawImage(tileImage, renderX, renderY, renderWidth, renderHeight, null);
                break;

            case CENTER:
                renderWidth = originalWidth;
                renderHeight = originalHeight;
                renderX = screenX + (tileWidth - renderWidth) / 2 + customization.offsetX;
                renderY = screenY + (tileHeight - renderHeight) / 2 + customization.offsetY;
                g2d.drawImage(tileImage, renderX, renderY, renderWidth, renderHeight, null);
                break;
        }
    }

    public void addPathTileCustomization(int gid, String imagePath, int targetTileIndex,
                                         int tileWidth, int tileHeight,
                                         PathTileCustomization.RenderMode renderMode,
                                         boolean isGrass) {
        pathTileCustomizations.put(gid, new PathTileCustomization(imagePath, targetTileIndex,
                tileWidth, tileHeight, renderMode, 0, 0, isGrass));
        System.out.println("Path 타일 커스터마이징 추가: GID " + gid + " -> " + imagePath +
                " [모드: " + renderMode + ", 잔디: " + isGrass + "]");
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
        if (mapWidth <= 0 || mapHeight <= 0) return;

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

    private void renderUI(Graphics2D g2d) {
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2d.setFont(new Font("Arial", Font.BOLD, 12));

        renderMainInfoPanel(g2d);
        renderMapDetailPanel(g2d);
        renderSystemInfoPanel(g2d);
    }

    private void renderMainInfoPanel(Graphics2D g2d) {
        renderPanel(g2d, 10, 10, 280, 100, () -> {
            int yOffset = 25;
            final int lineHeight = 16;

            g2d.setColor(Color.WHITE);
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
        });
    }

    private void renderMapDetailPanel(Graphics2D g2d) {
        int panelWidth = 300;
        int panelHeight = 120;
        int panelX = canvas.getWidth() - panelWidth - 10;

        renderPanel(g2d, panelX, 10, panelWidth, panelHeight, () -> {
            int yOffset = 25;
            final int lineHeight = 14;

            int mapPixelWidth = mapWidth * tileWidth * TILE_SCALE;
            int mapPixelHeight = mapHeight * tileHeight * TILE_SCALE;

            g2d.setColor(Color.CYAN);
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
        });
    }

    private void renderSystemInfoPanel(Graphics2D g2d) {
        int lineHeight = 14;
        int baseHeight = 40;
        int layerHeight = layers.size() * lineHeight;
        int keyHeight = keysPressed.isEmpty() ? 0 : lineHeight + 5;

        int panelWidth = 400;
        int panelHeight = baseHeight + layerHeight + keyHeight;
        int panelY = canvas.getHeight() - panelHeight - 10;

        renderPanel(g2d, 10, panelY, panelWidth, panelHeight, () -> {
            int yOffset = panelY + 15;

            if (!layers.isEmpty()) {
                g2d.setColor(Color.ORANGE);
                g2d.drawString("Layer Order (rendering sequence):", 15, yOffset);
                yOffset += lineHeight;

                for (Layer layer : layers) {
                    g2d.setColor(Color.WHITE);
                    g2d.drawString(String.format("  %s (%s) - Order: %d",
                            layer.name, layer.layerType, layer.renderOrder), 20, yOffset);
                    yOffset += lineHeight;
                }
            }

            if (!keysPressed.isEmpty()) {
                yOffset += 5;
                g2d.setColor(Color.GREEN);
                g2d.drawString("Keys Pressed: " + String.join(", ", keysPressed), 15, yOffset);
            }
        });
    }

    private void renderPanel(Graphics2D g2d, int x, int y, int width, int height, Runnable contentRenderer) {
        g2d.setColor(new Color(0, 0, 0, 150));
        g2d.fillRect(x, y, width, height);

        g2d.setColor(new Color(100, 100, 100));
        g2d.drawRect(x, y, width, height);

        contentRenderer.run();
    }

    private void setupCollisionLayer() {
        collisionLayer = layers.stream()
                .filter(layer -> "BUILDINGS".equals(layer.layerType))
                .findFirst()
                .orElse(null);

        if (collisionLayer != null) {
            System.out.println("충돌 레이어 설정됨: " + collisionLayer.name);
        } else {
            System.out.println("충돌 레이어를 찾을 수 없습니다.");
        }
    }

    private boolean isTileBlocked(int tileX, int tileY) {
        if (collisionLayer == null) return false;

        if (tileX < 0 || tileY < 0 || tileX >= mapWidth || tileY >= mapHeight) {
            return true;
        }

        int index = tileY * collisionLayer.width + tileX;
        if (index >= collisionLayer.data.length) return true;

        return collisionLayer.data[index] != 0;
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

        int[] hitboxOffsets = {4, playerWidth - 4, playerHeight - 23, playerHeight - 1};

        int[][] corners = {
                {newX + hitboxOffsets[0], newY + hitboxOffsets[3]},
                {newX + hitboxOffsets[1], newY + hitboxOffsets[3]},
                {newX + hitboxOffsets[0], newY + hitboxOffsets[2]},
                {newX + hitboxOffsets[1], newY + hitboxOffsets[2]}
        };

        return Arrays.stream(corners).noneMatch(corner -> isPixelBlocked(corner[0], corner[1]));
    }

    // Map transition methods
    public void addMapTransition(String fromMap, int triggerX, int triggerY,
                                 String toMap, int destX, int destY) {
        mapTransitions.add(new MapTransition(toMap, triggerX, triggerY, destX, destY));
        System.out.println("맵 전환 추가: " + fromMap + "(" + triggerX + "," + triggerY +
                ") -> " + toMap + "(" + destX + "," + destY + ")");
    }

    private void checkMapTransition(int playerX, int playerY) {
        int playerTileX = (playerX - mapOffsetX) / (tileWidth * TILE_SCALE);
        int playerTileY = (playerY - mapOffsetY) / (tileHeight * TILE_SCALE);

        mapTransitions.stream()
                .filter(transition -> playerTileX == transition.triggerTileX && playerTileY == transition.triggerTileY)
                .findFirst()
                .ifPresent(transition -> {
                    System.out.println("맵 전환 트리거 발동: (" + playerTileX + "," + playerTileY +
                            ") -> " + transition.targetMapPath);
                    switchToMap(transition.targetMapPath, transition.destinationTileX, transition.destinationTileY);
                });
    }

    private void switchToMap(String targetMapPath, int destinationTileX, int destinationTileY) {
        System.out.println("맵 전환 시작: " + currentMapPath + " -> " + targetMapPath);

        if (loadTMX(targetMapPath)) {
            currentMapPath = targetMapPath;
            setPlayerStartPosition(destinationTileX, destinationTileY);

            String mapName = extractMapName(targetMapPath);
            frame.setTitle("TMX 타일맵 뷰어 - " + mapName);

            System.out.println("맵 전환 완료: " + mapName);

            SwingUtilities.invokeLater(() -> {
                canvas.revalidate();
                canvas.repaint();
            });
        } else {
            System.err.println("맵 전환 실패: " + targetMapPath);
        }
    }

    private void loadCustomPathImages() {
        pathTileCustomizations.values().forEach(customization -> {
            if (!customPathImages.containsKey(customization.imagePath)) {
                BufferedImage image = findImageByName(customization.imagePath);
                if (image != null) {
                    customPathImages.put(customization.imagePath, image);

                    if (customization.isGrass) {
                        grassRenderer.preExtractGrassTiles(customization.imagePath, customization);
                    }
                }
            }
        });
    }

    private BufferedImage createCustomPathTileImage(int gid) {
        PathTileCustomization customization = pathTileCustomizations.get(gid);
        if (customization == null) return null;

        BufferedImage sourceImage = customPathImages.get(customization.imagePath);
        if (sourceImage == null) return null;

        try {
            int tilesPerRow = sourceImage.getWidth() / customization.tileWidth;
            int tileX = (customization.targetTileIndex % tilesPerRow) * customization.tileWidth;
            int tileY = (customization.targetTileIndex / tilesPerRow) * customization.tileHeight;

            BufferedImage customTile = sourceImage.getSubimage(tileX, tileY, customization.tileWidth, customization.tileHeight);
            System.out.println("커스텀 Path 타일 생성됨: GID " + gid);
            return customTile;

        } catch (Exception e) {
            System.err.println("커스텀 Path 타일 생성 실패: GID " + gid + " - " + e.getMessage());
            return null;
        }
    }

    public void printPathLayerGids() {
        layers.stream()
                .filter(layer -> "PATHS".equals(layer.layerType))
                .findFirst()
                .ifPresentOrElse(layer -> {
                    System.out.println("=== Path 레이어 '" + layer.name + "' GID 정보 ===");
                    Set<Integer> uniqueGids = Arrays.stream(layer.data)
                            .filter(gid -> gid > 0)
                            .boxed()
                            .collect(Collectors.toSet());

                    System.out.println("사용 중인 GID들: " + uniqueGids);
                    System.out.println("총 " + uniqueGids.size() + "개의 서로 다른 타일이 사용됨");
                }, () -> System.out.println("Path 레이어를 찾을 수 없습니다."));
    }

    public int getPathTileGidAt(int tileX, int tileY) {
        return layers.stream()
                .filter(layer -> "PATHS".equals(layer.layerType))
                .findFirst()
                .filter(layer -> tileX >= 0 && tileX < layer.width && tileY >= 0 && tileY < layer.height)
                .map(layer -> {
                    int index = tileY * layer.width + tileX;
                    return index < layer.data.length ? layer.data[index] : 0;
                })
                .orElse(0);
    }

    // Getters and utility methods
    public void setCurrentMapPath(String mapPath) { this.currentMapPath = mapPath; }

    public String extractMapName(String filePath) {
        String fileName = filePath.substring(filePath.lastIndexOf('/') + 1);
        return fileName.contains(".") ? fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
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
}