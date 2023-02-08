package com.maxwen.osmviewer;

import com.github.cliftonlabs.json_simple.JsonArray;
import com.github.cliftonlabs.json_simple.JsonObject;
import com.maxwen.osmviewer.nmea.NMEAHandler;
import com.maxwen.osmviewer.routing.RoutingWrapper;
import com.maxwen.osmviewer.shared.Config;
import com.maxwen.osmviewer.shared.GISUtils;
import com.maxwen.osmviewer.shared.LogUtils;
import com.maxwen.osmviewer.shared.OSMUtils;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.*;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.effect.BlurType;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;
import javafx.scene.transform.Rotate;
import javafx.stage.FileChooser;
import javafx.stage.Popup;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.maxwen.osmviewer.shared.GISUtils.M_LN2;
import static com.maxwen.osmviewer.shared.GISUtils.TILESIZE;

public class MainController implements Initializable, NMEAHandler {
    public static final int ROTATE_X_VALUE = 60;
    public static final int PREFETCH_MARGIN_PIXEL = 800;
    @FXML
    Button quitButton;
    @FXML
    Button searchButton;
    @FXML
    Button zoomInButton;
    @FXML
    Button zoomOutButton;
    @FXML
    Label zoomLabel;
    @FXML
    Label zoomTitle;
    @FXML
    Label posLabel;
    @FXML
    Label posTitle;
    @FXML
    BorderPane borderPane;
    @FXML
    ToggleButton trackModeButton;
    @FXML
    Label speedLabel;
    @FXML
    Label altLabel;
    @FXML
    Button startReplayButton;
    @FXML
    Button stopReplayButton;
    @FXML
    Button pauseReplayButton;
    @FXML
    HBox trackButtons;
    @FXML
    Button stepReplayButton;
    @FXML
    HBox bottomPane;
    @FXML
    HBox topPane;
    @FXML
    Button menuButton;
    @FXML
    Label wayLabel;
    @FXML
    HBox rightPane;
    @FXML
    VBox rightButtons;
    @FXML
    HBox infoBox;
    @FXML
    Label infoLabel;
    @FXML
    Label infoTitle;
    @FXML
    Pane infoPane;
    @FXML
    VBox searchContent;
    @FXML
    StackPane mainStackPane;
    Pane mainPane;

    private static final int MIN_ZOOM = 6;
    private static final int MAX_ZOOM = 20;
    private int mMapZoom = 17;
    private double mMapZeroX;
    private double mMapZeroY;
    private double mCenterLat = 47.793938;
    private double mCenterLon = 12.992203;
    private double mCenterPosX;
    private double mCenterPosY;
    private Point2D mMovePoint;
    private Stage mPrimaryStage;
    private boolean mShow3D;
    private Scene mScene;
    private BoundingBox mFetchBBox;
    private BoundingBox mVisibleBBox;
    private Rotate mRotate;
    private Map<Integer, List<Node>> mPolylines;
    private List<OSMImageView> mNodes;
    private Rotate mZRotate;
    private OSMShape mSelectdShape;
    private long mSelectdOSMId = -1;
    private Map<Long, JsonObject> mOSMObjects;
    private Point2D mGPSPos = new Point2D(0, 0);
    private Circle mGPSDot;
    private JsonObject mGPSData;
    private boolean mTrackMode;
    private GPSThread mGPSThread;
    private boolean mTrackReplayMode;
    private Set<Long> mPredictionWays = new HashSet<>();
    private ContextMenu mMenuButtonMenu;
    private ContextMenu mContextMenu;
    private Point2D mMapGPSPos;
    private TrackReplayThread mTrackReplayThread;
    private JsonObject mCurrentEdge;
    private JsonArray mNextEdgeList = new JsonArray();
    private long mNextRefId = -1;
    private File mCurrentTrackFile;
    private JsonObject mLastUsedEdge;
    private OSMShape mTrackingShape;
    private long mTrackingOSMId = -1;
    private Pane mMapPane = new Pane();
    private Pane mNodePane = new Pane();
    private Pane mRoutingPane = new Pane();
    private Pane mTrackingPane = new Pane();
    private ExecutorService mExecutorService;
    private LoadPOITask mLoadPOITask;
    private LoadAdminAreaTask mLoadAdminAreaTask;
    private Map<Integer, List<Node>> mAreaPolylines;
    private Map<Integer, List<Node>> mCountryPolylines;
    private JsonObject mSelectedEdge;
    private Polyline mSelectedEdgeShape;
    private ResolvePositionTask mResolvePositionTask;
    private List<RoutingNode> mRoutingNodes = new ArrayList<>();
    private Point2D mMouseClickedNodePos;
    private Point2D mMouseClickedCoordsPos;
    private List<Polyline> mRoutePolylineList = new ArrayList<>();
    private List<Long> mRouteEdgeIdList = new ArrayList<>();
    private boolean mMapLoading;
    private LoadMapDataTask mLoadMapDataTask;
    private boolean mRigthSideExpanded = false;
    ObservableList<QueryItem> mQueryItems = FXCollections.observableArrayList();
    private ProgressIndicator mQueryListProgress;
    private ProgressIndicator mLoadMapProgress;
    private MenuButton mFilterHeader;
    //private Circle mCenterDot;

    enum FilterType {
        POI,
        ADDRESS,
        CITY,
        LOCAL
    }

    private FilterType mFilterType = FilterType.POI;
    private JsonArray mLocationHistory = new JsonArray();
    private int mLocationHistoryIndex;

    public static final int TUNNEL_LAYER_LEVEL = -1;
    public static final int ADMIN_AREA_LAYER_LEVEL = 0;
    public static final int AREA_LAYER_LEVEL = 1;
    public static final int BUILDING_AREA_LAYER_LEVEL = 2;
    public static final int HIDDEN_STREET_LAYER_LEVEL = 3;
    public static final int STREET_LAYER_LEVEL = 4;
    public static final int RAILWAY_LAYER_LEVEL = 5;
    public static final int BRIDGE_LAYER_LEVEL = 6;

    EventHandler<MouseEvent> mouseHandler = new EventHandler<MouseEvent>() {
        @Override
        public void handle(MouseEvent mouseEvent) {
            if (mouseEvent.getEventType() == MouseEvent.MOUSE_CLICKED) {
                if (!mouseEvent.isStillSincePress()) {
                    return;
                }
                //LogUtils.log("MOUSE_CLICKED:mouseHandler");

                Point2D mouseScreenPos = new Point2D(mouseEvent.getX(), mouseEvent.getY());
                Point2D paneZeroPos = mNodePane.localToScreen(0, 0);
                Point2D nodePos = new Point2D(mouseEvent.getScreenX() - paneZeroPos.getX() + mMapZeroX,
                        mouseEvent.getScreenY() - paneZeroPos.getY() + mMapZeroY);

                mMouseClickedNodePos = nodePos;
                mMouseClickedCoordsPos = getCoordOfPos(mouseScreenPos);
                mMovePoint = null;

                OSMShape clickedShape = null;
                hideAllMenues();

                if (mouseEvent.getButton() == MouseButton.SECONDARY) {
                    if (!mMapLoading) {
                        buildContextMenu();
                        mContextMenu.show(mainPane, mouseEvent.getScreenX(), mouseEvent.getScreenY());
                    }
                    return;
                }

                LogUtils.log("admin = " + getAdminAreaStringAtPos(mMouseClickedCoordsPos) + " " + getAdminIdAtPos(mMouseClickedCoordsPos));

                if (mMapZoom > 16) {
                    // mouseScreenPos will be transformed pos

                    if (!mTrackReplayMode && !mTrackMode) {
                        posLabel.setText(String.format("%.5f:%.5f", mMouseClickedCoordsPos.getX(), mMouseClickedCoordsPos.getY()));
                    }

                    // first check for poi nodes with screen pos
                    for (OSMImageView node : mNodes) {
                        if (node.contains(nodePos)) {
                            clickedShape = node;
                            break;
                        }
                    }
                    if (clickedShape == null) {
                        Point2D mapPosNormalized = new Point2D(mouseScreenPos.getX() + mMapZeroX, mouseScreenPos.getY() + mMapZeroY);
                        clickedShape = findShapeAtPoint(mapPosNormalized, OSMUtils.SELECT_AREA_TYPE);
                    }
                }

                if (clickedShape != null) {
                    MainController.this.doSelectShape(clickedShape);
                    if (mSelectdShape instanceof OSMPolyline) {
                        JsonObject osmObject = mOSMObjects.get(mSelectdOSMId);
                        if (osmObject.get("type").equals("way")) {
                            if (mResolvePositionTask != null) {
                                mResolvePositionTask.cancel();
                            }
                            mResolvePositionTask = new ResolvePositionTask(mMouseClickedCoordsPos, mSelectdOSMId);
                            mResolvePositionTask.setOnSucceeded((succeededEvent) -> {
                                mSelectedEdge = mResolvePositionTask.getResolvedEdge();
                                if (mSelectedEdge != null) {
                                    mSelectedEdgeShape = displayCoordsPolyline((JsonArray) mSelectedEdge.get("coords"));
                                    mSelectedEdgeShape.setStrokeWidth(2);
                                    mSelectedEdgeShape.setStroke(Color.GREEN);
                                    drawShapes();
                                }
                            });
                            mExecutorService.submit(mResolvePositionTask);
                        }
                    }
                }
            }
            if (mouseEvent.getEventType() == MouseEvent.MOUSE_DRAGGED) {
                //LogUtils.log("MOUSE_DRAGGED:mouseHandler");
                hideAllMenues();

                if (mMovePoint == null) {
                    mMovePoint = new Point2D(mouseEvent.getSceneX(), mouseEvent.getSceneY());
                }
                int diffX = (int) (mMovePoint.getX() - mouseEvent.getSceneX());
                int diffY = (int) (mMovePoint.getY() - mouseEvent.getSceneY());
                if (Math.abs(diffX) > 5 || Math.abs(diffY) > 5) {
                    moveMap(diffX, diffY);
                    mMovePoint = new Point2D(mouseEvent.getSceneX(), mouseEvent.getSceneY());
                }
            }
            if (mouseEvent.getEventType() == MouseEvent.MOUSE_RELEASED) {
                //LogUtils.log("MOUSE_RELEASED:mouseHandler");
                mMovePoint = null;
            }
        }
    };


    private Circle mCalcPoint;
    private ListView<QueryItem> mQueryListView;
    private TextField mQueryField;
    private QueryTaskPOI mQueryTask;
    private String mQueryText;
    private long mAdminId;

    public class LoadPOITask extends LoadMapTask {

        public LoadPOITask(List<Double> bbox) {
            super(bbox);
        }

        @Override
        protected Void call() throws Exception {
            LogUtils.log("LoadPOITask " + bbox);

            mNodes.clear();
            //mRoutePolylineList.clear();

            if (isCancelled()) {
                LogUtils.log("LoadPOITask cancel");
                return null;
            }

            if (mMapZoom >= 17) {
                JsonArray nodes = QueryController.getInstance().getPOINodesInBBoxWithGeom(bbox.get(0), bbox.get(1),
                        bbox.get(2), bbox.get(3), getPoiTypeListForZoom(), MainController.this);
                for (int i = 0; i < nodes.size(); i++) {
                    if (isCancelled() || Thread.interrupted()) {
                        throw new InterruptedException();
                    }
                    JsonObject node = (JsonObject) nodes.get(i);
                    long osmId = (long) node.get("osmId");
                    int nodeType = (int) node.get("nodeType");
                    JsonArray coord = (JsonArray) node.get("coords");
                    Double posX = getPixelXPosForLocationDeg(coord.getDouble(0));
                    Double posY = getPixelYPosForLocationDeg(coord.getDouble(1));

                    Point2D nodePos = new Point2D(posX, posY);
                    Image poiImage = OSMStyle.getNodeTypeImage(nodeType);
                    if (poiImage == null) {
                        poiImage = OSMStyle.getDefaultNodeImage();
                    }
                    OSMImageView poi = new OSMImageView(nodePos, poiImage, osmId);
                    updatePOINode(poi, true);
                    poi.setTranslateX(-mMapZeroX);
                    poi.setTranslateY(-mMapZeroY);
                    mNodes.add(poi);
                }
            }
            if (mMapZoom >= 14) {
                // updateRoutingNode depends on calc point so dont do before added
                for (RoutingNode node : mRoutingNodes) {
                    updateRoutingNode(node, true);
                }
                //createRoutePolylineList(bbox);
            }
            return null;
        }
    }

    public class LoadAdminAreaTask extends LoadMapTask {
        public LoadAdminAreaTask(List<Double> bbox) {
            super(bbox);
        }

        @Override
        protected Void call() throws Exception {
            LogUtils.log("LoadAdminAreaTask " + bbox);

            if (isCancelled()) {
                LogUtils.log("LoadAdminAreaTask cancel");
                return null;
            }
            if (mMapZoom >= 14) {
                QueryController.getInstance().getLinesInBboxWithGeom(bbox.get(0), bbox.get(1),
                        bbox.get(2), bbox.get(3), getAreaTypeListForZoom(), getSimplifyToleranceForZoom(), mAreaPolylines, MainController.this);
            }
            QueryController.getInstance().getAdminAreasInBboxWithGeom(bbox.get(0), bbox.get(1),
                    bbox.get(2), bbox.get(3), getAdminLevelListForZoom(), getSimplifyToleranceForZoom(), mAreaPolylines, MainController.this);
            return null;
        }
    }

    public class ResolvePositionTask extends Task<Void> {
        private Point2D mCoordsPos;
        private JsonObject mEdge;
        private long mWayId;

        public ResolvePositionTask(Point2D coordsPos, long wayId) {
            mCoordsPos = coordsPos;
            mWayId = wayId;
        }

        public JsonObject getResolvedEdge() {
            return mEdge;
        }

        @Override
        protected Void call() throws Exception {
            JsonArray edgeList = QueryController.getInstance().getEdgeOnPos(mCoordsPos.getX(), mCoordsPos.getY(), 0.0005, 30, 20);
            if (edgeList.size() != 0) {
                if (edgeList.size() == 1) {
                    mEdge = (JsonObject) edgeList.get(0);
                } else {
                    for (int i = 0; i < edgeList.size(); i++) {
                        JsonObject edge = (JsonObject) edgeList.get(i);
                        if ((long) edge.get("wayId") == mWayId) {
                            mEdge = edge;
                            break;
                        }
                    }
                }
                if (mEdge != null) {
                    if ((long) mEdge.get("wayId") == mWayId) {
                        LogUtils.log("mEdge = " + mEdge);
                    } else {
                        LogUtils.error("failed to resolve mEdge");
                        mEdge = null;
                    }
                }
            }
            return null;
        }
    }

    public class CalcRouteTask extends Task<Void> {
        private long mStartEdgeId;
        private long mEndEdgeId;
        private List<Long> mRouteEdgeIdList = new ArrayList<>();

        public CalcRouteTask(long startEdgeId, long endEdgeId) {
            mStartEdgeId = startEdgeId;
            mEndEdgeId = endEdgeId;
        }

        public List<Long> getRouteEdgeIdList() {
            return mRouteEdgeIdList;
        }

        @Override
        protected Void call() throws Exception {
            RoutingWrapper routing = new RoutingWrapper();
            LogUtils.log("mStartEdgeId = " + mStartEdgeId + " mEndEdgeId = " + mEndEdgeId);
            JsonArray route = routing.computeRoute(mStartEdgeId, 0.f, mEndEdgeId, 0.f);
            if (route != null) {
                route.forEach(edgeId -> {
                    mRouteEdgeIdList.add(((BigDecimal) edgeId).longValue());
                });
                if (mRouteEdgeIdList.get(0) != mStartEdgeId) {
                    mRouteEdgeIdList.add(0, mStartEdgeId);
                }
                if (mRouteEdgeIdList.get(mRouteEdgeIdList.size() - 1) != mEndEdgeId) {
                    mRouteEdgeIdList.add(mEndEdgeId);
                }
                LogUtils.log("route = " + mRouteEdgeIdList);
            }
            routing.resetData();
            return null;
        }
    }

    public class LoadMapDataTask extends LoadMapTask {
        public LoadMapDataTask(List<Double> bbox) {
            super(bbox);
        }

        @Override
        protected Void call() throws Exception {
            LogUtils.log("LoadMapDataTask " + mMapZoom);
            mMapLoading = true;
            long t = System.currentTimeMillis();
            doLoadMapData(bbox);
            LogUtils.log("loadMapDataTask time = " + (System.currentTimeMillis() - t));
            return null;
        }
    }

    public class QueryTaskPOI extends Task<List<QueryItem>> implements QueryTaskCallback {
        private String mQueryString;
        private FilterType mFilterType;
        private List<QueryItem> mQueryItems = new ArrayList<>();
        private long mAdminId;

        public QueryTaskPOI(String queryString, FilterType filterType, long adminId) {
            mQueryString = queryString;
            mFilterType = filterType;
            mAdminId = adminId;
        }

        @Override
        protected List<QueryItem> call() {
            if (mFilterType == FilterType.POI) {
                QueryController.getInstance().queryPOINodesMatchingName(mQueryString, null, this);
            } else if (mFilterType == FilterType.ADDRESS) {
                QueryController.getInstance().queryAddressMatchingName(mQueryString, this);
            } else if (mFilterType == FilterType.CITY) {
                QueryController.getInstance().queryCityMatchingName(mQueryString, this);
            } else if (mFilterType == FilterType.LOCAL) {
                JsonArray adminIdList = QueryController.getInstance().getAdminAreaChildren(mAdminId);
                adminIdList.add(mAdminId);
                LogUtils.log("" + adminIdList.size());
                QueryController.getInstance().queryPOINodesMatchingNameAndAdminId(mQueryString, adminIdList, null, this);
                QueryController.getInstance().queryAddressMatchingNameAndAdminId(mQueryString, adminIdList, this);
            }
            return mQueryItems;
        }

        @Override
        public boolean addQueryItemPOI(JsonObject node) {
            String type = (String) node.get("type");
            StringBuffer nodeArea = new StringBuffer();

            JsonArray coords = (JsonArray) node.get("coords");
            JsonObject adminData = (JsonObject) node.get("adminData");
            if (node.containsKey("adminId")) {
                long adminId = (long) node.get("adminId");
                adminData = QueryController.getInstance().getAdminAreasWithId(adminId);
            }
            if (adminData != null) {
                JsonObject tags = (JsonObject) adminData.get("tags");
                if (tags != null && tags.containsKey("name")) {
                    if (!type.equals("city")) {
                        // skip city itsself
                        nodeArea.append(tags.get("name"));
                        nodeArea.append("/");
                    }

                    JsonArray parents = (JsonArray) tags.get("parents");
                    if (parents != null) {
                        for (int j = 0; j < parents.size(); j++) {
                            JsonObject parentAdminArea = QueryController.getInstance().getAdminAreasWithId(GISUtils.getLongValue(parents.get(j)));
                            if (parentAdminArea != null) {
                                JsonObject parentTags = (JsonObject) parentAdminArea.get("tags");
                                if (parentTags != null && parentTags.containsKey("name")) {
                                    nodeArea.append(parentTags.get("name"));
                                    if (j < parents.size() - 1) {
                                        nodeArea.append("/");
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                String adminAreaString = getAdminAreaStringAtPos(new Point2D((double) coords.get(0), (double) coords.get(1)));
                nodeArea.append(adminAreaString);
            }

            if (type.equals("poi")) {
                int poiType = (int) node.get("nodeType");
                QueryItem item = new QueryItem((String) node.get("name"), nodeArea.toString(),
                        OSMStyle.getNodeTypeImage(poiType), coords);
                mQueryItems.add(item);
            } else if (type.equals("address")) {
                QueryItem item = new QueryItem((String) node.get("name"), nodeArea.toString(),
                        null, coords);
                mQueryItems.add(item);
            } else if (type.equals("city")) {
                QueryItem item = new QueryItem((String) node.get("name"), nodeArea.toString(),
                        null, coords);
                mQueryItems.add(item);
            }
            return !isCancelled();
        }
    }

    public class QueryMapTilesTask extends Task<Void> {

        public QueryMapTilesTask() {
        }

        @Override
        protected Void call() throws Exception {
            int width = (int) mScene.getWidth();
            int height = (int) mScene.getHeight();
            int map_x = (int) mMapZeroX;
            int map_y = (int) mMapZeroY;

            int offset_x = -map_x % TILESIZE;
            if (offset_x == 0) {
                offset_x = -TILESIZE;
            }

            int offset_y = -map_y % TILESIZE;
            if (offset_y == 0) {
                offset_y = -TILESIZE;
            }

            if (offset_x >= 0) {
                offset_x -= TILESIZE * 4;
            }
            if (offset_y >= 0) {
                offset_y -= TILESIZE * 4;
            }

            int tiles_nx = (width - offset_x) / TILESIZE + 1;
            int tiles_ny = (height - offset_y) / TILESIZE + 1;

            int tile_x0 = (int) (Math.floor((map_x - TILESIZE) / TILESIZE)) - 2;
            int tile_y0 = (int) (Math.floor((map_y - TILESIZE) / TILESIZE)) - 2;

            int i = tile_x0;
            int j = tile_y0;
            int offset_y0 = offset_y;

            while (i < (tile_x0 + tiles_nx)) {
                while (j < (tile_y0 + tiles_ny)) {
                    if (j < 0 || i < 0 || i >= Math.exp(mMapZoom * M_LN2) || j >= Math.exp(mMapZoom * M_LN2)) {
                        //pixbuf = self.getEmptyTile()
                    } else {
                        //pixbuf = self.getTile(self.map_zoom, i, j);
                    }
                    LogUtils.log("" + mMapZoom + "/" + i + "/" + j + ".png");
                    //self.drawPixmap(offset_x, offset_y, TILESIZE, TILESIZE, pixbuf)
                    LogUtils.log("" + offset_x + ":" + offset_y + "x" + TILESIZE + ":" + TILESIZE);

                    offset_y += TILESIZE;
                    j = j + 1;
                }
                offset_x += TILESIZE;
                offset_y = offset_y0;
                i = i + 1;
                j = tile_y0;
            }
            return null;
        }
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        LogUtils.log("initialize");

        mMapZoom = GISUtils.getIntValue(Config.getInstance().get("zoom", mMapZoom));
        mCenterLon = GISUtils.getDoubleValue(Config.getInstance().get("lon", mCenterLon));
        mCenterLat = GISUtils.getDoubleValue(Config.getInstance().get("lat", mCenterLat));
        mShow3D = (boolean) Config.getInstance().get("show3D", mShow3D);
        mLocationHistory = (JsonArray)
                Config.getInstance().get("locationHistory", new JsonArray());
        mLocationHistoryIndex = mLocationHistory.size() - 1;

        init();
    }

    private void init() {
        LogUtils.log("init");
        mPolylines = new LinkedHashMap<>();
        mPolylines.put(TUNNEL_LAYER_LEVEL, new ArrayList<>());
        mPolylines.put(ADMIN_AREA_LAYER_LEVEL, new ArrayList<>());
        mPolylines.put(AREA_LAYER_LEVEL, new ArrayList<>());
        mPolylines.put(BUILDING_AREA_LAYER_LEVEL, new ArrayList<>());
        mPolylines.put(HIDDEN_STREET_LAYER_LEVEL, new ArrayList<>());
        mPolylines.put(STREET_LAYER_LEVEL, new ArrayList<>());
        mPolylines.put(RAILWAY_LAYER_LEVEL, new ArrayList<>());
        mPolylines.put(BRIDGE_LAYER_LEVEL, new ArrayList<>());

        mAreaPolylines = new LinkedHashMap<>();
        mAreaPolylines.put(TUNNEL_LAYER_LEVEL, new ArrayList<>());
        mAreaPolylines.put(ADMIN_AREA_LAYER_LEVEL, new ArrayList<>());
        mAreaPolylines.put(AREA_LAYER_LEVEL, new ArrayList<>());
        mAreaPolylines.put(BUILDING_AREA_LAYER_LEVEL, new ArrayList<>());
        mAreaPolylines.put(HIDDEN_STREET_LAYER_LEVEL, new ArrayList<>());
        mAreaPolylines.put(STREET_LAYER_LEVEL, new ArrayList<>());
        mAreaPolylines.put(RAILWAY_LAYER_LEVEL, new ArrayList<>());
        mAreaPolylines.put(BRIDGE_LAYER_LEVEL, new ArrayList<>());

        mCountryPolylines = new LinkedHashMap<>();
        mCountryPolylines.put(ADMIN_AREA_LAYER_LEVEL, new ArrayList<>());

        mNodes = new ArrayList<>();

        mExecutorService = Executors.newFixedThreadPool(4);

        mOSMObjects = new HashMap<>();

        quitButton.setGraphic(new ImageView(new Image("/images/ui/quit.png")));
        //quitButton.setShape(new Circle(40));
        quitButton.setOnAction(e -> {
            Platform.exit();
        });

        searchButton.setGraphic(new ImageView(new Image("/images/ui/map-search.png")));
        //searchButton.setShape(new Circle(40));
        searchButton.setOnAction(e -> {
            if (!mRigthSideExpanded) {
                searchContent.setVisible(true);
                rightPane.getChildren().add(0, searchContent);
                // TODO move in func
                Timer animTimer = new Timer();
                animTimer.scheduleAtFixedRate(new TimerTask() {
                    int i = 0;

                    @Override
                    public void run() {
                        if (i < 10) {
                            searchContent.setPrefWidth(Math.min(searchContent.getPrefWidth() + 30, 300));
                        } else {
                            this.cancel();
                            Platform.runLater(new Runnable() {
                                @Override
                                public void run() {
                                    mRigthSideExpanded = true;
                                }
                            });
                        }
                        i++;
                    }

                }, 0, 25);
            } else {
                // TODO move in func
                Timer animTimer = new Timer();
                animTimer.scheduleAtFixedRate(new TimerTask() {
                    int i = 0;

                    @Override
                    public void run() {
                        if (i < 10) {
                            searchContent.setPrefWidth(Math.max(0, searchContent.getPrefWidth() - 30));
                        } else {
                            this.cancel();
                            Platform.runLater(new Runnable() {
                                @Override
                                public void run() {
                                    searchContent.setVisible(false);
                                    rightPane.getChildren().remove(0);
                                    mRigthSideExpanded = false;
                                }
                            });
                        }
                        i++;
                    }

                }, 0, 25);
            }
        });

        mQueryField = new TextField();
        mQueryField.setPromptText("Search...");
        mQueryField.setMinHeight(40);

        mQueryField.setOnKeyReleased(event -> {
            mQueryText = mQueryField.getText();
            updateListContent();
        });
        searchContent.getChildren().add(mQueryField);

        HBox filterSelect = new HBox();
        filterSelect.setPadding(new Insets(10, 10, 10, 10));

        ToggleGroup filterGroup = new ToggleGroup();

        RadioButton poiFilter = new RadioButton("POI");
        poiFilter.setToggleGroup(filterGroup);
        poiFilter.setOnAction(event -> {
            mFilterHeader.setVisible(false);
            mFilterType = FilterType.POI;
            updateListContent();
        });

        RadioButton addressFilter = new RadioButton("Address");
        addressFilter.setPadding(new Insets(0, 0, 0, 10));
        addressFilter.setToggleGroup(filterGroup);
        addressFilter.setOnAction(event -> {
            mFilterHeader.setVisible(false);
            mFilterType = FilterType.ADDRESS;
            updateListContent();
        });

        RadioButton cityFilter = new RadioButton("City");
        cityFilter.setPadding(new Insets(0, 0, 0, 10));
        cityFilter.setToggleGroup(filterGroup);
        cityFilter.setOnAction(event -> {
            mFilterHeader.setVisible(false);
            mFilterType = FilterType.CITY;
            updateListContent();
        });

        RadioButton localFilter = new RadioButton("Local");
        localFilter.setPadding(new Insets(0, 0, 0, 10));
        localFilter.setToggleGroup(filterGroup);
        localFilter.setOnAction(event -> {
            mFilterHeader.getItems().clear();
            // show area popup
            Point2D coordPos = new Point2D(mCenterLon, mCenterLat);
            mFilterHeader.setVisible(true);
            JsonArray adminDataList = getAdminAreaListAtPos(coordPos);
            for (int i = 0; i < adminDataList.size(); i++) {
                JsonObject adminData = (JsonObject) adminDataList.get(i);
                long osmId = GISUtils.getLongValue(adminData.get("osmId"));
                int adminLevel = GISUtils.getIntValue(adminData.get("adminLevel"));
                String name = (String) adminData.get("name");
                LogUtils.log(name + " " + adminLevel);
                RadioMenuItem menuItem = new RadioMenuItem(name);
                menuItem.setUserData(osmId);
                menuItem.setOnAction(e -> {
                    mAdminId = (long) menuItem.getUserData();
                    mFilterHeader.setText(menuItem.getText());
                    updateListContent();
                });
                mFilterHeader.getItems().add(menuItem);

                if (i == 0) {
                    mAdminId = osmId;
                    mFilterHeader.setText(menuItem.getText());
                }
            }
            mFilterType = FilterType.LOCAL;
            updateListContent();
        });

        poiFilter.setSelected(true);

        filterSelect.getChildren().add(poiFilter);
        filterSelect.getChildren().add(addressFilter);
        filterSelect.getChildren().add(cityFilter);
        filterSelect.getChildren().add(localFilter);

        searchContent.getChildren().add(filterSelect);

        mFilterHeader = new MenuButton("Area                            ");
        mFilterHeader.setVisible(false);
        searchContent.getChildren().add(mFilterHeader);

        mQueryListView = new ListView<>();

        VBox listViewLayout = new VBox(0);
        VBox.setVgrow(listViewLayout, Priority.ALWAYS);
        listViewLayout.getChildren().add(mQueryListView);
        VBox.setVgrow(mQueryListView, Priority.ALWAYS);

        mQueryListView.setItems(mQueryItems);
        mQueryListView.setCellFactory(queryListView -> new QueryListViewCell());
        mQueryListView.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<QueryItem>() {
            @Override
            public void changed(ObservableValue<? extends QueryItem> observable, QueryItem oldValue, QueryItem newValue) {
                if (newValue != null) {
                    JsonArray coords = newValue.getCoords();
                    //infoLabel.setText(newValue.getName());
                    centerMapOnLocation((double) coords.get(0), (double) coords.get(1));
                    addToLocationHistory(coords);
                }
            }
        });

        StackPane listViewPane = new StackPane();
        searchContent.getChildren().add(listViewPane);
        VBox.setVgrow(listViewPane, Priority.ALWAYS);

        mQueryListProgress = new ProgressIndicator();
        listViewPane.getChildren().add(listViewLayout);
        listViewPane.getChildren().add(mQueryListProgress);
        mQueryListProgress.setVisible(false);

        menuButton.setGraphic(new ImageView(new Image("/images/ui/menu.png")));
        //menuButton.setShape(new Circle(30));
        menuButton.setOnMouseClicked(e -> {
            if (!mMapLoading) {
                buildButtonMenu();
                mMenuButtonMenu.show(menuButton, e.getScreenX(), e.getScreenY());
            }
        });


        zoomInButton.setGraphic(new ImageView(new Image("/images/ui/plus.png")));
        //zoomInButton.setShape(new Circle(40));
        zoomInButton.setOnAction(e -> {
            int zoom = mMapZoom + 1;
            zoom = Math.min(MAX_ZOOM, zoom);
            if (zoom != mMapZoom) {
                if (!mMapLoading) {
                    mMapZoom = zoom;
                    zoomLabel.setText(String.valueOf(mMapZoom));
                    calcMapCenterPos();
                    calcMapZeroPos(true);
                    maybeLoadMapData(null);
                }
            }
        });
        zoomOutButton.setGraphic(new ImageView(new Image("/images/ui/minus.png")));
        //zoomOutButton.setShape(new Circle(30));
        zoomOutButton.setOnAction(e -> {
            int zoom = mMapZoom - 1;
            zoom = Math.max(MIN_ZOOM, zoom);
            if (!mMapLoading) {
                if (zoom != mMapZoom) {
                    mMapZoom = zoom;
                    zoomLabel.setText(String.valueOf(mMapZoom));
                    calcMapCenterPos();
                    calcMapZeroPos(true);
                    maybeLoadMapData(null);
                }
            }
        });

        trackModeButton.setGraphic(new ImageView(new Image(mTrackMode ? "/images/ui/gps.png" : "/images/ui/gps-circle.png")));
        //trackModeButton.setShape(new Circle(30));
        trackModeButton.setOnAction(event -> {
            if (!mMapLoading) {
                if (!mTrackReplayMode) {
                    mTrackMode = trackModeButton.isSelected();
                    updateTrackMode();
                }
            }
        });

        startReplayButton.setGraphic(new ImageView(new Image("/images/ui/play.png")));
        //startReplayButton.setShape(new Circle(30));
        startReplayButton.setOnAction(event -> {
            if (mTrackReplayThread != null) {
                mTrackReplayThread.startThread();
                startReplayButton.setDisable(true);
                stopReplayButton.setDisable(false);
                pauseReplayButton.setDisable(false);
                stepReplayButton.setDisable(false);
            } else {
                if (mCurrentTrackFile != null) {
                    mTrackReplayThread = new TrackReplayThread();
                    if (!mTrackReplayThread.setupReplay(mCurrentTrackFile, this)) {
                        LogUtils.error("failed to setup replay thread");
                        mTrackReplayThread = null;
                    } else {
                        mTrackReplayMode = true;
                        mTrackReplayThread.startThread();
                        startReplayButton.setDisable(true);
                        stopReplayButton.setDisable(false);
                        pauseReplayButton.setDisable(false);
                        stepReplayButton.setDisable(false);
                    }
                }
            }
        });
        stopReplayButton.setGraphic(new ImageView(new Image("/images/ui/stop.png")));
        //stopReplayButton.setShape(new Circle(30));
        stopReplayButton.setOnAction(event -> {
            startReplayButton.setDisable(false);
            stopReplayButton.setDisable(true);
            pauseReplayButton.setDisable(true);
            stepReplayButton.setDisable(true);

            stopReplay();
            resetTracking();
            drawShapes();
        });
        pauseReplayButton.setGraphic(new ImageView(new Image("/images/ui/pause.png")));
        //pauseReplayButton.setShape(new Circle(30));
        pauseReplayButton.setOnAction(event -> {
            if (mTrackReplayThread != null) {
                mTrackReplayThread.pauseThread();
            }
        });
        stepReplayButton.setGraphic(new ImageView(new Image("/images/ui/next.png")));
        //stepReplayButton.setShape(new Circle(30));
        stepReplayButton.setOnAction(event -> {
            if (mTrackReplayThread != null) {
                mTrackReplayThread.stepThread();
            }
        });

        zoomLabel.setText(String.valueOf(mMapZoom));

        mContextMenu = new ContextMenu();
        mMenuButtonMenu = new ContextMenu();

        mGPSDot = new Circle();
        mGPSDot.setRadius(30);
        mGPSDot.setFill(Color.TRANSPARENT);
        mGPSDot.setStrokeWidth(2);

        //mCenterDot = new Circle();
        //mCenterDot.setStroke(Color.BLACK);
        //mCenterDot.setFill(Color.TRANSPARENT);
        //mCenterDot.setStrokeWidth(3);
        //mCenterDot.setRadius(50);
        //mCenterDot.setVisible(true);

        mRotate = new Rotate(-ROTATE_X_VALUE, Rotate.X_AXIS);
        mZRotate = new Rotate();

        borderPane.setTop(topPane);
        borderPane.setRight(rightPane);
        borderPane.setBottom(infoPane);
        mainPane = new Pane();
        mainStackPane.getChildren().add(mainPane);
        mLoadMapProgress = new ProgressIndicator();
        mainStackPane.getChildren().add(mLoadMapProgress);
        mLoadMapProgress.setVisible(false);

        borderPane.setCenter(mainStackPane);

        topPane.setBackground(new Background(new BackgroundFill(Color.rgb(0, 0, 0, 0.2), null, null)));
        //rightPane.setBackground(new Background(new BackgroundFill(Color.rgb(0, 0, 0, 0.2), null, null)));
        infoPane.setBackground(new Background(new BackgroundFill(Color.rgb(0, 0, 0, 0.2), null, null)));
        bottomPane.setBackground(new Background(new BackgroundFill(Color.rgb(0, 0, 0, 0.2), null, null)));

        DropShadow shadow = new DropShadow(
                BlurType.ONE_PASS_BOX, Color.BLACK, 2, 2, 0, 0);

        zoomLabel.setTextFill(Color.WHITE);
        zoomLabel.setEffect(shadow);
        zoomTitle.setTextFill(Color.WHITE);
        zoomTitle.setEffect(shadow);

        speedLabel.setTextFill(Color.WHITE);
        speedLabel.setEffect(shadow);

        altLabel.setTextFill(Color.WHITE);
        altLabel.setEffect(shadow);

        posLabel.setTextFill(Color.WHITE);
        posLabel.setEffect(shadow);
        posTitle.setTextFill(Color.WHITE);
        posTitle.setEffect(shadow);

        wayLabel.setTextFill(Color.WHITE);
        wayLabel.setEffect(shadow);

        infoLabel.setTextFill(Color.WHITE);
        infoLabel.setEffect(shadow);
        infoTitle.setTextFill(Color.WHITE);
        infoTitle.setEffect(shadow);

        mainPane.getChildren().add(mMapPane);
        mainPane.getChildren().add(mNodePane);
        mainPane.getChildren().add(mRoutingPane);
        mainPane.getChildren().add(mTrackingPane);

        // pass events through
        mNodePane.setDisable(true);
        mRoutingPane.setDisable(true);
        mTrackingPane.setDisable(true);

        borderPane.setOnKeyPressed(new EventHandler<KeyEvent>() {
            @Override
            public void handle(KeyEvent event) {
                switch (event.getCode()) {
                    case ESCAPE:
                        mSelectdShape = null;
                        mSelectdOSMId = -1;
                        mSelectedEdge = null;
                        mSelectedEdgeShape = null;
                        drawShapes();
                        break;
                }
            }
        });

        // NEVER EVER add eventlistener to anythign else
        // needed for correct x,y translation in 3d mode
        mMapPane.addEventFilter(MouseEvent.MOUSE_RELEASED, mouseHandler);
        mMapPane.addEventFilter(MouseEvent.MOUSE_CLICKED, mouseHandler);
        mMapPane.addEventFilter(MouseEvent.MOUSE_DRAGGED, mouseHandler);

        restoreRoutingNodes();
    }

    public void stop() {
        stopGPSTracking();
        Config.getInstance().put("zoom", mMapZoom);
        Config.getInstance().put("lon", mCenterLon);
        Config.getInstance().put("lat", mCenterLat);
        Config.getInstance().put("show3D", mShow3D);
        mExecutorService.shutdown();
    }

    protected void setStage(Stage primaryStage) {
        mPrimaryStage = primaryStage;
    }

    protected void setScene(Scene scene) {
        mScene = scene;
        mScene.widthProperty().addListener((observableValue, oldSceneWidth, newSceneWidth) -> {
            calcMapCenterPos();
            calcMapZeroPos(true);
            drawShapes();
        });
        mScene.heightProperty().addListener((observableValue, oldSceneHeight, newSceneHeight) -> {
            calcMapCenterPos();
            calcMapZeroPos(true);
            drawShapes();
        });
    }

    public void addToOSMCache(long osmId, JsonObject osmObject) {
        mOSMObjects.put(osmId, osmObject);
    }

    private Popup createPopup(String text) {
        Label label = new Label(text);
        Popup popup = new Popup();
        label.setStyle("-fx-font-size: 20; -fx-background-color: white;");
        popup.getContent().add(label);
        label.setMinWidth(250);
        label.setMinHeight(200);
        return popup;
    }

    private List<Integer> getStreetTypeListForZoom() {
        if (mMapZoom <= 8) {
            return null;
        } else if (mMapZoom <= 10) {
            List<Integer> typeFilterList = new ArrayList<>();
            Collections.addAll(typeFilterList, OSMUtils.STREET_TYPE_MOTORWAY,
                    OSMUtils.STREET_TYPE_MOTORWAY_LINK,
                    OSMUtils.STREET_TYPE_TRUNK,
                    OSMUtils.STREET_TYPE_TRUNK_LINK,
                    OSMUtils.STREET_TYPE_PRIMARY,
                    OSMUtils.STREET_TYPE_PRIMARY_LINK);
            return typeFilterList;
        } else if (mMapZoom <= 13) {
            List<Integer> typeFilterList = new ArrayList<>();
            Collections.addAll(typeFilterList, OSMUtils.STREET_TYPE_MOTORWAY,
                    OSMUtils.STREET_TYPE_MOTORWAY_LINK,
                    OSMUtils.STREET_TYPE_TRUNK,
                    OSMUtils.STREET_TYPE_TRUNK_LINK,
                    OSMUtils.STREET_TYPE_PRIMARY,
                    OSMUtils.STREET_TYPE_PRIMARY_LINK,
                    OSMUtils.STREET_TYPE_SECONDARY,
                    OSMUtils.STREET_TYPE_SECONDARY_LINK,
                    OSMUtils.STREET_TYPE_TERTIARY,
                    OSMUtils.STREET_TYPE_TERTIARY_LINK);
            return typeFilterList;
        } else if (mMapZoom <= MAX_ZOOM) {
            List<Integer> typeFilterList = new ArrayList<>();
            Collections.addAll(typeFilterList, OSMUtils.STREET_TYPE_MOTORWAY,
                    OSMUtils.STREET_TYPE_MOTORWAY_LINK,
                    OSMUtils.STREET_TYPE_TRUNK,
                    OSMUtils.STREET_TYPE_TRUNK_LINK,
                    OSMUtils.STREET_TYPE_PRIMARY,
                    OSMUtils.STREET_TYPE_PRIMARY_LINK,
                    OSMUtils.STREET_TYPE_SECONDARY,
                    OSMUtils.STREET_TYPE_SECONDARY_LINK,
                    OSMUtils.STREET_TYPE_TERTIARY,
                    OSMUtils.STREET_TYPE_TERTIARY_LINK,
                    OSMUtils.STREET_TYPE_RESIDENTIAL,
                    OSMUtils.STREET_TYPE_ROAD,
                    OSMUtils.STREET_TYPE_UNCLASSIFIED);
            return typeFilterList;
        } else {
            return null;
        }
    }

    private String getAdminLevelListForZoom() {
        // we alwats show level 2 == countries
        if (mMapZoom <= 8) {
            return "(4)";
        } else if (mMapZoom <= MAX_ZOOM) {
            return "(4, 6)";
        /*} else if (mMapZoom <= MAX_ZOOM) {
            return "(4, 6, 8)";*/
        } else {
            return null;
        }
    }

    private String getAdminLevelListForCountries() {
        return "(2)";
    }

    private List<Integer> getAreaTypeListForZoom() {
        if (mMapZoom <= 11) {
            return null;
        } else if (mMapZoom <= 12) {
            List<Integer> typeFilterList = new ArrayList<>();
            Collections.addAll(typeFilterList, OSMUtils.AREA_TYPE_LANDUSE,
                    OSMUtils.AREA_TYPE_NATURAL,
                    OSMUtils.AREA_TYPE_AEROWAY,
                    OSMUtils.AREA_TYPE_RAILWAY,
                    OSMUtils.AREA_TYPE_WATER);
            return typeFilterList;
        } else if (mMapZoom <= 16) {
            List<Integer> typeFilterList = new ArrayList<>();
            Collections.addAll(typeFilterList, OSMUtils.AREA_TYPE_LANDUSE,
                    OSMUtils.AREA_TYPE_NATURAL,
                    OSMUtils.AREA_TYPE_HIGHWAY_AREA,
                    OSMUtils.AREA_TYPE_AEROWAY,
                    OSMUtils.AREA_TYPE_RAILWAY,
                    OSMUtils.AREA_TYPE_TOURISM,
                    OSMUtils.AREA_TYPE_LEISURE,
                    OSMUtils.AREA_TYPE_WATER);
            return typeFilterList;
        } else if (mMapZoom <= MAX_ZOOM) {
            List<Integer> typeFilterList = new ArrayList<>();
            Collections.addAll(typeFilterList, OSMUtils.AREA_TYPE_LANDUSE,
                    OSMUtils.AREA_TYPE_NATURAL,
                    OSMUtils.AREA_TYPE_HIGHWAY_AREA,
                    OSMUtils.AREA_TYPE_AEROWAY,
                    OSMUtils.AREA_TYPE_RAILWAY,
                    OSMUtils.AREA_TYPE_TOURISM,
                    OSMUtils.AREA_TYPE_LEISURE,
                    OSMUtils.AREA_TYPE_BUILDING,
                    OSMUtils.AREA_TYPE_WATER);
            return typeFilterList;
        } else {
            return null;
        }
    }

    public double getSimplifyToleranceForZoom() {
        if (mMapZoom <= 6) {
            return 200.0;
        } else if (mMapZoom <= 8) {
            return 120.0;
        } else if (mMapZoom <= 10) {
            return 80.0;
        } else if (mMapZoom <= 12) {
            return 40.0;
        } else if (mMapZoom <= 14) {
            return 30.0;
        } else if (mMapZoom <= 16) {
            return 20.0;
        } else {
            return 0;
        }
    }

    private int getAreaMinSizeForZoom() {
        if (mMapZoom <= 12) {
            return 100000;
        } else if (mMapZoom <= 13) {
            return 20000;
        } else if (mMapZoom <= 14) {
            return 10000;
        } else if (mMapZoom <= 16) {
            return 2000;
        } else if (mMapZoom <= MAX_ZOOM) {
            return 0;
        } else {
            return 0;
        }
    }

    private List<Integer> getPoiTypeListForZoom() {
        return OSMUtils.SELECT_POI_TYPE;
    }

    private void doUpdateMapData(List<Double> bbox, Runnable doAfter) {
        LogUtils.log("doUpdateMapData");
        long t = System.currentTimeMillis();

        mMapPane.getChildren().clear();
        mRoutingPane.getChildren().clear();
        mTrackingPane.getChildren().clear();

        if (mSelectdOSMId != -1) {
            mSelectdShape = findShapeOfOSMId(mSelectdOSMId);
            if (mSelectdShape == null) {
                mSelectdOSMId = -1;
            } else {
                mSelectdShape.setSelected();
            }
        }

        if (mTrackingOSMId != -1) {
            mTrackingShape = findShapeOfOSMId(mTrackingOSMId);
            if (mTrackingShape == null) {
                mTrackingOSMId = -1;
            } else {
                mTrackingShape.setTracking();
            }
        }
        for (List<Node> polyList : mCountryPolylines.values()) {
            mMapPane.getChildren().addAll(polyList);
        }
        for (List<Node> polyList : mPolylines.values()) {
            mMapPane.getChildren().addAll(polyList);
        }

        if (mMapZoom >= 16) {
            if (mSelectdShape != null) {
                mRoutingPane.getChildren().add(mSelectdShape.getShape());
            }
            if (mSelectedEdge != null) {
                mSelectedEdgeShape = displayCoordsPolyline((JsonArray) mSelectedEdge.get("coords"));
                mSelectedEdgeShape.setStrokeWidth(2);
                mSelectedEdgeShape.setStroke(Color.GREEN);
                mRoutingPane.getChildren().add(mSelectedEdgeShape);
            }
            if (mTrackingShape != null) {
                mTrackingPane.getChildren().add(mTrackingShape.getShape());
            }
        }
        if (mTrackMode || mTrackReplayMode) {
            if (isPositionVisible(mMapGPSPos)) {
                addGPSDot();
            }
            if (mMapZoom >= 16) {
                if (mPredictionWays.size() != 0) {
                    for (Long osmId : mPredictionWays) {
                        OSMShape s = findShapeOfOSMId(osmId);
                        if (s != null) {
                            s.getShape().setStrokeWidth(2);
                            s.getShape().setStroke(Color.GREEN);
                            mTrackingPane.getChildren().add(s.getShape());
                        }
                    }
                }
            }
        }
        if (mMapZoom >= 14) {
            mRoutingPane.getChildren().addAll(mRoutePolylineList);
        }

        //updateCenterDot();
        //mTrackingPane.getChildren().add(mCenterDot);

        mCalcPoint = new Circle();
        mCalcPoint.setCenterX(0);
        mCalcPoint.setCenterY(0);
        mCalcPoint.setRadius(0);
        mCalcPoint.setVisible(false);
        mTrackingPane.getChildren().add(mCalcPoint);

        mLoadAdminAreaTask = new LoadAdminAreaTask(bbox);

        mLoadAdminAreaTask.setOnSucceeded((succeededEvent) -> {
            LogUtils.log("loadMapData: LoadAdminAreaTask succeededEvent");
            for (List<Node> polyList : mAreaPolylines.values()) {
                mMapPane.getChildren().addAll(polyList);
            }
            drawShapes();
        });

        mExecutorService.submit(mLoadAdminAreaTask);

        mLoadPOITask = new LoadPOITask(bbox);

        mLoadPOITask.setOnSucceeded((succeededEvent) -> {
            LogUtils.log("loadMapData: LoadPOITask succeededEvent");
            drawShapes();
        });

        mExecutorService.submit(mLoadPOITask);
        stopProgress();

        if (doAfter != null) {
            doAfter.run();
        }
        LogUtils.log("doUpdateMapData time = " + (System.currentTimeMillis() - t));
    }

    private void doLoadMapData(List<Double> bbox) {
        LogUtils.log("doLoadMapData");
        mMapLoading = true;

        long t = System.currentTimeMillis();
        // always show countries
        QueryController.getInstance().getAdminAreasInBboxWithGeom(bbox.get(0), bbox.get(1),
                bbox.get(2), bbox.get(3), getAdminLevelListForCountries(), getSimplifyToleranceForZoom(),
                mCountryPolylines, MainController.this);
        LogUtils.log("doLoadMapData getCountriesInBboxWithGeom time = " + (System.currentTimeMillis() - t));

        // cant load areas async cause they are always on lowest level and ways are above
        if (mMapZoom >= 14) {
            t = System.currentTimeMillis();
            QueryController.getInstance().getAreasInBboxWithGeom(bbox.get(0), bbox.get(1),
                    bbox.get(2), bbox.get(3), getAreaTypeListForZoom(), getSimplifyToleranceForZoom(),
                    getAreaMinSizeForZoom(), mPolylines, MainController.this);
            LogUtils.log("doLoadMapData getAreasInBboxWithGeom time = " + (System.currentTimeMillis() - t));

        }
        if (mMapZoom >= 10) {
            t = System.currentTimeMillis();
            QueryController.getInstance().getWaysInBboxWithGeom(bbox.get(0), bbox.get(1),
                    bbox.get(2), bbox.get(3), getStreetTypeListForZoom(), getSimplifyToleranceForZoom(), mPolylines,
                    MainController.this);
            LogUtils.log("doLoadMapData getWaysInBboxWithGeom time = " + (System.currentTimeMillis() - t));
        }
        if (mMapZoom >= 14) {
            createRoutePolylineList(bbox);
        }
    }

    public void loadFirstMapData() {
        startProgress();
        calcMapCenterPos();
        calcMapZeroPos(true);
        loadMapData(null);
    }

    private void maybeLoadMapData(Runnable doAfter) {
        BoundingBox bbox = getVisibleBBox();
        if (!mFetchBBox.contains(bbox)) {
            if (!mMapLoading) {
                loadMapData(doAfter);
            }
        } else {
            drawShapes();
            if (doAfter != null) {
                doAfter.run();
            }
        }
    }

    public void loadMapData(Runnable doAfter) {
        if (mLoadMapDataTask != null) {
            mLoadMapDataTask.cancel();
        }
        LogUtils.log("loadMapData " + mMapZoom);

        mVisibleBBox = getVisibleBBox();
        mFetchBBox = getVisibleBBoxWithMargin(mVisibleBBox);

        if (mLoadPOITask != null) {
            LogUtils.log("loadMapData: LoadPOITask cancel");
            mLoadPOITask.cancel(true);
            mLoadPOITask = null;
        }

        if (mLoadAdminAreaTask != null) {
            LogUtils.log("loadMapData: LoadAreaTask cancel");
            mLoadAdminAreaTask.cancel(true);
            mLoadAdminAreaTask = null;
        }

        mOSMObjects.clear();
        for (List<Node> polyList : mPolylines.values()) {
            polyList.clear();
        }
        for (List<Node> polyList : mCountryPolylines.values()) {
            polyList.clear();
        }
        mRoutePolylineList.clear();

        List<Double> bbox = getBBoxInDeg(mFetchBBox);
        mLoadMapDataTask = new LoadMapDataTask(bbox);
        mLoadMapDataTask.setOnSucceeded((succeededEvent) -> {
            LogUtils.log("loadMapDataTask: LoadMapDataTask succeededEvent");
            doUpdateMapData(bbox, doAfter);
            mMapLoading = false;
        });
        mExecutorService.submit(mLoadMapDataTask);
    }

    private synchronized void drawShapes() {
        //mMapPane.getChildren().clear();
        mNodePane.getChildren().clear();
        mRoutingPane.getChildren().clear();
        mTrackingPane.getChildren().clear();

        /*for (List<Node> polyList : mPolylines.values()) {
            for (Node s : polyList) {
                s.setTranslateX(-mMapZeroX);
                s.setTranslateY(-mMapZeroY);
            }
        }

        for (List<Node> polyList : mAreaPolylines.values()) {
            for (Node s : polyList) {
                s.setTranslateX(-mMapZeroX);
                s.setTranslateY(-mMapZeroY);
            }
        }

        for (List<Node> polyList : mCountryPolylines.values()) {
            for (Node s : polyList) {
                s.setTranslateX(-mMapZeroX);
                s.setTranslateY(-mMapZeroY);
            }
        }*/

        /*if (mSelectdShape != null) {
            mSelectdShape.getShape().setTranslateX(-mMapZeroX);
            mSelectdShape.getShape().setTranslateY(-mMapZeroY);
        }
        if (mSelectedEdgeShape != null) {
            mSelectedEdgeShape.setTranslateX(-mMapZeroX);
            mSelectedEdgeShape.setTranslateY(-mMapZeroY);
        }*/
        if (mTrackingShape != null) {
            mTrackingShape.getShape().setTranslateX(-mMapZeroX);
            mTrackingShape.getShape().setTranslateY(-mMapZeroY);
        }
        /*for (List<Node> polyList : mCountryPolylines.values()) {
            mMapPane.getChildren().addAll(polyList);
        }
        for (List<Node> polyList : mPolylines.values()) {
            mMapPane.getChildren().addAll(polyList);
        }
        for (List<Node> polyList : mAreaPolylines.values()) {
            mMapPane.getChildren().addAll(polyList);
        }*/
        if (mSelectdShape != null) {
            mRoutingPane.getChildren().add(mSelectdShape.getShape());
        }
        if (mSelectedEdgeShape != null) {
            mRoutingPane.getChildren().add(mSelectedEdgeShape);
        }

        //updateCenterDot();
        //mTrackingPane.getChildren().add(mCenterDot);

        if (mTrackingShape != null) {
            mTrackingPane.getChildren().add(mTrackingShape.getShape());
        }
        if (mTrackMode || mTrackReplayMode) {
            if (isPositionVisible(mMapGPSPos)) {
                addGPSDot();
            }
            if (mPredictionWays.size() != 0) {
                for (Long osmId : mPredictionWays) {
                    OSMShape s = findShapeOfOSMId(osmId);
                    if (s != null) {
                        s.getShape().setStrokeWidth(2);
                        s.getShape().setStroke(Color.GREEN);
                        mTrackingPane.getChildren().add(s.getShape());
                    }
                }
            }
        }
        mTrackingPane.getChildren().add(mCalcPoint);

        if (mMapZoom >= 17) {
            for (OSMImageView node : mNodes) {
                updatePOINode(node, false);
            }
            mNodePane.getChildren().addAll(mNodes);
        }

        if (mMapZoom >= 14) {
            for (RoutingNode node : mRoutingNodes) {
                updateRoutingNode(node, false);
            }
            mNodePane.getChildren().addAll(mRoutingNodes);
            mRoutingPane.getChildren().addAll(mRoutePolylineList);
        }
        for (Node s : mNodePane.getChildren()) {
            s.setTranslateX(-mMapZeroX);
            s.setTranslateY(-mMapZeroY);
        }
        for (Node s : mRoutingPane.getChildren()) {
            s.setTranslateX(-mMapZeroX);
            s.setTranslateY(-mMapZeroY);
        }
        for (Node s : mMapPane.getChildren()) {
            s.setTranslateX(-mMapZeroX);
            s.setTranslateY(-mMapZeroY);
        }
    }

    private Point2D calcNodePanePos(Point2D nodePos, Point2D paneZeroPos) {
        mCalcPoint.setCenterY(nodePos.getY());
        mCalcPoint.setCenterX(nodePos.getX());
        mCalcPoint.setTranslateX(-mMapZeroX);
        mCalcPoint.setTranslateY(-mMapZeroY);
        Point2D cPos = mCalcPoint.localToScreen(mCalcPoint.getCenterX(), mCalcPoint.getCenterY());
        Point2D cPosNode = new Point2D(cPos.getX() - paneZeroPos.getX(), cPos.getY() - paneZeroPos.getY());
        return new Point2D(cPosNode.getX() + mMapZeroX, cPosNode.getY() + mMapZeroY);
    }

    private double getPrefetchBoxMargin() {
        return PREFETCH_MARGIN_PIXEL;
    }

    private Double getPixelXPosForLocationDeg(double lon) {
        return getPixelXPosForLocationRad(GISUtils.deg2rad(lon));
    }

    private Double getPixelXPosForLocationRad(double lon) {
        return GISUtils.lon2pixel(mMapZoom, lon);
    }

    private Double getPixelYPosForLocationDeg(double lat) {
        return getPixelYPosForLocationRad(GISUtils.deg2rad(lat));
    }

    private Double getPixelYPosForLocationRad(double lat) {
        return GISUtils.lat2pixel(mMapZoom, lat);
    }

    public Polyline displayCoordsPolyline(long osmId, JsonArray coords) {
        OSMPolyline polyline = new OSMPolyline(osmId);
        Double[] points = new Double[coords.size() * 2];
        int j = 0;
        for (int i = 0; i < coords.size(); i++) {
            JsonArray coord = (JsonArray) coords.get(i);
            double lon = coord.getDouble(0);
            double lat = coord.getDouble(1);

            Double posX = getPixelXPosForLocationDeg(lon);
            Double posY = getPixelYPosForLocationDeg(lat);

            points[j] = posX;
            points[j + 1] = posY;
            j += 2;
        }
        polyline.getPoints().addAll(points);
        polyline.setTranslateX(-mMapZeroX);
        polyline.setTranslateY(-mMapZeroY);
        return polyline;
    }

    public Polyline displayCoordsPolyline(JsonArray coords) {
        Polyline polyline = new Polyline();
        Double[] points = new Double[coords.size() * 2];
        int j = 0;
        for (int i = 0; i < coords.size(); i++) {
            JsonArray coord = (JsonArray) coords.get(i);
            double lon = coord.getDouble(0);
            double lat = coord.getDouble(1);

            Double posX = getPixelXPosForLocationDeg(lon);
            Double posY = getPixelYPosForLocationDeg(lat);

            points[j] = posX;
            points[j + 1] = posY;
            j += 2;
        }
        polyline.getPoints().addAll(points);
        polyline.setTranslateX(-mMapZeroX);
        polyline.setTranslateY(-mMapZeroY);
        return polyline;
    }

    public Polyline clonePolyline(long osmId, Polyline p) {
        OSMPolyline polyline = new OSMPolyline(osmId);
        polyline.getPoints().addAll(p.getPoints());
        polyline.setTranslateX(-mMapZeroX);
        polyline.setTranslateY(-mMapZeroY);
        return polyline;
    }

    public Polygon displayCoordsPolygon(long osmId, int areaType, JsonArray coords) {
        OSMPolygon polygon = new OSMPolygon(osmId, areaType);
        Double[] points = new Double[coords.size() * 2];
        int j = 0;
        for (int i = 0; i < coords.size(); i++) {
            JsonArray coord = (JsonArray) coords.get(i);
            double lon = coord.getDouble(0);
            double lat = coord.getDouble(1);

            Double posX = getPixelXPosForLocationDeg(lon);
            Double posY = getPixelYPosForLocationDeg(lat);

            points[j] = posX;
            points[j + 1] = posY;
            j += 2;
        }
        polygon.getPoints().addAll(points);
        polygon.setTranslateX(-mMapZeroX);
        polygon.setTranslateY(-mMapZeroY);
        return polygon;
    }


    private void centerMapOnLocation(double lon, double lat) {
        mCenterLon = lon;
        mCenterLat = lat;
        calcMapCenterPos();
        calcMapZeroPos(false);
        maybeLoadMapData(() -> showShapeAtCenter());
    }

    private void calcMapCenterPos() {
        mCenterPosX = getPixelXPosForLocationDeg(mCenterLon);
        mCenterPosY = getPixelYPosForLocationDeg(mCenterLat);
    }

    private void calcMapZeroPos(boolean sceneChanged) {
        if (sceneChanged) {
            mRotate.setPivotY(mScene.getHeight() / 2);
            mZRotate.setPivotY(mScene.getHeight() / 2);
            mZRotate.setPivotX(mScene.getWidth() / 2);
            setTransforms();
        }

        mMapZeroX = mCenterPosX - mScene.getWidth() / 2;
        mMapZeroY = mCenterPosY - mScene.getHeight() / 2 + topPane.getHeight();

        if (mTrackMode || mTrackReplayMode) {
            calcGPSPos();
        }
        //QueryMapTilesTask tileTask = new QueryMapTilesTask();
        //mExecutorService.submit(tileTask);
    }

    private void calcCenterCoord() {
        mCenterLon = GISUtils.rad2deg(GISUtils.pixel2lon(mMapZoom, mCenterPosX));
        mCenterLat = GISUtils.rad2deg(GISUtils.pixel2lat(mMapZoom, mCenterPosY));
    }

    private Point2D getCoordOfPos(Point2D mousePos) {
        double lat = GISUtils.pixel2lat(mMapZoom, mMapZeroY + mousePos.getY());
        double lon = GISUtils.pixel2lon(mMapZoom, mMapZeroX + mousePos.getX());
        return new Point2D(GISUtils.rad2deg(lon), GISUtils.rad2deg(lat));
    }

    private void moveMap(double stepX, double stepY) {
        //long t = System.currentTimeMillis();
        double posX = mCenterPosX - mMapZeroX + stepX;
        double posY = mCenterPosY - mMapZeroY + stepY;

        mCenterPosX = mMapZeroX + posX;
        mCenterPosY = mMapZeroY + posY;

        calcCenterCoord();
        calcMapZeroPos(false);
        maybeLoadMapData(() -> {
            infoLabel.setText("");
            posLabel.setText("");
        });
        //LogUtils.log("moveMap " + (System.currentTimeMillis() - t));
    }

    private List<Double> createBBoxAroundPoint(double lon, double lat, double margin) {
        List<Double> bbox = new ArrayList<>();
        double latRangeMax = lat + margin;
        double lonRangeMax = lon + margin * 1.4;
        double latRangeMin = lat - margin;
        double lonRangeMin = lon - margin * 1.4;
        Collections.addAll(bbox, lonRangeMin, latRangeMin, lonRangeMax, latRangeMax);
        return bbox;
    }

    private BoundingBox getVisibleBBox() {
        if (isShow3DActive()) {
            return new BoundingBox(mMapZeroX - mScene.getWidth(), mMapZeroY - mScene.getHeight(),
                    mScene.getWidth() * 3, mScene.getHeight() * 3);
        } else {
            return new BoundingBox(mMapZeroX, mMapZeroY, mScene.getWidth(), mScene.getHeight());
        }
    }

    private BoundingBox getVisibleBBoxWithMargin(BoundingBox bbox) {
        double margin = getPrefetchBoxMargin();
        return new BoundingBox(bbox.getMinX() - margin, bbox.getMinY() - margin,
                bbox.getWidth() + 2 * margin, bbox.getHeight() + 2 * margin);
    }

    private List<Double> getBBoxInDeg(BoundingBox bbox) {
        double lat1 = GISUtils.rad2deg(GISUtils.pixel2lat(mMapZoom, bbox.getMinY()));
        double lon1 = GISUtils.rad2deg(GISUtils.pixel2lon(mMapZoom, bbox.getMinX()));

        double lat2 = GISUtils.rad2deg(GISUtils.pixel2lat(mMapZoom, bbox.getMaxY()));
        double lon2 = GISUtils.rad2deg(GISUtils.pixel2lon(mMapZoom, bbox.getMaxX()));

        List<Double> l = new ArrayList<>();
        Collections.addAll(l, lon1, lat1, lon2, lat2);
        return l;
    }

    public int getZoom() {
        return mMapZoom;
    }

    private OSMShape findShapeAtPoint(Point2D pos, Set<Integer> areaTypes, int layer, Map<
            Integer, List<Node>> polylines) {
        List<Node> polyList = polylines.get(layer);
        // again reverse by layer order
        for (int i = polyList.size() - 1; i >= 0; i--) {
            Node s = polyList.get(i);
            if (s instanceof OSMPolygon) {
                if (areaTypes.size() == 0 || areaTypes.contains(((OSMPolygon) s).getAreaType())) {
                    if (s.contains(pos)) {
                        OSMPolygon polygon = new OSMPolygon((OSMPolygon) s);
                        polygon.getPoints().addAll(((OSMPolygon) s).getPoints());
                        polygon.setTranslateX(-mMapZeroX);
                        polygon.setTranslateY(-mMapZeroY);
                        return polygon;
                    }
                }
            } else if (s instanceof OSMPolyline) {
                if (s.contains(pos)) {
                    OSMPolyline polyline = new OSMPolyline((OSMPolyline) s);
                    polyline.getPoints().addAll(((OSMPolyline) s).getPoints());
                    polyline.setTranslateX(-mMapZeroX);
                    polyline.setTranslateY(-mMapZeroY);
                    return polyline;
                }
            } else if (s instanceof ImageView) {
                // POI images
                if (s.contains(pos)) {
                    return null;
                }
            }
        }
        return null;
    }

    // returns a copy
    private OSMShape findShapeAtPoint(Point2D pos, Set<Integer> areaTypes) {
        // from top layer down
        List<Integer> keyList = new ArrayList<>();
        keyList.addAll(mPolylines.keySet());
        Collections.reverse(keyList);

        for (int layer : keyList) {
            OSMShape shape = findShapeAtPoint(pos, areaTypes, layer, mPolylines);
            if (shape != null) {
                return shape;
            }
            shape = findShapeAtPoint(pos, areaTypes, layer, mAreaPolylines);
            if (shape != null) {
                return shape;
            }
        }
        return null;
    }

    private OSMPolyline findWayAtPoint(Point2D pos) {
        JsonArray edgeList = QueryController.getInstance().getEdgeOnPos(pos.getX(), pos.getY(), 0.0005, 30, 20);
        if (edgeList.size() != 0) {
            JsonObject edge = (JsonObject) edgeList.get(0);
            long osmId = (long) edge.get("wayId");
            OSMPolyline shape = findWayOfOSMId(osmId);
            if (shape != null) {
                return shape;
            }
        }
        return null;
    }

    private OSMShape findShapeOfOSMId(long osmId, Map<Integer, List<Node>> polylines) {
        for (List<Node> polyList : polylines.values()) {
            for (Node s : polyList) {
                if (s instanceof OSMPolygon) {
                    if (((OSMPolygon) s).getOSMId() == osmId) {
                        OSMPolygon polygon = new OSMPolygon((OSMPolygon) s);
                        polygon.getPoints().addAll(((OSMPolygon) s).getPoints());
                        polygon.setTranslateX(-mMapZeroX);
                        polygon.setTranslateY(-mMapZeroY);
                        return polygon;
                    }
                } else if (s instanceof OSMPolyline) {
                    if (((OSMPolyline) s).getOSMId() == osmId) {
                        OSMPolyline polyline = new OSMPolyline((OSMPolyline) s);
                        polyline.getPoints().addAll(((OSMPolyline) s).getPoints());
                        polyline.setTranslateX(-mMapZeroX);
                        polyline.setTranslateY(-mMapZeroY);
                        return polyline;
                    }
                }
            }
        }
        return null;
    }

    private OSMShape findShapeOfOSMId(long osmId) {
        OSMShape shape = findShapeOfOSMId(osmId, mPolylines);
        if (shape != null) {
            return shape;
        }
        shape = findShapeOfOSMId(osmId, mAreaPolylines);
        if (shape != null) {
            return shape;
        }
        return null;
    }

    private OSMPolyline findWayOfOSMId(long osmId) {
        for (List<Node> polyList : mPolylines.values()) {
            for (Node s : polyList) {
                if (s instanceof OSMPolyline) {
                    if (((OSMPolyline) s).getOSMId() == osmId) {
                        OSMPolyline polyline = new OSMPolyline((OSMPolyline) s);
                        polyline.getPoints().addAll(((OSMPolyline) s).getPoints());
                        polyline.setTranslateX(-mMapZeroX);
                        polyline.setTranslateY(-mMapZeroY);
                        return polyline;
                    }
                }
            }
        }
        return null;
    }

    private void updateGPSPos(JsonObject gpsData, boolean force) {
        double lat = ((BigDecimal) gpsData.get("lat")).doubleValue();
        double lon = ((BigDecimal) gpsData.get("lon")).doubleValue();
        if (lat == -1 || lon == -1) {
            return;
        }
        boolean hasMoved = false;

        if (mGPSData != null) {
            if (lat != ((BigDecimal) mGPSData.get("lat")).doubleValue() || lon != ((BigDecimal) mGPSData.get("lon")).doubleValue()) {
                int speed = ((BigDecimal) gpsData.get("speed")).intValue();
                if (speed > 1) {
                    hasMoved = true;
                }
            }
        } else {
            hasMoved = true;
        }

        if ((force || hasMoved) && (mTrackMode || mTrackReplayMode)) {
            mGPSData = gpsData;
            if (!mTrackReplayMode) {
                GPSUtils.addGPSData(mGPSData);
            }
            mGPSPos = new Point2D(lon, lat);
            moveToGPSPos();
        }
    }

    private void moveToGPSPos() {
        if (mGPSData == null) {
            return;
        }
        //LogUtils.log("moveToGPSPos " + mGPSPos);
        mCenterLat = mGPSPos.getY();
        mCenterLon = mGPSPos.getX();
        int bearing = ((BigDecimal) mGPSData.get("bearing")).intValue();
        if (bearing != -1) {
            mZRotate.setAngle(360 - bearing);
            mGPSDot.setRotate(bearing);
        }

        posLabel.setText(String.format("%.5f:%.5f", mCenterLon, mCenterLat));
        int speed = ((BigDecimal) mGPSData.get("speed")).intValue();
        speedLabel.setText((int) (speed * 3.6) + "km/h");
        int alt = ((BigDecimal) mGPSData.get("altitude")).intValue();
        altLabel.setText(alt + "m");

        calcMapCenterPos();
        calcMapZeroPos(false);
        maybeLoadMapData(null);

        Task<Void> findEdge = new Task() {
            @Override
            protected Object call() throws Exception {
                boolean foundEdge = false;
                long t = System.currentTimeMillis();


                if (mCurrentEdge == null) {
                    // #1 if we know nothing just pick the closest edge we can find in an area
                    // closest point of the edge with max 30m away
                    LogUtils.log(LogUtils.TAG_TRACKING, "search nearest edge");
                    JsonArray edgeList = QueryController.getInstance().getEdgeOnPos(mGPSPos.getX(), mGPSPos.getY(), 0.0005, 30, 20);
                    if (edgeList.size() != 0) {
                        LogUtils.log(LogUtils.TAG_TRACKING, "possible edges " + edgeList);

                        JsonObject edge = (JsonObject) edgeList.get(0);
                        LogUtils.log(LogUtils.TAG_TRACKING, "use minimal distance edge " + edge);
                        mCurrentEdge = edge;
                        mLastUsedEdge = mCurrentEdge;
                        foundEdge = true;

                        // find out where we are going and set mNextRefId
                        calcApproachingRef(bearing);
                        JsonArray nextEdgeList = QueryController.getInstance().getEdgesWithStartOrEndRef(mNextRefId, -1);
                        calPredicationWays(nextEdgeList);
                    }
                } else {
                    // #2 we caclulated mNextEdgeList in calPredicationWays in the last run
                    // use that to filter edges that are around the current position as possible
                    // next edges
                    List<Double> bbox = createBBoxAroundPoint(mGPSPos.getX(), mGPSPos.getY(), 0.00008);
                    Map<Long, JsonObject> edgeMap = QueryController.getInstance().getEdgesAroundPointWithGeom(bbox.get(0), bbox.get(1), bbox.get(2), bbox.get(3));

                    LogUtils.log(LogUtils.TAG_TRACKING, "getEdgesAroundPointWithGeom = " + edgeMap.keySet());
                    boolean searchNextEdge = true;
                    if (mCurrentEdge != null) {
                        long currentEdgeId = (long) mCurrentEdge.get("id");
                        if (edgeMap.containsKey(currentEdgeId)) {
                            LogUtils.log(LogUtils.TAG_TRACKING, "prefer current edge");
                            searchNextEdge = false;
                            foundEdge = true;

                            // find out where we are going and set mNextRefId
                            if (mNextRefId == -1) {
                                calcApproachingRef(bearing);
                                JsonArray nextEdgeList = QueryController.getInstance().getEdgesWithStartOrEndRef(mNextRefId, -1);
                                calPredicationWays(nextEdgeList);
                            }
                        }

                        if (searchNextEdge) {
                            JsonObject nextEdge = null;
                            boolean foundNext = false;

                            // first check if any edge that is in mNextEdgeList
                            // has fallen outside of edgeMap - if yes remove that one from mNextEdgeList
                            JsonArray nextEdgeList = new JsonArray();
                            for (int i = 0; i < mNextEdgeList.size(); i++) {
                                JsonObject edge = (JsonObject) mNextEdgeList.get(i);
                                long edgeId = (long) edge.get("id");
                                if (edgeMap.containsKey(edgeId)) {
                                    nextEdgeList.add(edge);
                                } else {
                                    LogUtils.log(LogUtils.TAG_TRACKING, "remove prediction edge outside if possible edges " + edgeId);
                                }
                            }
                            mNextEdgeList = nextEdgeList;

                            // calculate heading of all edges in mNextEdgeList based on mNextRefId
                            // if there are edges that have a diff of heading vs. current bearing  < 15 deg
                            // add as possible next edges
                            JsonArray headingEdges = getNextEdgeWithBestHeading(bearing);
                            long headingEdgeId = -1;
                            JsonObject headingEdge = null;
                            if (headingEdges.size() > 1) {
                                // we have more then one edge with a close enough heading
                                // instead of continue and maybe pick the wrong cancel this run
                                // and wait for the next position and start with #1
                                LogUtils.log(LogUtils.TAG_TRACKING, "delay because multiple best heading matching edges: " + headingEdges.size());
                                // delay to resolve from pos in next round
                                /*headingEdge = getClosestEdge(mGPSPos.getX(), mGPSPos.getY(), headingEdges, 30);
                                if (headingEdge != null) {
                                    headingEdgeId = (long) headingEdge.get("edgeId");
                                    System.out.println("picked heading matching edge: " + headingEdgeId);
                                }*/
                                mCurrentEdge = null;
                                return null;
                            } else if (headingEdges.size() == 1) {
                                // we have exactly once edge with a close enough heading
                                // remember that for later
                                headingEdge = (JsonObject) headingEdges.get(0);
                                headingEdgeId = (long) headingEdge.get("id");
                                nextEdge = headingEdge;
                                foundNext = true;
                                LogUtils.log(LogUtils.TAG_TRACKING, "one best heading matching edge: " + headingEdgeId);
                            }
                            // filter out mNextEdgeList which edges are still in area
                            // the edges in mNextEdgeList are sorted with the best matching one
                            // on position 0 so try that one first
                            /*for (JsonObject edge : edgeMap.values()) {
                                long edgeId = (long) edge.get("id");
                                if (edgeId == currentEdgeId) {
                                    continue;
                                }

                                if (mNextEdgeList.size() > 0) {
                                    JsonObject firstEdge = (JsonObject) mNextEdgeList.get(0);
                                    long firstEdgeId = (long) firstEdge.get("edgeId");
                                    if (firstEdgeId == edgeId) {
                                        System.out.println("found matching first edge: " + firstEdge);
                                        foundNext = true;
                                        nextEdge = firstEdge;
                                        break;
                                    }
                                }
                            }
                            if (!foundNext) {
                                // if needed try all the other edges
                                for (JsonObject edge : edgeMap.values()) {
                                    long edgeId = (long) edge.get("id");
                                    if (edgeId == currentEdgeId) {
                                        continue;
                                    }

                                    if (mNextEdgeList.size() > 0) {
                                        for (int j = 1; j < mNextEdgeList.size(); j++) {
                                            JsonObject possibleEdge = (JsonObject) mNextEdgeList.get(j);
                                            long possibleEdgeId = (long) possibleEdge.get("id");
                                            if (possibleEdgeId == edgeId) {
                                                System.out.println("found matching next edge: " + possibleEdge);
                                                foundNext = true;
                                                nextEdge = possibleEdge;
                                                break;
                                            }
                                        }
                                    }
                                }
                            }*/
                            if (foundNext && nextEdge != null) {
                                // but a edge with better heading will overwrite all of this above
                                long nextEdgeId = (long) nextEdge.get("id");
                                if (headingEdgeId != -1) {
                                    if (headingEdgeId != nextEdgeId) {
                                        LogUtils.log(LogUtils.TAG_TRACKING, "headingEdgeId = " + headingEdgeId + " nextEdgeId = " + nextEdgeId);
                                        nextEdge = headingEdge;
                                    } else {
                                        LogUtils.log(LogUtils.TAG_TRACKING, "best heading edge picked");
                                    }
                                }
                                long nextStartRef = (long) nextEdge.get("startRef");
                                long nextEndRef = (long) nextEdge.get("endRef");

                                if (mCurrentEdge != null) {
                                    long currStartRef = (long) mCurrentEdge.get("startRef");
                                    long currEndRef = (long) mCurrentEdge.get("endRef");

                                    if (nextStartRef == currEndRef || nextStartRef == currStartRef) {
                                        mNextRefId = nextEndRef;
                                    } else if (nextEndRef == currEndRef || nextEndRef == currStartRef) {
                                        mNextRefId = nextStartRef;
                                    }
                                }
                                mCurrentEdge = nextEdge;
                                mLastUsedEdge = mCurrentEdge;
                                foundEdge = true;

                                JsonArray edgeList = null;
                                if (mNextRefId != -1) {
                                    edgeList = QueryController.getInstance().getEdgesWithStartOrEndRef(mNextRefId, -1);
                                } else {
                                    edgeList = QueryController.getInstance().getEdgesWithStartOrEndRef(nextStartRef, nextEndRef);
                                }
                                calPredicationWays(edgeList);
                            }
                        }
                    }
                }

                if (!foundEdge || mCurrentEdge == null) {
                    LogUtils.log(LogUtils.TAG_TRACKING, "no matching next edge found");
                    mCurrentEdge = null;
                } else {
                    if (mCurrentEdge != null) {
                        long wayId = (long) mCurrentEdge.get("wayId");
                        OSMPolyline shape = findWayOfOSMId(wayId);
                        if (shape != null) {
                            mTrackingOSMId = wayId;
                            mTrackingShape = shape;
                            mTrackingShape.setTracking();

                            JsonObject way = mOSMObjects.get(mTrackingOSMId);
                            if (way != null) {
                                JsonObject tags = (JsonObject) way.get("tags");
                                if (tags != null) {
                                    String name = (String) tags.get("name");
                                    if (name != null) {
                                        Platform.runLater(() -> {
                                            wayLabel.setText(name);
                                        });
                                    }
                                }
                            }
                        }
                    }
                }

                return null;
            }
        };

        mExecutorService.submit(findEdge);
        //new Thread(findEdge).start();
    }

    private void calPredicationWays(JsonArray nextEdgeList) {
        Objects.requireNonNull(mCurrentEdge, "calPredicationWays");

        mPredictionWays.clear();
        mNextEdgeList = new JsonArray();

        LinkedHashMap<Integer, JsonArray> predictionMap = new LinkedHashMap<>();
        long currentEdgeId = (long) mCurrentEdge.get("id");
        int currentStreetInfo = (int) mCurrentEdge.get("streetInfo");
        int currentStreetTypeId = currentStreetInfo & 15;

        for (int j = 0; j < nextEdgeList.size(); j++) {
            JsonObject nextEdge = (JsonObject) nextEdgeList.get(j);
            long predictionWayId = (long) nextEdge.get("wayId");
            long predictionEdgeId = (long) nextEdge.get("id");
            if (predictionEdgeId == currentEdgeId) {
                continue;
            }
            JsonObject way = mOSMObjects.get(predictionWayId);
            if (way != null) {
                int quality = 0;
                int streetInfo = (int) way.get("streetInfo");
                int streetTypeId = streetInfo & 15;
                int oneway = (streetInfo & 63) >> 4;
                int roundabout = (streetInfo & 127) >> 6;

                if (mNextRefId != -1) {
                    if (oneway != 0) {
                        if (!OSMUtils.isValidOnewayEnter(oneway, mNextRefId, nextEdge)) {
                            LogUtils.log(LogUtils.TAG_TRACKING, "forbidden oneway enter " + nextEdge);
                            continue;
                        }
                    }
                    if (roundabout != 0) {
                        long startRef = (long) nextEdge.get("startRef");
                        if (mNextRefId != startRef) {
                            LogUtils.log(LogUtils.TAG_TRACKING, "forbidden roundabout left turn " + nextEdge);
                            continue;
                        }
                    }
                }
                if (streetTypeId == currentStreetTypeId) {
                    quality += 10;
                }
                if (predictionMap.containsKey(quality)) {
                    predictionMap.get(quality).add(nextEdge);
                } else {
                    JsonArray edgeList = new JsonArray();
                    edgeList.add(nextEdge);
                    predictionMap.put(quality, edgeList);
                }
                mPredictionWays.add((long) nextEdge.get("wayId"));
            }
        }
        ArrayList<Integer> qualityList = new ArrayList<>(predictionMap.keySet());
        Collections.sort(qualityList);

        LogUtils.log(LogUtils.TAG_TRACKING, "calPredicationWays currentEdge = " + currentEdgeId + " streetTypeId= " + currentStreetTypeId + " qualityList = " + qualityList);

        for (int i = qualityList.size() - 1; i >= 0; i--) {
            JsonArray edgeList = predictionMap.get(qualityList.get(i));
            for (int j = 0; j < edgeList.size(); j++) {
                JsonObject nextEdge = (JsonObject) edgeList.get(j);
                int streetInfo = (int) nextEdge.get("streetInfo");
                int streetTypeId = streetInfo & 15;
                mNextEdgeList.add(nextEdge);
                LogUtils.log(LogUtils.TAG_TRACKING, "calPredicationWays nextEdge = " + nextEdge + " streetTypeId = " + streetTypeId);
            }
        }
    }

    private boolean isPositionVisible(Point2D mapPos) {
        return mFetchBBox.contains(mapPos);
    }

    private void addGPSDot() {
        mGPSDot.setStroke(mTrackReplayMode ? Color.RED : Color.BLACK);
        mTrackingPane.getChildren().add(mGPSDot);
    }

    private void calcGPSPos() {
        mMapGPSPos = new Point2D(getPixelXPosForLocationDeg(mGPSPos.getX()),
                getPixelYPosForLocationDeg(mGPSPos.getY()));
        mGPSDot.setCenterX(mMapGPSPos.getX() - mMapZeroX);
        mGPSDot.setCenterY(mMapGPSPos.getY() - mMapZeroY);
    }

    @Override
    public void onLocation(JsonObject gpsData) {
        Platform.runLater(() -> updateGPSPos(gpsData, false));
    }

    @Override
    public void onLocation(JsonObject gpsData, boolean force) {
        Platform.runLater(() -> updateGPSPos(gpsData, force));
    }

    /*private void updateCenterDot() {
        Point2D centerPos = new Point2D(getPixelXPosForLocationDeg(mCenterLon),
                getPixelYPosForLocationDeg(mCenterLat));
        mCenterDot.setCenterX(centerPos.getX() - mMapZeroX);
        mCenterDot.setCenterY(centerPos.getY() - mMapZeroY);
    }*/

    private void updateTrackMode() {
        if (mTrackMode) {
            mGPSData = null;
            mGPSThread = new GPSThread();
            if (!mGPSThread.startThread(MainController.this)) {
                LogUtils.error("open port " + GPSThread.DEV_TTY_ACM_0 + " failed");
                mMapGPSPos = new Point2D(0, 0);
            } else {
                try {
                    GPSUtils.startTrackLog();
                } catch (IOException e) {
                    LogUtils.error("start GPS tracker failed", e);
                }
            }
        } else {
            if (mGPSThread != null) {
                stopGPSTracking();
                mMapGPSPos = new Point2D(0, 0);
                mGPSData = null;
                mZRotate.setAngle(0);
                drawShapes();
            }
        }
        trackModeButton.setGraphic(new ImageView(new Image(mTrackMode ? "/images/ui/gps.png" : "/images/ui/gps-circle.png")));
    }

    private void stopReplay() {
        if (mTrackReplayThread != null) {
            mTrackReplayMode = false;
            mTrackReplayThread.stopThread();
            try {
                mTrackReplayThread.join();
            } catch (InterruptedException e) {
            }
            mTrackReplayThread = null;
        }
        posLabel.setText("");
        speedLabel.setText("");
        altLabel.setText("");
        wayLabel.setText("");
    }

    private void stopGPSTracking() {
        if (mGPSThread != null) {
            GPSUtils.stopTrackLog();
            mTrackMode = false;
            mGPSThread.stopThread();
            try {
                mGPSThread.join();
            } catch (InterruptedException e) {
            }
        }
        posLabel.setText("");
        speedLabel.setText("");
        altLabel.setText("");
        wayLabel.setText("");
    }

    private void setTransforms() {
        if (isShow3DActive()) {
            mMapPane.getTransforms().clear();
            mMapPane.getTransforms().add(mRotate);
            mMapPane.getTransforms().add(mZRotate);

            mRoutingPane.getTransforms().clear();
            mRoutingPane.getTransforms().add(mRotate);
            mRoutingPane.getTransforms().add(mZRotate);

            mTrackingPane.getTransforms().clear();
            mTrackingPane.getTransforms().add(mRotate);
            mTrackingPane.getTransforms().add(mZRotate);
        } else {
            mMapPane.getTransforms().clear();
            mMapPane.getTransforms().add(mZRotate);

            mRoutingPane.getTransforms().clear();
            mRoutingPane.getTransforms().add(mZRotate);

            mTrackingPane.getTransforms().clear();
            mTrackingPane.getTransforms().add(mZRotate);
        }
    }

    private JsonArray getNextEdgeWithBestHeading(int bearing) {
        Objects.requireNonNull(mCurrentEdge, "getNextEdgeWithBestHeading");
        long currentEdgeId = (long) mCurrentEdge.get("id");
        JsonArray bestEdgeList = new JsonArray();
        for (int i = 0; i < mNextEdgeList.size(); i++) {
            JsonObject edge = (JsonObject) mNextEdgeList.get(i);
            long edgeId = (long) edge.get("id");
            if (edgeId == currentEdgeId) {
                continue;
            }
            JsonArray coords = (JsonArray) edge.get("coords");
            JsonArray pos0 = null;
            JsonArray pos1 = null;
            int diff1 = 360;
            int diff2 = 360;

            if (mNextRefId != -1) {
                long startRef = (long) edge.get("startRef");
                long endRef = (long) edge.get("endRef");
                if (mNextRefId == startRef) {
                    pos0 = (JsonArray) coords.get(0);
                    pos1 = (JsonArray) coords.get(1);
                } else if (mNextRefId == endRef) {
                    pos0 = (JsonArray) coords.get(coords.size() - 1);
                    pos1 = (JsonArray) coords.get(coords.size() - 2);
                }

                int heading = GISUtils.headingDegrees((double) pos0.get(0), (double) pos0.get(1), (double) pos1.get(0), (double) pos1.get(1));
                diff1 = GISUtils.headingDiffAbsolute(bearing, heading);
            } else {
                pos0 = (JsonArray) coords.get(0);
                pos1 = (JsonArray) coords.get(coords.size() - 1);

                int heading1 = GISUtils.headingDegrees((double) pos0.get(0), (double) pos0.get(1), (double) pos1.get(0), (double) pos1.get(1));
                int heading2 = GISUtils.headingDegrees((double) pos1.get(0), (double) pos1.get(1), (double) pos0.get(0), (double) pos0.get(1));
                diff1 = GISUtils.headingDiffAbsolute(bearing, heading1);
                diff2 = GISUtils.headingDiffAbsolute(bearing, heading2);
            }

            LogUtils.log(LogUtils.TAG_TRACKING, "getNextEdgeWithBestHeading " + diff1 + " " + diff2);

            if (diff1 < 30 || diff2 < 30) {
                bestEdgeList.add(edge);
            }
        }
        return bestEdgeList;
    }

    public JsonObject getClosestEdge(double lon, double lat, JsonArray edgeList, int maxDistance) {
        JsonObject closestEdge = null;
        for (int i = 0; i < edgeList.size(); i++) {
            JsonObject edge = (JsonObject) edgeList.get(i);
            JsonArray coords = (JsonArray) edge.get("coords");
            JsonArray coord = (JsonArray) coords.get(0);
            double lon1 = coord.getDouble(0);
            double lat1 = coord.getDouble(1);
            int minDistance = maxDistance;
            for (int j = 1; j < coords.size(); j++) {
                coord = (JsonArray) coords.get(j);
                double lon2 = coord.getDouble(0);
                double lat2 = coord.getDouble(1);

                int distance = GISUtils.isMinimalDistanceOnLineBetweenPoints(lon, lat, lon1, lat1, lon2, lat2, maxDistance);
                if (distance != -1) {
                    if (distance < minDistance) {
                        minDistance = distance;
                        closestEdge = edge;
                    }
                }
            }
        }
        return closestEdge;
    }

    private void resetTracking() {
        mCurrentEdge = null;
        mNextRefId = -1;
        mLastUsedEdge = null;
        if (mNextEdgeList != null) {
            mNextEdgeList.clear();
        }
        if (mPredictionWays != null) {
            mPredictionWays.clear();
        }
        mTrackingShape = null;
    }

    // find out which way we are going on mCurrentEdge
    // based on current pos and bearing and heading to first and last pos of this edge
    private void calcApproachingRef(int bearing) {
        Objects.requireNonNull(mCurrentEdge, "calcApproachingRef");
        long currStartRef = (long) mCurrentEdge.get("startRef");
        long currEndRef = (long) mCurrentEdge.get("endRef");

        JsonArray coords = (JsonArray) mCurrentEdge.get("coords");
        JsonArray pos0 = (JsonArray) coords.get(0);
        JsonArray pos1 = (JsonArray) coords.get(coords.size() - 1);

        int heading1 = GISUtils.headingDegrees(mGPSPos.getX(), mGPSPos.getY(), (double) pos0.get(0), (double) pos0.get(1));
        int heading2 = GISUtils.headingDegrees(mGPSPos.getX(), mGPSPos.getY(), (double) pos1.get(0), (double) pos1.get(1));

        int diff1 = GISUtils.headingDiffAbsolute(bearing, heading1);
        int diff2 = GISUtils.headingDiffAbsolute(bearing, heading2);

        if (diff1 < diff2) {
            mNextRefId = currStartRef;
        } else {
            mNextRefId = currEndRef;
        }
        LogUtils.log(LogUtils.TAG_TRACKING, "calcApproachingRef = " + mNextRefId);
    }

    private boolean isShow3DActive() {
        return mMapZoom >= 14 && mShow3D;
    }

    private void addRoutingNode(RoutingNode.TYPE type, Point2D coordsPos, long edgeId, long wayId) {
        RoutingNode routingNode = new RoutingNode(type, coordsPos, edgeId, wayId);
        updateRoutingNode(routingNode, true);
        mRoutingNodes.add(routingNode);
        saveRoutingNodes();
    }

    private void updateRoutingNode(RoutingNode routingNode, boolean updateSize) {
        if (updateSize) {
            int size = OSMStyle.getPoiSizeForZoom(mMapZoom, 48);
            routingNode.setFitHeight(size);
            routingNode.setFitWidth(size);
            routingNode.setPreserveRatio(true);
        }
        Point2D coordsPos = routingNode.getCoordsPos();
        Double posX = getPixelXPosForLocationDeg(coordsPos.getX());
        Double posY = getPixelYPosForLocationDeg(coordsPos.getY());

        Point2D nodePos = new Point2D(posX, posY);
        Point2D paneZeroPos = mNodePane.localToScreen(0, 0);
        Point2D pos = calcNodePanePos(nodePos, paneZeroPos);
        routingNode.setX(pos.getX() - routingNode.getFitWidth() / 2);
        routingNode.setY(pos.getY() - routingNode.getFitHeight());
        //routingNode.setTranslateX(-mMapZeroX);
        //routingNode.setTranslateY(-mMapZeroY);
    }

    private void updatePOINode(OSMImageView node, boolean updateSize) {
        if (updateSize) {
            int size = OSMStyle.getPoiSizeForZoom(mMapZoom, 48);
            node.setFitHeight(size);
            node.setFitWidth(size);
            node.setPreserveRatio(true);
        }
        Point2D paneZeroPos = mNodePane.localToScreen(0, 0);
        Point2D nodePos = node.getPos();
        Point2D pos = calcNodePanePos(nodePos, paneZeroPos);
        node.setX(pos.getX() - node.getFitWidth() / 2);
        node.setY(pos.getY() - node.getFitHeight());
        //node.setTranslateX(-mMapZeroX);
        //node.setTranslateY(-mMapZeroY);
    }

    private void buildContextMenu() {
        mContextMenu.getItems().clear();
        MenuItem menuItem;

        RoutingNode node = getSelectedRoutingNode(mMouseClickedNodePos);
        if (node != null) {
            menuItem = new MenuItem(" Remove");
            menuItem.setOnAction(ev -> {
                mRoutingNodes.remove(node);
                saveRoutingNodes();
                drawShapes();
            });
            menuItem.setStyle("-fx-font-size: 20");
            mContextMenu.getItems().add(menuItem);
        } else {
            if (!hasRoutingStart() || !hasRoutingEnd()) {
                menuItem = new MenuItem(hasRoutingStart() ? " Add finish" : " Add start");
                menuItem.setOnAction(ev -> {
                    if (mSelectedEdge != null) {
                        addRoutingNode(hasRoutingStart() ? RoutingNode.TYPE.END : RoutingNode.TYPE.START, mMouseClickedCoordsPos,
                                (long) mSelectedEdge.get("id"),
                                (long) mSelectedEdge.get("wayId"));
                        drawShapes();
                    }
                });
                menuItem.setStyle("-fx-font-size: 20");
                mContextMenu.getItems().add(menuItem);
            }
        }
        if (hasRoutingStart() && hasRoutingEnd()) {
            menuItem = new MenuItem(" Calc route ");
            menuItem.setOnAction(ev -> {
                CalcRouteTask task = new CalcRouteTask(getRoutingStart().getEdgeId(), getRoutingEnd().getEdgeId());
                task.setOnSucceeded((event) -> {
                    LogUtils.log("CalcRouteTask succeededEvent");
                    stopProgress();
                    mRouteEdgeIdList = task.getRouteEdgeIdList();
                    List<Double> bbox = getBBoxInDeg(mFetchBBox);
                    createRoutePolylineList(bbox);
                    drawShapes();
                });
                task.setOnRunning((event) -> {
                    startProgress();
                });
                task.setOnCancelled((event) -> {
                    stopProgress();
                });
                mExecutorService.submit(task);
            });
            menuItem.setStyle("-fx-font-size: 20");
            mContextMenu.getItems().add(menuItem);
        }
        if (hasRoutingStart() || hasRoutingEnd()) {
            menuItem = new MenuItem(" Clear route ");
            menuItem.setOnAction(ev -> {
                mRoutingNodes.clear();
                saveRoutingNodes();
                drawShapes();
            });
            menuItem.setStyle("-fx-font-size: 20");
            mContextMenu.getItems().add(menuItem);
        }
        if (hasRoutingStart()) {
            menuItem = new MenuItem(" Go to start ");
            menuItem.setOnAction(ev -> {
                Point2D pos = getRoutingStart().getCoordsPos();
                centerMapOnLocation(pos.getX(), pos.getY());
            });
            menuItem.setStyle("-fx-font-size: 20");
            mContextMenu.getItems().add(menuItem);
        }
        if (hasRoutingEnd()) {
            menuItem = new MenuItem(" Go to finish ");
            menuItem.setOnAction(ev -> {
                Point2D pos = getRoutingEnd().getCoordsPos();
                centerMapOnLocation(pos.getX(), pos.getY());
            });
            menuItem.setStyle("-fx-font-size: 20");
            mContextMenu.getItems().add(menuItem);
        }
        mContextMenu.getItems().add(new SeparatorMenuItem());

        menuItem = new MenuItem(" Toggle 3D ");
        menuItem.setOnAction(ev -> {
            mShow3D = !mShow3D;
            calcMapCenterPos();
            calcMapZeroPos(true);
            maybeLoadMapData(null);
        });
        menuItem.setStyle("-fx-font-size: 20");
        mContextMenu.getItems().add(menuItem);

        mContextMenu.getItems().add(new SeparatorMenuItem());

        if (mLocationHistory.size() != 0 && mLocationHistoryIndex != 0) {
            menuItem = new MenuItem(" Back ");
            menuItem.setOnAction(ev -> {
                goBackInLocationHistory();
            });
            menuItem.setStyle("-fx-font-size: 20");
            mContextMenu.getItems().add(menuItem);
        }
        if (mLocationHistory.size() != 0 && mLocationHistoryIndex < mLocationHistory.size() - 1) {
            menuItem = new MenuItem(" Forward ");
            menuItem.setOnAction(ev -> {
                goForwardInLocationHistory();
            });
            menuItem.setStyle("-fx-font-size: 20");
            mContextMenu.getItems().add(menuItem);
        }
    }

    private void buildButtonMenu() {
        mMenuButtonMenu.getItems().clear();
        MenuItem menuItem = new MenuItem(" Load track ");
        menuItem.setOnAction(ev -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Track file");
            File logDir = new File(System.getProperty("user.dir"), "logs");
            fileChooser.setInitialDirectory(logDir);
            mCurrentTrackFile = fileChooser.showOpenDialog(mPrimaryStage);
            if (mCurrentTrackFile != null) {
                // stop current first
                if (mTrackReplayThread != null) {
                    mTrackReplayMode = false;
                    mTrackReplayThread.stopThread();
                    try {
                        mTrackReplayThread.join();
                    } catch (InterruptedException e) {
                    }
                    mTrackReplayThread = null;
                }

                mTrackReplayThread = new TrackReplayThread();
                if (!mTrackReplayThread.setupReplay(mCurrentTrackFile, this)) {
                    LogUtils.error("failed to setup replay thread");
                    mTrackReplayThread = null;
                    mTrackReplayMode = false;
                } else {
                    borderPane.setBottom(bottomPane);
                    mTrackMode = false;
                    updateTrackMode();
                    mTrackReplayMode = true;
                    startReplayButton.setDisable(false);
                    stopReplayButton.setDisable(true);
                    pauseReplayButton.setDisable(true);
                    stepReplayButton.setDisable(true);
                    trackModeButton.setDisable(true);
                    resetTracking();
                }
            }
        });
        menuItem.setStyle("-fx-font-size: 20");
        mMenuButtonMenu.getItems().add(menuItem);
        menuItem = new MenuItem(" Clear track ");
        menuItem.setOnAction(ev -> {
            trackModeButton.setDisable(false);
            borderPane.setBottom(infoPane);
            stopReplay();
            resetTracking();
            mZRotate.setAngle(0);
            drawShapes();
        });
        menuItem.setStyle("-fx-font-size: 20");
        mMenuButtonMenu.getItems().add(menuItem);
    }

    RoutingNode getSelectedRoutingNode(Point2D nodePos) {
        for (RoutingNode node : mRoutingNodes) {
            if (node.contains(nodePos)) {
                return node;
            }
        }
        return null;
    }

    private void hideAllMenues() {
        if (mContextMenu.isShowing()) {
            mContextMenu.hide();
        }
        if (mMenuButtonMenu.isShowing()) {
            mMenuButtonMenu.hide();
        }
    }

    private void saveRoutingNodes() {
        JsonArray nodes = new JsonArray();
        for (RoutingNode node : mRoutingNodes) {
            nodes.add(node.toJson());
            LogUtils.log("saved node = " + node.toJson() + " edgeId = " + node.getEdgeId());
        }
        Config.getInstance().put("routingNodes", nodes);
        Config.getInstance().save();
    }

    private void restoreRoutingNodes() {
        JsonArray nodes = (JsonArray) Config.getInstance().get("routingNodes", new JsonArray());
        for (int i = 0; i < nodes.size(); i++) {
            RoutingNode node = new RoutingNode((JsonObject) nodes.get(i));
            mRoutingNodes.add(node);

            ResolvePositionTask task = new ResolvePositionTask(node.getCoordsPos(), node.getWayId());
            task.setOnSucceeded((succeededEvent) -> {
                LogUtils.log("restoreRoutingNodes: ResolvePositionTask succeededEvent");
                JsonObject edge = task.getResolvedEdge();
                if (edge != null) {
                    node.setEdgeId((long) edge.get("id"));
                    LogUtils.log("resolved node = " + node.toJson() + " edgeId = " + node.getEdgeId());
                }
            });
            mExecutorService.submit(task);
        }
    }

    private boolean hasRoutingStart() {
        return mRoutingNodes.stream().filter(routingNode -> routingNode.getType() == RoutingNode.TYPE.START).count() != 0;
    }

    private RoutingNode getRoutingStart() {
        return mRoutingNodes.stream().filter(routingNode -> routingNode.getType() == RoutingNode.TYPE.START).findFirst().get();
    }

    private boolean hasRoutingEnd() {
        return mRoutingNodes.stream().filter(routingNode -> routingNode.getType() == RoutingNode.TYPE.END).count() != 0;
    }

    private RoutingNode getRoutingEnd() {
        return mRoutingNodes.stream().filter(routingNode -> routingNode.getType() == RoutingNode.TYPE.END).findFirst().get();
    }

    private void createRoutePolylineList(List<Double> bbox) {
        mRoutePolylineList.clear();
        for (Long edgeId : mRouteEdgeIdList) {
            JsonObject edge = QueryController.getInstance().getEdgeEntryForIdInBBox(edgeId, bbox.get(0), bbox.get(1),
                    bbox.get(2), bbox.get(3));
            if (edge != null) {
                Polyline edgeShape = displayCoordsPolyline((JsonArray) edge.get("coords"));
                edgeShape.setStrokeWidth(2);
                edgeShape.setStroke(Color.RED);
                mRoutePolylineList.add(edgeShape);
            }
        }
    }

    private void startProgress() {
        mLoadMapProgress.setVisible(true);
    }

    private void stopProgress() {
        mLoadMapProgress.setVisible(false);
    }

    private void updateListContent() {
        if (mQueryTask != null) {
            mQueryTask.cancel(true);
            mQueryTask = null;
        }
        mQueryItems.clear();
        if (mQueryText != null && mQueryText.length() > 1) {
            mQueryTask = new QueryTaskPOI(mQueryText, mFilterType, mAdminId);
            mQueryTask.setOnRunning((runningEvent) -> {
                mQueryListProgress.setVisible(true);
            });
            mQueryTask.setOnSucceeded((succeededEvent) -> {
                LogUtils.log("QueryTaskPOI::succeeded " + mQueryTask.getValue().size());
                mQueryListProgress.setVisible(false);
                mQueryItems.addAll(mQueryTask.getValue());
            });
            mQueryTask.setOnCancelled((cancelEvent) -> {
                LogUtils.log("QueryTaskPOI::cancel");
                mQueryListProgress.setVisible(false);
            });
            mExecutorService.submit(mQueryTask);
        }
    }

    private void addToLocationHistory(JsonArray coords) {
        if (mLocationHistory.size() > 0) {
            if (mLocationHistory.get(mLocationHistory.size() - 1).equals(coords)) {
                return;
            }
        }
        if (mLocationHistory.size() == 10) {
            mLocationHistory.remove(0);
        }
        mLocationHistory.add(coords);
        mLocationHistoryIndex = mLocationHistory.size() - 1;
        //LogUtils.log("locationHistory = " + mLocationHistory);
        Config.getInstance().put("locationHistory", mLocationHistory);
        Config.getInstance().save();
    }

    private void goBackInLocationHistory() {
        if (mLocationHistoryIndex != 0) {
            mLocationHistoryIndex--;
            JsonArray coords = (JsonArray) mLocationHistory.get(mLocationHistoryIndex);
            centerMapOnLocation(GISUtils.getDoubleValue(coords.get(0)), GISUtils.getDoubleValue(coords.get(1)));
        }
    }

    private void goForwardInLocationHistory() {
        if (mLocationHistoryIndex < mLocationHistory.size() - 1) {
            mLocationHistoryIndex++;
            JsonArray coords = (JsonArray) mLocationHistory.get(mLocationHistoryIndex);
            centerMapOnLocation(GISUtils.getDoubleValue(coords.get(0)), GISUtils.getDoubleValue(coords.get(1)));
        }
    }

    private void showShapeAtCenter() {
        Point2D mapCenterPos = new Point2D(mCenterPosX, mCenterPosY);
        OSMShape s = findShapeAtPoint(mapCenterPos, OSMUtils.SELECT_AREA_TYPE);
        if (s != null) {
            doSelectShape(s);
        }
    }

    private void doSelectShape(OSMShape shape) {
        mSelectdOSMId = shape.getOSMId();
        JsonObject osmObject = mOSMObjects.get(mSelectdOSMId);
        if (osmObject != null) {
            JsonObject tags = (JsonObject) osmObject.get("tags");
            LogUtils.log("type = " + osmObject.get("type"));
            if (shape instanceof OSMPolyline) {
                // only show ways as selected not areas
                if (osmObject.get("type").equals("way")) {
                    mSelectdShape = shape;
                    mSelectdShape.setSelected();
                }
            }
            if (shape instanceof OSMPolygon) {
                if (shape.getAreaType() == OSMUtils.AREA_TYPE_BUILDING) {
                    mSelectdShape = shape;
                    mSelectdShape.setSelected();
                }
            }

            final StringBuffer s = new StringBuffer();
            s.append(shape.getInfoLabel(tags));

            if (shape instanceof OSMPolyline) {
                LogUtils.log("Polyline: " + mSelectdOSMId + "  " + tags);
            } else if (shape instanceof OSMPolygon) {
                LogUtils.log("Polygon: " + mSelectdOSMId + "  " + tags);
            } else if (shape instanceof OSMImageView) {
                LogUtils.log("Node: " + mSelectdOSMId + " " + tags);
            }
            infoLabel.setText(s.toString().trim());
            drawShapes();
        }
    }

    private String getAdminAreaStringAtPos(Point2D coordPos) {
        JsonArray adminDataList = getAdminAreaListAtPos(coordPos);
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < adminDataList.size(); i++) {
            JsonObject adminData = (JsonObject) adminDataList.get(i);
            String name = (String) adminData.get("name");
            s.append(name);
            if (i < adminDataList.size() - 1) {
                s.append("/");
            }
        }
        return s.toString();
    }

    private JsonArray getAdminAreaListAtPos(Point2D coordPos) {
        JsonArray adminDataList = QueryController.getInstance().getAdminAreasAtPointWithGeom(coordPos.getX(), coordPos.getY(),
                "(8)", this);
        JsonArray adminAreaList = new JsonArray();
        if (adminDataList.size() != 0) {
            JsonObject adminData = (JsonObject) adminDataList.get(0);
            JsonObject tags = (JsonObject) adminData.get("tags");
            if (tags != null) {
                adminAreaList.add(adminData);
                JsonArray parents = (JsonArray) tags.get("parents");
                if (parents != null) {
                    for (int j = 0; j < parents.size(); j++) {
                        JsonObject parentAdminArea = QueryController.getInstance().getAdminAreasWithId(GISUtils.getLongValue(parents.get(j)));
                        if (parentAdminArea != null) {
                            int parentAdminLevel = GISUtils.getIntValue(parentAdminArea.get("adminLevel"));
                            if (OSMUtils.mAdminLevelSet.contains(parentAdminLevel)) {
                                adminAreaList.add(parentAdminArea);
                            }
                        }
                    }
                }
            }
        }
        return adminAreaList;
    }

    private long getAdminIdAtPos(Point2D coordPos) {
        JsonArray adminDataList = QueryController.getInstance().getAdminAreasAtPointWithGeom(coordPos.getX(), coordPos.getY(),
                "(8)", this);
        if (adminDataList.size() != 0) {
            JsonObject adminData = (JsonObject) adminDataList.get(0);
            LogUtils.log("getAdminIdAtPos " + adminData.get("name"));
            return GISUtils.getLongValue(adminData.get("osmId"));
        }
        return 0;
    }


    private void saveCurrentMapPos() {
        Config.getInstance().put("zoom", mMapZoom);
        Config.getInstance().put("lon", mCenterLon);
        Config.getInstance().put("lat", mCenterLat);
        Config.getInstance().save();
    }
}

